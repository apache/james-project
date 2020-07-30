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

import java.time.Instant;

import org.apache.james.json.DTOModule;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.queue.api.MailQueueName;
import org.apache.james.queue.rabbitmq.RabbitMQMailQueue;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RepublishNotProcessedMailsTaskDTO implements TaskDTO {

    public static class UnknownMailQueueException extends RuntimeException {
        public UnknownMailQueueException(MailQueueName mailQueueName) {
            super("Unknown mail queue " + mailQueueName.asString());
        }
    }

    public static TaskDTOModule<RepublishNotprocessedMailsTask, RepublishNotProcessedMailsTaskDTO> module(MailQueueFactory<RabbitMQMailQueue> mailQueueFactory) {
        return DTOModule
            .forDomainObject(RepublishNotprocessedMailsTask.class)
            .convertToDTO(RepublishNotProcessedMailsTaskDTO.class)
            .toDomainObjectConverter(dto -> dto.fromDTO(mailQueueFactory))
            .toDTOConverter(RepublishNotProcessedMailsTaskDTO::toDTO)
            .typeName(RepublishNotprocessedMailsTask.TYPE.asString())
            .withFactory(TaskDTOModule::new);
    }

    public static RepublishNotProcessedMailsTaskDTO toDTO(RepublishNotprocessedMailsTask domainObject, String typeName) {
        return new RepublishNotProcessedMailsTaskDTO(typeName, domainObject.getMailQueue().asString(), domainObject.getOlderThan());
    }

    private final String type;
    private final String mailQueue;
    private final Instant olderThan;

    public RepublishNotProcessedMailsTaskDTO(@JsonProperty("type") String type, @JsonProperty("mailQueue") String mailQueue, @JsonProperty("olderThan") Instant olderThan) {
        this.type = type;
        this.mailQueue = mailQueue;
        this.olderThan = olderThan;
    }

    public RepublishNotprocessedMailsTask fromDTO(MailQueueFactory<RabbitMQMailQueue> mailQueueFactory) {
        MailQueueName requestedMailQueueName = MailQueueName.of(mailQueue);
        RabbitMQMailQueue queue = mailQueueFactory
            .getQueue(requestedMailQueueName)
            .orElseThrow(() -> new UnknownMailQueueException(requestedMailQueueName));

        return new RepublishNotprocessedMailsTask(queue, olderThan);
    }

    @Override
    public String getType() {
        return type;
    }

    public Instant getOlderThan() {
        return olderThan;
    }

    public String getMailQueue() {
        return mailQueue;
    }
}
