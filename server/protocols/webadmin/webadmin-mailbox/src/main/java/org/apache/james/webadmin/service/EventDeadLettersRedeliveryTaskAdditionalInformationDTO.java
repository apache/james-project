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

import static org.apache.james.webadmin.service.EventDeadLettersRedeliverService.RunningOptions;

import java.time.Instant;
import java.util.Optional;

import org.apache.james.events.EventDeadLetters;
import org.apache.james.events.Group;
import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.fge.lambdas.Throwing;

public class EventDeadLettersRedeliveryTaskAdditionalInformationDTO implements AdditionalInformationDTO {
    public static class EventDeadLettersRedeliveryTaskAdditionalInformationForAll extends EventDeadLettersRedeliveryTaskAdditionalInformation {

        public static class DTO extends EventDeadLettersRedeliveryTaskAdditionalInformationDTO {

            private final RunningOptions runningOptions;

            public DTO(@JsonProperty("type") String type,
                       @JsonProperty("successfulRedeliveriesCount") long successfulRedeliveriesCount,
                       @JsonProperty("failedRedeliveriesCount") long failedRedeliveriesCount,
                       @JsonProperty("group") Optional<String> group,
                       @JsonProperty("insertionId") Optional<String> insertionId,
                       @JsonProperty("timestamp") Instant timestamp,
                       @JsonProperty("runningOptions") RunningOptions runningOptions) {
                super(type, successfulRedeliveriesCount, failedRedeliveriesCount, group, insertionId, timestamp);
                this.runningOptions = runningOptions;
            }

            public RunningOptions getRunningOptions() {
                return runningOptions;
            }
        }

        public static AdditionalInformationDTOModule<EventDeadLettersRedeliveryTaskAdditionalInformationForAll, DTO> module() {
            return DTOModule
                .forDomainObject(EventDeadLettersRedeliveryTaskAdditionalInformationForAll.class)
                .convertToDTO(DTO.class)
                .toDomainObjectConverter(EventDeadLettersRedeliveryTaskAdditionalInformationDTO::fromAll)
                .toDTOConverter((domainObject, typeName) -> new DTO(typeName,
                    domainObject.getSuccessfulRedeliveriesCount(),
                    domainObject.getFailedRedeliveriesCount(),
                    domainObject.getGroup(),
                    domainObject.getInsertionId(),
                    domainObject.timestamp(),
                    domainObject.getRunningOptions()))
                .typeName(EventDeadLettersRedeliverAllTask.TYPE.asString())
                .withFactory(AdditionalInformationDTOModule::new);
        }

        private final RunningOptions runningOptions;

        EventDeadLettersRedeliveryTaskAdditionalInformationForAll(long successfulRedeliveriesCount, long failedRedeliveriesCount, Instant timestamp, RunningOptions runningOptions) {
            super(successfulRedeliveriesCount, failedRedeliveriesCount, Optional.empty(), Optional.empty(), timestamp);
            this.runningOptions = runningOptions;
        }

        public RunningOptions getRunningOptions() {
            return runningOptions;
        }
    }

    public static class EventDeadLettersRedeliveryTaskAdditionalInformationForGroup extends EventDeadLettersRedeliveryTaskAdditionalInformation {

        public static class DTO extends EventDeadLettersRedeliveryTaskAdditionalInformationDTO {
            private final RunningOptions runningOptions;

            public DTO(@JsonProperty("type") String type,
                       @JsonProperty("successfulRedeliveriesCount") long successfulRedeliveriesCount,
                       @JsonProperty("failedRedeliveriesCount") long failedRedeliveriesCount,
                       @JsonProperty("group") Optional<String> group,
                       @JsonProperty("insertionId") Optional<String> insertionId,
                       @JsonProperty("timestamp") Instant timestamp,
                       @JsonProperty("runningOptions") RunningOptions runningOptions) {
                super(type, successfulRedeliveriesCount, failedRedeliveriesCount, group, insertionId, timestamp);
                this.runningOptions = runningOptions;
            }

            public RunningOptions getRunningOptions() {
                return runningOptions;
            }
        }

        public static AdditionalInformationDTOModule<EventDeadLettersRedeliveryTaskAdditionalInformationForGroup, DTO> module() {
            return DTOModule.forDomainObject(EventDeadLettersRedeliveryTaskAdditionalInformationForGroup.class)
                .convertToDTO(DTO.class)
                .toDomainObjectConverter(EventDeadLettersRedeliveryTaskAdditionalInformationDTO::fromGroup)
                .toDTOConverter((domainObject, typeName) -> new DTO(typeName,
                    domainObject.getSuccessfulRedeliveriesCount(),
                    domainObject.getFailedRedeliveriesCount(),
                    domainObject.getGroup(),
                    domainObject.getInsertionId(),
                    domainObject.timestamp(),
                    domainObject.getRunningOptions()))
                .typeName(EventDeadLettersRedeliverGroupTask.TYPE.asString())
                .withFactory(AdditionalInformationDTOModule::new);
        }

        private final RunningOptions runningOptions;

        EventDeadLettersRedeliveryTaskAdditionalInformationForGroup(long successfulRedeliveriesCount, long failedRedeliveriesCount, Optional<Group> group, Instant timestamp, RunningOptions runningOptions) {
            super(successfulRedeliveriesCount, failedRedeliveriesCount, group, Optional.empty(), timestamp);
            this.runningOptions = runningOptions;
        }

        public RunningOptions getRunningOptions() {
            return runningOptions;
        }
    }

    public static class EventDeadLettersRedeliveryTaskAdditionalInformationForOne extends EventDeadLettersRedeliveryTaskAdditionalInformation {
        public static class DTO extends EventDeadLettersRedeliveryTaskAdditionalInformationDTO {
            public DTO(@JsonProperty("type") String type,
                       @JsonProperty("successfulRedeliveriesCount") long successfulRedeliveriesCount,
                       @JsonProperty("failedRedeliveriesCount") long failedRedeliveriesCount,
                       @JsonProperty("group") Optional<String> group,
                       @JsonProperty("insertionId") Optional<String> insertionId,
                       @JsonProperty("timestamp") Instant timestamp) {
                super(type, successfulRedeliveriesCount, failedRedeliveriesCount, group, insertionId, timestamp);
            }
        }

        public static AdditionalInformationDTOModule<EventDeadLettersRedeliveryTaskAdditionalInformationForOne, DTO> module() {
            return DTOModule.forDomainObject(EventDeadLettersRedeliveryTaskAdditionalInformationForOne.class)
                .convertToDTO(DTO.class)
                .toDomainObjectConverter(EventDeadLettersRedeliveryTaskAdditionalInformationDTO::fromOne)
                .toDTOConverter((domainObject, typeName) -> new DTO(typeName,
                    domainObject.getSuccessfulRedeliveriesCount(),
                    domainObject.getFailedRedeliveriesCount(),
                    domainObject.getGroup(),
                    domainObject.getInsertionId(),
                    domainObject.timestamp()))
                .typeName(EventDeadLettersRedeliverOneTask.TYPE.asString())
                .withFactory(AdditionalInformationDTOModule::new);
        }


        EventDeadLettersRedeliveryTaskAdditionalInformationForOne(
            long successfulRedeliveriesCount,
            long failedRedeliveriesCount,
            Optional<Group> group,
            Optional<EventDeadLetters.InsertionId> insertionId,
            Instant timestamp) {
            super(successfulRedeliveriesCount, failedRedeliveriesCount, group, insertionId, timestamp);
        }
    }

    private static EventDeadLettersRedeliveryTaskAdditionalInformationForAll fromAll(EventDeadLettersRedeliveryTaskAdditionalInformationForAll.DTO dto) {
        return new EventDeadLettersRedeliveryTaskAdditionalInformationForAll(
            dto.getSuccessfulRedeliveriesCount(),
            dto.getFailedRedeliveriesCount(),
            dto.getTimestamp(),
            dto.runningOptions);
    }

    private static EventDeadLettersRedeliveryTaskAdditionalInformationForGroup fromGroup(EventDeadLettersRedeliveryTaskAdditionalInformationForGroup.DTO dto) {
        return new EventDeadLettersRedeliveryTaskAdditionalInformationForGroup(
            dto.getSuccessfulRedeliveriesCount(),
            dto.getFailedRedeliveriesCount(),
            dto.getGroup().map(Throwing.function(Group::deserialize).sneakyThrow()),
            dto.getTimestamp(),
            dto.runningOptions);
    }

    private static EventDeadLettersRedeliveryTaskAdditionalInformationForOne fromOne(EventDeadLettersRedeliveryTaskAdditionalInformationDTO dto) {
        return new EventDeadLettersRedeliveryTaskAdditionalInformationForOne(
            dto.successfulRedeliveriesCount,
            dto.failedRedeliveriesCount,
            dto.group.map(Throwing.function(Group::deserialize).sneakyThrow()),
            dto.insertionId.map(EventDeadLetters.InsertionId::of),
            dto.timestamp);
    }

    private final String type;
    private final long successfulRedeliveriesCount;
    private final long failedRedeliveriesCount;
    private final Optional<String> group;
    private final Optional<String> insertionId;
    private final Instant timestamp;

    public EventDeadLettersRedeliveryTaskAdditionalInformationDTO(@JsonProperty("type") String type,
                                                                  @JsonProperty("successfulRedeliveriesCount") long successfulRedeliveriesCount,
                                                                  @JsonProperty("failedRedeliveriesCount") long failedRedeliveriesCount,
                                                                  @JsonProperty("group") Optional<String> group,
                                                                  @JsonProperty("insertionId") Optional<String> insertionId,
                                                                  @JsonProperty("timestamp") Instant timestamp
    ) {
        this.type = type;
        this.successfulRedeliveriesCount = successfulRedeliveriesCount;
        this.failedRedeliveriesCount = failedRedeliveriesCount;
        this.group = group;
        this.insertionId = insertionId;
        this.timestamp = timestamp;
    }


    public long getSuccessfulRedeliveriesCount() {
        return successfulRedeliveriesCount;
    }

    public long getFailedRedeliveriesCount() {
        return failedRedeliveriesCount;
    }

    public Optional<String> getGroup() {
        return group;
    }

    public Optional<String> getInsertionId() {
        return insertionId;
    }

    @Override
    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public String getType() {
        return type;
    }
}