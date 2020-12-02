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

public class AclV2MigrationTaskAdditionalInformationDTO implements AdditionalInformationDTO {
    private static AclV2MigrationTaskAdditionalInformationDTO fromDomainObject(AclV2Migration.AdditionalInformation additionalInformation, String type) {
        return new AclV2MigrationTaskAdditionalInformationDTO(
            type,
            additionalInformation.timestamp()
        );
    }

    public static final AdditionalInformationDTOModule<AclV2Migration.AdditionalInformation, AclV2MigrationTaskAdditionalInformationDTO> MODULE =
        DTOModule
            .forDomainObject(AclV2Migration.AdditionalInformation.class)
            .convertToDTO(AclV2MigrationTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(AclV2MigrationTaskAdditionalInformationDTO::toDomainObject)
            .toDTOConverter(AclV2MigrationTaskAdditionalInformationDTO::fromDomainObject)
            .typeName(AclV2Migration.TYPE.asString())
            .withFactory(AdditionalInformationDTOModule::new);

    private final String type;
    private final Instant timestamp;

    public AclV2MigrationTaskAdditionalInformationDTO(@JsonProperty("type") String type,
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

    private AclV2Migration.AdditionalInformation toDomainObject() {
        return new AclV2Migration.AdditionalInformation(timestamp);
    }
}
