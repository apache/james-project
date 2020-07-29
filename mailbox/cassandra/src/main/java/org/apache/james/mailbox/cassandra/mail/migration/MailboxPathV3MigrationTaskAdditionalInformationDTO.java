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

public class MailboxPathV3MigrationTaskAdditionalInformationDTO implements AdditionalInformationDTO {

    private static MailboxPathV3MigrationTaskAdditionalInformationDTO fromDomainObject(MailboxPathV3Migration.AdditionalInformation additionalInformation, String type) {
        return new MailboxPathV3MigrationTaskAdditionalInformationDTO(
            type,
            additionalInformation.getRemainingCount(),
            additionalInformation.getInitialCount(),
            additionalInformation.timestamp()
        );
    }

    public static final AdditionalInformationDTOModule<MailboxPathV3Migration.AdditionalInformation, MailboxPathV3MigrationTaskAdditionalInformationDTO> MODULE =
        DTOModule
            .forDomainObject(MailboxPathV3Migration.AdditionalInformation.class)
            .convertToDTO(MailboxPathV3MigrationTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(MailboxPathV3MigrationTaskAdditionalInformationDTO::toDomainObject)
            .toDTOConverter(MailboxPathV3MigrationTaskAdditionalInformationDTO::fromDomainObject)
            .typeName(MailboxPathV3Migration.TYPE.asString())
            .withFactory(AdditionalInformationDTOModule::new);

    private final String type;
    private final long remainingCount;
    private final long initialCount;
    private final Instant timestamp;

    public MailboxPathV3MigrationTaskAdditionalInformationDTO(@JsonProperty("type") String type,
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

    private MailboxPathV3Migration.AdditionalInformation toDomainObject() {
        return new MailboxPathV3Migration.AdditionalInformation(
            remainingCount,
            initialCount,
            timestamp);
    }
}
