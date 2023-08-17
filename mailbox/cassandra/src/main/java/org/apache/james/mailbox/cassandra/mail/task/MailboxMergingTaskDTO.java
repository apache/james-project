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

import org.apache.james.json.DTOModule;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MailboxMergingTaskDTO implements TaskDTO {
    private static final CassandraId.Factory CASSANDRA_ID_FACTORY = new CassandraId.Factory();

    private static MailboxMergingTaskDTO toDTO(MailboxMergingTask domainObject, String typeName) {
        return new MailboxMergingTaskDTO(
            typeName,
            domainObject.getContext().getTotalMessageCount(),
            domainObject.getOldMailboxId().serialize(),
            domainObject.getNewMailboxId().serialize()
        );
    }

    public static TaskDTOModule<MailboxMergingTask, MailboxMergingTaskDTO> module(MailboxMergingTaskRunner taskRunner) {
        return DTOModule
            .forDomainObject(MailboxMergingTask.class)
            .convertToDTO(MailboxMergingTaskDTO.class)
            .toDomainObjectConverter(dto -> dto.toDomainObject(taskRunner))
            .toDTOConverter(MailboxMergingTaskDTO::toDTO)
            .typeName(MailboxMergingTask.MAILBOX_MERGING.asString())
            .withFactory(TaskDTOModule::new);
    }

    private final String type;

    private final long totalMessageCount;
    private final String oldMailboxId;
    private final String newMailboxId;

    public MailboxMergingTaskDTO(@JsonProperty("type") String type,
                                 @JsonProperty("totalMessageCount") long totalMessageCount,
                                 @JsonProperty("oldMailboxId") String oldMailboxId,
                                 @JsonProperty("newMailboxId") String newMailboxId) {
        this.type = type;
        this.totalMessageCount = totalMessageCount;
        this.oldMailboxId = oldMailboxId;
        this.newMailboxId = newMailboxId;
    }

    private MailboxMergingTask toDomainObject(MailboxMergingTaskRunner taskRunner) {
        return new MailboxMergingTask(
            taskRunner,
            totalMessageCount,
            CASSANDRA_ID_FACTORY.fromString(oldMailboxId),
            CASSANDRA_ID_FACTORY.fromString(newMailboxId));
    }

    @Override
    public String getType() {
        return type;
    }

    public long getTotalMessageCount() {
        return totalMessageCount;
    }

    public String getOldMailboxId() {
        return oldMailboxId;
    }

    public String getNewMailboxId() {
        return newMailboxId;
    }
}
