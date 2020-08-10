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

package org.apache.james.jmap.json

import java.io.InputStream
import java.net.URL

import javax.inject.Inject

import org.apache.james.core.{Domain, Username}
import org.apache.james.jmap.mail.{DelegatedNamespace, Ids, IsSubscribed, Mailbox, MailboxGetRequest, MailboxGetResponse, MailboxNamespace, MailboxRights, MayAddItems, MayCreateChild, MayDelete, MayReadItems, MayRemoveItems, MayRename, MaySetKeywords, MaySetSeen, MaySubmit, NotFound, PersonalNamespace, Properties, Quota, QuotaId, QuotaRoot, Quotas, Right, Rights, SortOrder, TotalEmails, TotalThreads, UnreadEmails, UnreadThreads, Value}
import org.apache.james.jmap.model
import org.apache.james.jmap.model.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.model.Invocation.{Arguments, MethodCallId, MethodName}
import org.apache.james.jmap.model.{Account, Invocation, Session, _}
import org.apache.james.mailbox.Role
import org.apache.james.mailbox.model.{MailboxACL, MailboxId}

import play.api.libs.functional.syntax._
import play.api.libs.json._

import scala.collection.{Seq => LegacySeq}
import scala.language.implicitConversions
import scala.util.Try

class Serializer @Inject() (mailboxIdFactory: MailboxId.Factory) {
  // CreateIds
  private implicit val clientIdFormat: Format[ClientId] = Json.valueFormat[ClientId]
  private implicit val serverIdFormat: Format[ServerId] = Json.valueFormat[ServerId]
  private implicit val createdIdsFormat: Format[CreatedIds] = Json.valueFormat[CreatedIds]

  private implicit def createdIdsIdWrites(implicit serverIdWriter: Writes[ServerId]): Writes[Map[ClientId, ServerId]] =
    (ids: Map[ClientId, ServerId]) => {
      ids.foldLeft(JsObject.empty)((jsObject, kv) => {
        val (clientId: ClientId, serverId: ServerId) = kv
        jsObject.+(clientId.value.value, serverIdWriter.writes(serverId))
      })
    }

  private implicit def createdIdsIdRead(implicit serverIdReader: Reads[ServerId]): Reads[Map[ClientId, ServerId]] =
    Reads.mapReads[ClientId, ServerId] {
      clientIdString => Json.fromJson[ClientId](JsString(clientIdString))
    }

  // Invocation
  private implicit val methodNameFormat: Format[MethodName] = Json.valueFormat[MethodName]
  private implicit val argumentFormat: Format[Arguments] = Json.valueFormat[Arguments]
  private implicit val methodCallIdFormat: Format[MethodCallId] = Json.valueFormat[MethodCallId]
  private implicit val invocationRead: Reads[Invocation] = (
    (JsPath \ model.Invocation.METHOD_NAME).read[MethodName] and
      (JsPath \ model.Invocation.ARGUMENTS).read[Arguments] and
      (JsPath \ model.Invocation.METHOD_CALL).read[MethodCallId]
    ) (model.Invocation.apply _)

  private implicit val invocationWrite: Writes[Invocation] = (invocation: Invocation) =>
    Json.arr(invocation.methodName, invocation.arguments, invocation.methodCallId)

  // RequestObject
  private implicit val requestObjectRead: Format[RequestObject] = Json.format[RequestObject]

  // ResponseObject
  private implicit val responseObjectFormat: Format[ResponseObject] = Json.format[ResponseObject]

  private implicit val maxSizeUploadWrites: Writes[MaxSizeUpload] = Json.valueWrites[MaxSizeUpload]
  private implicit val maxConcurrentUploadWrites: Writes[MaxConcurrentUpload] = Json.valueWrites[MaxConcurrentUpload]
  private implicit val maxSizeRequestWrites: Writes[MaxSizeRequest] = Json.valueWrites[MaxSizeRequest]
  private implicit val maxConcurrentRequestsWrites: Writes[MaxConcurrentRequests] = Json.valueWrites[MaxConcurrentRequests]
  private implicit val maxCallsInRequestWrites: Writes[MaxCallsInRequest] = Json.valueWrites[MaxCallsInRequest]
  private implicit val maxObjectsInGetWrites: Writes[MaxObjectsInGet] = Json.valueWrites[MaxObjectsInGet]
  private implicit val maxObjectsInSetWrites: Writes[MaxObjectsInSet] = Json.valueWrites[MaxObjectsInSet]

  private implicit val maxMailboxesPerEmailWrites: Writes[MaxMailboxesPerEmail] = Json.valueWrites[MaxMailboxesPerEmail]
  private implicit val maxMailboxDepthWrites: Writes[MaxMailboxDepth] = Json.valueWrites[MaxMailboxDepth]
  private implicit val maxSizeMailboxNameWrites: Writes[MaxSizeMailboxName] = Json.valueWrites[MaxSizeMailboxName]
  private implicit val maxSizeAttachmentsPerEmailWrites: Writes[MaxSizeAttachmentsPerEmail] = Json.valueWrites[MaxSizeAttachmentsPerEmail]
  private implicit val mayCreateTopLevelMailboxWrites: Writes[MayCreateTopLevelMailbox] = Json.valueWrites[MayCreateTopLevelMailbox]

  private implicit val usernameWrites: Writes[Username] = username => JsString(username.asString)
  private implicit val urlWrites: Writes[URL] = url => JsString(url.toString)
  private implicit val coreCapabilityWrites: Writes[CoreCapabilityProperties] = Json.writes[CoreCapabilityProperties]
  private implicit val mailCapabilityWrites: Writes[MailCapabilityProperties] = Json.writes[MailCapabilityProperties]
  private implicit val quotaCapabilityWrites: Writes[QuotaCapabilityProperties] = OWrites[QuotaCapabilityProperties](_ => Json.obj())
  private implicit val sharesCapabilityWrites: Writes[SharesCapabilityProperties] = OWrites[SharesCapabilityProperties](_ => Json.obj())

  private implicit def setCapabilityWrites(implicit corePropertiesWriter: Writes[CoreCapabilityProperties],
                                   mailCapabilityWrites: Writes[MailCapabilityProperties],
                                   quotaCapabilityWrites: Writes[QuotaCapabilityProperties],
                                   sharesCapabilityWrites: Writes[SharesCapabilityProperties]): Writes[Set[_ <: Capability]] =
    (set: Set[_ <: Capability]) => {
      set.foldLeft(JsObject.empty)((jsObject, capability) => {
        capability match {
          case capability: CoreCapability =>
            jsObject.+(capability.identifier.value, corePropertiesWriter.writes(capability.properties))
          case capability: MailCapability =>
            jsObject.+(capability.identifier.value, mailCapabilityWrites.writes(capability.properties))
          case capability: QuotaCapability =>
            jsObject.+(capability.identifier.value, quotaCapabilityWrites.writes(capability.properties))
          case capability: SharesCapability =>
            jsObject.+(capability.identifier.value, sharesCapabilityWrites.writes(capability.properties))
          case _ => jsObject
        }
      })
    }

  private implicit val capabilitiesWrites: Writes[Capabilities] = capabilities => setCapabilityWrites.writes(capabilities.toSet)

  private implicit val accountIdWrites: Format[AccountId] = Json.valueFormat[AccountId]
  private implicit def identifierMapWrite[Any](implicit idWriter: Writes[AccountId]): Writes[Map[CapabilityIdentifier, Any]] =
    (m: Map[CapabilityIdentifier, Any]) => {
      m.foldLeft(JsObject.empty)((jsObject, kv) => {
        val (identifier: CapabilityIdentifier, id: AccountId) = kv
        jsObject.+(identifier.value, idWriter.writes(id))
      })
    }

  private implicit val isPersonalFormat: Format[IsPersonal] = Json.valueFormat[IsPersonal]
  private implicit val isReadOnlyFormat: Format[IsReadOnly] = Json.valueFormat[IsReadOnly]
  private implicit val accountWrites: Writes[Account] = (
      (JsPath \ Account.NAME).write[Username] and
      (JsPath \ Account.IS_PERSONAL).write[IsPersonal] and
      (JsPath \ Account.IS_READ_ONLY).write[IsReadOnly] and
      (JsPath \ Account.ACCOUNT_CAPABILITIES).write[Set[_ <: Capability]]
    ) (unlift(Account.unapplyIgnoreAccountId))

  private implicit def accountListWrites(implicit accountWrites: Writes[Account]): Writes[List[Account]] =
    (list: List[Account]) => JsObject(list.map(account => (account.accountId.id.value, accountWrites.writes(account))))

  private implicit val sessionWrites: Writes[Session] = Json.writes[Session]

  private implicit val mailboxIdWrites: Writes[MailboxId] = mailboxId => JsString(mailboxId.serialize)
  private implicit val mailboxIdReads: Reads[MailboxId] = {
    case JsString(serializedMailboxId) => Try(JsSuccess(mailboxIdFactory.fromString(serializedMailboxId))).getOrElse(JsError())
    case _ => JsError()
  }

  private implicit val roleWrites: Writes[Role] = role => JsString(role.serialize)
  private implicit val sortOrderWrites: Writes[SortOrder] = Json.valueWrites[SortOrder]
  private implicit val totalEmailsWrites: Writes[TotalEmails] = Json.valueWrites[TotalEmails]
  private implicit val unreadEmailsWrites: Writes[UnreadEmails] = Json.valueWrites[UnreadEmails]
  private implicit val totalThreadsWrites: Writes[TotalThreads] = Json.valueWrites[TotalThreads]
  private implicit val unreadThreadsWrites: Writes[UnreadThreads] = Json.valueWrites[UnreadThreads]
  private implicit val isSubscribedWrites: Writes[IsSubscribed] = Json.valueWrites[IsSubscribed]

  private implicit val mayReadItemsWrites: Writes[MayReadItems] = Json.valueWrites[MayReadItems]
  private implicit val mayAddItemsWrites: Writes[MayAddItems] = Json.valueWrites[MayAddItems]
  private implicit val mayRemoveItemsWrites: Writes[MayRemoveItems] = Json.valueWrites[MayRemoveItems]
  private implicit val maySetSeenWrites: Writes[MaySetSeen] = Json.valueWrites[MaySetSeen]
  private implicit val maySetKeywordsWrites: Writes[MaySetKeywords] = Json.valueWrites[MaySetKeywords]
  private implicit val mayCreateChildWrites: Writes[MayCreateChild] = Json.valueWrites[MayCreateChild]
  private implicit val mayRenameWrites: Writes[MayRename] = Json.valueWrites[MayRename]
  private implicit val mayDeleteWrites: Writes[MayDelete] = Json.valueWrites[MayDelete]
  private implicit val maySubmitWrites: Writes[MaySubmit] = Json.valueWrites[MaySubmit]
  private implicit val mailboxRightsWrites: Writes[MailboxRights] = Json.writes[MailboxRights]

  private implicit val mailboxNamespaceWrites: Writes[MailboxNamespace] = {
    case _: PersonalNamespace => JsString("Personal")
    case delegated: DelegatedNamespace => JsString(s"Delegated[${delegated.owner.asString}]")
  }

  private implicit val mailboxACLWrites: Writes[MailboxACL.Right] = right => JsString(right.asCharacter.toString)

  private implicit val rightWrites: Writes[Right] = Json.valueWrites[Right]
  private implicit val rightsWrites: Writes[Rights] = Json.valueWrites[Rights]

  private implicit def rightsMapWrites(implicit rightWriter: Writes[Seq[Right]]): Writes[Map[Username, Seq[Right]]] =
    (m: Map[Username, Seq[Right]]) => {
      m.foldLeft(JsObject.empty)((jsObject, kv) => {
        val (username: Username, rights: Seq[Right]) = kv
        jsObject.+(username.asString, rightWriter.writes(rights))
      })
    }

  private implicit val domainWrites: Writes[Domain] = domain => JsString(domain.asString)
  private implicit val quotaRootWrites: Writes[QuotaRoot] = Json.writes[QuotaRoot]
  private implicit val quotaIdWrites: Writes[QuotaId] = Json.valueWrites[QuotaId]

  private implicit val quotaValueWrites: Writes[Value] = Json.writes[Value]
  private implicit val quotaWrites: Writes[Quota] = Json.valueWrites[Quota]

  private implicit def quotaMapWrites(implicit valueWriter: Writes[Value]): Writes[Map[Quotas.Type, Value]] =
    (m: Map[Quotas.Type, Value]) => {
      m.foldLeft(JsObject.empty)((jsObject, kv) => {
        val (quotaType: Quotas.Type, value: Value) = kv
        jsObject.+(quotaType.toString, valueWriter.writes(value))
      })
    }

  private implicit val quotasWrites: Writes[Quotas] = Json.valueWrites[Quotas]

  private implicit def quotasMapWrites(implicit quotaWriter: Writes[Quota]): Writes[Map[QuotaId, Quota]] =
    (m: Map[QuotaId, Quota]) => {
      m.foldLeft(JsObject.empty)((jsObject, kv) => {
        val (quotaId: QuotaId, quota: Quota) = kv
        jsObject.+(quotaId.getName, quotaWriter.writes(quota))
      })
    }

  implicit def mailboxWrites(properties: Set[String]): Writes[Mailbox] = Json.writes[Mailbox]
    .transform((o: JsObject) => JsObject(o.fields.filter(entry => properties.contains(entry._1))))

  private implicit val idsRead: Reads[Ids] = Json.valueReads[Ids]
  private implicit val propertiesRead: Reads[Properties] = Json.valueReads[Properties]
  private implicit val mailboxGetRequest: Reads[MailboxGetRequest] = Json.reads[MailboxGetRequest]

  private implicit def notFoundWrites(implicit mailboxIdWrites: Writes[MailboxId]): Writes[NotFound] =
    notFound => JsArray(notFound.value.toList.map(mailboxIdWrites.writes))

  private implicit def mailboxGetResponseWrites(implicit mailboxWrites: Writes[Mailbox]): Writes[MailboxGetResponse] = Json.writes[MailboxGetResponse]

  private implicit val jsonValidationErrorWrites: Writes[JsonValidationError] = error => JsString(error.message)

  private implicit def jsonValidationErrorsWrites(implicit jsonValidationErrorWrites: Writes[JsonValidationError]): Writes[LegacySeq[JsonValidationError]] =
    (errors: LegacySeq[JsonValidationError]) => {
      JsArray(errors.map(error => jsonValidationErrorWrites.writes(error)).toArray[JsValue])
    }

  private implicit def errorsWrites(implicit jsonValidationErrorsWrites: Writes[LegacySeq[JsonValidationError]]): Writes[LegacySeq[(JsPath, LegacySeq[JsonValidationError])]] =
    (errors: LegacySeq[(JsPath, LegacySeq[JsonValidationError])]) => {
      errors.foldLeft(JsArray.empty)((jsArray, jsError) => {
        val (path: JsPath, list: LegacySeq[JsonValidationError]) = jsError
        jsArray:+ JsObject(Seq(
          "path" -> JsString(path.toJsonString),
          "messages" -> jsonValidationErrorsWrites.writes(list)))
      })
    }

  private def mailboxWritesWithFilteredProperties(properties: Option[Properties], capabilities: Set[CapabilityIdentifier]): Writes[Mailbox] = {
    mailboxWrites(Mailbox.propertiesFiltered(properties, capabilities))
  }

  private implicit def jsErrorWrites: Writes[JsError] = Json.writes[JsError]

  private implicit val problemDetailsWrites: Writes[ProblemDetails] = Json.writes[ProblemDetails]

  def serialize(session: Session): JsValue = Json.toJson(session)

  def serialize(requestObject: RequestObject): JsValue = Json.toJson(requestObject)

  def serialize(responseObject: ResponseObject): JsValue = Json.toJson(responseObject)

  def serialize(problemDetails: ProblemDetails): JsValue = Json.toJson(problemDetails)

  def serialize(mailbox: Mailbox)(implicit mailboxWrites: Writes[Mailbox]): JsValue = Json.toJson(mailbox)

  def serialize(mailboxGetResponse: MailboxGetResponse)(implicit mailboxWrites: Writes[Mailbox]): JsValue = Json.toJson(mailboxGetResponse)

  def serialize(mailboxGetResponse: MailboxGetResponse, properties: Option[Properties], capabilities: Set[CapabilityIdentifier]): JsValue = {
    serialize(mailboxGetResponse)(mailboxWritesWithFilteredProperties(properties, capabilities))
  }

  def serialize(errors: JsError): JsValue = Json.toJson(errors)

  def deserializeRequestObject(input: String): JsResult[RequestObject] = Json.parse(input).validate[RequestObject]

  def deserializeRequestObject(input: InputStream): JsResult[RequestObject] = Json.parse(input).validate[RequestObject]

  def deserializeResponseObject(input: String): JsResult[ResponseObject] = Json.parse(input).validate[ResponseObject]

  def deserializeMailboxGetRequest(input: String): JsResult[MailboxGetRequest] = Json.parse(input).validate[MailboxGetRequest]

  def deserializeMailboxGetRequest(input: JsValue): JsResult[MailboxGetRequest] = Json.fromJson[MailboxGetRequest](input)
}