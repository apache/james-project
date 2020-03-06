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
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;

public class RecomputeMailboxCountersTaskAdditionalInformationDTO implements AdditionalInformationDTO {
    private static RecomputeMailboxCountersTaskAdditionalInformationDTO fromDomainObject(RecomputeMailboxCountersTask.Details details, String type) {
        return new RecomputeMailboxCountersTaskAdditionalInformationDTO(
            type,
            details.getProcessedMailboxes(),
            details.getFailedMailboxes(),
            details.timestamp());
    }

    public static final AdditionalInformationDTOModule<RecomputeMailboxCountersTask.Details, RecomputeMailboxCountersTaskAdditionalInformationDTO> MODULE =
        DTOModule
            .forDomainObject(RecomputeMailboxCountersTask.Details.class)
            .convertToDTO(RecomputeMailboxCountersTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(RecomputeMailboxCountersTaskAdditionalInformationDTO::toDomainObject)
            .toDTOConverter(RecomputeMailboxCountersTaskAdditionalInformationDTO::fromDomainObject)
            .typeName(RecomputeMailboxCountersTask.RECOMPUTE_MAILBOX_COUNTERS.asString())
            .withFactory(AdditionalInformationDTOModule::new);

    private final String type;
    private final long processedMailboxes;
    private final ImmutableList<String> failedMailboxes;
    private final Instant timestamp;

    public RecomputeMailboxCountersTaskAdditionalInformationDTO(@JsonProperty("type") String type,
                                                                @JsonProperty("processedMailboxes") long processedMailboxes,
                                                                @JsonProperty("failedMailboxes") ImmutableList<String> failedMailboxes,
                                                                @JsonProperty("timestamp") Instant timestamp) {
        this.type = type;
        this.processedMailboxes = processedMailboxes;
        this.failedMailboxes = failedMailboxes;
        this.timestamp = timestamp;
    }

    public long getProcessedMailboxes() {
        return processedMailboxes;
    }

    public ImmutableList<String> getFailedMailboxes() {
        return failedMailboxes;
    }

    @Override
    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public String getType() {
        return type;
    }

    private RecomputeMailboxCountersTask.Details toDomainObject() {
        return new RecomputeMailboxCountersTask.Details(timestamp,
            processedMailboxes,
            failedMailboxes);
    }
}
