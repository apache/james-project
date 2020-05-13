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

import static org.apache.james.webadmin.data.jmap.MessageFastViewProjectionCorrector.Progress;
import static org.apache.james.webadmin.data.jmap.MessageFastViewProjectionCorrector.RunningOptions;

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
        private static AdditionalInformation from(Progress progress, RunningOptions runningOptions, Username username) {
            return new AdditionalInformation(runningOptions, username,
                progress.getProcessedMessageCount(),
                progress.getFailedMessageCount(),
                Clock.systemUTC().instant());
        }

        private final RunningOptions runningOptions;
        private final Username username;
        private final long processedMessageCount;
        private final long failedMessageCount;
        private final Instant timestamp;

        public AdditionalInformation(RunningOptions runningOptions, Username username, long processedMessageCount, long failedMessageCount, Instant timestamp) {
            this.runningOptions = runningOptions;
            this.username = username;
            this.processedMessageCount = processedMessageCount;
            this.failedMessageCount = failedMessageCount;
            this.timestamp = timestamp;
        }

        public RunningOptions getRunningOptions() {
            return runningOptions;
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
        private final Optional<RunningOptionsDTO> runningOptions;

        public RecomputeUserFastViewTaskDTO(
                @JsonProperty("type") String type,
                @JsonProperty("username") String username,
                @JsonProperty("runningOptions") Optional<RunningOptionsDTO> runningOptions) {

            this.type = type;
            this.username = username;
            this.runningOptions = runningOptions;
        }

        @Override
        public String getType() {
            return type;
        }

        public String getUsername() {
            return username;
        }

        public Optional<RunningOptionsDTO> getRunningOptions() {
            return runningOptions;
        }
    }

    public static TaskDTOModule<RecomputeUserFastViewProjectionItemsTask, RecomputeUserFastViewTaskDTO> module(MessageFastViewProjectionCorrector corrector) {
        return DTOModule
            .forDomainObject(RecomputeUserFastViewProjectionItemsTask.class)
            .convertToDTO(RecomputeUserFastViewTaskDTO.class)
            .toDomainObjectConverter(dto -> asTask(corrector, dto))
            .toDTOConverter(RecomputeUserFastViewProjectionItemsTask::asDTO)
            .typeName(TASK_TYPE.asString())
            .withFactory(TaskDTOModule::new);
    }

    private static RecomputeUserFastViewTaskDTO asDTO(RecomputeUserFastViewProjectionItemsTask task, String type) {
        return new RecomputeUserFastViewTaskDTO(type, task.username.asString(),
            Optional.of(RunningOptionsDTO.asDTO(task.runningOptions)));
    }

    private static RecomputeUserFastViewProjectionItemsTask asTask(MessageFastViewProjectionCorrector corrector, RecomputeUserFastViewTaskDTO dto) {
        return new RecomputeUserFastViewProjectionItemsTask(corrector,
            dto.getRunningOptions()
                .map(RunningOptionsDTO::asDomainObject)
                .orElse(RunningOptions.DEFAULT),
            Username.of(dto.username));
    }

    private final MessageFastViewProjectionCorrector corrector;
    private final RunningOptions runningOptions;
    private final Progress progress;
    private final Username username;

    RecomputeUserFastViewProjectionItemsTask(MessageFastViewProjectionCorrector corrector, RunningOptions runningOptions, Username username) {
        this.corrector = corrector;
        this.runningOptions = runningOptions;
        this.username = username;
        this.progress = new MessageFastViewProjectionCorrector.Progress();
    }

    @Override
    public Result run() {
        return corrector.correctUsersProjectionItems(progress, username, runningOptions)
            .subscribeOn(Schedulers.elastic())
            .block();
    }

    @Override
    public TaskType type() {
        return TASK_TYPE;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.of(AdditionalInformation.from(progress, runningOptions, username));
    }
}