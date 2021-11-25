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

import org.apache.james.jmap.api.model.{HtmlSignature, IdentityId, IdentityName, MayDeleteIdentity, TextSignature}
import org.apache.james.jmap.core.Id.Id
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{AccountId, Properties, SetError, UuidState}
import org.apache.james.jmap.method.IdentitySetUpdatePerformer.IdentitySetUpdateResponse
import org.apache.james.jmap.method.WithAccountId
import play.api.libs.json.{JsObject, JsPath, JsonValidationError}

object IdentitySet {
  def validateProperties(serverSetProperty: Set[String], knownProperties: Set[String], jsObject: JsObject): Either[IdentitySetParseException, JsObject] =
    (jsObject.keys.intersect(serverSetProperty), jsObject.keys.diff(knownProperties)) match {
      case (_, unknownProperties) if unknownProperties.nonEmpty =>
        Left(IdentitySetParseException(SetError.invalidArguments(
          SetErrorDescription("Some unknown properties were specified"),
          Some(Properties.toProperties(unknownProperties.toSet)))))
      case (specifiedServerSetProperties, _) if specifiedServerSetProperties.nonEmpty =>
        Left(IdentitySetParseException(SetError.invalidArguments(
          SetErrorDescription("Some server-set properties were specified"),
          Some(Properties.toProperties(specifiedServerSetProperties.toSet)))))
      case _ => scala.Right(jsObject)
    }
}

object IdentityCreation {
  val serverSetProperty: Set[String] = Set("id", "mayDelete")
  val assignableProperties: Set[String] = Set("name", "email", "replyTo", "bcc", "textSignature", "htmlSignature")
  val knownProperties: Set[String] = assignableProperties ++ serverSetProperty
}

case class IdentitySetRequest(accountId: AccountId,
                              create: Option[Map[IdentityCreationId, JsObject]],
                              update: Option[Map[UnparsedIdentityId, JsObject]],
                              destroy: Option[Seq[UnparsedIdentityId]]) extends WithAccountId

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
                               notCreated: Option[Map[IdentityCreationId, SetError]],
                               updated: Option[Map[IdentityId, IdentitySetUpdateResponse]],
                               notUpdated: Option[Map[UnparsedIdentityId, SetError]],
                               destroyed: Option[Seq[IdentityId]],
                               notDestroyed: Option[Map[UnparsedIdentityId, SetError]])

case class IdentitySetParseException(setError: SetError) extends IllegalArgumentException

object IdentitySetParseException {
  def from(errors: collection.Seq[(JsPath, collection.Seq[JsonValidationError])]): IdentitySetParseException = {
    val setError: SetError = errors.head match {
      case (path, Seq()) => SetError.invalidArguments(SetErrorDescription(s"'$path' property in Identity object is not valid"))
      case (path, Seq(JsonValidationError(Seq("error.path.missing")))) =>
        SetError.invalidArguments(SetErrorDescription(s"Missing '$path' property in Identity object"))
      case (path, Seq(JsonValidationError(Seq(message)))) => SetError.invalidArguments(SetErrorDescription(s"'$path' property in Identity object is not valid: $message"))
      case (path, _) => SetError.invalidArguments(SetErrorDescription(s"Unknown error on property '$path'"))
    }
    IdentitySetParseException(setError)
  }
}