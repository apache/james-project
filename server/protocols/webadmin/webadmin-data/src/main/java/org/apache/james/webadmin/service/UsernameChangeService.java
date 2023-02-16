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

import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.user.api.UsernameChangeTaskStep;

import com.google.common.collect.ImmutableMap;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class UsernameChangeService {
    public enum StepState {
        WAITING,
        IN_PROGRESS,
        DONE,
        FAILED,
        ABORTED
    }

    public static class UsernameChangeStatus {
        private final Map<UsernameChangeTaskStep.StepName, StepState> states;

        public UsernameChangeStatus(Set<UsernameChangeTaskStep> steps) {
            states = new ConcurrentHashMap<>(steps.stream()
                .collect(ImmutableMap.toImmutableMap(step -> step.name(), any -> StepState.WAITING)));
        }

        public void beginStep(UsernameChangeTaskStep.StepName step) {
            states.put(step, StepState.IN_PROGRESS);
        }

        public void endStep(UsernameChangeTaskStep.StepName step) {
            states.put(step, StepState.DONE);
        }

        public void failedStep(UsernameChangeTaskStep.StepName step) {
            states.put(step, StepState.FAILED);
        }

        public void abortStep(UsernameChangeTaskStep.StepName step) {
            states.put(step, StepState.ABORTED);
        }

        public void abort() {
            states.entrySet()
                .stream()
                .filter(entry -> entry.getValue() == StepState.WAITING || entry.getValue() == StepState.IN_PROGRESS)
                .forEach(entry -> abortStep(entry.getKey()));
        }

        public Map<UsernameChangeTaskStep.StepName, StepState> getStates() {
            return ImmutableMap.copyOf(states);
        }
    }

    public static class Performer {
        private final Set<UsernameChangeTaskStep> steps;
        private final UsernameChangeStatus status;

        public Performer(Set<UsernameChangeTaskStep> steps, UsernameChangeStatus status) {
            this.steps = steps;
            this.status = status;
        }

        public Mono<Void> changeUsername(Username oldUsername, Username newUsername) {
            return Flux.fromIterable(steps)
                .sort(Comparator.comparingInt(UsernameChangeTaskStep::priority))
                .concatMap(step -> Mono.fromRunnable(() -> status.beginStep(step.name()))
                    .then(Mono.from(step.changeUsername(oldUsername, newUsername)))
                    .then(Mono.fromRunnable(() -> status.endStep(step.name())))
                    .doOnError(e -> status.failedStep(step.name())))
                .doOnError(e -> status.abort())
                .then();
        }

        public UsernameChangeStatus getStatus() {
            return status;
        }
    }

    private final Set<UsernameChangeTaskStep> steps;

    @Inject
    public UsernameChangeService(Set<UsernameChangeTaskStep> steps) {
        this.steps = steps;
    }

    public Performer performer() {
        return new Performer(steps, new UsernameChangeStatus(steps));
    }
}
