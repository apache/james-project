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

import eu.timepit.refined._
import eu.timepit.refined.collection.NonEmpty
import javax.inject.Inject
import org.apache.james.core.{Domain, Username}
import org.apache.james.jmap.mail.MailboxGet.{UnparsedMailboxId, UnparsedMailboxIdConstraint}
import org.apache.james.jmap.mail.MailboxSetRequest.MailboxCreationId
import org.apache.james.jmap.mail.{DelegatedNamespace, Ids, IsSubscribed, Mailbox, MailboxCreationRequest, MailboxCreationResponse, MailboxGetRequest, MailboxGetResponse, MailboxNamespace, MailboxPatchObject, MailboxRights, MailboxSetRequest, MailboxSetResponse, MailboxUpdateResponse, MayAddItems, MayCreateChild, MayDelete, MayReadItems, MayRemoveItems, MayRename, MaySetKeywords, MaySetSeen, MaySubmit, NotFound, PersonalNamespace, Quota, QuotaId, QuotaRoot, Quotas, RemoveEmailsOnDestroy, Rfc4314Rights, Right, Rights, SortOrder, TotalEmails, TotalThreads, UnreadEmails, UnreadThreads, Value}
import org.apache.james.jmap.model.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.model._
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

  private implicit val mailboxNamespaceWrites: Writes[MailboxNamespace] = {
    case _: PersonalNamespace => JsString("Personal")
    case delegated: DelegatedNamespace => JsString(s"Delegated[${delegated.owner.asString}]")
  }

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
  private implicit val rightsWrites: Writes[Rights] = Json.valueWrites[Rights]

  private implicit val mapRightsReads: Reads[Map[Username, Seq[Right]]] = _.validate[Map[String, Seq[Right]]]
    .map(rawMap =>
      rawMap.map(entry => (Username.of(entry._1), entry._2)))
  private implicit val rightsReads: Reads[Rights] = json => mapRightsReads.reads(json).map(rawMap => Rights(rawMap))

  private implicit def rightsMapWrites(implicit rightWriter: Writes[Seq[Right]]): Writes[Map[Username, Seq[Right]]] =
    mapWrites[Username, Seq[Right]](_.asString(), rightWriter)

  private implicit val domainWrites: Writes[Domain] = domain => JsString(domain.asString)
  private implicit val quotaRootWrites: Writes[QuotaRoot] = Json.writes[QuotaRoot]
  private implicit val quotaIdWrites: Writes[QuotaId] = Json.valueWrites[QuotaId]

  private implicit val quotaValueWrites: Writes[Value] = Json.writes[Value]
  private implicit val quotaWrites: Writes[Quota] = Json.valueWrites[Quota]

  private implicit def quotaMapWrites(implicit valueWriter: Writes[Value]): Writes[Map[Quotas.Type, Value]] =
    mapWrites[Quotas.Type, Value](_.toString, valueWriter)

  private implicit val quotasWrites: Writes[Quotas] = Json.valueWrites[Quotas]

  private implicit def quotasMapWrites(implicit quotaWriter: Writes[Quota]): Writes[Map[QuotaId, Quota]] =
    mapWrites[QuotaId, Quota](_.getName, quotaWriter)

  implicit def mailboxWrites(properties: Properties): Writes[Mailbox] = Json.writes[Mailbox]
    .transform(properties.filter(_))

  implicit def mailboxCreationResponseWrites(properties: Properties): Writes[MailboxCreationResponse] =
    Json.writes[MailboxCreationResponse]
      .transform(properties.filter(_))

  private implicit val idsRead: Reads[Ids] = Json.valueReads[Ids]

  private implicit val mailboxGetRequest: Reads[MailboxGetRequest] = Json.reads[MailboxGetRequest]

  private implicit val mailboxRemoveEmailsOnDestroy: Reads[RemoveEmailsOnDestroy] = Json.valueFormat[RemoveEmailsOnDestroy]
  implicit val mailboxCreationRequest: Reads[MailboxCreationRequest] = Json.reads[MailboxCreationRequest]
  private implicit val mailboxPatchObject: Reads[MailboxPatchObject] = Json.valueReads[MailboxPatchObject]

  private implicit val mapPatchObjectByMailboxIdReads: Reads[Map[UnparsedMailboxId, MailboxPatchObject]] =
    readMapEntry[UnparsedMailboxId, MailboxPatchObject](s => refineV[UnparsedMailboxIdConstraint](s),
      mailboxPatchObject)

  private implicit val mapCreationRequestByMailBoxCreationId: Reads[Map[MailboxCreationId, JsObject]] =
    readMapEntry[MailboxCreationId, JsObject](s => refineV[NonEmpty](s),
      {
        case o: JsObject => JsSuccess(o)
        case _ => JsError("Expecting a JsObject as a creation entry")
      })

  private implicit val mailboxSetRequestReads: Reads[MailboxSetRequest] = Json.reads[MailboxSetRequest]

  private implicit def notFoundWrites(implicit mailboxIdWrites: Writes[UnparsedMailboxId]): Writes[NotFound] =
    notFound => JsArray(notFound.value.toList.map(mailboxIdWrites.writes))

  private implicit def mailboxGetResponseWrites(implicit mailboxWrites: Writes[Mailbox]): Writes[MailboxGetResponse] = Json.writes[MailboxGetResponse]

  private implicit def mailboxSetResponseWrites(implicit mailboxCreationResponseWrites: Writes[MailboxCreationResponse]): Writes[MailboxSetResponse] = Json.writes[MailboxSetResponse]

  private implicit val mailboxSetUpdateResponseWrites: Writes[MailboxUpdateResponse] = Json.valueWrites[MailboxUpdateResponse]

  private implicit def mailboxMapSetErrorForCreationWrites: Writes[Map[MailboxCreationId, SetError]] =
    mapWrites[MailboxCreationId, SetError](_.value, setErrorWrites)
  private implicit def mailboxMapSetErrorWrites: Writes[Map[MailboxId, SetError]] =
    mapWrites[MailboxId, SetError](_.serialize(), setErrorWrites)
  private implicit def mailboxMapSetErrorWritesByClientId: Writes[Map[ClientId, SetError]] =
    mapWrites[ClientId, SetError](_.value.value, setErrorWrites)
  private implicit def mailboxMapCreationResponseWrites(implicit mailboxSetCreationResponseWrites: Writes[MailboxCreationResponse]): Writes[Map[MailboxCreationId, MailboxCreationResponse]] =
    mapWrites[MailboxCreationId, MailboxCreationResponse](_.value, mailboxSetCreationResponseWrites)
  private implicit def mailboxMapUpdateResponseWrites: Writes[Map[MailboxId, MailboxUpdateResponse]] =
    mapWrites[MailboxId, MailboxUpdateResponse](_.serialize(), mailboxSetUpdateResponseWrites)

  private def mailboxWritesWithFilteredProperties(properties: Properties, capabilities: Set[CapabilityIdentifier]): Writes[Mailbox] = {
    mailboxWrites(Mailbox.propertiesFiltered(properties, capabilities))
  }

  private def mailboxCreationResponseWritesWithFilteredProperties(capabilities: Set[CapabilityIdentifier]): Writes[MailboxCreationResponse] = {
    mailboxCreationResponseWrites(MailboxCreationResponse.propertiesFiltered(capabilities))
  }

  def serialize(mailbox: Mailbox)(implicit mailboxWrites: Writes[Mailbox]): JsValue = Json.toJson(mailbox)

  def serialize(mailboxGetResponse: MailboxGetResponse)(implicit mailboxWrites: Writes[Mailbox]): JsValue = Json.toJson(mailboxGetResponse)

  def serialize(mailboxGetResponse: MailboxGetResponse, properties: Properties, capabilities: Set[CapabilityIdentifier]): JsValue =
    serialize(mailboxGetResponse)(mailboxWritesWithFilteredProperties(properties, capabilities))

  def serialize(mailboxSetResponse: MailboxSetResponse)
               (implicit mailboxCreationResponseWrites: Writes[MailboxCreationResponse]): JsValue =
    Json.toJson(mailboxSetResponse)(mailboxSetResponseWrites(mailboxCreationResponseWrites))

  def serialize(mailboxSetResponse: MailboxSetResponse, capabilities: Set[CapabilityIdentifier]): JsValue =
    serialize(mailboxSetResponse)(mailboxCreationResponseWritesWithFilteredProperties(capabilities))

  def deserializeMailboxGetRequest(input: String): JsResult[MailboxGetRequest] = Json.parse(input).validate[MailboxGetRequest]

  def deserializeMailboxGetRequest(input: JsValue): JsResult[MailboxGetRequest] = Json.fromJson[MailboxGetRequest](input)

  def deserializeMailboxSetRequest(input: JsValue): JsResult[MailboxSetRequest] = Json.fromJson[MailboxSetRequest](input)

  def deserializeRights(input: JsValue): JsResult[Rights] = Json.fromJson[Rights](input)

  def deserializeRfc4314Rights(input: JsValue): JsResult[Rfc4314Rights] = Json.fromJson[Rfc4314Rights](input)
}