package org.apache.james.webadmin.service;

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
        public static final AdditionalInformationDTOModule<EventDeadLettersRedeliveryTaskAdditionalInformationForAll, EventDeadLettersRedeliveryTaskAdditionalInformationDTO> MODULE =
            DTOModule
                .forDomainObject(EventDeadLettersRedeliveryTaskAdditionalInformationForAll.class)
                .convertToDTO(EventDeadLettersRedeliveryTaskAdditionalInformationDTO.class)
                .toDomainObjectConverter(EventDeadLettersRedeliveryTaskAdditionalInformationDTO::fromAll)
                .toDTOConverter(EventDeadLettersRedeliveryTaskAdditionalInformationDTO::toDTO)
                .typeName(EventDeadLettersRedeliverAllTask.TYPE.asString())
                .withFactory(AdditionalInformationDTOModule::new);


        EventDeadLettersRedeliveryTaskAdditionalInformationForAll(long successfulRedeliveriesCount, long failedRedeliveriesCount) {
            super(successfulRedeliveriesCount, failedRedeliveriesCount, Optional.empty(), Optional.empty());
        }
    }

    public static class EventDeadLettersRedeliveryTaskAdditionalInformationForGroup extends EventDeadLettersRedeliveryTaskAdditionalInformation {
        public static final AdditionalInformationDTOModule<EventDeadLettersRedeliveryTaskAdditionalInformationForGroup, EventDeadLettersRedeliveryTaskAdditionalInformationDTO> MODULE =
            DTOModule
                .forDomainObject(EventDeadLettersRedeliveryTaskAdditionalInformationForGroup.class)
                .convertToDTO(EventDeadLettersRedeliveryTaskAdditionalInformationDTO.class)
                .toDomainObjectConverter(EventDeadLettersRedeliveryTaskAdditionalInformationDTO::fromGroup)
                .toDTOConverter(EventDeadLettersRedeliveryTaskAdditionalInformationDTO::toDTO)
                .typeName(EventDeadLettersRedeliverGroupTask.TYPE.asString())
                .withFactory(AdditionalInformationDTOModule::new);


        EventDeadLettersRedeliveryTaskAdditionalInformationForGroup(long successfulRedeliveriesCount, long failedRedeliveriesCount, Optional<Group> group) {
            super(successfulRedeliveriesCount, failedRedeliveriesCount, group, Optional.empty());
        }
    }

    public static class EventDeadLettersRedeliveryTaskAdditionalInformationForOne extends EventDeadLettersRedeliveryTaskAdditionalInformation {
        public static final AdditionalInformationDTOModule<EventDeadLettersRedeliveryTaskAdditionalInformationForOne, EventDeadLettersRedeliveryTaskAdditionalInformationDTO> MODULE =
            DTOModule
                .forDomainObject(EventDeadLettersRedeliveryTaskAdditionalInformationForOne.class)
                .convertToDTO(EventDeadLettersRedeliveryTaskAdditionalInformationDTO.class)
                .toDomainObjectConverter(EventDeadLettersRedeliveryTaskAdditionalInformationDTO::fromOne)
                .toDTOConverter(EventDeadLettersRedeliveryTaskAdditionalInformationDTO::toDTO)
                .typeName(EventDeadLettersRedeliverOneTask.TYPE.asString())
                .withFactory(AdditionalInformationDTOModule::new);


        EventDeadLettersRedeliveryTaskAdditionalInformationForOne(long successfulRedeliveriesCount, long failedRedeliveriesCount, Optional<Group> group, Optional<EventDeadLetters.InsertionId> insertionId) {
            super(successfulRedeliveriesCount, failedRedeliveriesCount, group, insertionId);
        }
    }

    private static EventDeadLettersRedeliveryTaskAdditionalInformationDTO toDTO(EventDeadLettersRedeliveryTaskAdditionalInformation domainObject, String typeName) {
        return new EventDeadLettersRedeliveryTaskAdditionalInformationDTO(
            typeName,
            domainObject.getSuccessfulRedeliveriesCount(),
            domainObject.getFailedRedeliveriesCount(),
            domainObject.getGroup(),
            domainObject.getInsertionId());
    }

    private static EventDeadLettersRedeliveryTaskAdditionalInformationForAll fromAll(EventDeadLettersRedeliveryTaskAdditionalInformationDTO dto) {
        return new EventDeadLettersRedeliveryTaskAdditionalInformationForAll(
            dto.successfulRedeliveriesCount,
            dto.failedRedeliveriesCount);
    }

    private static EventDeadLettersRedeliveryTaskAdditionalInformationForGroup fromGroup(EventDeadLettersRedeliveryTaskAdditionalInformationDTO dto) {
        return new EventDeadLettersRedeliveryTaskAdditionalInformationForGroup(
            dto.successfulRedeliveriesCount,
            dto.failedRedeliveriesCount,
            dto.group.map(Throwing.function(Group::deserialize).sneakyThrow()));
    }

    private static EventDeadLettersRedeliveryTaskAdditionalInformationForOne fromOne(EventDeadLettersRedeliveryTaskAdditionalInformationDTO dto) {
        return new EventDeadLettersRedeliveryTaskAdditionalInformationForOne(
            dto.successfulRedeliveriesCount,
            dto.failedRedeliveriesCount,
            dto.group.map(Throwing.function(Group::deserialize).sneakyThrow()),
            dto.insertionId.map(EventDeadLetters.InsertionId::of));
    }

    private final String type;
    private final long successfulRedeliveriesCount;
    private final long failedRedeliveriesCount;
    private final Optional<String> group;
    private final Optional<String> insertionId;

    public EventDeadLettersRedeliveryTaskAdditionalInformationDTO(@JsonProperty("type") String type,
                                                                  @JsonProperty("successfulRedeliveriesCount") long successfulRedeliveriesCount,
                                                                  @JsonProperty("failedRedeliveriesCount") long failedRedeliveriesCount,
                                                                  @JsonProperty("group") Optional<String> group,
                                                                  @JsonProperty("insertionId") Optional<String> insertionId
    ) {
        this.type = type;
        this.successfulRedeliveriesCount = successfulRedeliveriesCount;
        this.failedRedeliveriesCount = failedRedeliveriesCount;
        this.group = group;
        this.insertionId = insertionId;
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
    public String getType() {
        return type;
    }
}