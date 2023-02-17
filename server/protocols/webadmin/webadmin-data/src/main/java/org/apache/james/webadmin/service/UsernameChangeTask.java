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
import java.util.Map;
import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;
import org.apache.james.user.api.UsernameChangeTaskStep.StepName;

import reactor.core.publisher.Mono;

public class UsernameChangeTask implements Task {
    static final TaskType TYPE = TaskType.of("UsernameChangeTask");

    public static class AdditionalInformation implements TaskExecutionDetails.AdditionalInformation {
        private final Instant timestamp;
        private final Username oldUser;
        private final Username newUser;
        private final Map<StepName, UsernameChangeService.StepState> status;
        private final Optional<StepName> fromStep;

        public AdditionalInformation(Instant timestamp, Username oldUser, Username newUser, Map<StepName, UsernameChangeService.StepState> status, Optional<StepName> fromStep) {
            this.timestamp = timestamp;
            this.oldUser = oldUser;
            this.newUser = newUser;
            this.status = status;
            this.fromStep = fromStep;
        }

        public Optional<StepName> getFromStep() {
            return fromStep;
        }

        public Username getOldUser() {
            return oldUser;
        }

        public Username getNewUser() {
            return newUser;
        }

        public Map<StepName, UsernameChangeService.StepState> getStatus() {
            return status;
        }

        @Override
        public Instant timestamp() {
            return timestamp;
        }
    }

    private final Username oldUser;
    private final Username newUser;
    private final UsernameChangeService.Performer performer;
    private final Optional<StepName> fromStep;

    public UsernameChangeTask(UsernameChangeService service, Username oldUser, Username newUser, Optional<StepName> fromStep) {
        this.oldUser = oldUser;
        this.newUser = newUser;

        this.performer = service.performer(fromStep);
        this.fromStep = fromStep;
    }


    @Override
    public Result run() {
        return performer.changeUsername(oldUser, newUser)
            .thenReturn(Result.COMPLETED)
            .onErrorResume(e -> {
                LOGGER.error("Error while changing username {} into {}", oldUser.asString(), newUser.asString(), e);
                return Mono.just(Result.PARTIAL);
            })
            .block();
    }

    @Override
    public TaskType type() {
        return TYPE;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.of(new AdditionalInformation(Clock.systemUTC().instant(), oldUser, newUser, performer.getStatus().getStates(), fromStep));
    }

    public Username getOldUser() {
        return oldUser;
    }

    public Username getNewUser() {
        return newUser;
    }

    public Optional<StepName> getFromStep() {
        return fromStep;
    }
}
