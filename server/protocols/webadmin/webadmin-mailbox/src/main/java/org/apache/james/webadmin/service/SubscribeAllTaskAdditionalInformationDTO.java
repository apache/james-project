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

import org.apache.james.core.Username;
import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SubscribeAllTaskAdditionalInformationDTO implements AdditionalInformationDTO {
    public static final AdditionalInformationDTOModule<SubscribeAllTask.AdditionalInformation, SubscribeAllTaskAdditionalInformationDTO> SERIALIZATION_MODULE =
        DTOModule.forDomainObject(SubscribeAllTask.AdditionalInformation.class)
            .convertToDTO(SubscribeAllTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(SubscribeAllTaskAdditionalInformationDTO::toDomainObject)
            .toDTOConverter((details, type) -> new SubscribeAllTaskAdditionalInformationDTO(
                type,
                details.getUsername().asString(),
                details.getSubscribedCount(),
                details.getUnsubscribedCount(),
                details.timestamp()))
            .typeName(SubscribeAllTask.TASK_TYPE.asString())
            .withFactory(AdditionalInformationDTOModule::new);

    private final String type;
    private final String username;
    private final long subscribedCount;
    private final long unsubscribedCount;
    private final Instant timestamp;

    public SubscribeAllTaskAdditionalInformationDTO(@JsonProperty("type") String type,
                                                    @JsonProperty("username") String username,
                                                    @JsonProperty("subscribedCount") long subscribedCount,
                                                    @JsonProperty("unsubscribedCount") long unsubscribedCount,
                                                    @JsonProperty("timestamp") Instant timestamp) {
        this.type = type;
        this.username = username;
        this.subscribedCount = subscribedCount;
        this.unsubscribedCount = unsubscribedCount;
        this.timestamp = timestamp;
    }

    public String getUsername() {
        return username;
    }

    public long getSubscribedCount() {
        return subscribedCount;
    }

    public long getUnsubscribedCount() {
        return unsubscribedCount;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public Instant getTimestamp() {
        return timestamp;
    }

    public SubscribeAllTask.AdditionalInformation toDomainObject() {
        return new SubscribeAllTask.AdditionalInformation(Username.of(username), subscribedCount, unsubscribedCount, timestamp);
    }
}
