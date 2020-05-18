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

package org.apache.james.mailbox.cassandra.mail.task;

import static org.apache.james.mailbox.cassandra.mail.task.SolveMessageInconsistenciesTask.SOLVE_MESSAGE_INCONSISTENCIES;

import java.util.Optional;

import org.apache.james.json.DTOModule;
import org.apache.james.mailbox.cassandra.mail.task.SolveMessageInconsistenciesService.RunningOptions;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SolveMessageInconsistenciesTaskDTO implements TaskDTO {

    private static SolveMessageInconsistenciesTaskDTO toDTO(SolveMessageInconsistenciesTask domainObject, String typeName) {
        return new SolveMessageInconsistenciesTaskDTO(typeName, Optional.of(RunningOptionsDTO.asDTO(domainObject.getRunningOptions())));
    }

    public static TaskDTOModule<SolveMessageInconsistenciesTask, SolveMessageInconsistenciesTaskDTO> module(SolveMessageInconsistenciesService service) {
        return DTOModule
            .forDomainObject(SolveMessageInconsistenciesTask.class)
            .convertToDTO(SolveMessageInconsistenciesTaskDTO.class)
            .toDomainObjectConverter(dto -> dto.toDomainObject(service))
            .toDTOConverter(SolveMessageInconsistenciesTaskDTO::toDTO)
            .typeName(SOLVE_MESSAGE_INCONSISTENCIES.asString())
            .withFactory(TaskDTOModule::new);
    }

    private final String type;
    private final Optional<RunningOptionsDTO> runningOptions;

    public SolveMessageInconsistenciesTaskDTO(@JsonProperty("type") String type,
                                              @JsonProperty("runningOptions") Optional<RunningOptionsDTO> runningOptions) {
        this.type = type;
        this.runningOptions = runningOptions;
    }

    private SolveMessageInconsistenciesTask toDomainObject(SolveMessageInconsistenciesService service) {
        return new SolveMessageInconsistenciesTask(service,
            runningOptions
                .map(RunningOptionsDTO::asDomainObject)
                .orElse(RunningOptions.DEFAULT));
    }

    @Override
    public String getType() {
        return type;
    }

    public Optional<RunningOptionsDTO> getRunningOptions() {
        return runningOptions;
    }
}
