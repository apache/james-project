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

package org.apache.james.webadmin.tasks;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.apache.commons.lang3.StringUtils;
import org.apache.james.task.TaskManager;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import spark.Request;
import spark.Route;

public class TaskFromRequestRegistry implements TaskFromRequest {
    private static final String DEFAULT_PARAMETER = "action";

    public static class Builder {
        private Optional<String> taskParameterName;
        private ImmutableMap.Builder<TaskRegistrationKey, TaskFromRequest> tasks;

        public Builder() {
            taskParameterName = Optional.empty();
            tasks = ImmutableMap.builder();
        }

        public Builder parameterName(String parameterName) {
            this.taskParameterName = Optional.of(parameterName);
            return this;
        }

        public Builder registrations(TaskRegistration... taskRegistrations) {
            return this.registrations(ImmutableSet.copyOf(taskRegistrations));
        }

        public Builder registrations(Collection<TaskRegistration> taskRegistrations) {
            this.tasks.putAll(taskRegistrations.stream()
                .collect(ImmutableMap.toImmutableMap(
                    TaskRegistration::registrationKey,
                    Function.identity())));
            return this;
        }

        public Builder register(TaskRegistrationKey key, TaskFromRequest taskFromRequest) {
            this.tasks.put(key, taskFromRequest);
            return this;
        }

        public TaskFromRequestRegistry build() {
            return buildAsOptional()
                .orElseThrow(() -> new IllegalStateException("Expecting some registered tasks but got none"));
        }

        public Route buildAsRoute(TaskManager taskManager) {
            return build().asRoute(taskManager);
        }

        public Optional<Route> buildAsRouteOptional(TaskManager taskManager) {
            return buildAsOptional()
                .map(registry -> registry.asRoute(taskManager));
        }

        Optional<TaskFromRequestRegistry> buildAsOptional() {
            ImmutableMap<TaskRegistrationKey, TaskFromRequest> registrations = tasks.build();
            if (registrations.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new TaskFromRequestRegistry(
                taskParameterName.orElse(DEFAULT_PARAMETER),
                registrations));
        }
    }

    public static class TaskRegistration implements TaskFromRequest {
        private final TaskRegistrationKey taskRegistrationKey;
        private final TaskFromRequest taskFromRequest;

        public TaskRegistration(TaskRegistrationKey taskRegistrationKey, TaskFromRequest taskFromRequest) {
            this.taskRegistrationKey = taskRegistrationKey;
            this.taskFromRequest = taskFromRequest;
        }

        public TaskRegistrationKey registrationKey() {
            return taskRegistrationKey;
        }

        @Override
        public TaskHandler fromRequest(Request request) throws Exception {
            return taskFromRequest.fromRequest(request);
        }
    }

    public static class MultiTaskRegistration implements TaskFromRequest {
        private final TaskRegistrationKey taskRegistrationKey;
        private final TaskFromRequest taskFromRequest;

        public MultiTaskRegistration(TaskRegistrationKey taskRegistrationKey, TaskFromRequest taskFromRequest) {
            this.taskRegistrationKey = taskRegistrationKey;
            this.taskFromRequest = taskFromRequest;
        }

        public TaskRegistrationKey registrationKey() {
            return taskRegistrationKey;
        }

        @Override
        public TaskHandler fromRequest(Request request) throws Exception {
            return taskFromRequest.fromRequest(request);
        }

        @Override
        public Route asRoute(TaskManager taskManager) {
            return new MultiTaskRoute(this, taskManager);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static TaskFromRequestRegistry of(TaskRegistrationKey key, TaskFromRequest generator) {
        return TaskFromRequestRegistry.builder()
            .register(key, generator)
            .build();
    }

    private final String taskParameterName;
    private final Map<TaskRegistrationKey, TaskFromRequest> taskGenerators;

    private TaskFromRequestRegistry(String taskParameterName, Map<TaskRegistrationKey, TaskFromRequest> taskGenerators) {
        this.taskParameterName = taskParameterName;
        this.taskGenerators = taskGenerators;
    }

    @Override
    public TaskHandler fromRequest(Request request) throws Exception {
        TaskRegistrationKey registrationKey = parseRegistrationKey(request);
        return Optional.ofNullable(taskGenerators.get(registrationKey))
            .map(Throwing.<TaskFromRequest, TaskHandler>function(taskGenerator -> taskGenerator.fromRequest(request)).sneakyThrow())
            .orElseThrow(() -> new IllegalArgumentException("Invalid value supplied for query parameter '" + taskParameterName + "': " + registrationKey.asString()
                + ". " + supportedValueMessage()));
    }

    private TaskRegistrationKey parseRegistrationKey(Request request) {
        return Optional.ofNullable(request.queryParams(taskParameterName))
            .map(this::validateParameter)
            .map(TaskRegistrationKey::of)
            .orElseThrow(() -> new IllegalArgumentException("'" + taskParameterName + "' query parameter is compulsory. " + supportedValueMessage()));
    }

    private String validateParameter(String parameter) {
        if (StringUtils.isBlank(parameter)) {
            throw new IllegalArgumentException("'" + taskParameterName + "' query parameter cannot be empty or blank. " + supportedValueMessage());
        }
        return parameter;
    }

    private String supportedValueMessage() {
        ImmutableList<String> supportedTasks = taskGenerators.keySet()
            .stream()
            .map(TaskRegistrationKey::asString)
            .collect(ImmutableList.toImmutableList());
        return "Supported values are [" + Joiner.on(", ").join(supportedTasks) + "]";
    }
}
