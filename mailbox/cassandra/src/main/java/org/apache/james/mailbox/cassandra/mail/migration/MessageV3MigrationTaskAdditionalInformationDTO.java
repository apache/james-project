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

package org.apache.james.mailbox.cassandra.mail.migration;

import java.time.Instant;

import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MessageV3MigrationTaskAdditionalInformationDTO implements AdditionalInformationDTO {
    private static MessageV3MigrationTaskAdditionalInformationDTO fromDomainObject(MessageV3Migration.AdditionalInformation additionalInformation, String type) {
        return new MessageV3MigrationTaskAdditionalInformationDTO(
            type,
            additionalInformation.timestamp()
        );
    }

    public static final AdditionalInformationDTOModule<MessageV3Migration.AdditionalInformation, MessageV3MigrationTaskAdditionalInformationDTO> MODULE =
        DTOModule
            .forDomainObject(MessageV3Migration.AdditionalInformation.class)
            .convertToDTO(MessageV3MigrationTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(MessageV3MigrationTaskAdditionalInformationDTO::toDomainObject)
            .toDTOConverter(MessageV3MigrationTaskAdditionalInformationDTO::fromDomainObject)
            .typeName(MessageV3Migration.TYPE.asString())
            .withFactory(AdditionalInformationDTOModule::new);

    private final String type;
    private final Instant timestamp;

    public MessageV3MigrationTaskAdditionalInformationDTO(@JsonProperty("type") String type,
                                                          @JsonProperty("timestamp") Instant timestamp) {
        this.type = type;
        this.timestamp = timestamp;
    }

    @Override
    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public String getType() {
        return type;
    }

    private MessageV3Migration.AdditionalInformation toDomainObject() {
        return new MessageV3Migration.AdditionalInformation(timestamp);
    }
}
