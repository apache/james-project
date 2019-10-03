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

import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SingleMessageReindexingTaskDTO implements TaskDTO {

    public static TaskDTOModule<SingleMessageReindexingTask, SingleMessageReindexingTaskDTO> module(SingleMessageReindexingTask.Factory factory) {
        return DTOModule
            .forDomainObject(SingleMessageReindexingTask.class)
            .convertToDTO(SingleMessageReindexingTaskDTO.class)
            .toDomainObjectConverter(factory::create)
            .toDTOConverter(SingleMessageReindexingTaskDTO::of)
            .typeName(SingleMessageReindexingTask.MESSAGE_RE_INDEXING.asString())
            .withFactory(TaskDTOModule::new);
    }

    public static SingleMessageReindexingTaskDTO of(SingleMessageReindexingTask task, String type) {
        return new SingleMessageReindexingTaskDTO(type, task.getMailboxId().serialize(), task.getUid().asLong());
    }

    private final String type;
    private final String mailboxId;
    private final long uid;

    private SingleMessageReindexingTaskDTO(@JsonProperty("type") String type, @JsonProperty("mailboxId") String mailboxId, @JsonProperty("uid") long uid) {
        this.type = type;
        this.mailboxId = mailboxId;
        this.uid = uid;
    }

    @Override
    public String getType() {
        return type;
    }

    public String getMailboxId() {
        return mailboxId;
    }

    public long getUid() {
        return uid;
    }

}
