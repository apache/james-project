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

import java.time.Instant;

import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TasksCleanupTaskAdditionalInformationDTO implements AdditionalInformationDTO {

    public static AdditionalInformationDTOModule<TasksCleanupTask.Details, TasksCleanupTaskAdditionalInformationDTO> module() {
        return DTOModule
            .forDomainObject(TasksCleanupTask.Details.class)
            .convertToDTO(TasksCleanupTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(dto -> new TasksCleanupTask.Details(dto.getTimestamp(), dto.getRemovedTaskCount(), dto.getProcessedTaskCount(), dto.getOlderThan()))
            .toDTOConverter((domain, type) -> new TasksCleanupTaskAdditionalInformationDTO(type, domain.getRemovedTasksCount(), domain.getProcessedTaskCount(), domain.getOlderThan(), domain.timestamp()))
            .typeName(TasksCleanupTask.TASK_TYPE.asString())
            .withFactory(AdditionalInformationDTOModule::new);
    }

    private final String type;
    private final long removedTaskCount;
    private final long processedTaskCount;
    private final Instant olderThan;
    private final Instant timestamp;

    public TasksCleanupTaskAdditionalInformationDTO(@JsonProperty("type") String type,
                                                    @JsonProperty("removedTaskCount") long removedTaskCount,
                                                    @JsonProperty("processedTaskCount") long processedTaskCount,
                                                    @JsonProperty("olderThan") Instant olderThan,
                                                    @JsonProperty("timestamp") Instant timestamp) {
        this.type = type;
        this.removedTaskCount = removedTaskCount;
        this.processedTaskCount = processedTaskCount;
        this.olderThan = olderThan;
        this.timestamp = timestamp;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public Instant getTimestamp() {
        return timestamp;
    }

    public long getRemovedTaskCount() {
        return removedTaskCount;
    }

    public long getProcessedTaskCount() {
        return processedTaskCount;
    }

    public Instant getOlderThan() {
        return olderThan;
    }
}
