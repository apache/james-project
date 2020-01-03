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

import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;

import com.fasterxml.jackson.annotation.JsonProperty;

import reactor.core.scheduler.Schedulers;

public class RecomputeAllFastViewProjectionItemsTask implements Task {
    static final TaskType TASK_TYPE = TaskType.of("RecomputeAllFastViewProjectionItemsTask");

    public static class AdditionalInformation implements TaskExecutionDetails.AdditionalInformation {
        private static AdditionalInformation from(MessageFastViewProjectionCorrector.Progress progress) {
            return new AdditionalInformation(
                progress.getProcessedUserCount(),
                progress.getProcessedMessageCount(),
                progress.getFailedUserCount(),
                progress.getFailedMessageCount(),
                Clock.systemUTC().instant());
        }

        private final long processedUserCount;
        private final long processedMessageCount;
        private final long failedUserCount;
        private final long failedMessageCount;
        private final Instant timestamp;

        public AdditionalInformation(long processedUserCount, long processedMessageCount, long failedUserCount, long failedMessageCount, Instant timestamp) {
            this.processedUserCount = processedUserCount;
            this.processedMessageCount = processedMessageCount;
            this.failedUserCount = failedUserCount;
            this.failedMessageCount = failedMessageCount;
            this.timestamp = timestamp;
        }

        public long getProcessedUserCount() {
            return processedUserCount;
        }

        public long getProcessedMessageCount() {
            return processedMessageCount;
        }

        public long getFailedUserCount() {
            return failedUserCount;
        }

        public long getFailedMessageCount() {
            return failedMessageCount;
        }

        @Override
        public Instant timestamp() {
            return timestamp;
        }
    }

    public static class RecomputeAllPreviewsTaskDTO implements TaskDTO {
        private final String type;

        public RecomputeAllPreviewsTaskDTO(@JsonProperty("type") String type) {
            this.type = type;
        }

        @Override
        public String getType() {
            return type;
        }
    }

    public static TaskDTOModule<RecomputeAllFastViewProjectionItemsTask, RecomputeAllPreviewsTaskDTO> module(MessageFastViewProjectionCorrector corrector) {
        return DTOModule
            .forDomainObject(RecomputeAllFastViewProjectionItemsTask.class)
            .convertToDTO(RecomputeAllPreviewsTaskDTO.class)
            .toDomainObjectConverter(dto -> new RecomputeAllFastViewProjectionItemsTask(corrector))
            .toDTOConverter((task, type) -> new RecomputeAllPreviewsTaskDTO(type))
            .typeName(TASK_TYPE.asString())
            .withFactory(TaskDTOModule::new);
    }

    private final MessageFastViewProjectionCorrector corrector;
    private final MessageFastViewProjectionCorrector.Progress progress;

    RecomputeAllFastViewProjectionItemsTask(MessageFastViewProjectionCorrector corrector) {
        this.corrector = corrector;
        this.progress = new MessageFastViewProjectionCorrector.Progress();
    }

    @Override
    public Result run() {
        corrector.correctAllProjectionItems(progress)
            .subscribeOn(Schedulers.boundedElastic())
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
        return Optional.of(AdditionalInformation.from(progress));
    }
}