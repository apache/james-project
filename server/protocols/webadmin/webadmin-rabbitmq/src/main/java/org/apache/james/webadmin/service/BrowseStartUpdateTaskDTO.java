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

import static org.apache.james.queue.rabbitmq.MailQueueName.fromString;

import org.apache.james.json.DTOModule;
import org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueView;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public class BrowseStartUpdateTaskDTO implements TaskDTO {
    public static TaskDTOModule<BrowseStartUpdateTask, BrowseStartUpdateTaskDTO> module(CassandraMailQueueView.Factory cassandraMailQueueView) {
        return DTOModule
            .forDomainObject(BrowseStartUpdateTask.class)
            .convertToDTO(BrowseStartUpdateTaskDTO.class)
            .toDomainObjectConverter(dto -> new BrowseStartUpdateTask(fromString(dto.mailQueue), cassandraMailQueueView))
            .toDTOConverter(BrowseStartUpdateTaskDTO::toDTO)
            .typeName(BrowseStartUpdateTask.TYPE.asString())
            .withFactory(TaskDTOModule::new);
    }

    public static BrowseStartUpdateTaskDTO toDTO(BrowseStartUpdateTask domainObject, String typeName) {
        return new BrowseStartUpdateTaskDTO(typeName, domainObject.getMailQueue().asString());
    }

    private final String type;
    private final String mailQueue;

    public BrowseStartUpdateTaskDTO(@JsonProperty("type") String type, @JsonProperty("mailQueue") String mailQueue) {
        this.type = type;
        this.mailQueue = mailQueue;
    }

    @Override
    public String getType() {
        return type;
    }

    public String getMailQueue() {
        return mailQueue;
    }
}
