/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.jmap.method

import java.io.InputStream
import java.time.format.{DateTimeFormatter, DateTimeParseException}
import java.time.{Clock, Duration, LocalDateTime, ZoneId, ZonedDateTime}

import cats.implicits.toTraverseOps
import eu.timepit.refined.auto._
import eu.timepit.refined.refineV
import javax.annotation.PreDestroy
import javax.inject.Inject
import javax.mail.Address
import javax.mail.Message.RecipientType
import javax.mail.internet.{InternetAddress, MimeMessage}
import org.apache.james.core.{MailAddress, Username}
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, EMAIL_SUBMISSION, JMAP_CORE}
import org.apache.james.jmap.core.Id.{Id, IdConstraint}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.SetError.{SetErrorDescription, SetErrorType}
import org.apache.james.jmap.core.{ClientId, Invocation, Properties, ServerId, SessionTranslator, SetError, SubmissionCapabilityFactory, UTCDate, UuidState}
import org.apache.james.jmap.json.EmailSubmissionSetSerializer
import org.apache.james.jmap.mail.{EmailSubmissionAddress, EmailSubmissionCreationId, EmailSubmissionCreationRequest, EmailSubmissionCreationResponse, EmailSubmissionId, EmailSubmissionSetRequest, EmailSubmissionSetResponse, Envelope, ParameterName, ParameterValue}
import org.apache.james.jmap.method.EmailSubmissionSetMethod.{CreationFailure, CreationResult, CreationResults, CreationSuccess, LOGGER, MAIL_METADATA_USERNAME_ATTRIBUTE, NO_DELAY, VALID_PARAMETER_NAME_SET, formatter}
import org.apache.james.jmap.routes.{ProcessingContext, SessionSupplier}
import org.apache.james.lifecycle.api.{LifecycleUtil, Startable}
import org.apache.james.mailbox.model.{FetchGroup, MessageId, MessageResult}
import org.apache.james.mailbox.{MailboxSession, MessageIdManager}
import org.apache.james.metrics.api.MetricFactory
import org.apache.james.queue.api.MailQueueFactory.SPOOL
import org.apache.james.queue.api.{MailQueue, MailQueueFactory}
import org.apache.james.rrt.api.CanSendFrom
import org.apache.james.server.core.{MailImpl, MimeMessageSource, MimeMessageWrapper}
import org.apache.mailet.{Attribute, AttributeName, AttributeValue}
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json._
import reactor.core.scala.publisher.{SFlux, SMono}
import reactor.core.scheduler.Schedulers
import reactor.util.concurrent.Queues

import scala.jdk.CollectionConverters._
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

object EmailSubmissionSetMethod {
  val MAIL_METADATA_USERNAME_ATTRIBUTE: AttributeName = AttributeName.of("org.apache.james.jmap.send.MailMetaData.username")
  val LOGGER: Logger = LoggerFactory.getLogger(classOf[EmailSubmissionSetMethod])
  val noRecipients: SetErrorType = "noRecipients"
  val forbiddenFrom: SetErrorType = "forbiddenFrom"
  val forbiddenMailFrom: SetErrorType = "forbiddenMailFrom"
  val formatter = DateTimeFormatter.ISO_INSTANT.withZone(ZoneId.of("Z"))
  val VALID_PARAMETER_NAME_SET: Set[ParameterName] = Set(ParameterName.holdFor, ParameterName.holdUntil)
  val NO_DELAY: Duration = Duration.ZERO

  sealed trait CreationResult {
    def emailSubmissionCreationId: EmailSubmissionCreationId
  }
  case class CreationSuccess(emailSubmissionCreationId: EmailSubmissionCreationId,
                             emailSubmissionCreationResponse: EmailSubmissionCreationResponse,
                             messageId: MessageId) extends CreationResult
  case class CreationFailure(emailSubmissionCreationId: EmailSubmissionCreationId, exception: Throwable) extends CreationResult {
    def asSetError: SetError = exception match {
      case e: EmailSubmissionCreationParseException => e.setError
      case _: NoRecipientException => SetError(EmailSubmissionSetMethod.noRecipients,
        SetErrorDescription("Attempt to send a mail with no recipients"), None)
      case e: ForbiddenMailFromException => SetError(EmailSubmissionSetMethod.forbiddenMailFrom,
        SetErrorDescription(s"Attempt to send a mail whose MimeMessage From and Sender fields not allowed for connected user: ${e.from}"), None)
      case e: ForbiddenFromException => SetError(EmailSubmissionSetMethod.forbiddenFrom,
        SetErrorDescription(s"Attempt to send a mail whose envelope From not allowed for connected user: ${e.from}"),
        Some(Properties("envelope.mailFrom")))
      case _: MessageNotFoundException => SetError(SetError.invalidArgumentValue,
        SetErrorDescription("The email to be sent cannot be found"),
        Some(Properties("emailId")))
      case e: DateTimeParseException => SetError.invalidArguments(SetErrorDescription(e.getMessage))
      case e: IllegalArgumentException => SetError.invalidArguments(SetErrorDescription(e.getMessage))
      case e: Exception =>
        e.printStackTrace()
        SetError.serverFail(SetErrorDescription(exception.getMessage))
    }
  }
  case class CreationResults(created: Seq[CreationResult]) {
    def retrieveCreated: Map[EmailSubmissionCreationId, EmailSubmissionCreationResponse] = created
      .flatMap {
        case success: CreationSuccess => Some(success.emailSubmissionCreationId, success.emailSubmissionCreationResponse)
        case _ => None
      }
      .toMap
      .map(creation => (creation._1, creation._2))


    def retrieveErrors: Map[EmailSubmissionCreationId, SetError] = created
      .flatMap {
        case failure: CreationFailure => Some(failure.emailSubmissionCreationId, failure.asSetError)
        case _ => None
      }
      .toMap

    def resolveMessageId(creationId: EmailSubmissionCreationId): Either[IllegalArgumentException, MessageId] = {
      if (creationId.id.startsWith("#")) {
        val realId = creationId.id.substring(1)
        val validatedId: Either[String, Id] = refineV[IdConstraint](realId)
        validatedId
          .left.map(s => new IllegalArgumentException(s))
          .flatMap(id => retrieveMessageId(EmailSubmissionCreationId(id))
            .map(scala.Right(_))
            .getOrElse(Left(new IllegalArgumentException(s"$creationId cannot be referenced in current method call"))))
      } else {
        Left(new IllegalArgumentException(s"$creationId cannot be retrieved as storage for EmailSubmission is not yet implemented"))
      }
    }

    private def retrieveMessageId(creationId: EmailSubmissionCreationId): Option[MessageId] =
      created.flatMap {
        case success: CreationSuccess => Some(success)
        case _: CreationFailure => None
      }.filter(_.emailSubmissionCreationId.equals(creationId))
        .map(_.messageId)
        .toList
        .headOption
  }
}

case class EmailSubmissionCreationParseException(setError: SetError) extends Exception
case class NoRecipientException() extends Exception
case class ForbiddenFromException(from: String) extends Exception
case class ForbiddenMailFromException(from: List[String]) extends Exception

case class MessageMimeMessageSource(id: String, message: MessageResult) extends MimeMessageSource {
  override def getSourceId: String = id

  override def getInputStream: InputStream = message.getFullContent.getInputStream

  override def getMessageSize: Long = message.getFullContent.size()
}

class EmailSubmissionSetMethod @Inject()(serializer: EmailSubmissionSetSerializer,
                                         messageIdManager: MessageIdManager,
                                         mailQueueFactory: MailQueueFactory[_ <: MailQueue],
                                         canSendFrom: CanSendFrom,
                                         emailSetMethod: EmailSetMethod,
                                         clock: Clock,
                                         val metricFactory: MetricFactory,
                                         val sessionSupplier: SessionSupplier,
                                         val sessionTranslator: SessionTranslator) extends MethodRequiringAccountId[EmailSubmissionSetRequest] with Startable {
  override val methodName: MethodName = MethodName("EmailSubmission/set")
  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, EMAIL_SUBMISSION)
  var queue: MailQueue = _
  def init: Unit = queue = mailQueueFactory.createQueue(SPOOL)

  @PreDestroy def dispose: Unit =
    Try(queue.close())
      .recover(e => LOGGER.debug("error closing queue", e))

  override def doProcess(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession, request: EmailSubmissionSetRequest): SFlux[InvocationWithContext] =
    create(request, mailboxSession, invocation.processingContext)
      .flatMapMany(createdResults => {
        val explicitInvocation: InvocationWithContext = InvocationWithContext(
          invocation = Invocation(
            methodName = invocation.invocation.methodName,
            arguments = Arguments(serializer.serializeEmailSubmissionSetResponse(EmailSubmissionSetResponse(
              accountId = request.accountId,
              newState = UuidState.INSTANCE,
              created = Some(createdResults._1.retrieveCreated).filter(_.nonEmpty),
              notCreated = Some(createdResults._1.retrieveErrors).filter(_.nonEmpty)))
              .as[JsObject]),
            methodCallId = invocation.invocation.methodCallId),
          processingContext = createdResults._2)
        val emailSetCall: SMono[InvocationWithContext] = request.implicitEmailSetRequest(createdResults._1.resolveMessageId)
          .fold(e => SMono.error(e),
            maybeEmailSetRequest => maybeEmailSetRequest.map(emailSetRequest =>
                emailSetMethod.doProcess(
                  capabilities = capabilities,
                  invocation = invocation,
                  mailboxSession = mailboxSession,
                  request = emailSetRequest))
              .getOrElse(SMono.empty))

        SFlux.concat(SMono.just(explicitInvocation), emailSetCall)
      })

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[IllegalArgumentException, EmailSubmissionSetRequest] =
    serializer.deserializeEmailSubmissionSetRequest(invocation.arguments.value)
      .asEitherRequest
      .flatMap(_.validate)

  private def create(request: EmailSubmissionSetRequest,
                     session: MailboxSession,
                     processingContext: ProcessingContext): SMono[(CreationResults, ProcessingContext)] =
    SFlux.fromIterable(
      request.create
        .getOrElse(Map.empty)
        .view)
      .fold[SMono[(CreationResults, ProcessingContext)]](SMono.just((CreationResults(Nil), processingContext))) {
        (acc: SMono[(CreationResults, ProcessingContext)], elem: (EmailSubmissionCreationId, JsObject)) => {
          val (emailSubmissionCreationId, jsObject) = elem
          acc.flatMap {
            case (creationResults, processingContext) =>
              createSubmission(session, emailSubmissionCreationId, jsObject, processingContext)
                .map {
                  case (created, updatedProcessingContext) => CreationResults(creationResults.created :+ created) -> updatedProcessingContext
                }
              .switchIfEmpty(SMono.error(new RuntimeException("I should not be empty")))
          }.cache()
        }
      }
      .flatMap(x => x)

  private def createSubmission(mailboxSession: MailboxSession,
                            emailSubmissionCreationId: EmailSubmissionCreationId,
                            jsObject: JsObject,
                            processingContext: ProcessingContext): SMono[(CreationResult, ProcessingContext)] =
    parseCreate(jsObject)
      .fold(e => SMono.error(e), sendEmail(mailboxSession, _))
      .map {
        case (creationResponse, messageId) =>
          CreationSuccess(emailSubmissionCreationId, creationResponse, messageId) ->
            recordCreationIdInProcessingContext(emailSubmissionCreationId, processingContext, creationResponse.id)
      }
      .onErrorResume(e => SMono.just((CreationFailure(emailSubmissionCreationId, e), processingContext)))

  private def parseCreate(jsObject: JsObject): Either[EmailSubmissionCreationParseException, EmailSubmissionCreationRequest] =
    EmailSubmissionCreationRequest.validateProperties(jsObject)
      .flatMap(validJsObject => Json.fromJson(validJsObject)(serializer.emailSubmissionCreationRequestReads) match {
        case JsSuccess(creationRequest, _) => Right(creationRequest)
        case JsError(errors) => Left(EmailSubmissionCreationParseException(emailSubmissionSetError(errors)))
      })

  private def emailSubmissionSetError(errors: collection.Seq[(JsPath, collection.Seq[JsonValidationError])]): SetError =
    standardError(errors)

  private def sendEmail(mailboxSession: MailboxSession,
                        request: EmailSubmissionCreationRequest): SMono[(EmailSubmissionCreationResponse, MessageId)] =
    for {
      message <- SFlux(messageIdManager.getMessagesReactive(List(request.emailId).asJava, FetchGroup.FULL_CONTENT, mailboxSession))
        .next
        .switchIfEmpty(SMono.error(MessageNotFoundException(request.emailId)))
      submissionId = EmailSubmissionId.generate
      message <- SMono.fromTry(toMimeMessage(submissionId.value, message))
      envelope <- SMono.fromTry(resolveEnvelope(message, request.envelope))
      _ <- validate(mailboxSession)(message, envelope)
      _ <- SMono.fromTry(validateFromParameters(envelope.mailFrom.parameters))
      delay <- SMono.fromTry(retrieveDelay(envelope.mailFrom.parameters))
      _ <- SMono.fromTry(validateDelay(delay))
      _ <- validateRcptTo(envelope.rcptTo)

      mail = {
        val mailImpl = MailImpl.builder()
          .name(submissionId.value)
          .addRecipients(envelope.rcptTo.map(_.email).asJava)
          .sender(envelope.mailFrom.email)
          .addAttribute(new Attribute(MAIL_METADATA_USERNAME_ATTRIBUTE, AttributeValue.of(mailboxSession.getUser.asString())))
          .build()
        mailImpl.setMessageNoCopy(message)
        mailImpl
      }

      _ <- SMono(queue.enqueueReactive(mail, delay))
        .`then`(SMono.fromCallable(() => LifecycleUtil.dispose(mail)).subscribeOn(Schedulers.boundedElastic()))
        .`then`(SMono.just(submissionId))
      sendAt = UTCDate(ZonedDateTime.now(clock).plus(delay))
    } yield {
      EmailSubmissionCreationResponse(submissionId, sendAt) -> request.emailId
    }

  private def retrieveDelay(mailParameters: Option[Map[ParameterName, Option[ParameterValue]]]): Try[Duration] =
    mailParameters match {
      case None => Success(NO_DELAY)
      case Some(aMap) if aMap.contains(ParameterName.holdFor) =>
        aMap(ParameterName.holdFor).map(paramValue => Try(Duration.ofSeconds(paramValue.value.toLong)))
          .getOrElse(Success(NO_DELAY))
      case Some(aMap) if aMap.contains(ParameterName.holdUntil) =>
        aMap(ParameterName.holdUntil).map(paramValue => Try(Duration.between(LocalDateTime.now(clock), LocalDateTime.parse(paramValue.value, formatter))))
          .getOrElse(Success(NO_DELAY))
      case _ => Success(NO_DELAY)
    }


  def validateDelay(delay: Duration): Try[Duration] =
    if (delay.getSeconds >= 0 && delay.getSeconds <= SubmissionCapabilityFactory.maximumDelays.getSeconds) {
      Success(delay)
    } else {
      Failure(new IllegalArgumentException("Invalid delayed time!"))
    }

  def validateRcptTo(recipients: List[EmailSubmissionAddress]): SMono[List[EmailSubmissionAddress]] =
    SFlux.fromIterable(recipients)
      .filter(validateRecipient)
      .collectSeq()
      .flatMap(recipientsList => {
        if (recipientsList.length != recipients.length) {
          SMono.just(Failure(new IllegalArgumentException("Some recipients have invalid delay parameters")))
        } else {
          SMono.just(Success(recipientsList.toList))
        }
      }).handle[List[EmailSubmissionAddress]]((aTry, sink) => {
        aTry match {
          case Success(recipient) => sink.next(recipient)
          case Failure(ex) => sink.error(ex)
        }
      })

  private def validateRecipient(recipient: EmailSubmissionAddress): Boolean =
    recipient.parameters.isEmpty || !(recipient.parameters.get.contains(ParameterName.holdFor) || recipient.parameters.get.contains(ParameterName.holdUntil))

  def validateFromParameters(mailParameters: Option[Map[ParameterName, Option[ParameterValue]]]): Try[Option[Map[ParameterName, Option[ParameterValue]]]] = {
    val keySet: Set[ParameterName] = mailParameters.getOrElse(Map()).keySet
    val invalidEntries = keySet -- VALID_PARAMETER_NAME_SET
    if (invalidEntries.isEmpty) {
      if (invalidFutureReleaseParameter(keySet)) {
        Failure(new IllegalArgumentException("Can't specify holdFor and holdUntil simultaneously"))
      } else {
        Success(mailParameters)
      }
    } else {
      Failure(new IllegalArgumentException("Unsupported parameterName"))
    }
  }

  private def invalidFutureReleaseParameter(keySet: Set[ParameterName]) =
    keySet.contains(ParameterName.holdFor) && keySet.contains(ParameterName.holdUntil)

  private def toMimeMessage(name: String, message: MessageResult): Try[MimeMessageWrapper] = {
    val source = MessageMimeMessageSource(name, message)
    // if MimeMessageCopyOnWriteProxy throws an error in the constructor we
    // have to manually care disposing our source.
    Try(new MimeMessageWrapper(source))
      .recover(e => {
        LifecycleUtil.dispose(source)
        throw e
      })
  }

  private def validate(session: MailboxSession)(mimeMessage: MimeMessage, envelope: Envelope): SMono[MimeMessage] =
    SFlux.fromIterable(Option(mimeMessage.getSender).toList ++ Option(mimeMessage.getFrom).toList.flatten)
      .map(_.asInstanceOf[InternetAddress].getAddress)
      .filterWhen(addressAsString => SMono.fromPublisher(canSendFrom.userCanSendFromReactive(session.getUser, Username.fromMailAddress(new MailAddress(addressAsString))))
        .map(Boolean.unbox(_)).map(!_), Queues.SMALL_BUFFER_SIZE)
      .collectSeq()
      .flatMap(forbiddenMailFrom => {
        if (forbiddenMailFrom.nonEmpty) {
          SMono.just(Failure(ForbiddenMailFromException(forbiddenMailFrom.toList)))
        } else if (envelope.rcptTo.isEmpty) {
          SMono.just(Failure(NoRecipientException()))
        } else {
          SMono.fromPublisher(canSendFrom.userCanSendFromReactive(session.getUser, Username.fromMailAddress(envelope.mailFrom.email)))
            .filter(bool => bool.equals(false))
            .map(_ => Failure(ForbiddenFromException(envelope.mailFrom.email.asString)))
            .switchIfEmpty(SMono.just(Success(mimeMessage)))
        }
      })
      .handle[MimeMessage]((aTry, sink) => {
        aTry match {
          case Success(mimeMessage) => sink.next(mimeMessage)
          case Failure(ex) => sink.error(ex)
        }
      })

  private def resolveEnvelope(mimeMessage: MimeMessage, maybeEnvelope: Option[Envelope]): Try[Envelope] =
    maybeEnvelope.map(Success(_)).getOrElse(extractEnvelope(mimeMessage))

  private def extractEnvelope(mimeMessage: MimeMessage): Try[Envelope] = {
    val to: List[Address] = Option(mimeMessage.getRecipients(RecipientType.TO)).toList.flatten
    val cc: List[Address] = Option(mimeMessage.getRecipients(RecipientType.CC)).toList.flatten
    val bcc: List[Address] = Option(mimeMessage.getRecipients(RecipientType.BCC)).toList.flatten
    for {
      mailFrom <- Option(mimeMessage.getFrom).toList.flatten
        .headOption
        .map(_.asInstanceOf[InternetAddress].getAddress)
        .map(s => Try(new MailAddress(s)))
        .getOrElse(Failure(new IllegalArgumentException("Implicit envelope detection requires a from field")))
        .map(EmailSubmissionAddress(_))
      rcptTo <- (to ++ cc ++ bcc)
        .map(_.asInstanceOf[InternetAddress].getAddress)
        .map(s => Try(new MailAddress(s)))
        .sequence
    } yield {
      Envelope(mailFrom, rcptTo.map(EmailSubmissionAddress(_)))
    }
  }

  private def recordCreationIdInProcessingContext(emailSubmissionCreationId: EmailSubmissionCreationId,
                                                  processingContext: ProcessingContext,
                                                  emailSubmissionId: EmailSubmissionId): ProcessingContext =
    processingContext.recordCreatedId(ClientId(emailSubmissionCreationId.id), ServerId(emailSubmissionId.value))
}
