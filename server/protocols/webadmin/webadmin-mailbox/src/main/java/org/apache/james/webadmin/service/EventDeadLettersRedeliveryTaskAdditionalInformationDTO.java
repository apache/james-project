package org.apache.james.webadmin.service;

import java.time.Instant;
import java.util.Optional;

import org.apache.james.json.DTOModule;
import org.apache.james.mailbox.events.EventDeadLetters;
import org.apache.james.mailbox.events.Group;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.fge.lambdas.Throwing;

public class EventDeadLettersRedeliveryTaskAdditionalInformationDTO implements AdditionalInformationDTO {
    public static class EventDeadLettersRedeliveryTaskAdditionalInformationForAll extends EventDeadLettersRedeliveryTaskAdditionalInformation {

        public static class DTO extends EventDeadLettersRedeliveryTaskAdditionalInformationDTO {
            public DTO(@JsonProperty("type") String type,
                       @JsonProperty("successfulRedeliveriesCount") long successfulRedeliveriesCount,
                       @JsonProperty("failedRedeliveriesCount") long failedRedeliveriesCount,
                       @JsonProperty("group") Optional<String> group,
                       @JsonProperty("insertionId") Optional<String> insertionId,
                       @JsonProperty("timestamp") Instant timestamp) {
                super(type, successfulRedeliveriesCount, failedRedeliveriesCount, group,insertionId, timestamp);
            }
        }

        public static final AdditionalInformationDTOModule<EventDeadLettersRedeliveryTaskAdditionalInformationForAll, DTO> MODULE =
            DTOModule
                .forDomainObject(EventDeadLettersRedeliveryTaskAdditionalInformationForAll.class)
                .convertToDTO(DTO.class)
                .toDomainObjectConverter(EventDeadLettersRedeliveryTaskAdditionalInformationDTO::fromAll)
                .toDTOConverter((domainObject, typeName) -> new DTO(typeName,
                    domainObject.getSuccessfulRedeliveriesCount(),
                    domainObject.getFailedRedeliveriesCount(),
                    domainObject.getGroup(),
                    domainObject.getInsertionId(),
                    domainObject.timestamp()))
                .typeName(EventDeadLettersRedeliverAllTask.TYPE.asString())
                .withFactory(AdditionalInformationDTOModule::new);


        EventDeadLettersRedeliveryTaskAdditionalInformationForAll(long successfulRedeliveriesCount, long failedRedeliveriesCount, Instant timestamp) {
            super(successfulRedeliveriesCount, failedRedeliveriesCount, Optional.empty(), Optional.empty(), timestamp);
        }
    }

    public static class EventDeadLettersRedeliveryTaskAdditionalInformationForGroup extends EventDeadLettersRedeliveryTaskAdditionalInformation {

        public static class DTO extends EventDeadLettersRedeliveryTaskAdditionalInformationDTO {
            public DTO(@JsonProperty("type") String type,
                       @JsonProperty("successfulRedeliveriesCount") long successfulRedeliveriesCount,
                       @JsonProperty("failedRedeliveriesCount") long failedRedeliveriesCount,
                       @JsonProperty("group") Optional<String> group,
                       @JsonProperty("insertionId") Optional<String> insertionId,
                       @JsonProperty("timestamp") Instant timestamp) {
                super(type, successfulRedeliveriesCount, failedRedeliveriesCount, group,insertionId, timestamp);
            }
        }

        public static final AdditionalInformationDTOModule<EventDeadLettersRedeliveryTaskAdditionalInformationForGroup, DTO> MODULE =
            DTOModule
                .forDomainObject(EventDeadLettersRedeliveryTaskAdditionalInformationForGroup.class)
                .convertToDTO(DTO.class)
                .toDomainObjectConverter(EventDeadLettersRedeliveryTaskAdditionalInformationDTO::fromGroup)
                .toDTOConverter((domainObject, typeName) -> new DTO(typeName,
                    domainObject.getSuccessfulRedeliveriesCount(),
                    domainObject.getFailedRedeliveriesCount(),
                    domainObject.getGroup(),
                    domainObject.getInsertionId(),
                    domainObject.timestamp()))
                .typeName(EventDeadLettersRedeliverGroupTask.TYPE.asString())
                .withFactory(AdditionalInformationDTOModule::new);


        EventDeadLettersRedeliveryTaskAdditionalInformationForGroup(long successfulRedeliveriesCount, long failedRedeliveriesCount, Optional<Group> group, Instant timestamp) {
            super(successfulRedeliveriesCount, failedRedeliveriesCount, group, Optional.empty(), timestamp);
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
                super(type, successfulRedeliveriesCount, failedRedeliveriesCount, group,insertionId, timestamp);
            }
        }

        public static final AdditionalInformationDTOModule<EventDeadLettersRedeliveryTaskAdditionalInformationForOne, DTO> MODULE =
            DTOModule
                .forDomainObject(EventDeadLettersRedeliveryTaskAdditionalInformationForOne.class)
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


        EventDeadLettersRedeliveryTaskAdditionalInformationForOne(
            long successfulRedeliveriesCount,
            long failedRedeliveriesCount,
            Optional<Group> group,
            Optional<EventDeadLetters.InsertionId> insertionId,
            Instant timestamp) {
            super(successfulRedeliveriesCount, failedRedeliveriesCount, group, insertionId, timestamp);
        }
    }

    private static EventDeadLettersRedeliveryTaskAdditionalInformationForAll fromAll(EventDeadLettersRedeliveryTaskAdditionalInformationDTO dto) {
        return new EventDeadLettersRedeliveryTaskAdditionalInformationForAll(
            dto.successfulRedeliveriesCount,
            dto.failedRedeliveriesCount,
            dto.timestamp);
    }

    private static EventDeadLettersRedeliveryTaskAdditionalInformationForGroup fromGroup(EventDeadLettersRedeliveryTaskAdditionalInformationDTO dto) {
        return new EventDeadLettersRedeliveryTaskAdditionalInformationForGroup(
            dto.successfulRedeliveriesCount,
            dto.failedRedeliveriesCount,
            dto.group.map(Throwing.function(Group::deserialize).sneakyThrow()),
            dto.timestamp);
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