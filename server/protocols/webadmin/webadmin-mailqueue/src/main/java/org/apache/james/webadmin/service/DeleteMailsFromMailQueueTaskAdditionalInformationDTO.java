package org.apache.james.webadmin.service;

import java.util.Optional;

import org.apache.james.core.MailAddress;
import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.fge.lambdas.Throwing;

public class DeleteMailsFromMailQueueTaskAdditionalInformationDTO implements AdditionalInformationDTO {

    public static final AdditionalInformationDTOModule<DeleteMailsFromMailQueueTask.AdditionalInformation, DeleteMailsFromMailQueueTaskAdditionalInformationDTO> MODULE =
        DTOModule
            .forDomainObject(DeleteMailsFromMailQueueTask.AdditionalInformation.class)
            .convertToDTO(DeleteMailsFromMailQueueTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(DeleteMailsFromMailQueueTaskAdditionalInformationDTO::fromDTO)
            .toDTOConverter(DeleteMailsFromMailQueueTaskAdditionalInformationDTO::toDTO)
            .typeName(DeleteMailsFromMailQueueTask.TYPE.asString())
            .withFactory(AdditionalInformationDTOModule::new);

    private static DeleteMailsFromMailQueueTaskAdditionalInformationDTO toDTO(DeleteMailsFromMailQueueTask.AdditionalInformation domainObject, String typeName) {
        return new DeleteMailsFromMailQueueTaskAdditionalInformationDTO(
            typeName,
            domainObject.getMailQueueName(),
            domainObject.getSender(),
            domainObject.getName(),
            domainObject.getRecipient(),
            domainObject.getInitialCount(),
            domainObject.getRemainingCount());
    }

    private static DeleteMailsFromMailQueueTask.AdditionalInformation fromDTO(DeleteMailsFromMailQueueTaskAdditionalInformationDTO dto) {
        return new DeleteMailsFromMailQueueTask.AdditionalInformation(
            dto.getQueue(),
            dto.getInitialCount(),
            dto.getRemainingCount(),
            dto.sender.map(Throwing.<String, MailAddress>function(MailAddress::new).sneakyThrow()),
            dto.name,
            dto.recipient.map(Throwing.<String, MailAddress>function(MailAddress::new).sneakyThrow()));
    }


    private final String queue;
    private final String type;
    private final Optional<String> sender;
    private final Optional<String> name;
    private final Optional<String> recipient;
    private final long initialCount;
    private final long remainingCount;

    public DeleteMailsFromMailQueueTaskAdditionalInformationDTO(@JsonProperty("type") String type,
                                                                @JsonProperty("queue") String queue,
                                                                @JsonProperty("sender") Optional<String> sender,
                                                                @JsonProperty("name") Optional<String> name,
                                                                @JsonProperty("recipient") Optional<String> recipient,
                                                                @JsonProperty("initialCount") long initialCount,
                                                                @JsonProperty("remainingCount") long remainingCount
    ) {
        this.type = type;
        this.queue = queue;
        this.sender = sender;
        this.name = name;
        this.recipient = recipient;
        this.initialCount = initialCount;
        this.remainingCount = remainingCount;
    }


    public String getQueue() {
        return queue;
    }

    public Optional<String> getSender() {
        return sender;
    }

    public Optional<String> getName() {
        return name;
    }

    public Optional<String> getRecipient() {
        return recipient;
    }

    public long getInitialCount() {
        return initialCount;
    }

    public long getRemainingCount() {
        return remainingCount;
    }

    @Override
    public String getType() {
        return type;
    }
}