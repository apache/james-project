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

import eu.timepit.refined
import eu.timepit.refined._
import jakarta.inject.Inject
import org.apache.james.core.{Domain, Username}
import org.apache.james.jmap.core.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.core.Id.IdConstraint
import org.apache.james.jmap.core.{ClientId, Properties, SetError, UuidState}
import org.apache.james.jmap.mail.{Ids, IsSubscribed, Mailbox, MailboxChangesRequest, MailboxChangesResponse, MailboxCreationId, MailboxCreationRequest, MailboxCreationResponse, MailboxGetRequest, MailboxGetResponse, MailboxNamespace, MailboxPatchObject, MailboxRights, MailboxSetRequest, MailboxSetResponse, MailboxUpdateResponse, MayAddItems, MayCreateChild, MayDelete, MayReadItems, MayRemoveItems, MayRename, MaySetKeywords, MaySetSeen, MaySubmit, NotFound, Quota, QuotaId, QuotaRoot, Quotas, RemoveEmailsOnDestroy, Rfc4314Rights, Right, Rights, SortOrder, TotalEmails, TotalThreads, UnparsedMailboxId, UnreadEmails, UnreadThreads, Value}
import org.apache.james.mailbox.Role
import org.apache.james.mailbox.model.MailboxACL.{Right => JavaRight}
import org.apache.james.mailbox.model.{MailboxACL, MailboxId}
import play.api.libs.json._

import scala.language.implicitConversions
import scala.util.Try

class MailboxSerializer @Inject()(mailboxIdFactory: MailboxId.Factory) {
  private implicit val mailboxIdWrites: Writes[MailboxId] = mailboxId => JsString(mailboxId.serialize)
  private implicit val mailboxIdReads: Reads[MailboxId] = {
    case JsString(serializedMailboxId) => Try(JsSuccess(mailboxIdFactory.fromString(serializedMailboxId))).getOrElse(JsError())
    case _ => JsError()
  }
  private implicit val unparsedMailboxIdWrites: Writes[UnparsedMailboxId] = Json.valueWrites[UnparsedMailboxId]
  private implicit val unparsedMailboxIdReads: Reads[UnparsedMailboxId] = {
    case JsString(string) =>
      refined.refineV[IdConstraint](string)
        .fold(
          e => JsError(s"mailboxId does not match Id constraints: $e"),
          id => JsSuccess(UnparsedMailboxId(id)))
    case _ => JsError("mailboxId needs to be represented by a JsString")
  }

  private implicit val roleWrites: Writes[Role] = Writes(role => JsString(role.serialize))
  private implicit val sortOrderWrites: Writes[SortOrder] = Json.valueWrites[SortOrder]
  private implicit val totalEmailsWrites: Writes[TotalEmails] = Json.valueWrites[TotalEmails]
  private implicit val unreadEmailsWrites: Writes[UnreadEmails] = Json.valueWrites[UnreadEmails]
  private implicit val totalThreadsWrites: Writes[TotalThreads] = Json.valueWrites[TotalThreads]
  private implicit val unreadThreadsWrites: Writes[UnreadThreads] = Json.valueWrites[UnreadThreads]
  private implicit val isSubscribedWrites: Format[IsSubscribed] = Json.valueFormat[IsSubscribed]

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

  private implicit val mailboxNamespaceWrites: Writes[MailboxNamespace] = value => JsString(value.serialize())

  private implicit val mailboxACLWrites: Writes[MailboxACL.Right] = right => JsString(right.asCharacter.toString)

  private implicit val rightWrites: Writes[Right] = Json.valueWrites[Right]
  private implicit val rightRead: Reads[Right] = {
    case jsString: JsString =>
      if (jsString.value.length != 1) {
        JsError("Rights must have size 1")
      } else {
        Right.forChar(jsString.value.charAt(0))
          .map(right => JsSuccess(right))
          .getOrElse(JsError(s"Unknown right '${jsString.value}'"))
      }
    case _ => JsError("Right must be represented as a String")
  }
  private implicit val mailboxJavaRightReads: Reads[JavaRight] = value => rightRead.reads(value).map(right => right.toMailboxRight)
  private implicit val mailboxRfc4314RightsReads: Reads[Rfc4314Rights] = Json.valueReads[Rfc4314Rights]
  private implicit val rightSeqWrites: Writes[Seq[Right]] = seq => JsArray(seq.map(rightWrites.writes))
  private implicit val rightsMapWrites: Writes[Map[Username, Seq[Right]]] =
    mapWrites[Username, Seq[Right]](_.asString(), rightSeqWrites)
  private implicit val rightsWrites: Writes[Rights] = Json.valueWrites[Rights]

  private implicit val mapRightsReads: Reads[Map[Username, Seq[Right]]] = _.validate[Map[String, Seq[Right]]]
    .map(rawMap =>
      rawMap.map(entry => (Username.of(entry._1), entry._2)))
  private implicit val rightsReads: Reads[Rights] = json => mapRightsReads.reads(json).map(rawMap => Rights(rawMap))

  private implicit val domainWrites: Writes[Domain] = domain => JsString(domain.asString)
  private implicit val quotaRootWrites: Writes[QuotaRoot] = Json.writes[QuotaRoot]
  private implicit val quotaIdWrites: Writes[QuotaId] = Json.valueWrites[QuotaId]

  private implicit val quotaValueWrites: Writes[Value] = Json.writes[Value]
  private implicit val quotaMapWrites: Writes[Map[Quotas.Type, Value]] =
    mapWrites[Quotas.Type, Value](_.toString, quotaValueWrites)
  private implicit val quotaWrites: Writes[Quota] = Json.valueWrites[Quota]
  private implicit val quotasMapWrites: Writes[Map[QuotaId, Quota]] =
    mapWrites[QuotaId, Quota](_.getName, quotaWrites)
  private implicit val quotasWrites: Writes[Quotas] = Json.valueWrites[Quotas]

  implicit val mailboxWrites: Writes[Mailbox] = Json.writes[Mailbox]

  implicit val mailboxCreationResponseWrites: Writes[MailboxCreationResponse] = Json.writes[MailboxCreationResponse]

  private implicit val idsRead: Reads[Ids] = Json.valueReads[Ids]

  private implicit val mailboxGetRequest: Reads[MailboxGetRequest] = Json.reads[MailboxGetRequest]

  private implicit val mailboxChangesRequest: Reads[MailboxChangesRequest] = Json.reads[MailboxChangesRequest]

  private implicit val mailboxRemoveEmailsOnDestroy: Reads[RemoveEmailsOnDestroy] = Json.valueFormat[RemoveEmailsOnDestroy]
  implicit val mailboxCreationRequest: Reads[MailboxCreationRequest] = Json.reads[MailboxCreationRequest]
  private implicit val mailboxPatchObject: Reads[MailboxPatchObject] = Json.valueReads[MailboxPatchObject]

  private implicit val mapPatchObjectByMailboxIdReads: Reads[Map[UnparsedMailboxId, MailboxPatchObject]] =
    Reads.mapReads[UnparsedMailboxId, MailboxPatchObject] {string =>refineV[IdConstraint](string)
      .fold(e => JsError(s"mailboxId needs to match id contraints: $e"),
        id => JsSuccess(UnparsedMailboxId(id))) }

  private implicit val mapCreationRequestByMailBoxCreationId: Reads[Map[MailboxCreationId, JsObject]] =
    Reads.mapReads[MailboxCreationId, JsObject] {string => refineV[IdConstraint](string)
      .fold(e => JsError(s"mailbox creationId needs to match id contraints: $e"),
        id => JsSuccess(MailboxCreationId(id))) }

  private implicit val mailboxSetRequestReads: Reads[MailboxSetRequest] = Json.reads[MailboxSetRequest]

  private implicit val notFoundWrites: Writes[NotFound] = Json.valueWrites[NotFound]

  private implicit val stateWrites: Writes[UuidState] = Json.valueWrites[UuidState]
  private implicit val mailboxGetResponseWrites: Writes[MailboxGetResponse] = Json.writes[MailboxGetResponse]

  private implicit val mailboxSetUpdateResponseWrites: Writes[MailboxUpdateResponse] = Json.valueWrites[MailboxUpdateResponse]

  private implicit val mailboxMapSetErrorForCreationWrites: Writes[Map[MailboxCreationId, SetError]] =
    mapWrites[MailboxCreationId, SetError](_.id.value, setErrorWrites)
  private implicit val mailboxMapSetErrorWrites: Writes[Map[MailboxId, SetError]] =
    mapWrites[MailboxId, SetError](_.serialize(), setErrorWrites)
  private implicit val mailboxMapSetErrorWritesByClientId: Writes[Map[ClientId, SetError]] =
    mapWrites[ClientId, SetError](_.value.value, setErrorWrites)
  private implicit val mailboxMapCreationResponseWrites: Writes[Map[MailboxCreationId, MailboxCreationResponse]] =
    mapWrites[MailboxCreationId, MailboxCreationResponse](_.id.value, mailboxCreationResponseWrites)
  private implicit val mailboxMapUpdateResponseWrites: Writes[Map[MailboxId, MailboxUpdateResponse]] =
    mapWrites[MailboxId, MailboxUpdateResponse](_.serialize(), mailboxSetUpdateResponseWrites)
  private implicit val mailboxMapUpdateErrorWrites: Writes[Map[UnparsedMailboxId, SetError]] =
    mapWrites[UnparsedMailboxId, SetError](_.id.value, setErrorWrites)

  private implicit val mailboxSetResponseWrites: Writes[MailboxSetResponse] = Json.writes[MailboxSetResponse]
  private implicit val changesResponseWrites: Writes[MailboxChangesResponse] = response =>
    Json.obj(
      "accountId" -> response.accountId,
      "oldState" -> response.oldState,
      "newState" -> response.newState,
      "hasMoreChanges" -> response.hasMoreChanges,
      "updatedProperties" -> response.updatedProperties,
      "created" -> response.created,
      "updated" -> response.updated,
      "destroyed" -> response.destroyed)

  def serializeChanges(changesResponse: MailboxChangesResponse): JsObject = Json.toJson(changesResponse).as[JsObject]

  def serialize(mailbox: Mailbox): JsValue = Json.toJson(mailbox)

  def serialize(mailboxGetResponse: MailboxGetResponse, properties: Properties, capabilities: Set[CapabilityIdentifier]): JsValue = {
    val mailboxTransformation = Mailbox.propertiesFiltered(properties, capabilities)
    Json.toJson(mailboxGetResponse)
      .transform((__ \ "list").json.update {
        case JsArray(underlying) => JsSuccess(JsArray(underlying.map {
          case jsonObject: JsObject => mailboxTransformation.filter(jsonObject)
          case jsValue => jsValue
        }))
        case jsValue => JsError(s"expected JsArray, got $jsValue")
      }).get
  }

  def serialize(mailboxSetResponse: MailboxSetResponse, capabilities: Set[CapabilityIdentifier]): JsValue =
    Json.toJson(mailboxSetResponse)
      .transform[JsValue] {
        case JsObject(underlying) => JsSuccess[JsValue](JsObject(underlying.map {
          case ("created", createdEntry: JsObject) =>
            ("created", createdEntry match {
              case JsObject(createdEntries) => JsObject(createdEntries.map {
                case (key, serializedMailbox: JsObject) => (key, MailboxCreationResponse.propertiesFiltered(capabilities).filter(serializedMailbox))
                case (key, value) => (key, value)
              })
              case jsValue: JsValue => jsValue
            })
          case (key, value) => (key, value)
        }))
        case jsValue => JsSuccess[JsValue](jsValue)
      }.get

  def deserializeMailboxGetRequest(input: String): JsResult[MailboxGetRequest] = Json.parse(input).validate[MailboxGetRequest]

  def deserializeMailboxChangesRequest(input: JsValue): JsResult[MailboxChangesRequest] = Json.fromJson[MailboxChangesRequest](input)

  def deserializeMailboxGetRequest(input: JsValue): JsResult[MailboxGetRequest] = Json.fromJson[MailboxGetRequest](input)

  def deserializeMailboxSetRequest(input: JsValue): JsResult[MailboxSetRequest] = Json.fromJson[MailboxSetRequest](input)

  def deserializeRights(input: JsValue): JsResult[Rights] = Json.fromJson[Rights](input)

  def deserializeRfc4314Rights(input: JsValue): JsResult[Rfc4314Rights] = Json.fromJson[Rfc4314Rights](input)
}