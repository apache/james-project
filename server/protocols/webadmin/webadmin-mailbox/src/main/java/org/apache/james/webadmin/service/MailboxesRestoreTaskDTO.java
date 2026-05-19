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

package org.apache.james.webadmin.service;

import static org.apache.james.webadmin.service.MailboxesRestoreTask.TASK_TYPE;

import java.util.Optional;

import org.apache.james.blob.api.BlobId;
import org.apache.james.core.Username;
import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MailboxesRestoreTaskDTO implements TaskDTO {
    private final String type;
    private final String username;
    private final String blobId;
    private final Optional<Boolean> force;

    public MailboxesRestoreTaskDTO(@JsonProperty("type") String type,
                                   @JsonProperty("username") String username,
                                   @JsonProperty("blobId") String blobId,
                                   @JsonProperty("force") Optional<Boolean> force) {
        this.type = type;
        this.username = username;
        this.blobId = blobId;
        this.force = force;
    }

    @Override
    public String getType() {
        return type;
    }

    public String getUsername() {
        return username;
    }

    public String getBlobId() {
        return blobId;
    }

    public Optional<Boolean> getForce() {
        return force;
    }

    public static TaskDTOModule<MailboxesRestoreTask, MailboxesRestoreTaskDTO> module(RestoreService service, BlobId.Factory blobIdFactory) {
        return DTOModule
            .forDomainObject(MailboxesRestoreTask.class)
            .convertToDTO(MailboxesRestoreTaskDTO.class)
            .toDomainObjectConverter(dto -> dto.fromDTO(service, blobIdFactory))
            .toDTOConverter(MailboxesRestoreTaskDTO::toDTO)
            .typeName(TASK_TYPE.asString())
            .withFactory(TaskDTOModule::new);
    }

    public MailboxesRestoreTask fromDTO(RestoreService service, BlobId.Factory blobIdFactory) {
        return new MailboxesRestoreTask(service, Username.of(username), blobIdFactory.parse(blobId), force);
    }

    public static MailboxesRestoreTaskDTO toDTO(MailboxesRestoreTask domainObject, String typeName) {
        return new MailboxesRestoreTaskDTO(typeName, domainObject.getUsername().asString(), domainObject.getBlobId().asString(), domainObject.isForce());
    }
}
