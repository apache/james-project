/** **************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 * *
 * http://www.apache.org/licenses/LICENSE-2.0                 *
 * *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 * ***************************************************************/

package org.apache.james.jmap

import java.net.URL

import org.apache.james.core.Username
import org.apache.james.jmap.model.{Account, CapabilityIdentifier, CoreCapability, Id, MailCapability, Session, UnsignedInt}
import play.api.libs.json.{JsNumber, JsObject, JsString, Json, Writes}
import org.apache.james.jmap.model._

class Serializer {
  implicit val unsignedIntWrites: Writes[UnsignedInt] = unsignedInt => JsNumber(unsignedInt.value)
  implicit val usernameWrites: Writes[Username] = username => JsString(username.asString)
  implicit val idWrites: Writes[Id] = id => JsString(id.value)
  implicit val urlWrites: Writes[URL] = url => JsString(url.toString)
  implicit val stateWrites: Writes[State] = state => JsString(state.value)
  implicit val capabilityIdentifierWrites: Writes[CapabilityIdentifier] = identifier => JsString(identifier.value.toString)
  implicit val coreCapabilityWrites: Writes[CoreCapability] = Json.writes[CoreCapability]
  implicit val mailCapabilityWrites: Writes[MailCapability] = Json.writes[MailCapability]

  implicit def identifierMapWrite[Any](implicit coreWriter: Writes[CoreCapability],
                                       mailWriter: Writes[MailCapability],
                                       idWriter: Writes[Id]): Writes[Map[CapabilityIdentifier, Any]] =
    (m: Map[CapabilityIdentifier, Any]) => {
      JsObject(
        m.map {
          case (identifier, capability: CoreCapability) => (identifier.value.toString, coreWriter.writes(capability))
          case (identifier, capability: MailCapability) => (identifier.value.toString, mailWriter.writes(capability))
          case (identifier, id: Id) => (identifier.value.toString, idWriter.writes(id))
          case _ => throw new RuntimeException("non supported serializer")
        }.toSeq
      )
    }

  implicit val accountWrites: Writes[Account] = Json.writes[Account]
  implicit val sessionWrites: Writes[Session] = Json.writes[Session]

  implicit def idMapWrite[Any](implicit vr: Writes[Any]): Writes[Map[Id, Any]] =
    (m: Map[Id, Any]) => {
      JsObject(m.map { case (k, v) => (k.value, vr.writes(v)) }.toSeq)
    }
}
