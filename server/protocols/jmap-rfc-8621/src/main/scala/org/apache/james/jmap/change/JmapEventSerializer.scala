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

import com.fasterxml.jackson.annotation.JsonProperty
import org.apache.james.core.Username
import org.apache.james.events.Event.EventId
import org.apache.james.events.{Event, EventSerializer}
import org.apache.james.json.JsonGenericSerializer

import javax.inject.Inject
import scala.jdk.CollectionConverters._

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
    getTypeStates = event.map.map(element => element._1.asString() -> element._2.serialize).asJava)
}

case class StateChangeEventDTO(@JsonProperty("type") getType: String,
                               @JsonProperty("eventId") getEventId: String,
                               @JsonProperty("username") getUsername: String,
                               @JsonProperty("typeStates") getTypeStates: java.util.Map[String, String]) extends EventDTO {
  def toDomainObject(typeStateFactory: TypeStateFactory): StateChangeEvent = StateChangeEvent(
    eventId = EventId.of(getEventId),
    username = Username.of(getUsername),
    map = getTypeStates.asScala.flatMap(element => typeStateFactory.parse(element._1).toOption
      .flatMap(typeName => typeName.parseState(element._2).toOption.map(state => typeName -> state))).toMap)
}

case class JmapEventSerializer @Inject()(stateChangeEventDTOFactory: StateChangeEventDTOFactory) extends EventSerializer {
  private val genericSerializer: JsonGenericSerializer[StateChangeEvent, StateChangeEventDTO] = JsonGenericSerializer
    .forModules(stateChangeEventDTOFactory.dtoModule)
    .withoutNestedType()

  override def toJson(event: Event): String = event match {
    case stateChangeEvent: StateChangeEvent => genericSerializer.serialize(stateChangeEvent)
  }

  override def asEvent(serialized: String): Event = genericSerializer.deserialize(serialized)
}
