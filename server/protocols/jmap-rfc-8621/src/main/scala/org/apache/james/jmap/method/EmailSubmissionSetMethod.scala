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

import cats.implicits._
import eu.timepit.refined.auto._
import eu.timepit.refined.refineV
import javax.annotation.PreDestroy
import javax.inject.Inject
import javax.mail.Address
import javax.mail.Message.RecipientType
import javax.mail.internet.{InternetAddress, MimeMessage}
import org.apache.james.core.{MailAddress, Username}
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, EMAIL_SUBMISSION, JMAP_CORE}
import org.apache.james.jmap.core.Id.IdConstraint
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.SetError.{SetErrorDescription, SetErrorType}
import org.apache.james.jmap.core.{ClientId, Id, Invocation, Properties, ServerId, SetError, State}
import org.apache.james.jmap.json.{EmailSubmissionSetSerializer, ResponseSerializer}
import org.apache.james.jmap.mail.EmailSubmissionSet.EmailSubmissionCreationId
import org.apache.james.jmap.mail.{EmailSubmissionAddress, EmailSubmissionCreationRequest, EmailSubmissionCreationResponse, EmailSubmissionId, EmailSubmissionSetRequest, EmailSubmissionSetResponse, Envelope}
import org.apache.james.jmap.method.EmailSubmissionSetMethod.{LOGGER, MAIL_METADATA_USERNAME_ATTRIBUTE}
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

import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

object EmailSubmissionSetMethod {
  val MAIL_METADATA_USERNAME_ATTRIBUTE: AttributeName = AttributeName.of("org.apache.james.jmap.send.MailMetaData.username")
  val LOGGER: Logger = LoggerFactory.getLogger(classOf[EmailSubmissionSetMethod])
  val noRecipients: SetErrorType = "noRecipients"
  val forbiddenFrom: SetErrorType = "forbiddenFrom"
  val forbiddenMailFrom: SetErrorType = "forbiddenMailFrom"
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
                                         val metricFactory: MetricFactory,
                                         val sessionSupplier: SessionSupplier) extends MethodRequiringAccountId[EmailSubmissionSetRequest] with Startable {
  override val methodName: MethodName = MethodName("EmailSubmission/set")
  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, EMAIL_SUBMISSION)
  var queue: MailQueue = _

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
      if (creationId.startsWith("#")) {
        val realId = creationId.substring(1)
        val validatedId: Either[String, EmailSubmissionCreationId] = refineV[IdConstraint](realId)
        validatedId
          .left.map(s => new IllegalArgumentException(s))
          .flatMap(id => retrieveMessageId(id)
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

  def init: Unit =
    queue = mailQueueFactory.createQueue(SPOOL)

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
              newState = State.INSTANCE,
              created = Some(createdResults._1.retrieveCreated).filter(_.nonEmpty),
              notCreated = Some(createdResults._1.retrieveErrors).filter(_.nonEmpty)))
              .as[JsObject]),
            methodCallId = invocation.invocation.methodCallId),
          processingContext = createdResults._2)


        val emailSetCall: SMono[InvocationWithContext] = request.implicitEmailSetRequest(createdResults._1.resolveMessageId)
          .fold(e => SMono.error(e),
            emailSetRequest => emailSetMethod.doProcess(
              capabilities = capabilities,
              invocation = invocation,
              mailboxSession = mailboxSession,
              request = emailSetRequest))

        SFlux.concat(SMono.just(explicitInvocation), emailSetCall)
      })

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[IllegalArgumentException, EmailSubmissionSetRequest] = {
    val maybeRequestRequest = serializer.deserializeEmailSubmissionSetRequest(invocation.arguments.value) match {
      case JsSuccess(emailSubmissionSetRequest, _) => Right(emailSubmissionSetRequest)
      case errors: JsError => Left(new IllegalArgumentException(ResponseSerializer.serialize(errors).toString))
    }
    maybeRequestRequest.flatMap(_.validate)
  }

  private def create(request: EmailSubmissionSetRequest,
                     session: MailboxSession,
                     processingContext: ProcessingContext): SMono[(CreationResults, ProcessingContext)] =
    SFlux.fromIterable(request.create
      .getOrElse(Map.empty)
      .view)
      .fold((CreationResults(Nil), processingContext)) {
        (acc : (CreationResults, ProcessingContext), elem: (EmailSubmissionCreationId, JsObject)) => {
          val (emailSubmissionCreationId, jsObject) = elem
          val (creationResult, updatedProcessingContext) = createSubmission(session, emailSubmissionCreationId, jsObject, acc._2)
          (CreationResults(acc._1.created :+ creationResult), updatedProcessingContext)
        }
      }
      .subscribeOn(Schedulers.elastic())

  private def createSubmission(mailboxSession: MailboxSession,
                            emailSubmissionCreationId: EmailSubmissionCreationId,
                            jsObject: JsObject,
                            processingContext: ProcessingContext): (CreationResult, ProcessingContext) =
    parseCreate(jsObject)
      .flatMap(emailSubmissionCreationRequest => sendEmail(mailboxSession, emailSubmissionCreationRequest))
      .flatMap {
        case (creationResponse, messageId) =>
          recordCreationIdInProcessingContext(emailSubmissionCreationId, processingContext, creationResponse.id)
            .map(context => (creationResponse, messageId, context))
      }
      .fold(e => (CreationFailure(emailSubmissionCreationId, e), processingContext),
        creation => CreationSuccess(emailSubmissionCreationId, creation._1, creation._2) -> creation._3)

  private def parseCreate(jsObject: JsObject): Either[EmailSubmissionCreationParseException, EmailSubmissionCreationRequest] =
    EmailSubmissionCreationRequest.validateProperties(jsObject)
      .flatMap(validJsObject => Json.fromJson(validJsObject)(serializer.emailSubmissionCreationRequestReads) match {
        case JsSuccess(creationRequest, _) => Right(creationRequest)
        case JsError(errors) => Left(EmailSubmissionCreationParseException(emailSubmissionSetError(errors)))
      })

  private def emailSubmissionSetError(errors: collection.Seq[(JsPath, collection.Seq[JsonValidationError])]): SetError =
    errors.head match {
      case (path, Seq()) => SetError.invalidArguments(SetErrorDescription(s"'$path' property in EmailSubmission object is not valid"))
      case (path, Seq(JsonValidationError(Seq("error.path.missing")))) => SetError.invalidArguments(SetErrorDescription(s"Missing '$path' property in EmailSubmission object"))
      case (path, Seq(JsonValidationError(Seq(message)))) => SetError.invalidArguments(SetErrorDescription(s"'$path' property in EmailSubmission object is not valid: $message"))
      case (path, _) => SetError.invalidArguments(SetErrorDescription(s"Unknown error on property '$path'"))
    }

  private def sendEmail(mailboxSession: MailboxSession,
                        request: EmailSubmissionCreationRequest): Either[Throwable, (EmailSubmissionCreationResponse, MessageId)] = {
    val message: Either[Exception, MessageResult] = messageIdManager.getMessage(request.emailId, FetchGroup.FULL_CONTENT, mailboxSession)
      .asScala
      .toList
      .headOption
      .toRight(MessageNotFoundException(request.emailId))

    message.flatMap(m => {
      val submissionId = EmailSubmissionId.generate

      val result: Try[EmailSubmissionCreationResponse] = for {
        message <- toMimeMessage(submissionId.value, m)
        envelope <- resolveEnvelope(message, request.envelope)
        validation <- validate(mailboxSession)(message, envelope)
        mail <- Try({
          val mailImpl = MailImpl.builder()
            .name(submissionId.value)
            .addRecipients(envelope.rcptTo.map(_.email).asJava)
            .sender(envelope.mailFrom.email)
            .addAttribute(new Attribute(MAIL_METADATA_USERNAME_ATTRIBUTE, AttributeValue.of(mailboxSession.getUser.asString())))
            .build()
          mailImpl.setMessageNoCopy(message)
          mailImpl
        })
        enqueue <- Try(queue.enQueue(mail))
      } yield {
        EmailSubmissionCreationResponse(submissionId)
      }
      result.toEither.map(response => (response, request.emailId))
    })
  }

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

  private def validate(session: MailboxSession)(mimeMessage: MimeMessage, envelope: Envelope): Try[MimeMessage] = {
    val forbiddenMailFrom: List[String] = (Option(mimeMessage.getSender).toList ++ Option(mimeMessage.getFrom).toList.flatten)
      .map(_.asInstanceOf[InternetAddress].getAddress)
      .filter(addressAsString => !canSendFrom.userCanSendFrom(session.getUser, Username.fromMailAddress(new MailAddress(addressAsString))))

    if (forbiddenMailFrom.nonEmpty) {
      Failure(ForbiddenMailFromException(forbiddenMailFrom))
    } else if (envelope.rcptTo.isEmpty) {
      Failure(NoRecipientException())
    } else if (!canSendFrom.userCanSendFrom(session.getUser, Username.fromMailAddress(envelope.mailFrom.email))) {
      Failure(ForbiddenFromException(envelope.mailFrom.email.asString))
    } else {
      Success(mimeMessage)
    }
  }

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
        .map(EmailSubmissionAddress)
      rcptTo <- (to ++ cc ++ bcc)
        .map(_.asInstanceOf[InternetAddress].getAddress)
        .map(s => Try(new MailAddress(s)))
        .sequence
    } yield {
      Envelope(mailFrom, rcptTo.map(EmailSubmissionAddress))
    }
  }

  private def recordCreationIdInProcessingContext(emailSubmissionCreationId: EmailSubmissionCreationId,
                                                  processingContext: ProcessingContext,
                                                  emailSubmissionId: EmailSubmissionId): Either[IllegalArgumentException, ProcessingContext] =
    for {
      creationId <- Id.validate(emailSubmissionCreationId)
      serverAssignedId <- Id.validate(emailSubmissionId.value)
    } yield {
      processingContext.recordCreatedId(ClientId(creationId), ServerId(serverAssignedId))
    }
}
