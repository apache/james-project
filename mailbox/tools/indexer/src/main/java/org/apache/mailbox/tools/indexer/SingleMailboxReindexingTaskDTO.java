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

public class SingleMailboxReindexingTaskDTO implements TaskDTO {

    private static SingleMailboxReindexingTaskDTO of(SingleMailboxReindexingTask task, String typeName) {
        return new SingleMailboxReindexingTaskDTO(typeName, task.getMailboxId().serialize(), Optional.of(RunningOptionsDTO.toDTO(task.getRunningOptions())));
    }

    public static TaskDTOModule<SingleMailboxReindexingTask, SingleMailboxReindexingTaskDTO> module(SingleMailboxReindexingTask.Factory factory) {
        return DTOModule
            .forDomainObject(SingleMailboxReindexingTask.class)
            .convertToDTO(SingleMailboxReindexingTaskDTO.class)
            .toDomainObjectConverter(factory::create)
            .toDTOConverter(SingleMailboxReindexingTaskDTO::of)
            .typeName(SingleMailboxReindexingTask.TYPE.asString())
            .withFactory(TaskDTOModule::new);
    }

    private final String type;
    private final String mailboxId;
    private final Optional<RunningOptionsDTO> runningOptions;

    public SingleMailboxReindexingTaskDTO(@JsonProperty("type") String type,
                                          @JsonProperty("mailboxId") String mailboxId,
                                          @JsonProperty("runningOptions") Optional<RunningOptionsDTO> runningOptions) {
        this.type = type;
        this.mailboxId = mailboxId;
        this.runningOptions = runningOptions;
    }

    @Override
    public String getType() {
        return type;
    }

    public String getMailboxId() {
        return mailboxId;
    }

    public Optional<RunningOptionsDTO> getRunningOptions() {
        return runningOptions;
    }
}
