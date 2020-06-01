package org.apache.james.webadmin.service;

import java.time.Instant;
import java.util.Optional;

import org.apache.james.core.MailAddress;
import org.apache.james.json.DTOModule;
import org.apache.james.queue.api.MailQueueName;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.fge.lambdas.Throwing;

public class WebAdminDeleteMailsFromMailQueueTaskAdditionalInformationDTO implements AdditionalInformationDTO {

    public static final AdditionalInformationDTOModule<DeleteMailsFromMailQueueTask.AdditionalInformation, WebAdminDeleteMailsFromMailQueueTaskAdditionalInformationDTO> MODULE =
        DTOModule
            .forDomainObject(DeleteMailsFromMailQueueTask.AdditionalInformation.class)
            .convertToDTO(WebAdminDeleteMailsFromMailQueueTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(WebAdminDeleteMailsFromMailQueueTaskAdditionalInformationDTO::fromDTO)
            .toDTOConverter(WebAdminDeleteMailsFromMailQueueTaskAdditionalInformationDTO::toDTO)
            .typeName(DeleteMailsFromMailQueueTask.TYPE.asString())
            .withFactory(AdditionalInformationDTOModule::new);

    private static WebAdminDeleteMailsFromMailQueueTaskAdditionalInformationDTO toDTO(DeleteMailsFromMailQueueTask.AdditionalInformation domainObject, String typeName) {
        return new WebAdminDeleteMailsFromMailQueueTaskAdditionalInformationDTO(
            typeName,
            domainObject.getMailQueueName(),
            domainObject.getSender(),
            domainObject.getName(),
            domainObject.getRecipient(),
            domainObject.getInitialCount(),
            domainObject.getRemainingCount(),
            domainObject.timestamp());
    }

    private static DeleteMailsFromMailQueueTask.AdditionalInformation fromDTO(WebAdminDeleteMailsFromMailQueueTaskAdditionalInformationDTO dto) {
        return new DeleteMailsFromMailQueueTask.AdditionalInformation(
            MailQueueName.of(dto.getMailQueueName()),
            dto.getInitialCount(),
            dto.getRemainingCount(),
            dto.sender.map(Throwing.<String, MailAddress>function(MailAddress::new).sneakyThrow()),
            dto.name,
            dto.recipient.map(Throwing.<String, MailAddress>function(MailAddress::new).sneakyThrow()),
            dto.timestamp);
    }

    private final String mailQueueName;
    private final String type;
    private final Optional<String> sender;
    private final Optional<String> name;
    private final Optional<String> recipient;
    private final long initialCount;
    private final long remainingCount;
    private final Instant timestamp;

    public WebAdminDeleteMailsFromMailQueueTaskAdditionalInformationDTO(@JsonProperty("type") String type,
                                                                        @JsonProperty("mailQueueName") String mailQueueName,
                                                                        @JsonProperty("sender") Optional<String> sender,
                                                                        @JsonProperty("name") Optional<String> name,
                                                                        @JsonProperty("recipient") Optional<String> recipient,
                                                                        @JsonProperty("initialCount") long initialCount,
                                                                        @JsonProperty("remainingCount") long remainingCount,
                                                                        @JsonProperty("timestamp") Instant timestamp) {
        this.type = type;
        this.mailQueueName = mailQueueName;
        this.sender = sender;
        this.name = name;
        this.recipient = recipient;
        this.initialCount = initialCount;
        this.remainingCount = remainingCount;
        this.timestamp = timestamp;
    }


    public String getMailQueueName() {
        return mailQueueName;
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
    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public String getType() {
        return type;
    }
}