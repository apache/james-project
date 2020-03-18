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
import org.apache.james.jmap.model.{Account, CapabilityIdentifier, CoreCapabilityProperties, Id, MailCapabilityProperties, Session, UnsignedInt, _}
import play.api.libs.json._

class Serializer {
  implicit val unsignedIntWrites: Writes[UnsignedInt] = unsignedInt => JsNumber(unsignedInt.value)
  implicit val usernameWrites: Writes[Username] = username => JsString(username.asString)
  implicit val idWrites: Writes[Id] = id => JsString(id.value)
  implicit val urlWrites: Writes[URL] = url => JsString(url.toString)
  implicit val stateWrites: Writes[State] = state => JsString(state.value)
  implicit val capabilityIdentifierWrites: Writes[CapabilityIdentifier] = identifier => JsString(identifier.value.toString)
  implicit val coreCapabilityWrites: Writes[CoreCapabilityProperties] = Json.writes[CoreCapabilityProperties]
  implicit val mailCapabilityWrites: Writes[MailCapabilityProperties] = Json.writes[MailCapabilityProperties]

  implicit def identifierMapWrite[Any](implicit idWriter: Writes[Id]): Writes[Map[CapabilityIdentifier, Any]] =
    (m: Map[CapabilityIdentifier, Any]) => {
      JsObject(
        m.map {
          case (identifier, id: Id) => (identifier.asString, idWriter.writes(id))
          case _ => throw new RuntimeException("non supported serializer")
        }.toSeq
      )
    }

  implicit def setCapabilityWrites(implicit corePropertiesWriter: Writes[CoreCapabilityProperties],
                                   mailCapabilityWrites: Writes[MailCapabilityProperties]): Writes[Set[_ <: Capability]] =
    (set: Set[_ <: Capability]) => {
      JsObject(set.map {
        case capability: CoreCapability => (
          capability.identifier.asString, corePropertiesWriter.writes(capability.properties))
        case capability: MailCapability => (
          capability.identifier.asString, mailCapabilityWrites.writes(capability.properties))
      }.toSeq)
    }

  implicit val accountWrites: Writes[Account] = Json.writes[Account]
  implicit val sessionWrites: Writes[Session] = Json.writes[Session]

  implicit def idMapWrite[Any](implicit vr: Writes[Any]): Writes[Map[Id, Any]] =
    (m: Map[Id, Any]) => {
      JsObject(m.map { case (k, v) => (k.value, vr.writes(v)) }.toSeq)
    }

  def serialize(session: Session): String = {
    sessionWrites.writes(session).toString()
  }
}
