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

package org.apache.james.jmap.change

import java.util.Optional

import com.fasterxml.jackson.annotation.JsonProperty
import org.apache.james.core.Username
import org.apache.james.events.Event.EventId
import org.apache.james.events.{Event, EventSerializer}
import org.apache.james.jmap.core.State
import org.apache.james.json.JsonGenericSerializer

import scala.jdk.OptionConverters._

object StateChangeEventDTO {
  val dtoModule: EventDTOModule[StateChangeEvent, StateChangeEventDTO] = EventDTOModule.forEvent(classOf[StateChangeEvent])
    .convertToDTO(classOf[StateChangeEventDTO])
    .toDomainObjectConverter(dto => dto.toDomainObject)
    .toDTOConverter((event, aType) => StateChangeEventDTO.toDTO(event))
    .typeName(classOf[StateChangeEvent].getCanonicalName)
    .withFactory(EventDTOModule.apply);

  def toDTO(event: StateChangeEvent): StateChangeEventDTO = StateChangeEventDTO(
    getType = classOf[StateChangeEvent].getCanonicalName,
    getEventId = event.eventId.getId.toString,
    getUsername = event.username.asString(),
    getMailboxState = event.mailboxState.map(_.value).map(_.toString).toJava,
    getEmailState = event.emailState.map(_.value).map(_.toString).toJava)
}

case class StateChangeEventDTO(@JsonProperty("type") getType: String,
                               @JsonProperty("eventId") getEventId: String,
                               @JsonProperty("username") getUsername: String,
                               @JsonProperty("mailboxState") getMailboxState: Optional[String],
                               @JsonProperty("emailState") getEmailState: Optional[String]) extends EventDTO {
  def toDomainObject: StateChangeEvent = StateChangeEvent(
    eventId = EventId.of(getEventId),
    username = Username.of(getUsername),
    mailboxState = getMailboxState.toScala.map(State.fromStringUnchecked),
    emailState = getEmailState.toScala.map(State.fromStringUnchecked))
}

case class JmapEventSerializer() extends EventSerializer {
  private val genericSerializer: JsonGenericSerializer[StateChangeEvent, StateChangeEventDTO] = JsonGenericSerializer
    .forModules(StateChangeEventDTO.dtoModule)
    .withoutNestedType()

  override def toJson(event: Event): String = event match {
    case stateChangeEvent: StateChangeEvent => genericSerializer.serialize(stateChangeEvent)
  }

  override def asEvent(serialized: String): Event = genericSerializer.deserialize(serialized)
}
