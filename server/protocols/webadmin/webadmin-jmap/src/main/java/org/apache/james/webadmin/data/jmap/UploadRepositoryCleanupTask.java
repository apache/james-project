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

import static org.apache.james.webadmin.data.jmap.UploadRepositoryCleanupTask.CleanupScope.EXPIRED;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;

import org.apache.james.jmap.api.upload.UploadRepository;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import reactor.core.publisher.Mono;

public class UploadRepositoryCleanupTask implements Task {
    private static final Logger LOGGER = LoggerFactory.getLogger(UploadRepositoryCleanupTask.class);
    public static final TaskType TASK_TYPE = TaskType.of("UploadRepositoryCleanupTask");
    public static final Duration EXPIRE_DURATION = Duration.ofDays(7);

    enum CleanupScope {
        EXPIRED;

        static class CleanupScopeInvalidException extends IllegalArgumentException {
        }

        public static Optional<CleanupScope> from(String name) {
            Preconditions.checkNotNull(name);
            return Arrays.stream(CleanupScope.values())
                .filter(value -> name.equalsIgnoreCase(value.name()))
                .findFirst();
        }
    }

    public static class AdditionalInformation implements TaskExecutionDetails.AdditionalInformation {

        private final CleanupScope scope;
        private final Instant timestamp;

        private static AdditionalInformation from(CleanupScope scope) {
            return new AdditionalInformation(scope, Clock.systemUTC().instant());
        }

        public AdditionalInformation(CleanupScope scope, Instant timestamp) {
            this.scope = scope;
            this.timestamp = timestamp;
        }

        @Override
        public Instant timestamp() {
            return timestamp;
        }

        public CleanupScope getScope() {
            return scope;
        }
    }

    private final UploadRepository uploadRepository;
    private final CleanupScope scope;

    public UploadRepositoryCleanupTask(UploadRepository uploadRepository, CleanupScope scope) {
        this.uploadRepository = uploadRepository;
        this.scope = scope;
    }

    @Override
    public Result run() {
        if (EXPIRED.equals(scope)) {
            return Mono.from(uploadRepository.deleteByUploadDateBefore(EXPIRE_DURATION))
                .thenReturn(Result.COMPLETED)
                .onErrorResume(error -> {
                    LOGGER.error("Error when cleaning upload repository", error);
                    return Mono.just(Result.PARTIAL);
                })
                .block();
        } else {
            return Result.COMPLETED;
        }
    }

    @Override
    public TaskType type() {
        return TASK_TYPE;
    }

    public CleanupScope getScope() {
        return scope;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.of(AdditionalInformation.from(scope));
    }
}
