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

package org.apache.james.messagefastview.cleanup;

import java.util.Optional;

import org.apache.james.json.DTOModule;
import org.apache.james.messagefastview.cleanup.MessageFastViewCleanupService.RunningOptions;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MessageFastViewCleanupTaskDTO implements TaskDTO {
    private static MessageFastViewCleanupTaskDTO toDTO(MessageFastViewCleanupTask domainObject, String typeName) {
        return new MessageFastViewCleanupTaskDTO(typeName,
            Optional.of(RunningOptionsDTO.asDTO(domainObject.getRunningOptions())));
    }

    public static TaskDTOModule<MessageFastViewCleanupTask, MessageFastViewCleanupTaskDTO> module(MessageFastViewCleanupService service) {
        return DTOModule
            .forDomainObject(MessageFastViewCleanupTask.class)
            .convertToDTO(MessageFastViewCleanupTaskDTO.class)
            .toDomainObjectConverter(dto -> dto.toDomainObject(service))
            .toDTOConverter(MessageFastViewCleanupTaskDTO::toDTO)
            .typeName(MessageFastViewCleanupTask.CLEANUP_MESSAGE_FAST_VIEW.asString())
            .withFactory(TaskDTOModule::new);
    }

    private final String type;
    private final Optional<RunningOptionsDTO> runningOptions;

    public MessageFastViewCleanupTaskDTO(@JsonProperty("type") String type,
                                         @JsonProperty("runningOptions") Optional<RunningOptionsDTO> runningOptions) {
        this.type = type;
        this.runningOptions = runningOptions;
    }

    private MessageFastViewCleanupTask toDomainObject(MessageFastViewCleanupService service) {
        return new MessageFastViewCleanupTask(service,
            runningOptions.map(RunningOptionsDTO::asDomainObject).orElse(RunningOptions.DEFAULT));
    }

    public Optional<RunningOptionsDTO> getRunningOptions() {
        return runningOptions;
    }

    @Override
    public String getType() {
        return type;
    }
}
