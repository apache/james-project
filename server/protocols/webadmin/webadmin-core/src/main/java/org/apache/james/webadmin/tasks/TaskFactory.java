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

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.apache.commons.lang3.StringUtils;
import org.apache.james.task.Task;
import org.apache.james.task.TaskManager;

import com.github.fge.lambdas.Throwing;
import com.github.steveash.guavate.Guavate;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import spark.Request;
import spark.Route;

public class TaskFactory implements TaskGenerator {
    private static final String DEFAULT_PARAMETER = "action";

    public static class Builder {
        private Optional<String> taskParameterName;
        private ImmutableMap.Builder<TaskRegistrationKey, TaskGenerator> tasks;

        public Builder() {
            taskParameterName = Optional.empty();
            tasks = ImmutableMap.builder();
        }

        public Builder parameterName(String parameterName) {
            this.taskParameterName = Optional.of(parameterName);
            return this;
        }

        public Builder registrations(TaskRegistration... taskRegistrations) {
            this.tasks.putAll(Arrays.stream(taskRegistrations)
                .collect(Guavate.toImmutableMap(
                    TaskRegistration::registrationKey,
                    Function.identity())));
            return this;
        }

        public Builder register(TaskRegistrationKey key, TaskGenerator taskGenerator) {
            this.tasks.put(key, taskGenerator);
            return this;
        }

        public TaskFactory build() {
            ImmutableMap<TaskRegistrationKey, TaskGenerator> registrations = tasks.build();
            Preconditions.checkState(!registrations.isEmpty());
            return new TaskFactory(
                taskParameterName.orElse(DEFAULT_PARAMETER),
                registrations);
        }

        public Route buildAsRoute(TaskManager taskManager) {
            return build().asRoute(taskManager);
        }
    }

    public static class TaskRegistration implements TaskGenerator {
        private final TaskRegistrationKey taskRegistrationKey;
        private final TaskGenerator taskGenerator;

        public TaskRegistration(TaskRegistrationKey taskRegistrationKey, TaskGenerator taskGenerator) {
            this.taskRegistrationKey = taskRegistrationKey;
            this.taskGenerator = taskGenerator;
        }

        public TaskRegistrationKey registrationKey() {
            return taskRegistrationKey;
        }

        @Override
        public Task generate(Request request) throws Exception {
            return taskGenerator.generate(request);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static TaskFactory of(TaskRegistrationKey key, TaskGenerator generator) {
        return TaskFactory.builder()
            .register(key, generator)
            .build();
    }

    private final String taskParameterName;
    private final Map<TaskRegistrationKey, TaskGenerator> taskGenerators;

    private TaskFactory(String taskParameterName, Map<TaskRegistrationKey, TaskGenerator> taskGenerators) {
        this.taskParameterName = taskParameterName;
        this.taskGenerators = taskGenerators;
    }

    @Override
    public Task generate(Request request) throws Exception {
        TaskRegistrationKey registrationKey = parseRegistrationKey(request);
        return Optional.ofNullable(taskGenerators.get(registrationKey))
            .map(Throwing.<TaskGenerator, Task>function(taskGenerator -> taskGenerator.generate(request)).sneakyThrow())
            .orElseThrow(() -> new IllegalArgumentException("Invalid value supplied for '" + taskParameterName + "': " + registrationKey.asString()
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
            .collect(Guavate.toImmutableList());
        return "Supported values are [" + Joiner.on(", ").join(supportedTasks) + "]";
    }
}
