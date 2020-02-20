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

import org.apache.james.json.DTOModule;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.queue.api.MailQueueName;
import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ClearMailQueueTaskDTO implements TaskDTO {

    public static TaskDTOModule<ClearMailQueueTask, ClearMailQueueTaskDTO> module(MailQueueFactory<? extends ManageableMailQueue> mailQueueFactory) {
        return DTOModule
            .forDomainObject(ClearMailQueueTask.class)
            .convertToDTO(ClearMailQueueTaskDTO.class)
            .toDomainObjectConverter(dto -> dto.fromDTO(mailQueueFactory))
            .toDTOConverter(ClearMailQueueTaskDTO::toDTO)
            .typeName(ClearMailQueueTask.TYPE.asString())
            .withFactory(TaskDTOModule::new);
    }

    public static ClearMailQueueTaskDTO toDTO(ClearMailQueueTask domainObject, String typeName) {
        return new ClearMailQueueTaskDTO(typeName, domainObject.getQueueName().asString());
    }

    private final String type;
    private final String queueName;

    public ClearMailQueueTaskDTO(@JsonProperty("type") String type, @JsonProperty("queue") String queueName) {
        this.type = type;
        this.queueName = queueName;
    }

    public ClearMailQueueTask fromDTO(MailQueueFactory<? extends ManageableMailQueue> mailQueueFactory) {
        return new ClearMailQueueTask(MailQueueName.of(queueName),
            name -> mailQueueFactory
                .getQueue(name)
                .orElseThrow(() -> new ClearMailQueueTask.UnknownSerializedQueue(queueName)));
    }

    @Override
    public String getType() {
        return type;
    }

    public String getQueue() {
        return queueName;
    }
}
