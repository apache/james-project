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

import java.time.Clock;
import java.time.Instant;

import org.apache.james.json.DTOModule;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MessageIdReindexingTaskAdditionalInformationDTO implements AdditionalInformationDTO {

    public static AdditionalInformationDTOModule<MessageIdReIndexingTask.AdditionalInformation, MessageIdReindexingTaskAdditionalInformationDTO> module(MessageId.Factory factory) {
        return DTOModule.forDomainObject(MessageIdReIndexingTask.AdditionalInformation.class)
            .convertToDTO(MessageIdReindexingTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(dto -> new MessageIdReIndexingTask.AdditionalInformation(factory.fromString(dto.getMessageId()), dto.timestamp))
            .toDTOConverter((details, type) -> new MessageIdReindexingTaskAdditionalInformationDTO(type, details.getMessageId(), details.timestamp()))
            .typeName(MessageIdReIndexingTask.TYPE.asString())
            .withFactory(AdditionalInformationDTOModule::new);
    }

    private final String type;
    private final String messageId;
    private final Instant timestamp;

    private MessageIdReindexingTaskAdditionalInformationDTO(@JsonProperty("type") String type,
                                                            @JsonProperty("messageId") String messageId,
                                                            @JsonProperty("timestamp") Instant timestamp) {
        this.type = type;
        this.messageId = messageId;
        this.timestamp = timestamp;
    }

    public String getMessageId() {
        return messageId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public String getType() {
        return type;
    }

    public static MessageIdReindexingTaskAdditionalInformationDTO of(MessageIdReIndexingTask task) {
        return new MessageIdReindexingTaskAdditionalInformationDTO(
            task.type().asString(),
            task.getMessageId().serialize(),
            Clock.systemUTC().instant());
    }
}
