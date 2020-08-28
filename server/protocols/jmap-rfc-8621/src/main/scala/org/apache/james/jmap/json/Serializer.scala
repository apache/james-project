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
import java.time.format.DateTimeFormatter

import eu.timepit.refined._
import eu.timepit.refined.auto._
import eu.timepit.refined.collection.NonEmpty
import eu.timepit.refined.types.string.NonEmptyString
import javax.inject.Inject
import org.apache.james.core.{Domain, Username}
import org.apache.james.jmap.mail.MailboxSetRequest.{MailboxCreationId, UnparsedMailboxId}
import org.apache.james.jmap.mail.VacationResponse.{UnparsedVacationResponseId, VACATION_RESPONSE_ID}
import org.apache.james.jmap.mail.{DelegatedNamespace, FromDate, HtmlBody, Ids, IsEnabled, IsSubscribed, Mailbox, MailboxCreationRequest, MailboxCreationResponse, MailboxGetRequest, MailboxGetResponse, MailboxNamespace, MailboxPatchObject, MailboxRights, MailboxSetError, MailboxSetRequest, MailboxSetResponse, MailboxUpdateResponse, MayAddItems, MayCreateChild, MayDelete, MayReadItems, MayRemoveItems, MayRename, MaySetKeywords, MaySetSeen, MaySubmit, NotFound, PersonalNamespace, Properties, Quota, QuotaId, QuotaRoot, Quotas, RemoveEmailsOnDestroy, Rfc4314Rights, Right, Rights, SetErrorDescription, SortOrder, Subject, TextBody, ToDate, TotalEmails, TotalThreads, UnreadEmails, UnreadThreads, VacationResponse, VacationResponseGetRequest, VacationResponseGetResponse, VacationResponseId, VacationResponseIds, VacationResponseNotFound, Value}
import org.apache.james.jmap.model
import org.apache.james.jmap.model.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.model.Invocation.{Arguments, MethodCallId, MethodName}
import org.apache.james.jmap.model.{Account, Invocation, Session, _}
import org.apache.james.mailbox.Role
import org.apache.james.mailbox.model.MailboxACL.{Right => JavaRight}
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
  private implicit val vacationResponseCapabilityWrites: Writes[VacationResponseCapabilityProperties] = OWrites[VacationResponseCapabilityProperties](_ => Json.obj())

  private implicit def setCapabilityWrites(implicit corePropertiesWriter: Writes[CoreCapabilityProperties],
                                   mailCapabilityWrites: Writes[MailCapabilityProperties],
                                   quotaCapabilityWrites: Writes[QuotaCapabilityProperties],
                                   sharesCapabilityWrites: Writes[SharesCapabilityProperties],
                                   vacationResponseCapabilityWrites: Writes[VacationResponseCapabilityProperties]): Writes[Set[_ <: Capability]] =
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
          case capability: VacationResponseCapability =>
            jsObject.+(capability.identifier.value, vacationResponseCapabilityWrites.writes(capability.properties))
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

  implicit def mailboxWrites(properties: Properties): Writes[Mailbox] = Json.writes[Mailbox]
    .transform((o: JsObject) => JsObject(o.fields.filter(entry => {
      val refined: Either[String, NonEmptyString] = refineV[NonEmpty](entry._1)
      refined.fold(e => throw new RuntimeException(e), property => properties.contains(property))
    })))

  implicit def mailboxCreationResponseWrites(properties: Set[String]): Writes[MailboxCreationResponse] =
    Json.writes[MailboxCreationResponse]
      .transform((o: JsObject) => JsObject(o.fields.filter(entry => properties.contains(entry._1))))

  private implicit val idsRead: Reads[Ids] = Json.valueReads[Ids]
  private implicit val propertiesRead: Reads[Properties] = Json.valueReads[Properties]
  private implicit val mailboxGetRequest: Reads[MailboxGetRequest] = Json.reads[MailboxGetRequest]


  private implicit val mailboxRemoveEmailsOnDestroy: Reads[RemoveEmailsOnDestroy] = Json.valueFormat[RemoveEmailsOnDestroy]
  implicit val mailboxCreationRequest: Reads[MailboxCreationRequest] = Json.reads[MailboxCreationRequest]
  private implicit val mailboxPatchObject: Reads[MailboxPatchObject] = Json.valueReads[MailboxPatchObject]

  private implicit val mapPatchObjectByMailboxIdReads: Reads[Map[UnparsedMailboxId, MailboxPatchObject]] = _.validate[Map[String, MailboxPatchObject]]
    .flatMap(mapWithStringKey =>{
      mapWithStringKey
        .foldLeft[Either[JsError, Map[UnparsedMailboxId, MailboxPatchObject]]](scala.util.Right[JsError, Map[UnparsedMailboxId, MailboxPatchObject]](Map.empty))((acc: Either[JsError, Map[UnparsedMailboxId, MailboxPatchObject]], keyValue) => {
          acc match {
            case error@Left(_) => error
            case scala.util.Right(validatedAcc) =>
              val refinedKey: Either[String, UnparsedMailboxId] = refineV(keyValue._1)
              refinedKey match {
                case Left(error) => Left(JsError(error))
                case scala.util.Right(unparsedMailboxId) => scala.util.Right(validatedAcc + (unparsedMailboxId -> keyValue._2))
              }
          }
        }) match {
        case Left(jsError) => jsError
        case scala.util.Right(value) => JsSuccess(value)
      }
    })

  private implicit val mapCreationRequestByMailBoxCreationId: Reads[Map[MailboxCreationId, JsObject]] = _.validate[Map[String, JsObject]]
    .flatMap(mapWithStringKey => {
      mapWithStringKey
        .foldLeft[Either[JsError, Map[MailboxCreationId, JsObject]]](scala.util.Right[JsError, Map[MailboxCreationId, JsObject]](Map.empty))((acc: Either[JsError, Map[MailboxCreationId, JsObject]], keyValue) => {
          acc match {
            case error@Left(_) => error
            case scala.util.Right(validatedAcc) =>
              val refinedKey: Either[String, MailboxCreationId] = refineV(keyValue._1)
              refinedKey match {
                case Left(error) => Left(JsError(error))
                case scala.util.Right(mailboxCreationId) => scala.util.Right(validatedAcc + (mailboxCreationId -> keyValue._2))
              }
          }
        }) match {
        case Left(jsError) => jsError
        case scala.util.Right(value) => JsSuccess(value)
      }
    })

  private implicit val mailboxSetRequestReads: Reads[MailboxSetRequest] = Json.reads[MailboxSetRequest]

  private implicit def notFoundWrites(implicit mailboxIdWrites: Writes[UnparsedMailboxId]): Writes[NotFound] =
    notFound => JsArray(notFound.value.toList.map(mailboxIdWrites.writes))

  private implicit def mailboxGetResponseWrites(implicit mailboxWrites: Writes[Mailbox]): Writes[MailboxGetResponse] = Json.writes[MailboxGetResponse]

  private implicit def mailboxSetResponseWrites(implicit mailboxCreationResponseWrites: Writes[MailboxCreationResponse]): Writes[MailboxSetResponse] = Json.writes[MailboxSetResponse]

  private implicit val mailboxSetUpdateResponseWrites: Writes[MailboxUpdateResponse] = Json.valueWrites[MailboxUpdateResponse]

  private implicit val propertiesWrites: Writes[Properties] = Json.valueWrites[Properties]

  private implicit val setErrorDescriptionWrites: Writes[SetErrorDescription] = Json.valueWrites[SetErrorDescription]

  private implicit val mailboxSetErrorWrites: Writes[MailboxSetError] = Json.writes[MailboxSetError]

  private implicit def mailboxMapSetErrorForCreationWrites: Writes[Map[MailboxCreationId, MailboxSetError]] =
    (m: Map[MailboxCreationId, MailboxSetError]) => {
      m.foldLeft(JsObject.empty)((jsObject, kv) => {
        val (mailboxCreationId: MailboxCreationId, mailboxSetError: MailboxSetError) = kv
        jsObject.+(mailboxCreationId, mailboxSetErrorWrites.writes(mailboxSetError))
      })
    }
  private implicit def mailboxMapSetErrorWrites: Writes[Map[MailboxId, MailboxSetError]] =
    (m: Map[MailboxId, MailboxSetError]) => {
      m.foldLeft(JsObject.empty)((jsObject, kv) => {
        val (mailboxId: MailboxId, mailboxSetError: MailboxSetError) = kv
        jsObject.+(mailboxId.serialize(), mailboxSetErrorWrites.writes(mailboxSetError))
      })
    }
  private implicit def mailboxMapSetErrorWritesByClientId: Writes[Map[ClientId, MailboxSetError]] =
    (m: Map[ClientId, MailboxSetError]) => {
      m.foldLeft(JsObject.empty)((jsObject, kv) => {
        val (clientId: ClientId, mailboxSetError: MailboxSetError) = kv
        jsObject.+(clientId.value, mailboxSetErrorWrites.writes(mailboxSetError))
      })
    }

  private implicit def mailboxMapCreationResponseWrites(implicit mailboxSetCreationResponseWrites: Writes[MailboxCreationResponse]): Writes[Map[MailboxCreationId, MailboxCreationResponse]] =
    (m: Map[MailboxCreationId, MailboxCreationResponse]) => {
      m.foldLeft(JsObject.empty)((jsObject, kv) => {
        val (mailboxCreationId: MailboxCreationId, mailboxCreationResponse: MailboxCreationResponse) = kv
        jsObject.+(mailboxCreationId, mailboxSetCreationResponseWrites.writes(mailboxCreationResponse))
      })
    }
  private implicit def mailboxMapUpdateResponseWrites: Writes[Map[MailboxId, MailboxUpdateResponse]] =
    (m: Map[MailboxId, MailboxUpdateResponse]) => {
      m.foldLeft(JsObject.empty)((jsObject, kv) => {
        val (mailboxId: MailboxId, mailboxUpdateResponse: MailboxUpdateResponse) = kv
        jsObject.+(mailboxId.serialize(), mailboxSetUpdateResponseWrites.writes(mailboxUpdateResponse))
      })
    }

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

  private def mailboxWritesWithFilteredProperties(properties: Properties, capabilities: Set[CapabilityIdentifier]): Writes[Mailbox] = {
    mailboxWrites(Mailbox.propertiesFiltered(properties, capabilities))
  }

  private def mailboxCreationResponseWritesWithFilteredProperties(capabilities: Set[CapabilityIdentifier]): Writes[MailboxCreationResponse] = {
    mailboxCreationResponseWrites(MailboxCreationResponse.propertiesFiltered(capabilities))
  }

  private implicit val utcDateWrites: Writes[UTCDate] =
    utcDate => JsString(utcDate.asUTC.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX")))

  private implicit val vacationResponseIdWrites: Writes[VacationResponseId] = _ => JsString(VACATION_RESPONSE_ID)
  private implicit val isEnabledWrites: Writes[IsEnabled] = Json.valueWrites[IsEnabled]
  private implicit val fromDateWrites: Writes[FromDate] = Json.valueWrites[FromDate]
  private implicit val toDateWrites: Writes[ToDate] = Json.valueWrites[ToDate]
  private implicit val subjectWrites: Writes[Subject] = Json.valueWrites[Subject]
  private implicit val textBodyWrites: Writes[TextBody] = Json.valueWrites[TextBody]
  private implicit val htmlBodyWrites: Writes[HtmlBody] = Json.valueWrites[HtmlBody]

  implicit val vacationResponseWrites: Writes[VacationResponse] = Json.writes[VacationResponse]

  private implicit val vacationResponseIdReads: Reads[VacationResponseIds] = Json.valueReads[VacationResponseIds]
  private implicit val vacationResponseGetRequest: Reads[VacationResponseGetRequest] = Json.reads[VacationResponseGetRequest]

  private implicit def vacationResponseNotFoundWrites(implicit idWrites: Writes[UnparsedVacationResponseId]): Writes[VacationResponseNotFound] =
    notFound => JsArray(notFound.value.toList.map(idWrites.writes))

  private implicit def vacationResponseGetResponseWrites(implicit vacationResponseWrites: Writes[VacationResponse]): Writes[VacationResponseGetResponse] =
    Json.writes[VacationResponseGetResponse]

  private implicit def jsErrorWrites: Writes[JsError] = Json.writes[JsError]

  private implicit val problemDetailsWrites: Writes[ProblemDetails] = Json.writes[ProblemDetails]

  def serialize(session: Session): JsValue = Json.toJson(session)

  def serialize(requestObject: RequestObject): JsValue = Json.toJson(requestObject)

  def serialize(responseObject: ResponseObject): JsValue = Json.toJson(responseObject)

  def serialize(problemDetails: ProblemDetails): JsValue = Json.toJson(problemDetails)

  def serialize(mailbox: Mailbox)(implicit mailboxWrites: Writes[Mailbox]): JsValue = Json.toJson(mailbox)

  def serialize(mailboxGetResponse: MailboxGetResponse)(implicit mailboxWrites: Writes[Mailbox]): JsValue = Json.toJson(mailboxGetResponse)

  def serialize(mailboxGetResponse: MailboxGetResponse, properties: Properties, capabilities: Set[CapabilityIdentifier]): JsValue =
    serialize(mailboxGetResponse)(mailboxWritesWithFilteredProperties(properties, capabilities))

  def serialize(mailboxSetResponse: MailboxSetResponse)
               (implicit mailboxCreationResponseWrites: Writes[MailboxCreationResponse]): JsValue =
    Json.toJson(mailboxSetResponse)(mailboxSetResponseWrites(mailboxCreationResponseWrites))

  def serialize(mailboxSetResponse: MailboxSetResponse, capabilities: Set[CapabilityIdentifier]): JsValue =
    serialize(mailboxSetResponse)(mailboxCreationResponseWritesWithFilteredProperties(capabilities))

  def serialize(errors: JsError): JsValue = Json.toJson(errors)

  def serialize(vacationResponse: VacationResponse): JsValue = Json.toJson(vacationResponse)

  def serialize(vacationResponseGetResponse: VacationResponseGetResponse): JsValue = Json.toJson(vacationResponseGetResponse)

  def deserializeRequestObject(input: String): JsResult[RequestObject] = Json.parse(input).validate[RequestObject]

  def deserializeRequestObject(input: InputStream): JsResult[RequestObject] = Json.parse(input).validate[RequestObject]

  def deserializeResponseObject(input: String): JsResult[ResponseObject] = Json.parse(input).validate[ResponseObject]

  def deserializeMailboxGetRequest(input: String): JsResult[MailboxGetRequest] = Json.parse(input).validate[MailboxGetRequest]

  def deserializeMailboxGetRequest(input: JsValue): JsResult[MailboxGetRequest] = Json.fromJson[MailboxGetRequest](input)

  def deserializeMailboxSetRequest(input: JsValue): JsResult[MailboxSetRequest] = Json.fromJson[MailboxSetRequest](input)

  def deserializeRights(input: JsValue): JsResult[Rights] = Json.fromJson[Rights](input)

  def deserializeRfc4314Rights(input: JsValue): JsResult[Rfc4314Rights] = Json.fromJson[Rfc4314Rights](input)

  def deserializeVacationResponseGetRequest(input: String): JsResult[VacationResponseGetRequest] = Json.parse(input).validate[VacationResponseGetRequest]

  def deserializeVacationResponseGetRequest(input: JsValue): JsResult[VacationResponseGetRequest] = Json.fromJson[VacationResponseGetRequest](input)
}