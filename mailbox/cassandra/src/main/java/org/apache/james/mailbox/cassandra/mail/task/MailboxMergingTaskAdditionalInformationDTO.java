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

package org.apache.james.mailbox.cassandra.mail.task;

import java.time.Instant;

import org.apache.james.json.DTOModule;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MailboxMergingTaskAdditionalInformationDTO implements AdditionalInformationDTO {
    private static final CassandraId.Factory ID_FACTORY = new CassandraId.Factory();

    private static MailboxMergingTaskAdditionalInformationDTO fromDomainObject(MailboxMergingTask.Details details, String type) {
        return new MailboxMergingTaskAdditionalInformationDTO(
            type,
            details.getOldMailboxId(),
            details.getNewMailboxId(),
            details.getTotalMessageCount(),
            details.getMessageMovedCount(),
            details.getMessageFailedCount(),
            details.timestamp()
        );
    }

    public static AdditionalInformationDTOModule<MailboxMergingTask.Details, MailboxMergingTaskAdditionalInformationDTO> module() {
        return DTOModule.forDomainObject(MailboxMergingTask.Details.class)
            .convertToDTO(MailboxMergingTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(MailboxMergingTaskAdditionalInformationDTO::toDomainObject)
            .toDTOConverter(MailboxMergingTaskAdditionalInformationDTO::fromDomainObject)
            .typeName(MailboxMergingTask.MAILBOX_MERGING.asString())
            .withFactory(AdditionalInformationDTOModule::new);
    }


    private final String type;
    private final String oldMailboxId;
    private final String newMailboxId;
    private final long totalMessageCount;
    private final long messageMovedCount;
    private final long messageFailedCount;
    private final Instant timestamp;

    public MailboxMergingTaskAdditionalInformationDTO(@JsonProperty("type") String type,
                                                      @JsonProperty("oldMailboxId") String oldMailboxId,
                                                      @JsonProperty("newMailboxId") String newMailboxId,
                                                      @JsonProperty("totalMessageCount") long totalMessageCount,
                                                      @JsonProperty("messageMovedCount") long messageMovedCount,
                                                      @JsonProperty("messageFailedCount") long messageFailedCount,
                                                      @JsonProperty("timestamp") Instant timestamp) {
        this.type = type;
        this.oldMailboxId = oldMailboxId;
        this.newMailboxId = newMailboxId;
        this.totalMessageCount = totalMessageCount;
        this.messageMovedCount = messageMovedCount;
        this.messageFailedCount = messageFailedCount;
        this.timestamp = timestamp;
    }

    public String getOldMailboxId() {
        return oldMailboxId;
    }

    public String getNewMailboxId() {
        return newMailboxId;
    }

    public long getTotalMessageCount() {
        return totalMessageCount;
    }

    public long getMessageMovedCount() {
        return messageMovedCount;
    }

    public long getMessageFailedCount() {
        return messageFailedCount;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public Instant getTimestamp() {
        return timestamp;
    }

    private MailboxMergingTask.Details toDomainObject() {
        return new MailboxMergingTask.Details(
            ID_FACTORY.fromString(oldMailboxId),
            ID_FACTORY.fromString(newMailboxId),
            totalMessageCount,
            messageMovedCount,
            messageFailedCount,
            timestamp
        );
    }
}
