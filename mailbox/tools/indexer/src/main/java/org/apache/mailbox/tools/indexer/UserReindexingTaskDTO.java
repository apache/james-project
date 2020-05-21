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
package org.apache.mailbox.tools.indexer;

import java.util.Optional;

import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public class UserReindexingTaskDTO implements TaskDTO {

    private static UserReindexingTaskDTO of(UserReindexingTask task, String type) {
        return new UserReindexingTaskDTO(type, task.getUsername().asString(), Optional.of(RunningOptionsDTO.toDTO(task.getRunningOptions())));
    }

    public static TaskDTOModule<UserReindexingTask, UserReindexingTaskDTO> module(UserReindexingTask.Factory factory) {
        return DTOModule
            .forDomainObject(UserReindexingTask.class)
            .convertToDTO(UserReindexingTaskDTO.class)
            .toDomainObjectConverter(factory::create)
            .toDTOConverter(UserReindexingTaskDTO::of)
            .typeName(UserReindexingTask.USER_RE_INDEXING.asString())
            .withFactory(TaskDTOModule::new);
    }

    private final String type;
    private final String username;
    private final Optional<RunningOptionsDTO> runningOptions;

    private UserReindexingTaskDTO(@JsonProperty("type") String type,
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
