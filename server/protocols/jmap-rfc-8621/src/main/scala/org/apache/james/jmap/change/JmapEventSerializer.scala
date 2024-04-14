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
import jakarta.inject.Inject
import org.apache.james.core.Username
import org.apache.james.events.Event.EventId
import org.apache.james.events.{Event, EventSerializer}
import org.apache.james.jmap.api.change.TypeStateFactory
import org.apache.james.jmap.api.model.{State, TypeName}
import org.apache.james.jmap.core.UuidState
import org.apache.james.json.JsonGenericSerializer

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

case class StateChangeEventDTOFactory @Inject()(typeStateFactory: TypeStateFactory) {
  val dtoModule: EventDTOModule[StateChangeEvent, StateChangeEventDTO] = EventDTOModule.forEvent(classOf[StateChangeEvent])
    .convertToDTO(classOf[StateChangeEventDTO])
    .toDomainObjectConverter(dto => dto.toDomainObject(typeStateFactory))
    .toDTOConverter((event, aType) => toDTO(event))
    .typeName(classOf[StateChangeEvent].getCanonicalName)
    .withFactory(EventDTOModule.apply);

  def toDTO(event: StateChangeEvent): StateChangeEventDTO = StateChangeEventDTO(
    getType = classOf[StateChangeEvent].getCanonicalName,
    getEventId = event.eventId.getId.toString,
    getUsername = event.username.asString,
    getEmailState = Optional.empty(),
    getMailboxState = Optional.empty(),
    getVacationResponseState = Optional.empty(),
    getTypeStates = Optional.of(event.map.map(element => element._1.asString() -> element._2.serialize).asJava))
}

case class StateChangeEventDTO(@JsonProperty("type") getType: String,
                               @JsonProperty("eventId") getEventId: String,
                               @JsonProperty("username") getUsername: String,
                               // Optionals are used here to maintain backward compatibility with old format
                               @JsonProperty("vacationResponseState") getVacationResponseState: Optional[String],
                               @JsonProperty("mailboxState") getMailboxState: Optional[String],
                               @JsonProperty("emailState") getEmailState: Optional[String],
                               @JsonProperty("typeStates") getTypeStates: Optional[java.util.Map[String, String]]) extends EventDTO {
  def toDomainObject(typeStateFactory: TypeStateFactory): StateChangeEvent = StateChangeEvent(
    eventId = EventId.of(getEventId),
    username = Username.of(getUsername),
    map = typeStatesFromMap(typeStateFactory))

  private def typeStatesFromMap(typeStateFactory: TypeStateFactory): Map[TypeName, State] =
    getTypeStates.toScala.map(typeStates => typeStates.asScala.flatMap(element => typeStateFactory.parse(element._1).toOption
      .flatMap(typeName => typeName.parseState(element._2).toOption.map(state => typeName -> state))).toMap)
      .getOrElse(fallbackToOldFormat())

  private def fallbackToOldFormat(): Map[TypeName, State] =
    List(
      getEmailState.toScala.map(UuidState.fromStringUnchecked).map(EmailTypeName -> _),
      getMailboxState.toScala.map(UuidState.fromStringUnchecked).map(MailboxTypeName -> _),
      getVacationResponseState.toScala.map(UuidState.fromStringUnchecked).map(VacationResponseTypeName -> _))
      .flatten.toMap
}

case class JmapEventSerializer @Inject()(stateChangeEventDTOFactory: StateChangeEventDTOFactory) extends EventSerializer {
  private val genericSerializer: JsonGenericSerializer[StateChangeEvent, StateChangeEventDTO] = JsonGenericSerializer
    .forModules(stateChangeEventDTOFactory.dtoModule)
    .withoutNestedType()

  override def toJson(event: Event): String = event match {
    case stateChangeEvent: StateChangeEvent => genericSerializer.serialize(stateChangeEvent)
  }

  override def asEvent(serialized: String): Event = genericSerializer.deserialize(serialized)

  override def toJsonBytes(event: Event): Array[Byte] =  event match {
    case stateChangeEvent: StateChangeEvent => genericSerializer.serializeToBytes(stateChangeEvent)
  }

  override def fromBytes(serialized: Array[Byte]): Event = genericSerializer.deserializeFromBytes(serialized)
}
