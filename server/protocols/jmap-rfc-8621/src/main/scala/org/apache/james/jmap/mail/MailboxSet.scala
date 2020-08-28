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

package org.apache.james.jmap.mail

import eu.timepit.refined
import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.collection.NonEmpty
import eu.timepit.refined.refineV
import eu.timepit.refined.types.string.NonEmptyString
import org.apache.james.core.Username
import org.apache.james.jmap.json.Serializer
import org.apache.james.jmap.mail.MailboxName.MailboxName
import org.apache.james.jmap.mail.MailboxPatchObject.MailboxPatchObjectKey
import org.apache.james.jmap.mail.MailboxSetRequest.{MailboxCreationId, UnparsedMailboxId}
import org.apache.james.jmap.method.MailboxCreationParseException
import org.apache.james.jmap.model.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.model.State.State
import org.apache.james.jmap.model.{AccountId, CapabilityIdentifier}
import org.apache.james.jmap.routes.ProcessingContext
import org.apache.james.mailbox.model.{MailboxId, MailboxACL => JavaMailboxACL}
import org.apache.james.mailbox.{MailboxSession, Role}
import play.api.libs.json.{JsBoolean, JsError, JsNull, JsObject, JsString, JsSuccess, JsValue}

case class MailboxSetRequest(accountId: AccountId,
                             ifInState: Option[State],
                             create: Option[Map[MailboxCreationId, JsObject]],
                             update: Option[Map[UnparsedMailboxId, MailboxPatchObject]],
                             destroy: Option[Seq[UnparsedMailboxId]],
                             onDestroyRemoveEmails: Option[RemoveEmailsOnDestroy])

object MailboxSetRequest {
  type UnparsedMailboxIdConstraint = NonEmpty
  type MailboxCreationId = String Refined NonEmpty
  type UnparsedMailboxId = String Refined UnparsedMailboxIdConstraint

  def asUnparsed(mailboxId: MailboxId): UnparsedMailboxId = refined.refineV[UnparsedMailboxIdConstraint](mailboxId.serialize()) match {
    case Left(e) => throw new IllegalArgumentException(e)
    case scala.Right(value) => value
  }
}

case class RemoveEmailsOnDestroy(value: Boolean) extends AnyVal

object MailboxCreationRequest {
  private val serverSetProperty = Set("id", "sortOrder", "role", "totalEmails", "totalThreads", "unreadEmails", "unreadThreads", "myRights")
  private val assignableProperties = Set("name", "parentId", "isSubscribed", "rights")
  private val knownProperties = assignableProperties ++ serverSetProperty

  def validateProperties(jsObject: JsObject): Either[MailboxCreationParseException, JsObject] =
    (jsObject.keys.intersect(serverSetProperty), jsObject.keys.diff(knownProperties)) match {
      case (_, unknownProperties) if unknownProperties.nonEmpty =>
        Left(MailboxCreationParseException(MailboxSetError.invalidArgument(
          Some(SetErrorDescription("Some unknown properties were specified")),
          Some(toProperties(unknownProperties.toSet)))))
      case (specifiedServerSetProperties, _) if specifiedServerSetProperties.nonEmpty =>
        Left(MailboxCreationParseException(MailboxSetError.invalidArgument(
          Some(SetErrorDescription("Some server-set properties were specified")),
          Some(toProperties(specifiedServerSetProperties.toSet)))))
      case _ => scala.Right(jsObject)
    }

  private def toProperties(strings: Set[String]): Properties = Properties(strings
    .flatMap(string => {
      val refinedValue: Either[String, NonEmptyString] = refineV[NonEmpty](string)
      refinedValue.fold(_ => None,  Some(_))
    }))
}

case class MailboxCreationRequest(name: MailboxName,
                                  parentId: Option[UnparsedMailboxId],
                                  isSubscribed: Option[IsSubscribed],
                                  rights: Option[Rights])

object MailboxPatchObject {
  type KeyConstraint = NonEmpty
  type MailboxPatchObjectKey = String Refined KeyConstraint

  def notFound(property: String): Either[PatchUpdateValidationException, Update] = {
    val refinedKey: Either[String, MailboxPatchObjectKey] = refineV(property)
    refinedKey.fold[Either[PatchUpdateValidationException, Update]](
      cause => Left(InvalidPropertyException(property = property, cause = s"Invalid property specified in a patch object: $cause")),
      value => Left(UnsupportedPropertyUpdatedException(value)))
  }

  val roleProperty: MailboxPatchObjectKey = "role"
  val sortOrderProperty: MailboxPatchObjectKey = "sortOrder"
  val quotasProperty: MailboxPatchObjectKey = "quotas"
  val namespaceProperty: MailboxPatchObjectKey = "namespace"
  val unreadThreadsProperty: MailboxPatchObjectKey = "unreadThreads"
  val totalThreadsProperty: MailboxPatchObjectKey = "totalThreads"
  val unreadEmailsProperty: MailboxPatchObjectKey = "unreadEmails"
  val totalEmailsProperty: MailboxPatchObjectKey = "totalEmails"
  val myRightsProperty: MailboxPatchObjectKey = "myRights"
  val sharedWithPrefix = "sharedWith/"
}

case class MailboxPatchObject(value: Map[String, JsValue]) {
  def validate(processingContext: ProcessingContext,
               mailboxIdFactory: MailboxId.Factory,
               serializer: Serializer,
               capabilities: Set[CapabilityIdentifier],
               mailboxSession: MailboxSession): Either[PatchUpdateValidationException, ValidatedMailboxPatchObject] = {
    val asUpdatedIterable = updates(serializer, capabilities, processingContext, mailboxIdFactory, mailboxSession)

    val maybeParseException: Option[PatchUpdateValidationException] = asUpdatedIterable
      .flatMap(x => x match {
        case Left(e) => Some(e)
        case _ => None
      }).headOption

    val nameUpdate: Option[NameUpdate] = asUpdatedIterable
      .flatMap(x => x match {
        case scala.Right(NameUpdate(newName)) => Some(NameUpdate(newName))
        case _ => None
      }).headOption

    val isSubscribedUpdate: Option[IsSubscribedUpdate] = asUpdatedIterable
      .flatMap(x => x match {
        case scala.Right(IsSubscribedUpdate(isSubscribed)) => Some(IsSubscribedUpdate(isSubscribed))
        case _ => None
      }).headOption

    val rightsReset: Option[SharedWithResetUpdate] = asUpdatedIterable
      .flatMap(x => x match {
        case scala.Right(SharedWithResetUpdate(rights)) => Some(SharedWithResetUpdate(rights))
        case _ => None
      }).headOption

    val parentIdUpdate: Option[ParentIdUpdate] = asUpdatedIterable
      .flatMap(x => x match {
        case scala.Right(ParentIdUpdate(newId)) => Some(ParentIdUpdate(newId))
        case _ => None
      }).headOption

    val partialRightsUpdates: Seq[SharedWithPartialUpdate] = asUpdatedIterable.flatMap(x => x match {
      case scala.Right(SharedWithPartialUpdate(username, rights)) => Some(SharedWithPartialUpdate(username, rights))
      case _ => None
    }).toSeq

    val bothPartialAndResetRights: Option[PatchUpdateValidationException] = if (rightsReset.isDefined && partialRightsUpdates.nonEmpty) {
      Some(InvalidPatchException("Resetting rights and partial updates cannot be done in the same method call"))
    } else {
       None
    }
    maybeParseException
      .orElse(bothPartialAndResetRights)
      .map(e => Left(e))
      .getOrElse(scala.Right(ValidatedMailboxPatchObject(
        nameUpdate = nameUpdate,
        parentIdUpdate = parentIdUpdate,
        isSubscribedUpdate = isSubscribedUpdate,
        rightsReset = rightsReset,
        rightsPartialUpdates = partialRightsUpdates)))
  }

  def updates(serializer: Serializer,
              capabilities: Set[CapabilityIdentifier],
              processingContext: ProcessingContext,
              mailboxIdFactory: MailboxId.Factory,
              mailboxSession: MailboxSession): Iterable[Either[PatchUpdateValidationException, Update]] = value.map({
    case (property, newValue) => property match {
      case "name" => NameUpdate.parse(newValue, mailboxSession)
      case "parentId" => ParentIdUpdate.parse(newValue, processingContext, mailboxIdFactory)
      case "sharedWith" => SharedWithResetUpdate.parse(serializer, capabilities)(newValue)
      case "role" => Left(ServerSetPropertyException(MailboxPatchObject.roleProperty))
      case "sortOrder" => Left(ServerSetPropertyException(MailboxPatchObject.sortOrderProperty))
      case "quotas" => rejectQuotasUpdate(capabilities)
      case "namespace" => Left(ServerSetPropertyException(MailboxPatchObject.namespaceProperty))
      case "unreadThreads" => Left(ServerSetPropertyException(MailboxPatchObject.unreadThreadsProperty))
      case "totalThreads" => Left(ServerSetPropertyException(MailboxPatchObject.totalThreadsProperty))
      case "unreadEmails" => Left(ServerSetPropertyException(MailboxPatchObject.unreadEmailsProperty))
      case "totalEmails" => Left(ServerSetPropertyException(MailboxPatchObject.totalEmailsProperty))
      case "myRights" => Left(ServerSetPropertyException(MailboxPatchObject.myRightsProperty))
      case "isSubscribed" => IsSubscribedUpdate.parse(newValue)
      case property: String if property.startsWith(MailboxPatchObject.sharedWithPrefix) =>
        SharedWithPartialUpdate.parse(serializer, capabilities)(property, newValue)
      case property => MailboxPatchObject.notFound(property)
    }
  })

  private def rejectQuotasUpdate(capabilities: Set[CapabilityIdentifier]) = if (capabilities.contains(CapabilityIdentifier.JAMES_QUOTA)) {
      Left(ServerSetPropertyException(MailboxPatchObject.quotasProperty))
    } else {
      MailboxPatchObject.notFound("quotas")
    }
}

object ValidatedMailboxPatchObject {
  val nameProperty: NonEmptyString = "name"
  val parentIdProperty: NonEmptyString = "parentId"
}

case class ValidatedMailboxPatchObject(nameUpdate: Option[NameUpdate],
                                       parentIdUpdate: Option[ParentIdUpdate],
                                       isSubscribedUpdate: Option[IsSubscribedUpdate],
                                       rightsReset: Option[SharedWithResetUpdate],
                                       rightsPartialUpdates: Seq[SharedWithPartialUpdate]) {
  val shouldUpdateMailboxPath: Boolean = nameUpdate.isDefined || parentIdUpdate.isDefined

  val updatedProperties: Properties = Properties(Set(
      nameUpdate.map(_ => ValidatedMailboxPatchObject.nameProperty),
      parentIdUpdate.map(_ => ValidatedMailboxPatchObject.parentIdProperty))
    .flatMap(_.toList))
}

case class MailboxSetResponse(accountId: AccountId,
                              oldState: Option[State],
                              newState: State,
                              created: Option[Map[MailboxCreationId, MailboxCreationResponse]],
                              updated: Option[Map[MailboxId, MailboxUpdateResponse]],
                              destroyed: Option[Seq[MailboxId]],
                              notCreated: Option[Map[MailboxCreationId, MailboxSetError]],
                              notUpdated: Option[Map[UnparsedMailboxId, MailboxSetError]],
                              notDestroyed: Option[Map[UnparsedMailboxId, MailboxSetError]])

object MailboxSetError {
  val invalidArgumentValue: SetErrorType = "invalidArguments"
  val serverFailValue: SetErrorType = "serverFail"
  val invalidPatchValue: SetErrorType = "invalidPatch"
  val notFoundValue: SetErrorType = "notFound"
  val mailboxHasEmailValue: SetErrorType = "mailboxHasEmail"
  val mailboxHasChildValue: SetErrorType = "mailboxHasChild"
  val forbiddenValue: SetErrorType = "forbidden"

  def invalidArgument(description: Option[SetErrorDescription], properties: Option[Properties]) = MailboxSetError(invalidArgumentValue, description, properties)
  def serverFail(description: Option[SetErrorDescription], properties: Option[Properties]) = MailboxSetError(serverFailValue, description, properties)
  def notFound(description: Option[SetErrorDescription]) = MailboxSetError(notFoundValue, description, None)
  def mailboxHasEmail(description: Option[SetErrorDescription]) = MailboxSetError(mailboxHasEmailValue, description, None)
  def mailboxHasChild(description: Option[SetErrorDescription]) = MailboxSetError(mailboxHasChildValue, description, None)
  def invalidPatch(description: Option[SetErrorDescription]) = MailboxSetError(invalidPatchValue, description, None)
  def forbidden(description: Option[SetErrorDescription], properties: Option[Properties]) = MailboxSetError(forbiddenValue, description, properties)
}

case class MailboxSetError(`type`: SetErrorType, description: Option[SetErrorDescription], properties: Option[Properties])


object MailboxCreationResponse {
  def allProperties: Set[String] = Set("id", "sortOrder", "role", "totalEmails", "unreadEmails",
    "totalThreads", "unreadThreads", "myRights", "isSubscribed", "quotas")

  def propertiesFiltered(allowedCapabilities : Set[CapabilityIdentifier]) : Set[String] = {
    val propertiesForCapabilities: Map[CapabilityIdentifier, Set[String]] = Map(
      CapabilityIdentifier.JAMES_QUOTA -> Set("quotas"))

    val propertiesToHide = propertiesForCapabilities.filterNot(entry => allowedCapabilities.contains(entry._1))
      .flatMap(_._2)
      .toSet

    allProperties -- propertiesToHide
  }
}

case class MailboxCreationResponse(id: MailboxId,
                                   role: Option[Role],
                                   sortOrder: SortOrder,
                                   totalEmails: TotalEmails,
                                   unreadEmails: UnreadEmails,
                                   totalThreads: TotalThreads,
                                   unreadThreads: UnreadThreads,
                                   myRights: MailboxRights,
                                   quotas: Option[Quotas],
                                   isSubscribed: Option[IsSubscribed])

object MailboxSetResponse {
  def empty: MailboxUpdateResponse = MailboxUpdateResponse(JsObject(Map[String, JsValue]()))
}

case class MailboxUpdateResponse(value: JsObject)

object NameUpdate {
  def parse(newValue: JsValue, mailboxSession: MailboxSession): Either[PatchUpdateValidationException, Update] = newValue match {
    case JsString(newName) => if (newName.contains(mailboxSession.getPathDelimiter)) {
      Left(InvalidUpdateException("name", s"The mailbox '$newName' contains an illegal character: '${mailboxSession.getPathDelimiter}'"))
    } else {
      scala.Right(NameUpdate(newName))
    }
    case _ => Left(InvalidUpdateException("name", "Expecting a JSON string as an argument"))
  }
}

object SharedWithResetUpdate {
  def parse(serializer: Serializer, capabilities: Set[CapabilityIdentifier])
           (newValue: JsValue): Either[PatchUpdateValidationException, Update] =
    if (capabilities.contains(CapabilityIdentifier.JAMES_SHARES)) {
      serializer.deserializeRights(input = newValue) match {
        case JsSuccess(value, _) => scala.Right(SharedWithResetUpdate(value))
        case JsError(errors) => Left(InvalidUpdateException("sharedWith", s"Specified value do not match the expected JSON format: $errors"))
      }
    } else {
      MailboxPatchObject.notFound("sharedWith")
    }
}

object IsSubscribedUpdate {
  def parse(newValue: JsValue): Either[PatchUpdateValidationException, Update] = newValue match {
    case JsBoolean(value) => scala.Right(IsSubscribedUpdate(Some(IsSubscribed(value))))
    case JsNull => scala.Right(IsSubscribedUpdate(None))
    case _ => Left(InvalidUpdateException("isSubscribed", "Expecting a JSON boolean as an argument"))
  }
}

object SharedWithPartialUpdate {
  def parse(serializer: Serializer, capabilities: Set[CapabilityIdentifier])
           ( property: String, newValue: JsValue): Either[PatchUpdateValidationException, Update] =
    if (capabilities.contains(CapabilityIdentifier.JAMES_SHARES)) {
      parseUsername(property)
        .flatMap(username => parseRights(newValue, property, serializer)
          .map(rights => SharedWithPartialUpdate(username, rights)))
    } else {
      MailboxPatchObject.notFound(property)
    }

  def parseUsername(property: String): Either[PatchUpdateValidationException, Username] = try {
    scala.Right(Username.of(property.substring(MailboxPatchObject.sharedWithPrefix.length)))
  } catch {
    case e: Exception => Left(InvalidPropertyException(property, e.getMessage))
  }

  def parseRights(newValue: JsValue, property: String, serializer: Serializer): Either[PatchUpdateValidationException, Rfc4314Rights] = serializer.deserializeRfc4314Rights(newValue) match {
    case JsSuccess(rights, _) => scala.Right(rights)
    case JsError(errors) =>
      val refinedKey: Either[String, MailboxPatchObjectKey] = refineV(property)
      refinedKey.fold(
        refinedError => Left(InvalidPropertyException(property = property, cause = s"Invalid property specified in a patch object: $refinedError")),
        refinedProperty => Left(InvalidUpdateException(refinedProperty, s"Specified value do not match the expected JSON format: $errors")))
  }
}

object ParentIdUpdate {
  def parse(newValue: JsValue, processingContext: ProcessingContext, mailboxIdFactory: MailboxId.Factory): Either[PatchUpdateValidationException, Update] =
    newValue match {
      case JsString(id) =>
        val value: Either[String, UnparsedMailboxId] = refineV(id)
        value.fold(error => Left(InvalidUpdateException("parentId", error)),
          id => processingContext.resolveMailboxId(id, mailboxIdFactory)
            .fold(e => Left(InvalidUpdateException("parentId", e.getMessage)),
              mailboxId => scala.Right(ParentIdUpdate(Some(mailboxId)))))
      case JsNull => scala.Right(ParentIdUpdate(None))
      case _ => Left(InvalidUpdateException("parentId", "Expecting a JSON string or null as an argument"))
    }
}

sealed trait Update
case class NameUpdate(newName: String) extends Update
case class SharedWithResetUpdate(rights: Rights) extends Update
case class IsSubscribedUpdate(isSubscribed: Option[IsSubscribed]) extends Update
case class SharedWithPartialUpdate(username: Username, rights: Rfc4314Rights) extends Update {
  def asACLCommand(): JavaMailboxACL.ACLCommand = JavaMailboxACL.command().forUser(username).rights(rights.asJava).asReplacement()
}
case class ParentIdUpdate(newId: Option[MailboxId]) extends Update

class PatchUpdateValidationException() extends IllegalArgumentException
case class UnsupportedPropertyUpdatedException(property: MailboxPatchObjectKey) extends PatchUpdateValidationException
case class InvalidPropertyUpdatedException(property: MailboxPatchObjectKey) extends PatchUpdateValidationException
case class InvalidPropertyException(property: String, cause: String) extends PatchUpdateValidationException
case class InvalidUpdateException(property: MailboxPatchObjectKey, cause: String) extends PatchUpdateValidationException
case class ServerSetPropertyException(property: MailboxPatchObjectKey) extends PatchUpdateValidationException
case class InvalidPatchException(cause: String) extends PatchUpdateValidationException
