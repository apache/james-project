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

package org.apache.james.server.task.json;

import java.io.IOException;
import java.util.Set;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.james.json.JsonGenericSerializer;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;
import org.apache.james.task.Task;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;

public class JsonTaskSerializer {

    public static JsonTaskSerializer of(TaskDTOModule<?, ?>... modules) {
        return new JsonTaskSerializer(ImmutableSet.copyOf(modules));
    }

    public static class InvalidTaskException  extends RuntimeException {
        public InvalidTaskException(JsonGenericSerializer.InvalidTypeException original) {
            super(original);
        }
    }

    public static class UnknownTaskException extends RuntimeException {
        public UnknownTaskException(JsonGenericSerializer.UnknownTypeException original) {
            super(original);
        }
    }

    private JsonGenericSerializer<Task, TaskDTO> jsonGenericSerializer;

    @Inject
    @VisibleForTesting
    public JsonTaskSerializer(@Named(TaskModuleInjectionKeys.TASK_DTO) Set<TaskDTOModule<? extends Task, ? extends TaskDTO>> modules) {
        jsonGenericSerializer = JsonGenericSerializer.forModules(modules).withoutNestedType();
    }

    public String serialize(Task task) throws JsonProcessingException {
        try {
            return jsonGenericSerializer.serialize(task);
        } catch (JsonGenericSerializer.UnknownTypeException e) {
            throw new UnknownTaskException(e);
        }
    }

    public Task deserialize(String value) throws IOException {
        try {
            return jsonGenericSerializer.deserialize(value);
        } catch (JsonGenericSerializer.UnknownTypeException e) {
            throw new UnknownTaskException(e);
        } catch (JsonGenericSerializer.InvalidTypeException e) {
            throw new InvalidTaskException(e);
        }
    }


}
