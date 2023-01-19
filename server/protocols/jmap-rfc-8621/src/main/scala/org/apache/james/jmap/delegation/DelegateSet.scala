/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.jmap.delegation

import org.apache.james.core.Username
import org.apache.james.jmap.core.Id.Id
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{AccountId, Id, SetError, UuidState}
import org.apache.james.jmap.method.WithAccountId
import play.api.libs.json.{JsObject, JsPath, JsonValidationError}

case class DelegateCreationId(id: Id) {
  def serialize: String = id.value
}

case class DelegateSetRequest(accountId: AccountId,
                              create: Option[Map[DelegateCreationId, JsObject]],
                              destroy: Option[Seq[UnparsedDelegateId]]) extends WithAccountId

case class DelegateCreationRequest(username: Username)

case class DelegateCreationResponse(id: DelegationId) {
  def asUnparsedDelegateId: UnparsedDelegateId = UnparsedDelegateId(Id.validate(id.serialize).toOption.get)
}

case class DelegateSetResponse(accountId: AccountId,
                               oldState: Option[UuidState],
                               newState: UuidState,
                               created: Option[Map[DelegateCreationId, DelegateCreationResponse]],
                               notCreated: Option[Map[DelegateCreationId, SetError]],
                               destroyed: Option[Seq[DelegationId]],
                               notDestroyed: Option[Map[UnparsedDelegateId, SetError]])

case class DelegateSetParseException(setError: SetError) extends IllegalArgumentException
case class ForbiddenAccountManagementException() extends RuntimeException()

object DelegateSetParseException {
  def from(errors: collection.Seq[(JsPath, collection.Seq[JsonValidationError])]): DelegateSetParseException = {
    val setError: SetError = errors.head match {
      case (path, Seq()) => SetError.invalidArguments(SetErrorDescription(s"'$path' property in Delegate object is not valid"))
      case (path, Seq(JsonValidationError(Seq("error.path.missing")))) =>
        SetError.invalidArguments(SetErrorDescription(s"Missing '$path' property in Delegate object"))
      case (path, Seq(JsonValidationError(Seq(message)))) => SetError.invalidArguments(SetErrorDescription(s"'$path' property in Delegate object is not valid: $message"))
      case (path, _) => SetError.invalidArguments(SetErrorDescription(s"Unknown error on property '$path'"))
    }
    DelegateSetParseException(setError)
  }
}