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

import eu.timepit.refined.api.Refined
import eu.timepit.refined.collection.NonEmpty
import eu.timepit.refined.refineV
import eu.timepit.refined.types.string.NonEmptyString
import org.apache.james.core.MailAddress
import org.apache.james.jmap.core.Id.Id
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.State.State
import org.apache.james.jmap.core.{AccountId, Id, Properties, SetError}
import org.apache.james.jmap.mail.EmailSubmissionSet.EmailSubmissionCreationId
import org.apache.james.jmap.method.{EmailSubmissionCreationParseException, WithAccountId}
import org.apache.james.mailbox.model.MessageId
import play.api.libs.json.JsObject

object EmailSubmissionSet {
  type EmailSubmissionCreationId = String Refined NonEmpty
}

object EmailSubmissionId {
  def generate: EmailSubmissionId = EmailSubmissionId(Id.validate(UUID.randomUUID().toString).toOption.get)
}

case class EmailSubmissionSetRequest(accountId: AccountId,
                                     create: Option[Map[EmailSubmissionCreationId, JsObject]]) extends WithAccountId

case class EmailSubmissionSetResponse(accountId: AccountId,
                                      newState: State,
                                      created: Option[Map[EmailSubmissionCreationId, EmailSubmissionCreationResponse]],
                                      notCreated: Option[Map[EmailSubmissionCreationId, SetError]])

case class EmailSubmissionId(value: Id)

case class EmailSubmissionCreationResponse(id: EmailSubmissionId)

case class Parameters(value: String)
case class EmailSubmissionAddress(email: MailAddress)

case class Envelope(mailFrom: EmailSubmissionAddress, rcptTo: List[EmailSubmissionAddress])

object EmailSubmissionCreationRequest {
  private val assignableProperties = Set("emailId", "envelope")

  def validateProperties(jsObject: JsObject): Either[EmailSubmissionCreationParseException, JsObject] =
    jsObject.keys.diff(assignableProperties) match {
      case unknownProperties if unknownProperties.nonEmpty =>
        Left(EmailSubmissionCreationParseException(SetError.invalidArguments(
          SetErrorDescription("Some unknown properties were specified"),
          Some(toProperties(unknownProperties.toSet)))))
      case _ => scala.Right(jsObject)
    }

  private def toProperties(strings: Set[String]): Properties = Properties(strings
    .flatMap(string => {
      val refinedValue: Either[String, NonEmptyString] = refineV[NonEmpty](string)
      refinedValue.fold(_ => None,  Some(_))
    }))
}

case class EmailSubmissionCreationRequest(emailId: MessageId,
                                          envelope: Envelope)