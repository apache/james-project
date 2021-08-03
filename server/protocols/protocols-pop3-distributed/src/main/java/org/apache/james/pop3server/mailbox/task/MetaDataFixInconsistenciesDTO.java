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

package org.apache.james.pop3server.mailbox.task;

import org.apache.james.json.DTOModule;
import org.apache.james.pop3server.mailbox.task.MetaDataFixInconsistenciesService.RunningOptions;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class MetaDataFixInconsistenciesDTO implements TaskDTO {
    public static TaskDTOModule<MetaDataFixInconsistenciesTask, MetaDataFixInconsistenciesDTO> module(MetaDataFixInconsistenciesService service) {
        return DTOModule.forDomainObject(MetaDataFixInconsistenciesTask.class)
            .convertToDTO(MetaDataFixInconsistenciesDTO.class)
            .toDomainObjectConverter(dto -> toDomainObject(dto, service))
            .toDTOConverter(MetaDataFixInconsistenciesDTO::toDto)
            .typeName(MetaDataFixInconsistenciesTask.TASK_TYPE.asString())
            .withFactory(TaskDTOModule::new);
    }

    private static MetaDataFixInconsistenciesTask toDomainObject(MetaDataFixInconsistenciesDTO dto,
                                                                 MetaDataFixInconsistenciesService service) {
        return new MetaDataFixInconsistenciesTask(service, dto.getRunningOptions());
    }

    private static MetaDataFixInconsistenciesDTO toDto(MetaDataFixInconsistenciesTask details, String type) {
        return new MetaDataFixInconsistenciesDTO(
            details.getRunningOptions(),
            type);
    }

    private final RunningOptions runningOptions;
    private final String type;

    @JsonCreator
    public MetaDataFixInconsistenciesDTO(@JsonProperty("runningOptions") RunningOptions runningOptions,
                                         @JsonProperty("type") String type) {
        this.runningOptions = runningOptions;
        this.type = type;
    }

    @Override
    public String getType() {
        return type;
    }

    public RunningOptions getRunningOptions() {
        return runningOptions;
    }
}
