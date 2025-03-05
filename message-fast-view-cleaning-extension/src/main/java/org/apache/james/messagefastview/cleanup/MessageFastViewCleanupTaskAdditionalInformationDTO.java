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

package org.apache.james.messagefastview.cleanup;

import java.time.Instant;
import java.util.Optional;

import org.apache.james.json.DTOModule;
import org.apache.james.messagefastview.cleanup.MessageFastViewCleanupService.RunningOptions;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MessageFastViewCleanupTaskAdditionalInformationDTO implements AdditionalInformationDTO {
    private static MessageFastViewCleanupTaskAdditionalInformationDTO fromDomainObject(MessageFastViewCleanupTask.Details details, String type) {
        return new MessageFastViewCleanupTaskAdditionalInformationDTO(
            type,
            details.getDeletedMessageFastViews(),
            Optional.of(RunningOptionsDTO.asDTO(details.getRunningOptions())),
            details.timestamp());
    }

    public static final AdditionalInformationDTOModule<MessageFastViewCleanupTask.Details, MessageFastViewCleanupTaskAdditionalInformationDTO> module() {
        return DTOModule
            .forDomainObject(MessageFastViewCleanupTask.Details.class)
            .convertToDTO(MessageFastViewCleanupTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(MessageFastViewCleanupTaskAdditionalInformationDTO::toDomainObject)
            .toDTOConverter(MessageFastViewCleanupTaskAdditionalInformationDTO::fromDomainObject)
            .typeName(MessageFastViewCleanupTask.CLEANUP_MESSAGE_FAST_VIEW.asString())
            .withFactory(AdditionalInformationDTOModule::new);
    }


    private final String type;
    private final long deletedMessageFastViews;
    private final Optional<RunningOptionsDTO> runningOptions;
    private final Instant timestamp;

    public MessageFastViewCleanupTaskAdditionalInformationDTO(@JsonProperty("type") String type,
                                                                @JsonProperty("deletedMessageFastViews") long deletedMessageFastViews,
                                                                @JsonProperty("runningOptions") Optional<RunningOptionsDTO> runningOptions,
                                                                @JsonProperty("timestamp") Instant timestamp) {
        this.type = type;
        this.deletedMessageFastViews = deletedMessageFastViews;
        this.runningOptions = runningOptions;
        this.timestamp = timestamp;
    }

    public long getDeletedMessageFastViews() {
        return deletedMessageFastViews;
    }

    public Optional<RunningOptionsDTO> getRunningOptions() {
        return runningOptions;
    }

    @Override
    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public String getType() {
        return type;
    }

    private MessageFastViewCleanupTask.Details toDomainObject() {
        return new MessageFastViewCleanupTask.Details(timestamp,
            deletedMessageFastViews,
            runningOptions.map(RunningOptionsDTO::asDomainObject).orElse(RunningOptions.DEFAULT));
    }
}
