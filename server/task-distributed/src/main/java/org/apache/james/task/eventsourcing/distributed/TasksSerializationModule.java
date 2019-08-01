/** **************************************************************
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
 * ***************************************************************/

package org.apache.james.task.eventsourcing.distributed;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.james.eventsourcing.eventstore.cassandra.dto.EventDTOModule;
import org.apache.james.server.task.json.JsonTaskSerializer;
import org.apache.james.task.eventsourcing.CancelRequested;
import org.apache.james.task.eventsourcing.Cancelled;
import org.apache.james.task.eventsourcing.Completed;
import org.apache.james.task.eventsourcing.Created;
import org.apache.james.task.eventsourcing.Failed;
import org.apache.james.task.eventsourcing.Started;

import com.github.steveash.guavate.Guavate;

public interface TasksSerializationModule {
    Function<JsonTaskSerializer, EventDTOModule<Created, CreatedDTO>> CREATED = jsonTaskSerializer -> EventDTOModule
        .forEvent(Created.class)
        .convertToDTO(CreatedDTO.class)
        .toDomainObjectConverter(dto -> dto.toDomainObject(jsonTaskSerializer))
        .toDTOConverter((event, typeName) -> CreatedDTO.fromDomainObject(event, typeName, jsonTaskSerializer))
        .typeName("task-manager-created")
        .withFactory(EventDTOModule::new);

    Function<JsonTaskSerializer, EventDTOModule<Started, StartedDTO>> STARTED = jsonTaskSerializer -> EventDTOModule
        .forEvent(Started.class)
        .convertToDTO(StartedDTO.class)
        .toDomainObjectConverter(StartedDTO::toDomainObject)
        .toDTOConverter(StartedDTO::fromDomainObject)
        .typeName("task-manager-started")
        .withFactory(EventDTOModule::new);

    Function<JsonTaskSerializer, EventDTOModule<CancelRequested, CancelRequestedDTO>> CANCEL_REQUESTED = jsonTaskSerializer -> EventDTOModule
        .forEvent(CancelRequested.class)
        .convertToDTO(CancelRequestedDTO.class)
        .toDomainObjectConverter(CancelRequestedDTO::toDomainObject)
        .toDTOConverter(CancelRequestedDTO::fromDomainObject)
        .typeName("task-manager-cancel-requested")
        .withFactory(EventDTOModule::new);

    Function<JsonTaskSerializer, EventDTOModule<Completed, CompletedDTO>> COMPLETED = jsonTaskSerializer -> EventDTOModule
        .forEvent(Completed.class)
        .convertToDTO(CompletedDTO.class)
        .toDomainObjectConverter(CompletedDTO::toDomainObject)
        .toDTOConverter(CompletedDTO::fromDomainObject)
        .typeName("task-manager-completed")
        .withFactory(EventDTOModule::new);

    Function<JsonTaskSerializer, EventDTOModule<Failed, FailedDTO>> FAILED = jsonTaskSerializer -> EventDTOModule
        .forEvent(Failed.class)
        .convertToDTO(FailedDTO.class)
        .toDomainObjectConverter(FailedDTO::toDomainObject)
        .toDTOConverter(FailedDTO::fromDomainObject)
        .typeName("task-manager-failed")
        .withFactory(EventDTOModule::new);

    Function<JsonTaskSerializer, EventDTOModule<Cancelled, CancelledDTO>> CANCELLED = jsonTaskSerializer -> EventDTOModule
        .forEvent(Cancelled.class)
        .convertToDTO(CancelledDTO.class)
        .toDomainObjectConverter(CancelledDTO::toDomainObject)
        .toDTOConverter(CancelledDTO::fromDomainObject)
        .typeName("task-manager-cancelled")
        .withFactory(EventDTOModule::new);

    Function<JsonTaskSerializer, List<EventDTOModule<?, ?>>> MODULES = jsonTaskSerializer -> Stream
        .of(CREATED, STARTED, CANCEL_REQUESTED, CANCELLED, COMPLETED, FAILED)
        .map(module -> module.apply(jsonTaskSerializer))
        .collect(Guavate.toImmutableList());
}
