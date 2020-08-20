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
import eu.timepit.refined.boolean.And
import eu.timepit.refined.collection.NonEmpty
import eu.timepit.refined.refineV
import eu.timepit.refined.string.StartsWith
import org.apache.james.jmap.json.Serializer
import org.apache.james.jmap.mail.MailboxName.MailboxName
import org.apache.james.jmap.mail.MailboxPatchObject.MailboxPatchObjectKey
import org.apache.james.jmap.mail.MailboxSetRequest.{MailboxCreationId, UnparsedMailboxId}
import org.apache.james.jmap.model.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.model.State.State
import org.apache.james.jmap.model.{AccountId, CapabilityIdentifier}
import org.apache.james.mailbox.Role
import org.apache.james.mailbox.model.MailboxId
import play.api.libs.json._

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
case class MailboxCreationRequest(name: MailboxName,
                                  parentId: Option[UnparsedMailboxId],
                                  isSubscribed: Option[IsSubscribed],
                                  rights: Option[Rights])

object MailboxPatchObject {
  type KeyConstraint = NonEmpty And StartsWith["/"]
  type MailboxPatchObjectKey = String Refined KeyConstraint
}

case class MailboxPatchObject(value: Map[String, JsValue]) {
  def updates(serializer: Serializer): Iterable[Either[PatchUpdateValidationException, Update]] = value.map({
    case (property, newValue) => property match {
      case "/name" => NameUpdate.parse(newValue)
      case "/sharedWith" => SharedWithResetUpdate.parse(newValue, serializer)
      case property =>
        val refinedKey: Either[String, MailboxPatchObjectKey] = refineV(property)
        refinedKey.fold[Either[PatchUpdateValidationException, Update]](
          cause => Left(InvalidPropertyException(property = property, cause = s"Invalid property specified in a patch object: $cause")),
          value => Left(UnsupportedPropertyUpdatedException(value)))
    }
  })
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
  def parse(newValue: JsValue): Either[PatchUpdateValidationException, Update] = newValue match {
    case JsString(newName) => scala.Right(NameUpdate(newName))
    case _ => Left(InvalidUpdateException("/name", "Expecting a JSON string as an argument"))
  }
}

object SharedWithResetUpdate {
  def parse(newValue: JsValue, serializer: Serializer): Either[PatchUpdateValidationException, Update] = serializer.deserializeRights(input = newValue) match {
    case JsSuccess(value, _) => scala.Right(SharedWithResetUpdate(value))
    case JsError(errors) => Left(InvalidUpdateException("/sharedWith", s"Specified value do not match the expected JSON format: $errors"))
  }
}

sealed trait Update
case class NameUpdate(newName: String) extends Update
case class SharedWithResetUpdate(rights: Rights) extends Update

class PatchUpdateValidationException() extends IllegalArgumentException
case class UnsupportedPropertyUpdatedException(property: MailboxPatchObjectKey) extends PatchUpdateValidationException
case class InvalidPropertyUpdatedException(property: MailboxPatchObjectKey) extends PatchUpdateValidationException
case class InvalidPropertyException(property: String, cause: String) extends PatchUpdateValidationException
case class InvalidUpdateException(property: MailboxPatchObjectKey, cause: String) extends PatchUpdateValidationException