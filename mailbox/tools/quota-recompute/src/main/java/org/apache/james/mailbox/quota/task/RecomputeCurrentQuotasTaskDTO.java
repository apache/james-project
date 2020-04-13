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

package org.apache.james.mailbox.quota.task;

import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RecomputeCurrentQuotasTaskDTO implements TaskDTO {
    private static RecomputeCurrentQuotasTaskDTO toDTO(RecomputeCurrentQuotasTask domainObject, String typeName) {
        return new RecomputeCurrentQuotasTaskDTO(typeName);
    }

    public static TaskDTOModule<RecomputeCurrentQuotasTask, RecomputeCurrentQuotasTaskDTO> module(RecomputeCurrentQuotasService service) {
        return DTOModule
            .forDomainObject(RecomputeCurrentQuotasTask.class)
            .convertToDTO(RecomputeCurrentQuotasTaskDTO.class)
            .toDomainObjectConverter(dto -> dto.toDomainObject(service))
            .toDTOConverter(RecomputeCurrentQuotasTaskDTO::toDTO)
            .typeName(RecomputeCurrentQuotasTask.RECOMPUTE_CURRENT_QUOTAS.asString())
            .withFactory(TaskDTOModule::new);
    }

    private final String type;

    public RecomputeCurrentQuotasTaskDTO(@JsonProperty("type") String type) {
        this.type = type;
    }

    private RecomputeCurrentQuotasTask toDomainObject(RecomputeCurrentQuotasService service) {
        return new RecomputeCurrentQuotasTask(service);
    }

    @Override
    public String getType() {
        return type;
    }
}
