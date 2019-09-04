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
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ClearMailQueueTaskAdditionalInformationDTO implements AdditionalInformationDTO {

    static final AdditionalInformationDTOModule<ClearMailQueueTask.AdditionalInformation, ClearMailQueueTaskAdditionalInformationDTO> SERIALIZATION_MODULE =
        DTOModule.forDomainObject(ClearMailQueueTask.AdditionalInformation.class)
            .convertToDTO(ClearMailQueueTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(dto -> new ClearMailQueueTask.AdditionalInformation(
                dto.mailQueueName,
                dto.initialCount,
                dto.remainingCount
            ))
            .toDTOConverter((details, type) -> new ClearMailQueueTaskAdditionalInformationDTO(
                details.getMailQueueName(),
                details.getInitialCount(),
                details.getRemainingCount()))
            .typeName(ClearMailQueueTask.TYPE.asString())
            .withFactory(AdditionalInformationDTOModule::new);

    private final String mailQueueName;
    private final long initialCount;
    private final long remainingCount;

    public ClearMailQueueTaskAdditionalInformationDTO(@JsonProperty("mailQueueName") String mailQueueName,
                                                      @JsonProperty("initialCount") long initialCount,
                                                      @JsonProperty("remainingCount") long remainingCount) {
        this.mailQueueName = mailQueueName;
        this.initialCount = initialCount;
        this.remainingCount = remainingCount;
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

}
