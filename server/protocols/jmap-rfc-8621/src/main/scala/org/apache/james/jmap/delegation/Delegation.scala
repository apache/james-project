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

import java.nio.charset.StandardCharsets
import java.util.UUID

import org.apache.james.core.Username
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{Properties, SetError}
import play.api.libs.json.JsObject

object DelegationCreation {
  val serverSetProperty: Set[String] = Set("id")
  val assignableProperties: Set[String] = Set("username")
  val knownProperties: Set[String] = assignableProperties ++ serverSetProperty

  def validateProperties(serverSetProperty: Set[String], knownProperties: Set[String], jsObject: JsObject): Either[DelegateSetParseException, JsObject] =
    (jsObject.keys.intersect(serverSetProperty), jsObject.keys.diff(knownProperties)) match {
      case (_, unknownProperties) if unknownProperties.nonEmpty =>
        Left(DelegateSetParseException(SetError.invalidArguments(
          SetErrorDescription("Some unknown properties were specified"),
          Some(Properties.toProperties(unknownProperties.toSet)))))
      case (specifiedServerSetProperties, _) if specifiedServerSetProperties.nonEmpty =>
        Left(DelegateSetParseException(SetError.invalidArguments(
          SetErrorDescription("Some server-set properties were specified"),
          Some(Properties.toProperties(specifiedServerSetProperties.toSet)))))
      case _ => scala.Right(jsObject)
    }
}

object DelegationId {
  def from(baseUser: Username, targetUser: Username): DelegationId =
    DelegationId(UUID.nameUUIDFromBytes((baseUser.asString() + targetUser.asString()).getBytes(StandardCharsets.UTF_8)))
}

case class DelegationId(id: UUID) {
  def serialize: String = id.toString
}
