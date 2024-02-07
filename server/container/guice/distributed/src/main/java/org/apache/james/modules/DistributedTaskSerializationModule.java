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
package org.apache.james.modules;

import java.util.Set;

import javax.inject.Named;

import org.apache.james.eventsourcing.Event;
import org.apache.james.eventsourcing.eventstore.EventNestedTypes;
import org.apache.james.eventsourcing.eventstore.dto.EventDTO;
import org.apache.james.eventsourcing.eventstore.dto.EventDTOModule;
import org.apache.james.json.DTOConverter;
import org.apache.james.json.DTOModule;
import org.apache.james.modules.mailbox.ReIndexingTaskSerializationModule;
import org.apache.james.server.task.json.JsonTaskSerializer;
import org.apache.james.server.task.json.TaskModuleInjectionKeys;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.eventsourcing.distributed.TasksSerializationModule;

import com.google.common.collect.Sets;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.multibindings.ProvidesIntoSet;

public class DistributedTaskSerializationModule extends AbstractModule {
    @Override
    protected void configure() {
        install(new ReIndexingTaskSerializationModule());
    }

    @ProvidesIntoSet
    public EventDTOModule<? extends Event, ? extends EventDTO> taskCreatedSerialization(JsonTaskSerializer jsonTaskSerializer,
                                                                                        DTOConverter<TaskExecutionDetails.AdditionalInformation, AdditionalInformationDTO> additionalInformationConverter,
                                                                                        DTOConverter<Task, TaskDTO> taskConverter) {
        return TasksSerializationModule.CREATED.create(jsonTaskSerializer, additionalInformationConverter, taskConverter);
    }

    @ProvidesIntoSet
    public EventDTOModule<? extends Event, ? extends EventDTO> taskStartedSerialization(JsonTaskSerializer jsonTaskSerializer,
                                                         DTOConverter<TaskExecutionDetails.AdditionalInformation, AdditionalInformationDTO> additionalInformationConverter,
                                                         DTOConverter<Task, TaskDTO> taskConverter) {
        return TasksSerializationModule.STARTED.create(jsonTaskSerializer, additionalInformationConverter, taskConverter);
    }

    @ProvidesIntoSet
    public EventDTOModule<? extends Event, ? extends EventDTO> taskCancelRequestedSerialization(JsonTaskSerializer jsonTaskSerializer,
                                                                 DTOConverter<TaskExecutionDetails.AdditionalInformation, AdditionalInformationDTO> additionalInformationConverter,
                                                                 DTOConverter<Task, TaskDTO> taskConverter) {
        return TasksSerializationModule.CANCEL_REQUESTED.create(jsonTaskSerializer, additionalInformationConverter, taskConverter);
    }

    @ProvidesIntoSet
    public EventDTOModule<? extends Event, ? extends EventDTO> taskCancelledSerialization(JsonTaskSerializer jsonTaskSerializer,
                                                           DTOConverter<TaskExecutionDetails.AdditionalInformation, AdditionalInformationDTO> additionalInformationConverter,
                                                           DTOConverter<Task, TaskDTO> taskConverter) {
        return TasksSerializationModule.CANCELLED.create(jsonTaskSerializer, additionalInformationConverter, taskConverter);
    }

    @ProvidesIntoSet
    public EventDTOModule<? extends Event, ? extends EventDTO> taskCompletedSerialization(JsonTaskSerializer jsonTaskSerializer,
                                                           DTOConverter<TaskExecutionDetails.AdditionalInformation, AdditionalInformationDTO> additionalInformationConverter,
                                                           DTOConverter<Task, TaskDTO> taskConverter) {
        return TasksSerializationModule.COMPLETED.create(jsonTaskSerializer, additionalInformationConverter, taskConverter);
    }

    @ProvidesIntoSet
    public EventDTOModule<? extends Event, ? extends EventDTO> taskFailedSerialization(JsonTaskSerializer jsonTaskSerializer,
                                                        DTOConverter<TaskExecutionDetails.AdditionalInformation, AdditionalInformationDTO> additionalInformationConverter,
                                                        DTOConverter<Task, TaskDTO> taskConverter) {
        return TasksSerializationModule.FAILED.create(jsonTaskSerializer, additionalInformationConverter, taskConverter);
    }

    @ProvidesIntoSet
    public EventDTOModule<? extends Event, ? extends EventDTO> taskUpdatedSerialization(JsonTaskSerializer jsonTaskSerializer,
                                                        DTOConverter<TaskExecutionDetails.AdditionalInformation, AdditionalInformationDTO> additionalInformationConverter,
                                                        DTOConverter<Task, TaskDTO> taskConverter) {
        return TasksSerializationModule.UPDATED.create(jsonTaskSerializer, additionalInformationConverter, taskConverter);
    }

    @Named(EventNestedTypes.EVENT_NESTED_TYPES_INJECTION_NAME)
    @Provides
    public Set<DTOModule<?, ? extends org.apache.james.json.DTO>> eventNestedTypes(@Named(TaskModuleInjectionKeys.ADDITIONAL_INFORMATION_DTO) Set<AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends AdditionalInformationDTO>> additionalInformationDTOModules,
                                                                                   @Named(TaskModuleInjectionKeys.TASK_DTO) Set<TaskDTOModule<? extends Task, ? extends TaskDTO>> taskDTOModules) {
        return Sets.union(additionalInformationDTOModules, taskDTOModules);
    }
}
