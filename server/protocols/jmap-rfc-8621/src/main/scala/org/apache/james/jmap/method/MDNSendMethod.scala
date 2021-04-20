/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *  http://www.apache.org/licenses/LICENSE-2.0                  *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.jmap.method

import eu.timepit.refined.auto._
import org.apache.james.core.MailAddress
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE, JMAP_MAIL, JMAP_MDN}
import org.apache.james.jmap.core.Invocation
import org.apache.james.jmap.core.Invocation._
import org.apache.james.jmap.json.{MDNSerializer, ResponseSerializer}
import org.apache.james.jmap.mail.MDN._
import org.apache.james.jmap.mail.MDNSend.MDN_ALREADY_SENT_FLAG
import org.apache.james.jmap.mail._
import org.apache.james.jmap.method.EmailSubmissionSetMethod.LOGGER
import org.apache.james.jmap.routes.{ProcessingContext, SessionSupplier}
import org.apache.james.lifecycle.api.Startable
import org.apache.james.mailbox.model.{FetchGroup, MessageResult}
import org.apache.james.mailbox.{MailboxSession, MessageIdManager}
import org.apache.james.mdn.fields.{ExtensionField, FinalRecipient, Text}
import org.apache.james.mdn.{MDN, MDNReport}
import org.apache.james.metrics.api.MetricFactory
import org.apache.james.mime4j.codec.DecodeMonitor
import org.apache.james.mime4j.dom.Message
import org.apache.james.mime4j.field.AddressListFieldLenientImpl
import org.apache.james.mime4j.message.DefaultMessageBuilder
import org.apache.james.mime4j.stream.MimeConfig
import org.apache.james.queue.api.MailQueueFactory.SPOOL
import org.apache.james.queue.api.{MailQueue, MailQueueFactory}
import org.apache.james.server.core.MailImpl
import play.api.libs.json.{JsError, JsObject, JsSuccess, Json}
import reactor.core.scala.publisher.{SFlux, SMono}
import reactor.core.scheduler.Schedulers

import javax.annotation.PreDestroy
import javax.inject.Inject
import javax.mail.internet.MimeMessage
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._
import scala.util.Try

class MDNSendMethod @Inject()(serializer: MDNSerializer,
                              mailQueueFactory: MailQueueFactory[_ <: MailQueue],
                              messageIdManager: MessageIdManager,
                              emailSetMethod: EmailSetMethod,
                              val identifyResolver: IdentifyResolver,
                              val metricFactory: MetricFactory,
                              val sessionSupplier: SessionSupplier) extends MethodRequiringAccountId[MDNSendRequest] with Startable {
  override val methodName: MethodName = MethodName("MDN/send")
  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_MDN, JMAP_MAIL, JMAP_CORE)
  var queue: MailQueue = _

  def init: Unit =
    queue = mailQueueFactory.createQueue(SPOOL)

  @PreDestroy def dispose: Unit =
    Try(queue.close())
      .recover(e => LOGGER.debug("error closing queue", e))

  override def doProcess(capabilities: Set[CapabilityIdentifier],
                         invocation: InvocationWithContext,
                         mailboxSession: MailboxSession,
                         request: MDNSendRequest): SFlux[InvocationWithContext] = {
    identifyResolver.resolveIdentityId(request.identityId, mailboxSession)
      .flatMap(maybeIdentity => if (maybeIdentity.isEmpty) {
        SMono.raiseError(IdentityIdNotFoundException("The IdentityId cannot be found"))
      } else {
        create(maybeIdentity.get, request, mailboxSession, invocation.processingContext)
      })
      .flatMapMany(createdResults => {
        val explicitInvocation: InvocationWithContext = InvocationWithContext(
          invocation = Invocation(
            methodName = invocation.invocation.methodName,
            arguments = Arguments(serializer.serializeMDNSendResponse(createdResults._1.asResponse(request.accountId))
              .as[JsObject]),
            methodCallId = invocation.invocation.methodCallId),
          processingContext = createdResults._2)

        val emailSetCall: SMono[InvocationWithContext] = request.implicitEmailSetRequest(createdResults._1.resolveMessageId)
          .fold(e => SMono.error(e),
            maybeEmailSetRequest => maybeEmailSetRequest.map(emailSetRequest => emailSetMethod.doProcess(
              capabilities = capabilities,
              invocation = invocation,
              mailboxSession = mailboxSession,
              request = emailSetRequest))
              .getOrElse(SMono.empty))

        SFlux.concat(SMono.just(explicitInvocation), emailSetCall)
      })
  }

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[Exception, MDNSendRequest] =
    serializer.deserializeMDNSendRequest(invocation.arguments.value) match {
      case JsSuccess(mdnSendRequest, _) => mdnSendRequest.validate
      case errors: JsError => Left(new IllegalArgumentException(Json.stringify(ResponseSerializer.serialize(errors))))
    }

  private def create(identity: Identity,
                     request: MDNSendRequest,
                     session: MailboxSession,
                     processingContext: ProcessingContext): SMono[(MDNSendResults, ProcessingContext)] =
    SFlux.fromIterable(request.send.view)
      .fold(MDNSendResults.empty -> processingContext) {
        (acc: (MDNSendResults, ProcessingContext), elem: (MDNSendCreationId, JsObject)) => {
          val (mdnSendId, jsObject) = elem
          val (creationResult, updatedProcessingContext) = createMDNSend(session, identity, mdnSendId, jsObject, acc._2)
          (MDNSendResults.merge(acc._1, creationResult) -> updatedProcessingContext)
        }
      }
      .subscribeOn(Schedulers.elastic())

  private def createMDNSend(session: MailboxSession,
                            identity: Identity,
                            mdnSendCreationId: MDNSendCreationId,
                            jsObject: JsObject,
                            processingContext: ProcessingContext): (MDNSendResults, ProcessingContext) =
    parseMDNRequest(jsObject)
      .flatMap(createRequest => sendMDN(session, identity, mdnSendCreationId, createRequest))
      .fold(error => (MDNSendResults.notSent(mdnSendCreationId, error) -> processingContext),
        creation => MDNSendResults.sent(creation) -> processingContext)

  private def parseMDNRequest(jsObject: JsObject): Either[MDNSendRequestInvalidException, MDNSendCreateRequest] =
    MDNSendCreateRequest.validateProperties(jsObject)
      .flatMap(validJson => serializer.deserializeMDNSendCreateRequest(validJson) match {
        case JsSuccess(createRequest, _) => createRequest.validate
        case JsError(errors) => Left(MDNSendRequestInvalidException.parse(errors))
      })

  private def sendMDN(session: MailboxSession,
                      identity: Identity,
                      mdnSendCreationId: MDNSendCreationId,
                      requestEntry: MDNSendCreateRequest): Either[Throwable, MDNSendCreateSuccess] =
    for {
      mdnRelatedMessageResult <- retrieveRelatedMessageResult(session, requestEntry)
      mdnRelatedMessageResultAlready <- validateMDNNotAlreadySent(mdnRelatedMessageResult)
      messageRelated = getOriginalMessage(mdnRelatedMessageResultAlready)
      mailAndResponseAndId <- buildMailAndResponse(identity, session.getUser.asString(), requestEntry, messageRelated)
      _ <- Try(queue.enQueue(mailAndResponseAndId._1)).toEither
    } yield {
      MDNSendCreateSuccess(
        mdnCreationId = mdnSendCreationId,
        createResponse = mailAndResponseAndId._2,
        forEmailId = mdnRelatedMessageResultAlready.getMessageId)
    }

  private def retrieveRelatedMessageResult(session: MailboxSession, requestEntry: MDNSendCreateRequest): Either[MDNSendNotFoundException, MessageResult] =
    messageIdManager.getMessage(requestEntry.forEmailId.originalMessageId, FetchGroup.FULL_CONTENT, session)
      .asScala
      .toList
      .headOption
      .toRight(MDNSendNotFoundException("The reference \"forEmailId\" cannot be found."))


  private def validateMDNNotAlreadySent(relatedMessageResult: MessageResult): Either[MDNSendAlreadySentException, MessageResult] =
    if (relatedMessageResult.getFlags.contains(MDN_ALREADY_SENT_FLAG)) {
      Left(MDNSendAlreadySentException())
    } else {
      scala.Right(relatedMessageResult)
    }

  private def buildMailAndResponse(identity: Identity, sender: String, requestEntry: MDNSendCreateRequest, originalMessage: Message): Either[Exception, (MailImpl, MDNSendCreateResponse)] =
    for {
      mailRecipient <- getMailRecipient(originalMessage)
      mdnFinalRecipient <- getMDNFinalRecipient(requestEntry, identity)
      mdn = buildMDN(requestEntry, originalMessage, mdnFinalRecipient)
      subject = buildMessageSubject(requestEntry, originalMessage)
      (mailImpl, mimeMessage) = buildMailAndMimeMessage(sender, mailRecipient, subject, mdn)
    } yield {
      (mailImpl, buildMDNSendCreateResponse(requestEntry, mdn, mimeMessage))
    }

  private def buildMailAndMimeMessage(sender: String, recipient: String, subject: String, mdn: MDN): (MailImpl, MimeMessage) = {
    val mimeMessage: MimeMessage = mdn.asMimeMessage()
    mimeMessage.setFrom(sender)
    mimeMessage.setRecipients(javax.mail.Message.RecipientType.TO, recipient)
    mimeMessage.setSubject(subject)
    mimeMessage.saveChanges()

    val mailImpl: MailImpl = MailImpl.builder()
      .name(MDNId.generate.value)
      .sender(sender)
      .addRecipient(recipient)
      .mimeMessage(mimeMessage)
      .build()
    mailImpl -> mimeMessage
  }

  private def getMailRecipient(originalMessage: Message): Either[MDNSendNotFoundException, String] =
    originalMessage.getHeader.getFields(DISPOSITION_NOTIFICATION_TO)
      .asScala
      .headOption
      .map(field => AddressListFieldLenientImpl.PARSER.parse(field, new DecodeMonitor))
      .map(addressListField => addressListField.getAddressList)
      .map(addressList => addressList.flatten())
      .flatMap(mailboxList => mailboxList.stream().findAny().toScala)
      .map(mailbox => mailbox.getAddress)
      .toRight(MDNSendNotFoundException("Invalid \"Disposition-Notification-To\" header field."))

  private def getMDNFinalRecipient(requestEntry: MDNSendCreateRequest, identity: Identity): Either[MDNSendForbiddenFromException, FinalRecipient] =
    requestEntry.finalRecipient
      .map(finalRecipient => finalRecipient.getMailAddress)
      .map(mayBeMailAddress => (mayBeMailAddress.isSuccess && mayBeMailAddress.get.equals(identity.email)))
      .map {
        case true => scala.Right(requestEntry.finalRecipient.get.asJava.get)
        case false => Left(MDNSendForbiddenFromException("The user is not allowed to use the given \"finalRecipient\" property"))
      }
      .getOrElse(scala.Right(FinalRecipient.builder()
        .finalRecipient(Text.fromRawText(identity.email.asString()))
        .build()))

  private def buildMDN(requestEntry: MDNSendCreateRequest, originalMessage: Message, finalRecipient: FinalRecipient): MDN = {
    val reportBuilder: MDNReport.Builder = MDNReport.builder()
      .dispositionField(requestEntry.disposition.asJava.get)
      .finalRecipientField(finalRecipient)
      .originalRecipientField(originalMessage.getTo.asScala.head.toString)

    originalMessage.getHeader.getFields("Message-ID")
      .asScala
      .map(field => reportBuilder.originalMessageIdField(field.getBody))

    requestEntry.reportingUA
      .map(uaField => uaField.asJava
        .map(reportingUserAgent => reportBuilder.reportingUserAgentField(reportingUserAgent)))

    requestEntry.extensionFields.map(extensions => extensions
      .map(extension => reportBuilder.withExtensionField(
        ExtensionField.builder()
          .fieldName(extension._1.value)
          .rawValue(extension._2.value)
          .build())))

    originalMessage.getHeader.getFields(EmailHeaderName.MESSAGE_ID.value)
      .asScala
      .headOption
      .map(messageIdHeader => reportBuilder.originalMessageIdField(TextHeaderValue.from(messageIdHeader).value))

    MDN.builder()
      .report(reportBuilder.build())
      .humanReadableText(buildMDNHumanReadableText(requestEntry))
      .message(requestEntry.includeOriginalMessage
        .filter(isInclude => isInclude.value)
        .map(_ => originalMessage)
        .toJava)
      .build()
  }

  private def buildMDNHumanReadableText(requestEntry: MDNSendCreateRequest): String =
    requestEntry.textBody.map(textBody => textBody.value)
      .getOrElse(s"The email has been ${requestEntry.disposition.`type`} on your recipient's computer")

  private def buildMessageSubject(requestEntry: MDNSendCreateRequest, originalMessage: Message): String =
    requestEntry.subject
      .map(subject => subject.value)
      .getOrElse(s"""[Received] ${originalMessage.getSubject}""")

  private def buildMDNSendCreateResponse(requestEntry: MDNSendCreateRequest, mdn: MDN, mimeMessage: MimeMessage): MDNSendCreateResponse =
    MDNSendCreateResponse(
      subject = requestEntry.subject match {
        case Some(_) => None
        case None => Some(SubjectField(mimeMessage.getSubject))
      },
      textBody = requestEntry.textBody match {
        case Some(_) => None
        case None => Some(TextBodyField(mdn.getHumanReadableText))
      },
      reportingUA = requestEntry.reportingUA match {
        case Some(_) => None
        case None => mdn.getReport.getReportingUserAgentField
          .map(ua => ReportUAField(ua.fieldValue()))
          .toScala
      },
      mdnGateway = mdn.getReport.getGatewayField
        .map(gateway => MDNGatewayField(gateway.fieldValue()))
        .toScala,
      originalRecipient = mdn.getReport.getOriginalRecipientField
        .map(originalRecipient => OriginalRecipientField(originalRecipient.fieldValue()))
        .toScala,
      includeOriginalMessage = requestEntry.includeOriginalMessage match {
        case Some(_) => None
        case None => Some(IncludeOriginalMessageField(mdn.getOriginalMessage.isPresent))
      },
      error = Option(mdn.getReport.getErrorFields.asScala
        .map(error => ErrorField(error.getText.formatted()))
        .toSeq)
        .filter(error => error.nonEmpty),
      finalRecipient = requestEntry.finalRecipient match {
        case Some(_) => None
        case None => Some(FinalRecipientField(mdn.getReport.getFinalRecipientField.fieldValue()))
      },
      originalMessageId = mdn.getReport.getOriginalMessageIdField
        .map(originalMessageId => OriginalMessageIdField(originalMessageId.getOriginalMessageId))
        .toScala)

  private def getOriginalMessage(messageRelated: MessageResult): Message = {
    val messageBuilder: DefaultMessageBuilder = new DefaultMessageBuilder
    messageBuilder.setMimeEntityConfig(MimeConfig.PERMISSIVE)
    messageBuilder.setDecodeMonitor(DecodeMonitor.SILENT)
    messageBuilder.parseMessage(messageRelated.getFullContent.getInputStream)
  }
}
