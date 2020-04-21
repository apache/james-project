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
package org.apache.james.backends.cassandra.migration;

import org.apache.james.backends.cassandra.versions.SchemaVersion;
import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MigrationTaskDTO implements TaskDTO {

    public static TaskDTOModule<MigrationTask, MigrationTaskDTO> module(MigrationTask.Factory factory) {
        return DTOModule.forDomainObject(MigrationTask.class)
            .convertToDTO(MigrationTaskDTO.class)
            .toDomainObjectConverter(dto -> factory.create(new SchemaVersion(dto.targetVersion)))
            .toDTOConverter((task, type) -> new MigrationTaskDTO(type, task.getTarget().getValue()))
            .typeName(MigrationTask.CASSANDRA_MIGRATION.asString())
            .withFactory(TaskDTOModule::new);
    }

    private final int targetVersion;
    private final String type;

    MigrationTaskDTO(@JsonProperty("type") String type, @JsonProperty("targetVersion") int targetVersion) {
        this.type = type;
        this.targetVersion = targetVersion;
    }

    @Override
    public String getType() {
        return type;
    }

    public int getTargetVersion() {
        return targetVersion;
    }
}
