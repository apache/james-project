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

import cats.implicits._
import eu.timepit.refined.auto.autoUnwrap
import eu.timepit.refined.refineV
import org.apache.james.core.MailAddress
import org.apache.james.jmap.core.Id.{Id, IdConstraint}
import org.apache.james.jmap.core.Properties.toProperties
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{AccountId, Id, JmapRfc8621Configuration, SetError, UTCDate, UuidState}
import org.apache.james.jmap.method.{EmailSubmissionCreationParseException, ValidableRequest, WithAccountId}
import org.apache.james.mailbox.model.MessageId
import play.api.libs.json.JsObject

object EmailSubmissionId {
  def generate: EmailSubmissionId = EmailSubmissionId(Id.validate(UUID.randomUUID().toString).toOption.get)
}

object ParameterName {
  val holdFor: ParameterName = ParameterName("holdFor")
  val holdUntil: ParameterName = ParameterName("holdUntil")
}
case class EmailSubmissionCreationId(id: Id)

case class EmailSubmissionSetRequest(accountId: AccountId,
                                     create: Option[Map[EmailSubmissionCreationId, JsObject]],
                                     onSuccessUpdateEmail: Option[Map[EmailSubmissionCreationId, JsObject]],
                                     onSuccessDestroyEmail: Option[List[EmailSubmissionCreationId]]) extends WithAccountId with ValidableRequest {
  def implicitEmailSetRequest(messageIdResolver: EmailSubmissionCreationId => Either[IllegalArgumentException, Option[MessageId]]): Either[IllegalArgumentException, Option[EmailSetRequest]] =
    for {
      update <- resolveOnSuccessUpdateEmail(messageIdResolver)
      destroy <- resolveOnSuccessDestroyEmail(messageIdResolver)
    } yield {
      if (update.exists(_.nonEmpty) || destroy.exists(_.nonEmpty)) {
        Some(EmailSetRequest(
          accountId = accountId,
          create = None,
          update = update,
          destroy = destroy.map(DestroyIds(_))))
      } else {
        None
      }
    }

  def resolveOnSuccessUpdateEmail(messageIdResolver: EmailSubmissionCreationId => Either[IllegalArgumentException, Option[MessageId]]): Either[IllegalArgumentException, Option[Map[UnparsedMessageId, JsObject]]]=
    onSuccessUpdateEmail.map(map => map.toList
        .map {
          case (creationId, json) => messageIdResolver.apply(creationId).map(msgIdOption => (msgIdOption, json))
        }
        .filter(e => e.isLeft || e.toOption.get._1.isDefined)
        .map(e => e.map { case (msgIdOption, json) => (EmailSet.asUnparsed(msgIdOption.get), json) })
        .sequence
        .map(list => list.toMap))
      .sequence

  def resolveOnSuccessDestroyEmail(messageIdResolver: EmailSubmissionCreationId => Either[IllegalArgumentException, Option[MessageId]]): Either[IllegalArgumentException, Option[List[UnparsedMessageId]]]=
    onSuccessDestroyEmail.map(list => list
        .map(creationId => messageIdResolver.apply(creationId))
        .filter(e => e.isLeft || e.toOption.get.isDefined)
        .map(e => e.map { case (msgIdOption) => EmailSet.asUnparsed(msgIdOption.get) })
        .sequence)
      .sequence

  def validate(configuration: JmapRfc8621Configuration): Either[Exception, EmailSubmissionSetRequest] = {
    val supportedCreationIds: List[EmailSubmissionCreationId] = create.getOrElse(Map()).keys.toList

    for {
      _ <- validateOnSuccessUpdateEmail(supportedCreationIds)
      _ <- validateOnSuccessDestroyEmail(supportedCreationIds)
      _ <- validateIdCount(configuration)
    } yield {
      this
    }
  }

  private def validateIdCount(configuration: JmapRfc8621Configuration): Either[Exception, EmailSubmissionSetRequest] = {
    val idCount = create.map(_.size).getOrElse(0)
    if (idCount > configuration.maxObjectsInSet) {
      Left(RequestTooLargeException(s"Too many items in a set request ${this.getClass}. " +
        s"Got $idCount items instead of maximum ${configuration.maxObjectsInSet.value}."))
    } else {
      scala.Right(this)
    }
  }

  private def validateOnSuccessDestroyEmail(supportedCreationIds: List[EmailSubmissionCreationId]) : Either[IllegalArgumentException, EmailSubmissionSetRequest] =
    onSuccessDestroyEmail.getOrElse(List())
      .map(id => validate(id, supportedCreationIds))
      .sequence
      .map(_ => this)


  private def validateOnSuccessUpdateEmail(supportedCreationIds: List[EmailSubmissionCreationId]) : Either[IllegalArgumentException, EmailSubmissionSetRequest] =
    onSuccessUpdateEmail.getOrElse(Map())
      .keys
      .toList
      .map(id => validate(id, supportedCreationIds))
      .sequence
      .map(_ => this)

  private def validate(creationId: EmailSubmissionCreationId, supportedCreationIds: List[EmailSubmissionCreationId]): Either[IllegalArgumentException, EmailSubmissionCreationId] = {
    if (creationId.id.startsWith("#")) {
      val realId = creationId.id.substring(1)
      val validatedId: Either[String, Id] = refineV[IdConstraint](realId)
      validatedId
        .left.map(s => new IllegalArgumentException(s))
        .flatMap(id => if (supportedCreationIds.contains(EmailSubmissionCreationId(id))) {
          scala.Right(EmailSubmissionCreationId(id))
        } else {
          Left(new IllegalArgumentException(s"${creationId.id} cannot be referenced in current method call"))
        })
    } else {
      Left(new IllegalArgumentException(s"${creationId.id} cannot be retrieved as storage for EmailSubmission is not yet implemented"))
    }
  }
}

case class EmailSubmissionSetResponse(accountId: AccountId,
                                      newState: UuidState,
                                      created: Option[Map[EmailSubmissionCreationId, EmailSubmissionCreationResponse]],
                                      notCreated: Option[Map[EmailSubmissionCreationId, SetError]])

case class EmailSubmissionId(value: Id)

case class EmailSubmissionCreationResponse(id: EmailSubmissionId, sendAt: UTCDate)
case class ParameterName(value: String) extends AnyVal
case class ParameterValue(value: String) extends AnyVal
case class EmailSubmissionAddress(email: MailAddress, parameters: Option[Map[ParameterName, Option[ParameterValue]]] = Option.empty)

case class Envelope(mailFrom: EmailSubmissionAddress, rcptTo: List[EmailSubmissionAddress])

object EmailSubmissionCreationRequest {
  private val assignableProperties = Set("emailId", "envelope", "identityId", "onSuccessUpdateEmail")

  def validateProperties(jsObject: JsObject): Either[EmailSubmissionCreationParseException, JsObject] =
    jsObject.keys.diff(assignableProperties) match {
      case unknownProperties if unknownProperties.nonEmpty =>
        Left(EmailSubmissionCreationParseException(SetError.invalidArguments(
          SetErrorDescription("Some unknown properties were specified"),
          Some(toProperties(unknownProperties.toSet)))))
      case _ => scala.Right(jsObject)
    }
}

case class EmailSubmissionCreationRequest(emailId: MessageId,
                                          identityId: Option[Id],
                                          envelope: Option[Envelope])

