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
package org.apache.james.eventsourcing.eventstore.dto

import org.apache.james.eventsourcing.TestEvent
import org.apache.james.eventsourcing.eventstore.dto.EventDTOModule

object TestEventDTOModules {
  val TEST_TYPE: EventDTOModule[TestEvent, TestEventDTO] = EventDTOModule.forEvent(classOf[TestEvent])
    .convertToDTO(classOf[TestEventDTO]).toDomainObjectConverter(_.toEvent)
    .toDTOConverter((event: TestEvent, typeName: String) => TestEventDTO(
      typeName,
      event.getData,
      event.eventId.serialize,
      event.getAggregateId.getId))
    .typeName("TestType")
    .withFactory(EventDTOModule.apply)


  val OTHER_TEST_TYPE: EventDTOModule[OtherEvent, OtherTestEventDTO] = EventDTOModule
    .forEvent(classOf[OtherEvent])
    .convertToDTO(classOf[OtherTestEventDTO])
    .toDomainObjectConverter(_.toEvent)
    .toDTOConverter((event: OtherEvent, typeName: String) => OtherTestEventDTO(
      typeName,
      event.getPayload,
      event.eventId.serialize,
      event.getAggregateId.getId))
    .typeName("other-type")
    .withFactory(EventDTOModule.apply)

  val SNAPSHOT_TYPE: EventDTOModule[SnapshotEvent, SnapshotEventDTO] = EventDTOModule
    .forEvent(classOf[SnapshotEvent])
    .convertToDTO(classOf[SnapshotEventDTO])
    .toDomainObjectConverter(_.toEvent)
    .toDTOConverter((event: SnapshotEvent, typeName: String) => SnapshotEventDTO(
      typeName,
      event.getPayload,
      event.eventId.serialize,
      event.getAggregateId.getId))
    .typeName("snapshot-type")
    .withFactory(EventDTOModule.apply)
}