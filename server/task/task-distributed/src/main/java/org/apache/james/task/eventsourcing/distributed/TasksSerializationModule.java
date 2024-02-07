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

package org.apache.james.task.eventsourcing.distributed;

import java.util.Set;
import java.util.stream.Stream;

import org.apache.james.eventsourcing.Event;
import org.apache.james.eventsourcing.eventstore.dto.EventDTO;
import org.apache.james.eventsourcing.eventstore.dto.EventDTOModule;
import org.apache.james.json.DTOConverter;
import org.apache.james.server.task.json.JsonTaskSerializer;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.eventsourcing.AdditionalInformationUpdated;
import org.apache.james.task.eventsourcing.CancelRequested;
import org.apache.james.task.eventsourcing.Cancelled;
import org.apache.james.task.eventsourcing.Completed;
import org.apache.james.task.eventsourcing.Created;
import org.apache.james.task.eventsourcing.Failed;
import org.apache.james.task.eventsourcing.Started;

import com.google.common.collect.ImmutableSet;

public interface TasksSerializationModule {
    @FunctionalInterface
    interface TaskSerializationModuleFactory {
        EventDTOModule<? extends Event, ? extends EventDTO> create(JsonTaskSerializer taskSerializer,
                                    DTOConverter<TaskExecutionDetails.AdditionalInformation, AdditionalInformationDTO> additionalInformationConverter,
                                    DTOConverter<Task, TaskDTO> dtoConverter);
    }

    TaskSerializationModuleFactory CREATED = (jsonTaskSerializer, additionalInformationConverter, dtoConverter) -> EventDTOModule
        .forEvent(Created.class)
        .convertToDTO(CreatedDTO.class)
        .toDomainObjectConverter(dto -> dto.toDomainObject(dtoConverter))
        .toDTOConverter((event, typeName) -> CreatedDTO.fromDomainObject(dtoConverter, event, typeName))
        .typeName("task-manager-created")
        .withFactory(EventDTOModule::new);

    TaskSerializationModuleFactory STARTED = (jsonTaskSerializer, additionalInformationConverter, dtoConverter) -> EventDTOModule
        .forEvent(Started.class)
        .convertToDTO(StartedDTO.class)
        .toDomainObjectConverter(StartedDTO::toDomainObject)
        .toDTOConverter(StartedDTO::fromDomainObject)
        .typeName("task-manager-started")
        .withFactory(EventDTOModule::new);

    TaskSerializationModuleFactory CANCEL_REQUESTED = (jsonTaskSerializer, additionalInformationConverter, dtoConverter) -> EventDTOModule
        .forEvent(CancelRequested.class)
        .convertToDTO(CancelRequestedDTO.class)
        .toDomainObjectConverter(CancelRequestedDTO::toDomainObject)
        .toDTOConverter(CancelRequestedDTO::fromDomainObject)
        .typeName("task-manager-cancel-requested")
        .withFactory(EventDTOModule::new);

    TaskSerializationModuleFactory COMPLETED = (jsonTaskSerializer, additionalInformationConverter, dtoConverter) -> EventDTOModule
        .forEvent(Completed.class)
        .convertToDTO(CompletedDTO.class)
        .toDomainObjectConverter(dto -> dto.toDomainObject(additionalInformationConverter))
        .toDTOConverter((event, typeName) -> CompletedDTO.fromDomainObject(additionalInformationConverter, event, typeName))
        .typeName("task-manager-completed")
        .withFactory(EventDTOModule::new);

    TaskSerializationModuleFactory FAILED = (jsonTaskSerializer, additionalInformationConverter, dtoConverter) -> EventDTOModule
        .forEvent(Failed.class)
        .convertToDTO(FailedDTO.class)
        .toDomainObjectConverter(dto -> dto.toDomainObject(additionalInformationConverter))
        .toDTOConverter((event, typeName) -> FailedDTO.fromDomainObject(additionalInformationConverter, event, typeName))
        .typeName("task-manager-failed")
        .withFactory(EventDTOModule::new);

    TaskSerializationModuleFactory CANCELLED = (jsonTaskSerializer, additionalInformationConverter, dtoConverter) -> EventDTOModule
        .forEvent(Cancelled.class)
        .convertToDTO(CancelledDTO.class)
        .toDomainObjectConverter(dto -> dto.toDomainObject(additionalInformationConverter))
        .toDTOConverter((event, typeName) -> CancelledDTO.fromDomainObject(additionalInformationConverter, event, typeName))
        .typeName("task-manager-cancelled")
        .withFactory(EventDTOModule::new);

    TaskSerializationModuleFactory UPDATED = (jsonTaskSerializer, additionalInformationConverter, dtoConverter) -> EventDTOModule
        .forEvent(AdditionalInformationUpdated.class)
        .convertToDTO(AdditionalInformationUpdatedDTO.class)
        .toDomainObjectConverter(dto -> dto.toDomainObject(additionalInformationConverter))
        .toDTOConverter((event, typeName) -> AdditionalInformationUpdatedDTO.fromDomainObject(additionalInformationConverter, event, typeName))
        .typeName("task-manager-updated")
        .withFactory(EventDTOModule::new);

    static Set<EventDTOModule<? extends Event, ? extends EventDTO>> list(JsonTaskSerializer jsonTaskSerializer,
                                                                         DTOConverter<TaskExecutionDetails.AdditionalInformation, AdditionalInformationDTO> additionalInformationConverter,
                                                                         DTOConverter<Task, TaskDTO> dtoConverter) {
        return Stream
            .of(CREATED, STARTED, CANCEL_REQUESTED, CANCELLED, COMPLETED, FAILED, UPDATED)
            .map(moduleFactory -> moduleFactory.create(jsonTaskSerializer, additionalInformationConverter, dtoConverter))
            .collect(ImmutableSet.toImmutableSet());
    }
}
