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

import java.time.Instant;

import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public class BrowseStartUpdateTaskAdditionalInformationDTO implements AdditionalInformationDTO {
    public static AdditionalInformationDTOModule<BrowseStartUpdateTask.AdditionalInformation, BrowseStartUpdateTaskAdditionalInformationDTO> module() {
        return DTOModule.forDomainObject(BrowseStartUpdateTask.AdditionalInformation.class)
            .convertToDTO(BrowseStartUpdateTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(dto -> new BrowseStartUpdateTask.AdditionalInformation(
                fromString(dto.mailQueue),
                dto.timestamp))
            .toDTOConverter((details, type) -> new BrowseStartUpdateTaskAdditionalInformationDTO(
                type,
                details.getMailQueue().asString(),
                details.timestamp()))
            .typeName(BrowseStartUpdateTask.TYPE.asString())
            .withFactory(AdditionalInformationDTOModule::new);
    }

    private final String type;
    private final String mailQueue;
    private final Instant timestamp;

    public BrowseStartUpdateTaskAdditionalInformationDTO(@JsonProperty("type") String type,
                                                         @JsonProperty("mailQueue") String mailQueue,
                                                         @JsonProperty("timestamp") Instant timestamp) {
        this.type = type;
        this.mailQueue = mailQueue;
        this.timestamp = timestamp;
    }

    public String getMailQueue() {
        return mailQueue;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public Instant getTimestamp() {
        return timestamp;
    }
}
