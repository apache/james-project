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

import java.util.UUID

import cats.implicits.toTraverseOps
import org.apache.james.jmap.core.Id.Id
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{AccountId, Id, Properties, SetError}
import org.apache.james.jmap.method.{WithAccountId, standardError}
import org.apache.james.mailbox.model.MessageId
import play.api.libs.json.{JsObject, JsPath, JsonValidationError}

object MDNSend {
  val MDN_ALREADY_SENT_FLAG: String = "$mdnsent"
}

object MDNId {
  def generate: MDNId = MDNId(Id.validate(UUID.randomUUID().toString).toOption.get)
}

case class MDNSendCreationId(id: Id)

case class MDNId(value: Id)

object MDNSendRequestInvalidException {
  def parse(errors: collection.Seq[(JsPath, collection.Seq[JsonValidationError])]): MDNSendRequestInvalidException =
    MDNSendRequestInvalidException(standardError(errors))
}

case class MDNSendRequestInvalidException(error: SetError) extends Exception

case class MDNSendNotFoundException(description: String) extends Exception

case class MDNSendForbiddenException() extends Exception

case class MDNSendForbiddenFromException(description: String) extends Exception

case class MDNSendOverQuotaException() extends Exception

case class MDNSendTooLargeException() extends Exception

case class MDNSendRateLimitException() extends Exception

case class MDNSendInvalidPropertiesException() extends Exception

case class MDNSendAlreadySentException() extends Exception

case class IdentityIdNotFoundException(description: String) extends Exception

object MDNSendCreateRequest {
  private val assignableProperties: Set[String] = Set("forEmailId", "subject", "textBody", "reportingUA",
    "finalRecipient", "includeOriginalMessage", "disposition", "extensionFields")

  def validateProperties(jsObject: JsObject): Either[MDNSendRequestInvalidException, JsObject] =
    jsObject.keys.diff(assignableProperties) match {
      case unknownProperties if unknownProperties.nonEmpty =>
        Left(MDNSendRequestInvalidException(SetError.invalidArguments(
          SetErrorDescription("Some unknown properties were specified"),
          Some(Properties.toProperties(unknownProperties.toSet)))))
      case _ => scala.Right(jsObject)
    }
}

case class MDNSendCreateRequest(forEmailId: ForEmailIdField,
                                subject: Option[SubjectField],
                                textBody: Option[TextBodyField],
                                reportingUA: Option[ReportUAField],
                                finalRecipient: Option[FinalRecipientField],
                                includeOriginalMessage: Option[IncludeOriginalMessageField],
                                disposition: MDNDisposition,
                                extensionFields: Option[Map[ExtensionFieldName, ExtensionFieldValue]]) {
  def validate: Either[MDNSendRequestInvalidException, MDNSendCreateRequest] =
    validateDisposition.flatMap(_ => validateReportUA)
      .flatMap(_ => validateFinalRecipient)

  def validateDisposition: Either[MDNSendRequestInvalidException, MDNSendCreateRequest] =
    disposition.validate
      .fold(error => Left(error), _ => scala.Right(this))

  def validateReportUA: Either[MDNSendRequestInvalidException, MDNSendCreateRequest] =
    reportingUA match {
      case None => scala.Right(this)
      case Some(value) => value.validate.fold(error => Left(error), _ => scala.Right(this))
    }

  def validateFinalRecipient: Either[MDNSendRequestInvalidException, MDNSendCreateRequest] =
    finalRecipient match {
      case None => scala.Right(this)
      case Some(value) => value.validate.fold(error => Left(error), _ => scala.Right(this))
    }
}

case class MDNSendCreateResponse(subject: Option[SubjectField],
                                 textBody: Option[TextBodyField],
                                 reportingUA: Option[ReportUAField],
                                 mdnGateway: Option[MDNGatewayField],
                                 originalRecipient: Option[OriginalRecipientField],
                                 finalRecipient: Option[FinalRecipientField],
                                 includeOriginalMessage: Option[IncludeOriginalMessageField],
                                 originalMessageId: Option[OriginalMessageIdField],
                                 error: Option[Seq[ErrorField]])

case class MDNSendRequest(accountId: AccountId,
                          identityId: UnparsedIdentityId,
                          send: Map[MDNSendCreationId, JsObject],
                          onSuccessUpdateEmail: Option[Map[MDNSendCreationId, JsObject]]) extends WithAccountId {

  def validate: Either[IllegalArgumentException, MDNSendRequest] = {
    val supportedCreationIds: List[MDNSendCreationId] = send.keys.toList
    onSuccessUpdateEmail.getOrElse(Map())
      .keys
      .toList
      .map(id => validateOnSuccessUpdateEmail(id, supportedCreationIds))
      .sequence
      .map(_ => this)
  }

  private def validateOnSuccessUpdateEmail(creationId: MDNSendCreationId, supportedCreationIds: List[MDNSendCreationId]): Either[IllegalArgumentException, MDNSendCreationId] =
    if (creationId.id.value.startsWith("#")) {
      val realId = creationId.id.value.substring(1)
      val validateId: Either[IllegalArgumentException, MDNSendCreationId] = Id.validate(realId).map(id => MDNSendCreationId(id))
      validateId.flatMap(mdnSendId => if (supportedCreationIds.contains(mdnSendId)) {
        scala.Right(mdnSendId)
      } else {
        Left(new IllegalArgumentException(s"${creationId.id.value} cannot be referenced in current method call"))
      })
    } else {
      Left(new IllegalArgumentException(s"${creationId.id.value} cannot be retrieved as storage for MDNSend is not yet implemented"))
    }

  def implicitEmailSetRequest(messageIdResolver: MDNSendCreationId => Either[IllegalArgumentException, Option[MessageId]]): Either[IllegalArgumentException, Option[EmailSetRequest]] =
    resolveOnSuccessUpdateEmail(messageIdResolver)
      .map(update =>
        if (update.isEmpty) {
          None
        } else {
          Some(EmailSetRequest(
            accountId = accountId,
            create = None,
            update = update,
            destroy = None))
        })

  def resolveOnSuccessUpdateEmail(messageIdResolver: MDNSendCreationId => Either[IllegalArgumentException, Option[MessageId]]): Either[IllegalArgumentException, Option[Map[UnparsedMessageId, JsObject]]] =
    onSuccessUpdateEmail.map(map => map.toList
      .map {
        case (creationId, json) => messageIdResolver.apply(creationId).map(msgOpt => msgOpt.map(messageId => (EmailSet.asUnparsed(messageId), json)))
      }
      .sequence
      .map(list => list.flatten.toMap))
      .sequence
      .map {
        case Some(value) if value.isEmpty => None
        case e => e
      }
}

case class MDNSendResponse(accountId: AccountId,
                           sent: Option[Map[MDNSendCreationId, MDNSendCreateResponse]],
                           notSent: Option[Map[MDNSendCreationId, SetError]])

object MDNSendResults {
  def empty: MDNSendResults = MDNSendResults(None, None, Map.empty)

  def sent(createSuccess: MDNSendCreateSuccess): MDNSendResults =
    MDNSendResults(sent = Some(Map(createSuccess.mdnCreationId -> createSuccess.createResponse)),
      notSent = None,
      mdnSentIdResolver = Map(createSuccess.mdnCreationId -> createSuccess.forEmailId))

  def notSent(mdnSendId: MDNSendCreationId, throwable: Throwable): MDNSendResults = {
    val setError: SetError = throwable match {
      case notFound: MDNSendNotFoundException => SetError.notFound(SetErrorDescription(notFound.description))
      case _: MDNSendForbiddenException => SetError(SetError.forbiddenValue,
        SetErrorDescription("Violate an Access Control List (ACL) or other permissions policy."),
        None)
      case forbiddenFrom: MDNSendForbiddenFromException => SetError(SetError.forbiddenFromValue,
        SetErrorDescription(forbiddenFrom.description),
        None)
      case _: MDNSendInvalidPropertiesException => SetError(SetError.invalidArgumentValue,
        SetErrorDescription("The record given is invalid in some way."),
        None)
      case _: MDNSendAlreadySentException => SetError.mdnAlreadySent(SetErrorDescription("The message has the $mdnsent keyword already set."))
      case parseError: MDNSendRequestInvalidException => parseError.error
    }
    MDNSendResults(None, Some(Map(mdnSendId -> setError)), Map.empty)
  }

  def merge(result1: MDNSendResults, result2: MDNSendResults): MDNSendResults = MDNSendResults(
    sent = (result1.sent ++ result2.sent).reduceOption(_ ++ _),
    notSent = (result1.notSent ++ result2.notSent).reduceOption(_ ++ _),
    mdnSentIdResolver = result1.mdnSentIdResolver ++ result2.mdnSentIdResolver)
}

case class MDNSendCreateSuccess(mdnCreationId: MDNSendCreationId,
                                createResponse: MDNSendCreateResponse,
                                forEmailId: MessageId)

case class MDNSendResults(sent: Option[Map[MDNSendCreationId, MDNSendCreateResponse]],
                          notSent: Option[Map[MDNSendCreationId, SetError]],
                          mdnSentIdResolver: Map[MDNSendCreationId, MessageId]) {

  def resolveMessageId(sendId: MDNSendCreationId): Either[IllegalArgumentException, Option[MessageId]] =
    if (sendId.id.value.startsWith("#")) {
      val realId: String = sendId.id.value.substring(1)
      val validatedId: Either[IllegalArgumentException, MDNSendCreationId] = Id.validate(realId).map(id => MDNSendCreationId(id))
      validatedId
        .left.map(s => new IllegalArgumentException(s))
        .flatMap(id => retrieveMessageId(id)
          .map(id => scala.Right(Some(id))).getOrElse(scala.Right(None)))
    } else {
      Left(new IllegalArgumentException(s"${sendId.id.value} cannot be retrieved as storage for MDNSend is not yet implemented"))
    }

  private def retrieveMessageId(creationId: MDNSendCreationId): Option[MessageId] =
    sent.getOrElse(Map.empty).
      filter(sentResult => sentResult._1.equals(creationId)).keys
      .headOption
      .flatMap(mdnSendId => mdnSentIdResolver.get(mdnSendId))

  def asResponse(accountId: AccountId): MDNSendResponse = MDNSendResponse(accountId, sent, notSent)
}
