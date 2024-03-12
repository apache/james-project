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

import eu.timepit.refined.api.Refined
import org.apache.james.jmap.core.{AccountId, Id, JmapRfc8621Configuration}
import org.apache.james.jmap.mail.MDNParse._
import org.apache.james.jmap.method.{ValidableRequest, WithAccountId}
import org.apache.james.mailbox.model.MessageId
import org.apache.james.mdn.MDN
import org.apache.james.mime4j.dom.Message

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

object MDNParse {
  type UnparsedBlobId = String Refined Id.IdConstraint
}

case class BlobIds(value: Seq[UnparsedBlobId])

case class RequestTooLargeException(description: String) extends Exception

case class BlobUnParsableException(blobId: BlobId) extends RuntimeException

case object MDNParseRequest {
  val MAXIMUM_NUMBER_OF_BLOB_IDS: Int = 16
}

case class MDNParseRequest(accountId: AccountId,
                           blobIds: BlobIds) extends WithAccountId with ValidableRequest {

  import MDNParseRequest._

  override def validate(configuration: JmapRfc8621Configuration): Either[RequestTooLargeException, MDNParseRequest] = {
    if (blobIds.value.length > configuration.jmapEmailGetFullMaxSize.asLong()) {
      Left(RequestTooLargeException("The number of ids requested by the client exceeds the maximum number the server is willing to process in a single method call"))
    } else {
      scala.Right(this)
    }
  }
}

case class MDNNotFound(value: Set[UnparsedBlobId]) {
  def merge(other: MDNNotFound): MDNNotFound = MDNNotFound(this.value ++ other.value)
}

object MDNNotParsable {
  def merge(notParsable1: MDNNotParsable, notParsable2: MDNNotParsable): MDNNotParsable = MDNNotParsable(notParsable1.value ++ notParsable2.value)
}

case class MDNNotParsable(value: Set[UnparsedBlobId]) {
  def merge(other: MDNNotParsable): MDNNotParsable = MDNNotParsable(this.value ++ other.value)
}

object MDNParsed {
  def fromMDN(mdn: MDN, message: Message, originalMessageId: Option[MessageId]): MDNParsed = {
    val report = mdn.getReport
    MDNParsed(forEmailId = originalMessageId.map(ForEmailIdField(_)),
      subject = Option(message.getSubject).map(SubjectField),
      textBody = Some(TextBodyField(mdn.getHumanReadableText)),
      reportingUA = report.getReportingUserAgentField
        .map(userAgent => ReportUAField(userAgent.fieldValue()))
        .toScala,
      finalRecipient = FinalRecipientField(report.getFinalRecipientField.fieldValue()),
      originalMessageId = report.getOriginalMessageIdField
        .map(originalMessageId => OriginalMessageIdField(originalMessageId.getOriginalMessageId))
        .toScala,
      originalRecipient = report.getOriginalRecipientField
        .map(originalRecipient => OriginalRecipientField(originalRecipient.fieldValue()))
        .toScala,
      includeOriginalMessage = IncludeOriginalMessageField(mdn.getOriginalMessage.isPresent),
      disposition = MDNDisposition.fromJava(report.getDispositionField),
      error = Option(report.getErrorFields.asScala
          .map(error => ErrorField(error.getText.formatted()))
          .toSeq)
        .filter(error => error.nonEmpty),
      extensionFields = Option(report.getExtensionFields.asScala
        .map(extension => (extension.getFieldName, extension.getRawValue))
        .toMap).filter(_.nonEmpty))
  }
}

case class MDNParsed(forEmailId: Option[ForEmailIdField],
                     subject: Option[SubjectField],
                     textBody: Option[TextBodyField],
                     reportingUA: Option[ReportUAField],
                     finalRecipient: FinalRecipientField,
                     originalMessageId: Option[OriginalMessageIdField],
                     originalRecipient: Option[OriginalRecipientField],
                     includeOriginalMessage: IncludeOriginalMessageField,
                     disposition: MDNDisposition,
                     error: Option[Seq[ErrorField]],
                     extensionFields: Option[Map[String, String]])

object MDNParseResults {
  def notFound(blobId: UnparsedBlobId): MDNParseResults = MDNParseResults(None, Some(MDNNotFound(Set(blobId))), None)

  def notFound(blobId: BlobId): MDNParseResults = MDNParseResults(None, Some(MDNNotFound(Set(blobId.value))), None)

  def notParse(blobId: BlobId): MDNParseResults = MDNParseResults(None, None, Some(MDNNotParsable(Set(blobId.value))))

  def parse(blobId: BlobId, mdnParsed: MDNParsed): MDNParseResults = MDNParseResults(Some(Map(blobId -> mdnParsed)), None, None)

  def empty(): MDNParseResults = MDNParseResults(None, None, None)

  def merge(response1: MDNParseResults, response2: MDNParseResults): MDNParseResults = MDNParseResults(
      parsed = (response1.parsed ++ response2.parsed).reduceOption((parsed1, parsed2) => parsed1 ++ parsed2),
      notFound = (response1.notFound ++ response2.notFound).reduceOption((notFound1, notFound2) => notFound1.merge(notFound2)),
      notParsable = (response1.notParsable ++ response2.notParsable).reduceOption((notParsable1, notParsable2) => notParsable1.merge(notParsable2)))
}

case class MDNParseResults(parsed: Option[Map[BlobId, MDNParsed]],
                           notFound: Option[MDNNotFound],
                           notParsable: Option[MDNNotParsable]) {
  def asResponse(accountId: AccountId): MDNParseResponse = MDNParseResponse(accountId, parsed, notFound, notParsable)
}

case class MDNParseResponse(accountId: AccountId,
                            parsed: Option[Map[BlobId, MDNParsed]],
                            notFound: Option[MDNNotFound],
                            notParsable: Option[MDNNotParsable])
