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
import java.util.Optional;

import org.apache.james.core.MailAddress;
import org.apache.james.json.DTOModule;
import org.apache.james.queue.api.MailQueueName;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.fge.lambdas.Throwing;

public class DeleteMailsFromMailQueueTaskAdditionalInformationDTO implements AdditionalInformationDTO {
    public static AdditionalInformationDTOModule<DeleteMailsFromMailQueueTask.AdditionalInformation, DeleteMailsFromMailQueueTaskAdditionalInformationDTO> module() {
        return DTOModule.forDomainObject(DeleteMailsFromMailQueueTask.AdditionalInformation.class)
            .convertToDTO(DeleteMailsFromMailQueueTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(DeleteMailsFromMailQueueTaskAdditionalInformationDTO::fromDTO)
            .toDTOConverter(DeleteMailsFromMailQueueTaskAdditionalInformationDTO::toDTO)
            .typeName(DeleteMailsFromMailQueueTask.TYPE.asString())
            .withFactory(AdditionalInformationDTOModule::new);
    }

    private static DeleteMailsFromMailQueueTaskAdditionalInformationDTO toDTO(DeleteMailsFromMailQueueTask.AdditionalInformation domainObject, String typeName) {
        return new DeleteMailsFromMailQueueTaskAdditionalInformationDTO(
            typeName,
            domainObject.getMailQueueName(),
            domainObject.getSender(),
            domainObject.getName(),
            domainObject.getRecipient(),
            domainObject.getInitialCount(),
            domainObject.getRemainingCount(),
            domainObject.timestamp());
    }

    private static DeleteMailsFromMailQueueTask.AdditionalInformation fromDTO(DeleteMailsFromMailQueueTaskAdditionalInformationDTO dto) {
        return new DeleteMailsFromMailQueueTask.AdditionalInformation(
            MailQueueName.of(dto.getQueue()),
            dto.getInitialCount(),
            dto.getRemainingCount(),
            dto.sender.map(Throwing.<String, MailAddress>function(MailAddress::new).sneakyThrow()),
            dto.name,
            dto.recipient.map(Throwing.<String, MailAddress>function(MailAddress::new).sneakyThrow()),
            dto.timestamp);
    }


    private final String queue;
    private final String type;
    private final Optional<String> sender;
    private final Optional<String> name;
    private final Optional<String> recipient;
    private final long initialCount;
    private final long remainingCount;
    private final Instant timestamp;

    public DeleteMailsFromMailQueueTaskAdditionalInformationDTO(@JsonProperty("type") String type,
                                                                @JsonProperty("queue") String queue,
                                                                @JsonProperty("sender") Optional<String> sender,
                                                                @JsonProperty("name") Optional<String> name,
                                                                @JsonProperty("recipient") Optional<String> recipient,
                                                                @JsonProperty("initialCount") long initialCount,
                                                                @JsonProperty("remainingCount") long remainingCount,
                                                                @JsonProperty("timestamp") Instant timestamp
    ) {
        this.type = type;
        this.queue = queue;
        this.sender = sender;
        this.name = name;
        this.recipient = recipient;
        this.initialCount = initialCount;
        this.remainingCount = remainingCount;
        this.timestamp = timestamp;
    }


    public String getQueue() {
        return queue;
    }

    public Optional<String> getSender() {
        return sender;
    }

    public Optional<String> getName() {
        return name;
    }

    public Optional<String> getRecipient() {
        return recipient;
    }

    public long getInitialCount() {
        return initialCount;
    }

    public long getRemainingCount() {
        return remainingCount;
    }

    @Override
    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public String getType() {
        return type;
    }
}