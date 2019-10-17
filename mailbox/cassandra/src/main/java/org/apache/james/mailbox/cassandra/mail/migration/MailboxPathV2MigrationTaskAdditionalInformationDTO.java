/**
 * *************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 * *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 * *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ***************************************************************/

package org.apache.james.mailbox.cassandra.mail.migration;

import java.time.Instant;

import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MailboxPathV2MigrationTaskAdditionalInformationDTO implements AdditionalInformationDTO {

    private static MailboxPathV2MigrationTaskAdditionalInformationDTO fromDomainObject(MailboxPathV2Migration.AdditionalInformation additionalInformation, String type) {
        return new MailboxPathV2MigrationTaskAdditionalInformationDTO(
            type,
            additionalInformation.getRemainingCount(),
            additionalInformation.getInitialCount(),
            additionalInformation.timestamp()
        );
    }

    public static final AdditionalInformationDTOModule<MailboxPathV2Migration.AdditionalInformation, MailboxPathV2MigrationTaskAdditionalInformationDTO> MODULE =
        DTOModule
            .forDomainObject(MailboxPathV2Migration.AdditionalInformation.class)
            .convertToDTO(MailboxPathV2MigrationTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(MailboxPathV2MigrationTaskAdditionalInformationDTO::toDomainObject)
            .toDTOConverter(MailboxPathV2MigrationTaskAdditionalInformationDTO::fromDomainObject)
            .typeName(MailboxPathV2Migration.TYPE.asString())
            .withFactory(AdditionalInformationDTOModule::new);

    private final String type;
    private final long remainingCount;
    private final long initialCount;
    private final Instant timestamp;

    public MailboxPathV2MigrationTaskAdditionalInformationDTO(@JsonProperty("type") String type,
                                                              @JsonProperty("remainingCount") long remainingCount,
                                                              @JsonProperty("initialCount") long initialCount,
                                                              @JsonProperty("timestamp") Instant timestamp) {
        this.type = type;
        this.remainingCount = remainingCount;
        this.initialCount = initialCount;
        this.timestamp = timestamp;
    }

    public long getRemainingCount() {
        return remainingCount;
    }

    public long getInitialCount() {
        return initialCount;
    }

    @Override
    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public String getType() {
        return type;
    }

    private MailboxPathV2Migration.AdditionalInformation toDomainObject() {
        return new MailboxPathV2Migration.AdditionalInformation(
            remainingCount,
            initialCount,
            timestamp
        );
    }
}
