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

package org.apache.james.jmap.mail

import java.nio.charset.StandardCharsets.US_ASCII
import java.time.ZoneId
import java.util.Date

import cats.implicits._
import eu.timepit.refined
import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.collection.NonEmpty
import eu.timepit.refined.numeric.NonNegative
import eu.timepit.refined.refineV
import eu.timepit.refined.types.string.NonEmptyString
import javax.inject.Inject
import org.apache.james.jmap.api.model.Preview
import org.apache.james.jmap.api.projections.{MessageFastViewPrecomputedProperties, MessageFastViewProjection}
import org.apache.james.jmap.mail.BracketHeader.sanitize
import org.apache.james.jmap.mail.Email.{Size, sanitizeSize}
import org.apache.james.jmap.method.ZoneIdProvider
import org.apache.james.jmap.model.KeywordsFactory.LENIENT_KEYWORDS_FACTORY
import org.apache.james.jmap.model.{Keywords, Properties, UTCDate}
import org.apache.james.mailbox.model.FetchGroup.{FULL_CONTENT, HEADERS, MINIMAL}
import org.apache.james.mailbox.model.{FetchGroup, MailboxId, MessageId, MessageResult}
import org.apache.james.mailbox.{MailboxSession, MessageIdManager}
import org.apache.james.mime4j.codec.DecodeMonitor
import org.apache.james.mime4j.dom.field.{AddressListField, DateTimeField, MailboxField, MailboxListField}
import org.apache.james.mime4j.dom.{Header, Message}
import org.apache.james.mime4j.message.DefaultMessageBuilder
import org.apache.james.mime4j.stream.{Field, MimeConfig}
import org.apache.james.mime4j.util.MimeUtil
import org.slf4j.{Logger, LoggerFactory}
import reactor.core.scala.publisher.{SFlux, SMono}
import reactor.core.scheduler.Schedulers

import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

object Email {
  private val logger: Logger = LoggerFactory.getLogger(classOf[EmailView])

  type UnparsedEmailIdConstraint = NonEmpty
  type UnparsedEmailId = String Refined UnparsedEmailIdConstraint

  val defaultProperties: Properties = Properties("id", "size")
  val allowedProperties: Properties = Properties("id", "size", "bodyStructure", "textBody", "htmlBody",
    "attachments", "headers", "bodyValues", "messageId", "inReplyTo", "references", "to", "cc", "bcc",
    "from", "sender", "replyTo", "subject", "sentAt", "mailboxIds", "blobId", "threadId", "receivedAt",
    "preview", "hasAttachment", "keywords")
  val idProperty: Properties = Properties("id")

  def asUnparsed(messageId: MessageId): Try[UnparsedEmailId] =
    refined.refineV[UnparsedEmailIdConstraint](messageId.serialize()) match {
      case Left(e) => Failure(new IllegalArgumentException(e))
      case scala.Right(value) => Success(value)
    }

  type Size = Long Refined NonNegative
  val Zero: Size = 0L

  private[mail] def sanitizeSize(value: Long): Size = {
    val size: Either[String, Size] = refineV[NonNegative](value)
    size.fold(e => {
      logger.error(s"Encountered an invalid Email size: $e")
      Zero
    },
      refinedValue => refinedValue)
  }

  private[mail] def parseAsMime4JMessage(firstMessage: MessageResult): Try[Message] = {
    val defaultMessageBuilder = new DefaultMessageBuilder
    defaultMessageBuilder.setMimeEntityConfig(MimeConfig.PERMISSIVE)
    defaultMessageBuilder.setDecodeMonitor(DecodeMonitor.SILENT)
    val inputStream = firstMessage.getFullContent.getInputStream
    val resultMessage = Try(defaultMessageBuilder.parseMessage(inputStream))
    resultMessage.fold(e => {
      Try(inputStream.close())
          .flatMap(closeFailure => {
            logger.error(s"Could not close $inputStream", closeFailure)
            Failure(e)
          })
    }, msg => {
      Try(inputStream.close())
        .fold(
          closeFailure => {
            logger.error(s"Could not close $inputStream", closeFailure)
            Success(msg)
          },
          _ => Success(msg))
    })
  }
}

object ReadLevel {
  private val metadataProperty: Seq[NonEmptyString] = Seq("id", "size", "mailboxIds",
    "mailboxIds", "blobId", "threadId", "receivedAt")
  private val fastViewProperty: Seq[NonEmptyString] = Seq("preview", "hasAttachment")
  private val fullProperty: Seq[NonEmptyString] = Seq("bodyStructure", "textBody", "htmlBody",
    "attachments", "bodyValues")

  def of(property: NonEmptyString): ReadLevel = if (metadataProperty.contains(property)) {
    MetadataReadLevel
  } else if (fastViewProperty.contains(property)) {
    FastViewReadLevel
  }  else if (fullProperty.contains(property)) {
    FullReadLevel
  } else {
    HeaderReadLevel
  }

  def combine(readLevel1: ReadLevel, readLevel2: ReadLevel): ReadLevel = readLevel1 match {
    case MetadataReadLevel => readLevel2
    case FullReadLevel => FullReadLevel
    case HeaderReadLevel => readLevel2 match {
      case FullReadLevel => FullReadLevel
      case FastViewReadLevel => FastViewReadLevel
      case _ => HeaderReadLevel
    }
    case FastViewReadLevel => readLevel2 match {
      case FullReadLevel => FullReadLevel
      case _ => FastViewReadLevel
    }
  }
}

sealed trait ReadLevel
case object MetadataReadLevel extends ReadLevel
case object HeaderReadLevel extends ReadLevel
case object FastViewReadLevel extends ReadLevel
case object FullReadLevel extends ReadLevel

object HeaderMessageId {
  def from(string: String): HeaderMessageId = HeaderMessageId(sanitize(string))
}

object HeaderURL {
  def from(string: String): HeaderURL = HeaderURL(sanitize(string))
}

object BracketHeader {
  def sanitize(string: String): String = string match {
    case s if s.startsWith("<") => sanitize(s.substring(1))
    case s if s.endsWith(">") => sanitize(s.substring(0, s.length - 1))
    case s => s
  }
}

object ParseOptions {
  val allowedParseOption: Set[String] = Set("asRaw", "asText", "asAddresses", "asGroupedAddresses", "asMessageIds", "asDate", "asURLs")

  def validate(parseOption: String): Boolean = from(parseOption).isDefined

  def from(value: String): Option[ParseOption] = value match {
      case "asRaw" => Some(AsRaw)
      case "asText" => Some(AsText)
      case "asAddresses" => Some(AsAddresses)
      case "asGroupedAddresses" => Some(AsGroupedAddresses)
      case "asMessageIds" => Some(AsMessageIds)
      case "asDate" => Some(AsDate)
      case "asURLs" => Some(AsURLs)
      case _ => None
  }
}

sealed trait ParseOption {
  def extractHeaderValue(field: Field): EmailHeaderValue
}
case object AsRaw extends ParseOption {
  override def extractHeaderValue(field: Field): EmailHeaderValue = RawHeaderValue.from(field)
}
case object AsText extends ParseOption {
  override def extractHeaderValue(field: Field): EmailHeaderValue = TextHeaderValue.from(field)
}
case object AsAddresses extends ParseOption {
  override def extractHeaderValue(field: Field): EmailHeaderValue = AddressesHeaderValue.from(field)
}
case object AsGroupedAddresses extends ParseOption {
  override def extractHeaderValue(field: Field): EmailHeaderValue = GroupedAddressesHeaderValue.from(field)
}
case object AsMessageIds extends ParseOption {
  override def extractHeaderValue(field: Field): EmailHeaderValue = MessageIdsHeaderValue.from(field)
}
case object AsDate extends ParseOption {
  override def extractHeaderValue(field: Field): EmailHeaderValue = DateHeaderValue.from(field, ZoneId.systemDefault())

  def extractHeaderValue(field: Field, zoneId: ZoneId): EmailHeaderValue = DateHeaderValue.from(field, zoneId)
}
case object AsURLs extends ParseOption {
  override def extractHeaderValue(field: Field): EmailHeaderValue = URLsHeaderValue.from(field)
}

case class HeaderMessageId(value: String) extends AnyVal

case class Subject(value: String) extends AnyVal

case class MailboxIds(value: List[MailboxId])

case class ThreadId(value: String) extends AnyVal

case class HasAttachment(value: Boolean) extends AnyVal

case class HeaderURL(value: String) extends AnyVal

case class EmailMetadata(id: MessageId,
                         blobId: BlobId,
                         threadId: ThreadId,
                         keywords: Keywords,
                         mailboxIds: MailboxIds,
                         size: Size,
                         receivedAt: UTCDate)

object EmailHeaders {
  val SPECIFIC_HEADER_PREFIX = "header:"

  private[mail] def from(zoneId: ZoneId)(mime4JMessage: Message): EmailHeaders = {
    EmailHeaders(
      headers = asEmailHeaders(mime4JMessage.getHeader),
      messageId = extractMessageId(mime4JMessage, "Message-Id"),
      inReplyTo = extractMessageId(mime4JMessage, "In-Reply-To"),
      references = extractMessageId(mime4JMessage, "References"),
      to = extractAddresses(mime4JMessage, "To"),
      cc = extractAddresses(mime4JMessage, "Cc"),
      bcc = extractAddresses(mime4JMessage, "Bcc"),
      from = extractAddresses(mime4JMessage, "From"),
      replyTo = extractAddresses(mime4JMessage, "Reply-To"),
      sender = extractAddresses(mime4JMessage, "Sender"),
      subject = extractSubject(mime4JMessage),
      sentAt = extractDate(mime4JMessage, "Date").map(date => UTCDate.from(date, zoneId)))
  }

  def extractSpecificHeaders(properties: Option[Properties])(zoneId: ZoneId, mime4JMessage: Message) = {
    properties.getOrElse(Properties.empty()).value
      .flatMap(property => SpecificHeaderRequest.from(property).toOption)
      .map(_.retrieveHeader(zoneId, mime4JMessage))
      .toMap
  }

  private def asEmailHeaders(header: Header): List[EmailHeader] =
    header.iterator()
      .asScala
      .map(header => EmailHeader(
        EmailHeaderName(header.getName),
        RawHeaderValue(new String(header.getRaw.toByteArray, US_ASCII)
          .substring(header.getName.length + 1))))
      .toList

  private def extractSubject(mime4JMessage: Message) =
    extractLastField(mime4JMessage, "Subject")
      .map(_.getBody)
      .map(MimeUtil.unscrambleHeaderValue)
      .map(Subject)

  private def extractMessageId(mime4JMessage: Message, fieldName: String): MessageIdsHeaderValue =
    MessageIdsHeaderValue(
      Option(mime4JMessage.getHeader.getFields(fieldName))
        .map(_.asScala
          .map(_.getBody)
          .map(HeaderMessageId.from)
          .toList)
        .filter(_.nonEmpty))

  private def extractAddresses(mime4JMessage: Message, fieldName: String): Option[AddressesHeaderValue] =
    extractLastField(mime4JMessage, fieldName)
      .flatMap {
        case f: AddressListField => Some(AddressesHeaderValue(EmailAddress.from(f.getAddressList)))
        case f: MailboxListField => Some(AddressesHeaderValue(EmailAddress.from(f.getMailboxList)))
        case f: MailboxField => Some(AddressesHeaderValue(List(EmailAddress.from(f.getMailbox))))
        case _ => None
      }
      .filter(_.value.nonEmpty)

  private def extractDate(mime4JMessage: Message, fieldName: String): Option[Date] =
    extractLastField(mime4JMessage, fieldName)
      .flatMap {
        case f: DateTimeField => Option(f.getDate)
        case _ => None
      }

  private def extractLastField(mime4JMessage: Message, fieldName: String): Option[Field] =
    Option(mime4JMessage.getHeader.getFields(fieldName))
      .map(_.asScala)
      .flatMap(fields => fields.reverse.headOption)

}

case class EmailHeaders(headers: List[EmailHeader],
                        messageId: MessageIdsHeaderValue,
                        inReplyTo: MessageIdsHeaderValue,
                        references: MessageIdsHeaderValue,
                        to: Option[AddressesHeaderValue],
                        cc: Option[AddressesHeaderValue],
                        bcc: Option[AddressesHeaderValue],
                        from: Option[AddressesHeaderValue],
                        sender: Option[AddressesHeaderValue],
                        replyTo: Option[AddressesHeaderValue],
                        subject: Option[Subject],
                        sentAt: Option[UTCDate])

case class EmailBody(bodyStructure: EmailBodyPart,
                     textBody: List[EmailBodyPart],
                     htmlBody: List[EmailBodyPart],
                     attachments: List[EmailBodyPart],
                     bodyValues: Map[PartId, EmailBodyValue])

case class EmailBodyMetadata(hasAttachment: HasAttachment,
                        preview: Preview)

sealed trait EmailView {
  def metadata: EmailMetadata
}

case class EmailMetadataView(metadata: EmailMetadata) extends EmailView

case class EmailHeaderView(metadata: EmailMetadata,
                           header: EmailHeaders,
                           specificHeaders: Map[String, Option[EmailHeaderValue]]) extends EmailView

case class EmailFullView(metadata: EmailMetadata,
                         header: EmailHeaders,
                         body: EmailBody,
                         bodyMetadata: EmailBodyMetadata,
                         specificHeaders: Map[String, Option[EmailHeaderValue]]) extends EmailView

case class EmailFastView(metadata: EmailMetadata,
                         header: EmailHeaders,
                         bodyMetadata: EmailBodyMetadata,
                         specificHeaders: Map[String, Option[EmailHeaderValue]]) extends EmailView


class EmailViewReaderFactory @Inject() (metadataReader: EmailMetadataViewReader,
                                        headerReader: EmailHeaderViewReader,
                                        fastViewReader: EmailFastViewReader,
                                        fullReader: EmailFullViewReader) {
  def selectReader(request: EmailGetRequest): EmailViewReader[EmailView] = {
    val readLevel: ReadLevel = request.properties
      .getOrElse(Email.defaultProperties)
      .value
      .map(ReadLevel.of)
      .reduceOption(ReadLevel.combine)
      .getOrElse(MetadataReadLevel)

    readLevel match {
      case MetadataReadLevel => metadataReader
      case HeaderReadLevel => headerReader
      case FastViewReadLevel => fastViewReader
      case FullReadLevel => fullReader
    }
  }
}

sealed trait EmailViewReader[+EmailView] {
  def read[T >: EmailView](ids: Seq[MessageId], request: EmailGetRequest, mailboxSession: MailboxSession): SFlux[T]
}

private sealed trait EmailViewFactory[+EmailView] {
  def toEmail(request: EmailGetRequest)(message: (MessageId, Seq[MessageResult])): Try[EmailView]
}

private class GenericEmailViewReader[+EmailView](messageIdManager: MessageIdManager,
                                     fetchGroup: FetchGroup,
                                     metadataViewFactory: EmailViewFactory[EmailView]) extends EmailViewReader[EmailView] {
  override def read[T >: EmailView](ids: Seq[MessageId], request: EmailGetRequest, mailboxSession: MailboxSession): SFlux[T] =
    SFlux.fromPublisher(messageIdManager.getMessagesReactive(
        ids.toList.asJava,
        fetchGroup,
        mailboxSession))
      .groupBy(_.getMessageId)
      .flatMap(groupedFlux => groupedFlux.collectSeq().map(results => (groupedFlux.key(), results)))
      .map(metadataViewFactory.toEmail(request))
      .flatMap(SMono.fromTry(_))
}

private class EmailMetadataViewFactory @Inject()(zoneIdProvider: ZoneIdProvider) extends EmailViewFactory[EmailMetadataView] {
  override def toEmail(request: EmailGetRequest)(message: (MessageId, Seq[MessageResult])): Try[EmailMetadataView] = {
    val messageId: MessageId = message._1
    val mailboxIds: MailboxIds = MailboxIds(message._2
      .map(_.getMailboxId)
      .toList)

    for {
      firstMessage <- message._2
        .headOption
        .map(Success(_))
        .getOrElse(Failure(new IllegalArgumentException("No message supplied")))
      blobId <- BlobId.of(messageId)
      keywords <- LENIENT_KEYWORDS_FACTORY.fromFlags(firstMessage.getFlags)
    } yield {
      EmailMetadataView(
        metadata = EmailMetadata(
          id = messageId,
          blobId = blobId,
          threadId = ThreadId(messageId.serialize),
          keywords = keywords,
          mailboxIds = mailboxIds,
          receivedAt = UTCDate.from(firstMessage.getInternalDate, zoneIdProvider.get()),
          size = sanitizeSize(firstMessage.getSize)))
    }
  }
}

private class EmailHeaderViewFactory @Inject()(zoneIdProvider: ZoneIdProvider) extends EmailViewFactory[EmailHeaderView] {
  override def toEmail(request: EmailGetRequest)(message: (MessageId, Seq[MessageResult])): Try[EmailHeaderView] = {
    val messageId: MessageId = message._1
    val mailboxIds: MailboxIds = MailboxIds(message._2
      .map(_.getMailboxId)
      .toList)

    for {
      firstMessage <- message._2
        .headOption
        .map(Success(_))
        .getOrElse(Failure(new IllegalArgumentException("No message supplied")))
      mime4JMessage <- Email.parseAsMime4JMessage(firstMessage)
      blobId <- BlobId.of(messageId)
      keywords <- LENIENT_KEYWORDS_FACTORY.fromFlags(firstMessage.getFlags)
    } yield {
      EmailHeaderView(
        metadata = EmailMetadata(
          id = messageId,
          blobId = blobId,
          threadId = ThreadId(messageId.serialize),
          mailboxIds = mailboxIds,
          receivedAt = UTCDate.from(firstMessage.getInternalDate, zoneIdProvider.get()),
          size = sanitizeSize(firstMessage.getSize),
          keywords = keywords),
        header = EmailHeaders.from(zoneIdProvider.get())(mime4JMessage),
        specificHeaders = EmailHeaders.extractSpecificHeaders(request.properties)(zoneIdProvider.get(), mime4JMessage))
    }
  }
}

private class EmailFullViewFactory @Inject()(zoneIdProvider: ZoneIdProvider, previewFactory: Preview.Factory) extends EmailViewFactory[EmailFullView] {
  override def toEmail(request: EmailGetRequest)(message: (MessageId, Seq[MessageResult])): Try[EmailFullView] = {
    val messageId: MessageId = message._1
    val mailboxIds: MailboxIds = MailboxIds(message._2
      .map(_.getMailboxId)
      .toList)

    for {
      firstMessage <- message._2
        .headOption
        .map(Success(_))
        .getOrElse(Failure(new IllegalArgumentException("No message supplied")))
      mime4JMessage <- Email.parseAsMime4JMessage(firstMessage)
      bodyStructure <- EmailBodyPart.of(messageId, mime4JMessage)
      bodyValues <- extractBodyValues(bodyStructure, request)
      blobId <- BlobId.of(messageId)
      preview <- Try(previewFactory.fromMessageResult(firstMessage))
      keywords <- LENIENT_KEYWORDS_FACTORY.fromFlags(firstMessage.getFlags)
    } yield {
      EmailFullView(
        metadata = EmailMetadata(
          id = messageId,
          blobId = blobId,
          threadId = ThreadId(messageId.serialize),
          mailboxIds = mailboxIds,
          receivedAt = UTCDate.from(firstMessage.getInternalDate, zoneIdProvider.get()),
          keywords = keywords,
          size = sanitizeSize(firstMessage.getSize)),
        header = EmailHeaders.from(zoneIdProvider.get())(mime4JMessage),
        bodyMetadata = EmailBodyMetadata(
          hasAttachment = HasAttachment(!firstMessage.getLoadedAttachments.isEmpty),
          preview = preview),
        body = EmailBody(
          bodyStructure = bodyStructure,
          textBody = bodyStructure.textBody,
          htmlBody = bodyStructure.htmlBody,
          attachments = bodyStructure.attachments,
          bodyValues = bodyValues),
        specificHeaders = EmailHeaders.extractSpecificHeaders(request.properties)(zoneIdProvider.get(), mime4JMessage))
    }
  }

  private def extractBodyValues(bodyStructure: EmailBodyPart, request: EmailGetRequest): Try[Map[PartId, EmailBodyValue]] = for {
    textBodyValues <- extractBodyValues(bodyStructure.textBody, request, request.fetchTextBodyValues.exists(_.value))
    htmlBodyValues <- extractBodyValues(bodyStructure.htmlBody, request, request.fetchHTMLBodyValues.exists(_.value))
    allBodyValues <- extractBodyValues(bodyStructure.flatten, request, request.fetchAllBodyValues.exists(_.value))
  } yield {
    (textBodyValues ++ htmlBodyValues ++ allBodyValues)
      .distinctBy(_._1)
      .toMap
  }

  private def extractBodyValues(parts: List[EmailBodyPart], request: EmailGetRequest, shouldFetch: Boolean): Try[List[(PartId, EmailBodyValue)]] =
    if (shouldFetch) {
      parts
        .map(part => part.bodyContent.map(bodyValue => bodyValue.map(b => (part.partId, b.truncate(request.maxBodyValueBytes)))))
        .sequence
        .map(list => list.flatten)
    } else {
      Success(Nil)
    }
}

private class EmailMetadataViewReader @Inject()(messageIdManager: MessageIdManager,
                                                metadataViewFactory: EmailMetadataViewFactory) extends EmailViewReader[EmailMetadataView] {
  private val reader: GenericEmailViewReader[EmailMetadataView] = new GenericEmailViewReader[EmailMetadataView](messageIdManager, MINIMAL, metadataViewFactory)

  override def read[T >: EmailMetadataView](ids: Seq[MessageId], request: EmailGetRequest, mailboxSession: MailboxSession): SFlux[T] =
    reader.read(ids, request, mailboxSession)
}

private class EmailHeaderViewReader @Inject()(messageIdManager: MessageIdManager,
                                              headerViewFactory: EmailHeaderViewFactory) extends EmailViewReader[EmailHeaderView] {
  private val reader: GenericEmailViewReader[EmailHeaderView] = new GenericEmailViewReader[EmailHeaderView](messageIdManager, HEADERS, headerViewFactory)

  override def read[T >: EmailHeaderView](ids: Seq[MessageId], request: EmailGetRequest, mailboxSession: MailboxSession): SFlux[T] =
    reader.read(ids, request, mailboxSession)
}

private class EmailFullViewReader @Inject()(messageIdManager: MessageIdManager,
                                            fullViewFactory: EmailFullViewFactory) extends EmailViewReader[EmailFullView] {
  private val reader: GenericEmailViewReader[EmailFullView] = new GenericEmailViewReader[EmailFullView](messageIdManager, FULL_CONTENT, fullViewFactory)


  override def read[T >: EmailFullView](ids: Seq[MessageId], request: EmailGetRequest, mailboxSession: MailboxSession): SFlux[T] =
    reader.read(ids, request, mailboxSession)
}

object EmailFastViewReader {
  val logger: Logger = LoggerFactory.getLogger(classOf[EmailFastViewReader])
}

private class EmailFastViewReader @Inject()(messageIdManager: MessageIdManager,
                                            messageFastViewProjection: MessageFastViewProjection,
                                            zoneIdProvider: ZoneIdProvider,
                                            fullViewFactory: EmailFullViewFactory) extends EmailViewReader[EmailView] {
  private val fullReader: GenericEmailViewReader[EmailFullView] = new GenericEmailViewReader[EmailFullView](messageIdManager, FULL_CONTENT, fullViewFactory)

  private sealed trait FastViewResult

  private case class FastViewAvailable(id: MessageId, fastView: MessageFastViewPrecomputedProperties) extends FastViewResult

  private case class FastViewUnavailable(id: MessageId) extends FastViewResult

  override def read[T >: EmailView](ids: Seq[MessageId], request: EmailGetRequest, mailboxSession: MailboxSession): SFlux[T] = {
    SMono.fromPublisher(messageFastViewProjection.retrieve(ids.asJava))
      .map(_.asScala.toMap)
      .flatMapMany(fastViews => SFlux.fromIterable(ids)
        .map(id => fastViews.get(id)
          .map(FastViewAvailable(id, _))
          .getOrElse(FastViewUnavailable(id))))
      .collectSeq()
      .flatMapMany(results => toEmailViews(results, request, mailboxSession))
  }

  private def toEmailViews[T >: EmailView](results: Seq[FastViewResult], request: EmailGetRequest, mailboxSession: MailboxSession): SFlux[T] = {
    val availables: Seq[FastViewAvailable] = results.flatMap {
      case available: FastViewAvailable => Some(available)
      case _ => None
    }
    val unavailables: Seq[FastViewUnavailable] = results.flatMap {
      case unavailable: FastViewUnavailable => Some(unavailable)
      case _ => None
    }

    SFlux.merge(Seq(
      toFastViews(availables, request, mailboxSession),
      fullReader.read(unavailables.map(_.id), request, mailboxSession)
        .doOnNext(storeOnCacheMisses)))
  }

  private def storeOnCacheMisses(fullView: EmailFullView) = {
    SMono.fromPublisher(messageFastViewProjection.store(
      fullView.metadata.id,
      MessageFastViewPrecomputedProperties.builder()
        .preview(fullView.bodyMetadata.preview)
        .hasAttachment(fullView.bodyMetadata.hasAttachment.value)
        .build()))
      .doOnError(e => EmailFastViewReader.logger.error(s"Cannot store the projection to MessageFastViewProjection for ${fullView.metadata.id}", e))
      .subscribeOn(Schedulers.elastic())
      .subscribe()
  }

  private def toFastViews(fastViews: Seq[FastViewAvailable], request: EmailGetRequest, mailboxSession: MailboxSession): SFlux[EmailView] ={
    val fastViewsAsMap: Map[MessageId, MessageFastViewPrecomputedProperties] = fastViews.map(e => (e.id, e.fastView)).toMap
    val ids: Seq[MessageId] = fastViews.map(_.id)

    SFlux.fromPublisher(messageIdManager.getMessagesReactive(ids.asJava, HEADERS, mailboxSession))
      .groupBy(_.getMessageId)
      .flatMap(groupedFlux => groupedFlux.collectSeq().map(results => (groupedFlux.key(), results)))
      .map(x => toEmail(request)(x, fastViewsAsMap(x._1)))
      .flatMap(SMono.fromTry(_))
  }

  private def toEmail(request: EmailGetRequest)(message: (MessageId, Seq[MessageResult]), fastView: MessageFastViewPrecomputedProperties): Try[EmailView] = {
    val messageId: MessageId = message._1
    val mailboxIds: MailboxIds = MailboxIds(message._2
      .map(_.getMailboxId)
      .toList)

    for {
      firstMessage <- message._2
        .headOption
        .map(Success(_))
        .getOrElse(Failure(new IllegalArgumentException("No message supplied")))
      mime4JMessage <- Email.parseAsMime4JMessage(firstMessage)
      blobId <- BlobId.of(messageId)
      keywords <- LENIENT_KEYWORDS_FACTORY.fromFlags(firstMessage.getFlags)
    } yield {
      EmailFastView(
        metadata = EmailMetadata(
          id = messageId,
          blobId = blobId,
          threadId = ThreadId(messageId.serialize),
          mailboxIds = mailboxIds,
          receivedAt = UTCDate.from(firstMessage.getInternalDate, zoneIdProvider.get()),
          size = sanitizeSize(firstMessage.getSize),
          keywords = keywords),
        bodyMetadata = EmailBodyMetadata(
          hasAttachment = HasAttachment(fastView.hasAttachment),
          preview = fastView.getPreview),
        header = EmailHeaders.from(zoneIdProvider.get())(mime4JMessage),
        specificHeaders = EmailHeaders.extractSpecificHeaders(request.properties)(zoneIdProvider.get(), mime4JMessage))
    }
  }
}
