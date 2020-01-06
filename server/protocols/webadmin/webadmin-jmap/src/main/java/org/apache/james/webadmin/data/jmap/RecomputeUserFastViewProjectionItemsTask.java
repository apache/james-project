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

package org.apache.james.webadmin.data.jmap;

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

public class RecomputeUserFastViewProjectionItemsTask implements Task {
    static final TaskType TASK_TYPE = TaskType.of("RecomputeUserFastViewProjectionItemsTask");

    public static class AdditionalInformation implements TaskExecutionDetails.AdditionalInformation {
        private static AdditionalInformation from(MessageFastViewProjectionCorrector.Progress progress, Username username) {
            return new AdditionalInformation(username,
                progress.getProcessedMessageCount(),
                progress.getFailedMessageCount(),
                Clock.systemUTC().instant());
        }

        private final Username username;
        private final long processedMessageCount;
        private final long failedMessageCount;
        private final Instant timestamp;

        public AdditionalInformation(Username username, long processedMessageCount, long failedMessageCount, Instant timestamp) {
            this.username = username;
            this.processedMessageCount = processedMessageCount;
            this.failedMessageCount = failedMessageCount;
            this.timestamp = timestamp;
        }

        public long getProcessedMessageCount() {
            return processedMessageCount;
        }

        public long getFailedMessageCount() {
            return failedMessageCount;
        }

        public String getUsername() {
            return username.asString();
        }

        @Override
        public Instant timestamp() {
            return timestamp;
        }
    }

    public static class RecomputeUserFastViewTaskDTO implements TaskDTO {
        private final String type;
        private final String username;

        public RecomputeUserFastViewTaskDTO(
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

    public static TaskDTOModule<RecomputeUserFastViewProjectionItemsTask, RecomputeUserFastViewTaskDTO> module(MessageFastViewProjectionCorrector corrector) {
        return DTOModule
            .forDomainObject(RecomputeUserFastViewProjectionItemsTask.class)
            .convertToDTO(RecomputeUserFastViewTaskDTO.class)
            .toDomainObjectConverter(dto -> new RecomputeUserFastViewProjectionItemsTask(corrector, Username.of(dto.username)))
            .toDTOConverter((task, type) -> new RecomputeUserFastViewTaskDTO(type, task.username.asString()))
            .typeName(TASK_TYPE.asString())
            .withFactory(TaskDTOModule::new);
    }

    private final MessageFastViewProjectionCorrector corrector;
    private final MessageFastViewProjectionCorrector.Progress progress;
    private final Username username;

    RecomputeUserFastViewProjectionItemsTask(MessageFastViewProjectionCorrector corrector, Username username) {
        this.corrector = corrector;
        this.username = username;
        this.progress = new MessageFastViewProjectionCorrector.Progress();
    }

    @Override
    public Result run() {
        corrector.correctUsersProjectionItems(progress, username)
            .subscribeOn(Schedulers.elastic())
            .block();

        if (progress.failed()) {
            return Result.PARTIAL;
        }
        return Result.COMPLETED;
    }

    @Override
    public TaskType type() {
        return TASK_TYPE;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.of(AdditionalInformation.from(progress, username));
    }
}