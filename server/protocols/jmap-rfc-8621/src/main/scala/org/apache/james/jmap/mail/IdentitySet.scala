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

import eu.timepit.refined.collection.NonEmpty
import eu.timepit.refined.refineV
import eu.timepit.refined.types.string.NonEmptyString
import org.apache.james.jmap.api.model.{HtmlSignature, IdentityId, IdentityName, MayDeleteIdentity, TextSignature}
import org.apache.james.jmap.core.Id.Id
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{AccountId, Properties, SetError, UuidState}
import org.apache.james.jmap.method.WithAccountId
import play.api.libs.json.JsObject

object IdentityCreation {
  private val serverSetProperty: Set[String] = Set("id", "mayDelete")
  private val assignableProperties: Set[String] = Set("name", "email", "replyTo", "bcc", "textSignature", "htmlSignature")
  private val knownProperties: Set[String] = assignableProperties ++ serverSetProperty

  def validateProperties(jsObject: JsObject): Either[IdentityCreationParseException, JsObject] =
    (jsObject.keys.intersect(serverSetProperty), jsObject.keys.diff(knownProperties)) match {
      case (_, unknownProperties) if unknownProperties.nonEmpty =>
        Left(IdentityCreationParseException(SetError.invalidArguments(
          SetErrorDescription("Some unknown properties were specified"),
          Some(toProperties(unknownProperties.toSet)))))
      case (specifiedServerSetProperties, _) if specifiedServerSetProperties.nonEmpty =>
        Left(IdentityCreationParseException(SetError.invalidArguments(
          SetErrorDescription("Some server-set properties were specified"),
          Some(toProperties(specifiedServerSetProperties.toSet)))))
      case _ => scala.Right(jsObject)
    }

  private def toProperties(strings: Set[String]): Properties = Properties(strings
    .flatMap(string => {
      val refinedValue: Either[String, NonEmptyString] = refineV[NonEmpty](string)
      refinedValue.fold(_ => None, Some(_))
    }))
}

case class IdentitySetRequest(accountId: AccountId,
                              create: Option[Map[IdentityCreationId, JsObject]]) extends WithAccountId

case class IdentityCreationId(id: Id) {
  def serialise: String = id.value
}

case class IdentityCreationResponse(id: IdentityId,
                                    name: Option[IdentityName],
                                    textSignature: Option[TextSignature],
                                    htmlSignature: Option[HtmlSignature],
                                    mayDelete: MayDeleteIdentity)

case class IdentitySetResponse(accountId: AccountId,
                               oldState: Option[UuidState],
                               newState: UuidState,
                               created: Option[Map[IdentityCreationId, IdentityCreationResponse]],
                               notCreated: Option[Map[IdentityCreationId, SetError]])

case class IdentityCreationParseException(setError: SetError) extends IllegalArgumentException