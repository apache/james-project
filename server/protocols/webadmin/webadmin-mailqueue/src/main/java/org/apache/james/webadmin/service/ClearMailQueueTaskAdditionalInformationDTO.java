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
import org.apache.james.queue.api.MailQueueName;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ClearMailQueueTaskAdditionalInformationDTO implements AdditionalInformationDTO {

    public static AdditionalInformationDTOModule<ClearMailQueueTask.AdditionalInformation, ClearMailQueueTaskAdditionalInformationDTO> module() {
        return DTOModule.forDomainObject(ClearMailQueueTask.AdditionalInformation.class)
            .convertToDTO(ClearMailQueueTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(dto -> new ClearMailQueueTask.AdditionalInformation(
                MailQueueName.of(dto.mailQueueName),
                dto.initialCount,
                dto.remainingCount,
                dto.timestamp))
            .toDTOConverter((details, type) -> new ClearMailQueueTaskAdditionalInformationDTO(
                type,
                details.getMailQueueName(),
                details.getInitialCount(),
                details.getRemainingCount(),
                details.timestamp()))
            .typeName(ClearMailQueueTask.TYPE.asString())
            .withFactory(AdditionalInformationDTOModule::new);
    }

    private final String mailQueueName;
    private final String type;
    private final long initialCount;
    private final long remainingCount;
    private final Instant timestamp;

    public ClearMailQueueTaskAdditionalInformationDTO(@JsonProperty("type") String type,
                                                      @JsonProperty("mailQueueName") String mailQueueName,
                                                      @JsonProperty("initialCount") long initialCount,
                                                      @JsonProperty("remainingCount") long remainingCount,
                                                      @JsonProperty("timestamp") Instant timestamp) {
        this.type = type;
        this.mailQueueName = mailQueueName;
        this.initialCount = initialCount;
        this.remainingCount = remainingCount;
        this.timestamp = timestamp;
    }

    public String getMailQueueName() {
        return mailQueueName;
    }

    public long getInitialCount() {
        return initialCount;
    }

    public long getRemainingCount() {
        return remainingCount;
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
