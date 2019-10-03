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

public class MessageIdReindexingTaskDTO implements TaskDTO {

    public static TaskDTOModule<MessageIdReIndexingTask, MessageIdReindexingTaskDTO> module(MessageIdReIndexingTask.Factory factory) {
        return DTOModule
            .forDomainObject(MessageIdReIndexingTask.class)
            .convertToDTO(MessageIdReindexingTaskDTO.class)
            .toDomainObjectConverter(factory::create)
            .toDTOConverter(MessageIdReindexingTaskDTO::of)
            .typeName(MessageIdReIndexingTask.TYPE.asString())
            .withFactory(TaskDTOModule::new);
    }

    private final String type;
    private final String messageId;

    private MessageIdReindexingTaskDTO(@JsonProperty("type") String type, @JsonProperty("messageId") String messageId) {
        this.type = type;
        this.messageId = messageId;
    }

    @Override
    public String getType() {
        return type;
    }

    public String getMessageId() {
        return messageId;
    }

    public static MessageIdReindexingTaskDTO of(MessageIdReIndexingTask task, String type) {
        return new MessageIdReindexingTaskDTO(type, task.getMessageId().serialize());
    }
}
