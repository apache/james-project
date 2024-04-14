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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.user.api.DeleteUserDataTaskStep;
import org.apache.james.user.api.DeleteUserDataTaskStep.StepName;

import com.google.common.collect.ImmutableMap;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class DeleteUserDataService {
    public enum StepState {
        WAITING,
        IN_PROGRESS,
        DONE,
        FAILED,
        ABORTED,
        SKIPPED
    }

    public static class DeleteUserDataStatus {
        private final Map<StepName, StepState> states;

        public DeleteUserDataStatus(Set<DeleteUserDataTaskStep> steps) {
            states = new ConcurrentHashMap<>(steps.stream()
                .collect(ImmutableMap.toImmutableMap(DeleteUserDataTaskStep::name, any -> StepState.WAITING)));
        }

        public void beginStep(StepName step) {
            states.put(step, StepState.IN_PROGRESS);
        }

        public void endStep(StepName step) {
            states.put(step, StepState.DONE);
        }

        public void failedStep(StepName step) {
            states.put(step, StepState.FAILED);
        }

        public void abortStep(StepName step) {
            states.put(step, StepState.ABORTED);
        }

        public void skipStep(StepName step) {
            states.put(step, StepState.SKIPPED);
        }

        public void abort() {
            states.entrySet()
                .stream()
                .filter(entry -> entry.getValue() == StepState.WAITING || entry.getValue() == StepState.IN_PROGRESS)
                .forEach(entry -> abortStep(entry.getKey()));
        }

        public Map<StepName, StepState> getStates() {
            return ImmutableMap.copyOf(states);
        }
    }

    public static class Performer {
        private final Set<DeleteUserDataTaskStep> steps;
        private final DeleteUserDataStatus status;
        private final Optional<Integer> correspondingPriority;

        public Performer(Set<DeleteUserDataTaskStep> steps, DeleteUserDataStatus status, Optional<StepName> fromStep) {
            this.steps = steps;
            this.status = status;
            this.correspondingPriority = fromStep.map(this::correspondingPriority);
        }

        public Mono<Void> deleteUserData(Username username) {
            correspondingPriority.ifPresent(priority -> steps.stream()
                .filter(step -> step.priority() < priority)
                .forEach(step -> status.skipStep(step.name())));

            return steps()
                .concatMap(step -> Mono.fromRunnable(() -> status.beginStep(step.name()))
                    .then(Mono.from(step.deleteUserData(username)))
                    .then(Mono.fromRunnable(() -> status.endStep(step.name())))
                    .doOnError(e -> status.failedStep(step.name())))
                .doOnError(e -> status.abort())
                .then();
        }

        private Flux<DeleteUserDataTaskStep> steps() {
            return correspondingPriority
                .map(priority -> Flux.fromIterable(steps)
                    .filter(step -> step.priority() >= priority)
                    .sort(Comparator.comparingInt(DeleteUserDataTaskStep::priority)))
                .orElseGet(() -> Flux.fromIterable(steps)
                    .sort(Comparator.comparingInt(DeleteUserDataTaskStep::priority)));
        }

        private int correspondingPriority(StepName stepName) {
            return steps.stream()
                .filter(step -> step.name().equals(stepName))
                .map(DeleteUserDataTaskStep::priority)
                .findAny()
                .orElseThrow(() -> new IllegalArgumentException("Starting step not found: " + stepName.asString()));
        }

        public DeleteUserDataStatus getStatus() {
            return status;
        }
    }

    private final Set<DeleteUserDataTaskStep> steps;

    @Inject
    public DeleteUserDataService(Set<DeleteUserDataTaskStep> steps) {
        this.steps = steps;
    }

    public Performer performer(Optional<StepName> fromStep) {
        return new Performer(steps, new DeleteUserDataStatus(steps), fromStep);
    }

    public Performer performer() {
        return new Performer(steps, new DeleteUserDataStatus(steps), Optional.empty());
    }
}
