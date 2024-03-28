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

package org.apache.james.modules.mailbox;

import static com.rabbitmq.client.MessageProperties.PERSISTENT_TEXT_PLAIN;
import static org.apache.james.backends.rabbitmq.Constants.ALLOW_QUORUM;
import static org.apache.james.backends.rabbitmq.Constants.AUTO_DELETE;
import static org.apache.james.backends.rabbitmq.Constants.DIRECT_EXCHANGE;
import static org.apache.james.backends.rabbitmq.Constants.DURABLE;
import static org.apache.james.backends.rabbitmq.Constants.EMPTY_ROUTING_KEY;
import static org.apache.james.backends.rabbitmq.Constants.EXCLUSIVE;

import java.util.Date;
import java.util.Optional;

import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;

import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.backends.rabbitmq.ReactorRabbitMQChannelPool;
import org.apache.james.blob.api.BlobId;
import org.apache.james.core.Username;
import org.apache.james.lifecycle.api.Startable;
import org.apache.james.mailbox.cassandra.DeleteMessageListener;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.vault.metadata.DeletedMessageVaultDeletionCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.rabbitmq.AcknowledgableDelivery;
import reactor.rabbitmq.BindingSpecification;
import reactor.rabbitmq.ConsumeOptions;
import reactor.rabbitmq.ExchangeSpecification;
import reactor.rabbitmq.OutboundMessage;
import reactor.rabbitmq.QueueSpecification;
import reactor.rabbitmq.Receiver;
import reactor.rabbitmq.Sender;

public class DistributedDeletedMessageVaultDeletionCallback implements DeleteMessageListener.DeletionCallback, Startable {
    public static final Logger LOGGER = LoggerFactory.getLogger(DistributedDeletedMessageVaultDeletionCallback.class);

    private static class CopyCommandDTO {
        public static CopyCommandDTO of(DeleteMessageListener.DeletedMessageCopyCommand command) {
            return new CopyCommandDTO(
                command.getMessageId().serialize(),
                command.getMailboxId().serialize(),
                command.getOwner().asString(),
                command.getInternalDate(),
                command.getSize(),
                command.hasAttachments(),
                command.getHeaderId().asString(),
                command.getBodyId().asString());
        }

        private final String messageId;
        private final String mailboxId;
        private final String owner;
        private final Date internalDate;
        private final long size;
        private final boolean hasAttachments;
        private final String headerId;
        private final String bodyId;

        @JsonCreator
        public CopyCommandDTO(@JsonProperty("messageId") String messageId,
                              @JsonProperty("mailboxId") String mailboxId,
                              @JsonProperty("owner") String owner,
                              @JsonProperty("internalDate") Date internalDate,
                              @JsonProperty("size") long size,
                              @JsonProperty("hasAttachments") boolean hasAttachments,
                              @JsonProperty("headerId") String headerId,
                              @JsonProperty("bodyId") String bodyId) {
            this.messageId = messageId;
            this.mailboxId = mailboxId;
            this.owner = owner;
            this.internalDate = internalDate;
            this.size = size;
            this.hasAttachments = hasAttachments;
            this.headerId = headerId;
            this.bodyId = bodyId;
        }

        public String getMessageId() {
            return messageId;
        }

        public String getMailboxId() {
            return mailboxId;
        }

        public String getOwner() {
            return owner;
        }

        public Date getInternalDate() {
            return internalDate;
        }

        public long getSize() {
            return size;
        }

        public boolean isHasAttachments() {
            return hasAttachments;
        }

        public String getHeaderId() {
            return headerId;
        }

        public String getBodyId() {
            return bodyId;
        }

        @JsonIgnore
        DeleteMessageListener.DeletedMessageCopyCommand asPojo(MailboxId.Factory mailboxIdFactory, MessageId.Factory messageIdFactory, BlobId.Factory blobIdFactory) {
            return new DeleteMessageListener.DeletedMessageCopyCommand(messageIdFactory.fromString(messageId),
                mailboxIdFactory.fromString(messageId),
                Username.of(owner),
                internalDate,
                size,
                hasAttachments,
                blobIdFactory.from(headerId),
                blobIdFactory.from(bodyId));
        }
    }

    private static final String EXCHANGE = "deleted-message-vault";
    private static final String QUEUE = "deleted-message-vault-work-queue";
    private static final String DEAD_LETTER = QUEUE + "-dead-letter";
    private static final boolean REQUEUE = true;
    private static final int QOS = 5;

    private final ReactorRabbitMQChannelPool channelPool;
    private final RabbitMQConfiguration rabbitMQConfiguration;
    private final DeletedMessageVaultDeletionCallback callback;
    private final Sender sender;
    private final ObjectMapper objectMapper;
    private final MailboxId.Factory mailboxIdFactory;
    private final MessageId.Factory messageIdFactory;
    private final BlobId.Factory blobIdFactory;
    private Receiver receiver;
    private Disposable disposable;

    @Inject
    public DistributedDeletedMessageVaultDeletionCallback(Sender sender,
                                                          ReactorRabbitMQChannelPool channelPool,
                                                          RabbitMQConfiguration rabbitMQConfiguration,
                                                          DeletedMessageVaultDeletionCallback callback,
                                                          MailboxId.Factory mailboxIdFactory,
                                                          MessageId.Factory messageIdFactory,
                                                          BlobId.Factory blobIdFactory) {
        this.sender = sender;
        this.rabbitMQConfiguration = rabbitMQConfiguration;
        this.callback = callback;
        this.mailboxIdFactory = mailboxIdFactory;
        this.messageIdFactory = messageIdFactory;
        this.blobIdFactory = blobIdFactory;
        this.objectMapper = new ObjectMapper();
        this.channelPool = channelPool;
    }

    public void init() {
        Flux.concat(
                sender.declareExchange(ExchangeSpecification.exchange(EXCHANGE)
                    .durable(DURABLE)
                    .type(DIRECT_EXCHANGE)),
                sender.declareQueue(QueueSpecification.queue(DEAD_LETTER)
                    .durable(DURABLE)
                    .exclusive(!EXCLUSIVE)
                    .autoDelete(!AUTO_DELETE)
                    .arguments(rabbitMQConfiguration.workQueueArgumentsBuilder(!ALLOW_QUORUM)
                        .deadLetter(DEAD_LETTER)
                        .build())),
                sender.declareQueue(QueueSpecification.queue(QUEUE)
                    .durable(DURABLE)
                    .exclusive(!EXCLUSIVE)
                    .autoDelete(!AUTO_DELETE)
                    .arguments(rabbitMQConfiguration.workQueueArgumentsBuilder(!ALLOW_QUORUM)
                        .deadLetter(DEAD_LETTER)
                        .build())),
                sender.bind(BindingSpecification.binding()
                    .exchange(EXCHANGE)
                    .queue(QUEUE)
                    .routingKey(EMPTY_ROUTING_KEY)))
            .then()
            .block();

        receiver = channelPool.createReceiver();
        disposable = receiver.consumeManualAck(QUEUE, new ConsumeOptions().qos(QOS))
            .flatMap(this::handleMessage)
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe();
    }

    @PreDestroy
    public void stop() {
        Optional.ofNullable(disposable).ifPresent(Disposable::dispose);
        Optional.ofNullable(receiver).ifPresent(Receiver::close);
    }

    private Mono<Void> handleMessage(AcknowledgableDelivery delivery) {
        try {
            CopyCommandDTO copyCommandDTO = objectMapper.readValue(delivery.getBody(), CopyCommandDTO.class);

            return callback.forMessage(copyCommandDTO.asPojo(mailboxIdFactory, messageIdFactory, blobIdFactory))
                .doOnError(e -> {
                    LOGGER.error("Failed executing deletion callback for {}", copyCommandDTO.messageId, e);
                    delivery.nack(REQUEUE);
                })
                .doOnSuccess(any -> delivery.ack())
                .doOnCancel(() -> delivery.nack(REQUEUE));
        } catch (Exception e) {
            LOGGER.error("Deserialization error: reject poisonous message for distributed Deleted message vault callback", e);
            // Deserialization error: reject poisonous messages
            delivery.nack(!REQUEUE);
            return Mono.empty();
        }
    }

    @Override
    public Mono<Void> forMessage(DeleteMessageListener.DeletedMessageCopyCommand command) {
        CopyCommandDTO dto = CopyCommandDTO.of(command);
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(dto);
            return sender.send(Mono.just(new OutboundMessage(EXCHANGE, EMPTY_ROUTING_KEY, new AMQP.BasicProperties.Builder()
                .deliveryMode(PERSISTENT_TEXT_PLAIN.getDeliveryMode())
                .priority(PERSISTENT_TEXT_PLAIN.getPriority())
                .contentType(PERSISTENT_TEXT_PLAIN.getContentType())
                .build(), bytes)));
        } catch (JsonProcessingException e) {
            return Mono.error(e);
        }
    }


}
