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

package org.apache.james.webadmin.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;

import com.fasterxml.jackson.annotation.JsonProperty;

import reactor.core.scheduler.Schedulers;

public class MailboxesExportTask implements Task {
    static final TaskType TASK_TYPE = TaskType.of("MailboxesExportTask");

    public static class AdditionalInformation implements TaskExecutionDetails.AdditionalInformation {
        private static AdditionalInformation from(Username username) {
            return new AdditionalInformation(username, Clock.systemUTC().instant());
        }

        private final Username username;
        private final Instant timestamp;

        public AdditionalInformation(Username username, Instant timestamp) {
            this.username = username;
            this.timestamp = timestamp;
        }

        public String getUsername() {
            return username.asString();
        }

        @Override
        public Instant timestamp() {
            return timestamp;
        }
    }

    public static class MailboxesExportTaskDTO implements TaskDTO {
        private final String type;
        private final String username;

        public MailboxesExportTaskDTO(
            @JsonProperty("type") String type,
            @JsonProperty("username") String username) {
            this.type = type;
            this.username = username;
        }

        @Override
        public String getType() {
            return type;
        }

        public String getUsername() {
            return username;
        }
    }

    public static TaskDTOModule<MailboxesExportTask, MailboxesExportTaskDTO> module(ExportService service) {
        return DTOModule
            .forDomainObject(MailboxesExportTask.class)
            .convertToDTO(MailboxesExportTaskDTO.class)
            .toDomainObjectConverter(dto -> new MailboxesExportTask(service, Username.of(dto.username)))
            .toDTOConverter((task, type) -> new MailboxesExportTaskDTO(type, task.username.asString()))
            .typeName(TASK_TYPE.asString())
            .withFactory(TaskDTOModule::new);
    }

    private final Username username;
    private final ExportService service;

    MailboxesExportTask(ExportService service, Username username) {
        this.username = username;
        this.service = service;
    }

    @Override
    public Result run() {
        return service.export(username)
            .subscribeOn(Schedulers.elastic())
            .block();
    }

    @Override
    public TaskType type() {
        return TASK_TYPE;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.of(AdditionalInformation.from(username));
    }
}