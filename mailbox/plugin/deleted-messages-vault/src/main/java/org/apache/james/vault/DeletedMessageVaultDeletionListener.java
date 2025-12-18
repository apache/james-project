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

package org.apache.james.vault;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;

import jakarta.inject.Inject;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.events.Event;
import org.apache.james.events.EventListener;
import org.apache.james.events.Group;
import org.apache.james.mailbox.events.MailboxEvents.MessageContentDeletionEvent;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mime4j.MimeIOException;
import org.apache.james.mime4j.codec.DecodeMonitor;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.address.Mailbox;
import org.apache.james.mime4j.message.DefaultMessageBuilder;
import org.apache.james.mime4j.stream.MimeConfig;
import org.apache.james.server.core.Envelope;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Mono;

public class DeletedMessageVaultDeletionListener implements EventListener.ReactiveGroupEventListener {
    public static class DeletedMessageVaultListenerGroup extends Group {

    }

    private static final Group DELETED_MESSAGE_VAULT_DELETION_GROUP = new DeletedMessageVaultListenerGroup();
    private static final Logger LOGGER = LoggerFactory.getLogger(DeletedMessageVaultDeletionListener.class);

    private final BlobId.Factory blobIdFactory;
    private final DeletedMessageVault deletedMessageVault;
    private final BlobStore blobStore;
    private final Clock clock;

    @Inject
    public DeletedMessageVaultDeletionListener(BlobId.Factory blobIdFactory, DeletedMessageVault deletedMessageVault,
                                               BlobStore blobStore, Clock clock) {
        this.blobIdFactory = blobIdFactory;
        this.deletedMessageVault = deletedMessageVault;
        this.blobStore = blobStore;
        this.clock = clock;
    }

    @Override
    public Group getDefaultGroup() {
        return DELETED_MESSAGE_VAULT_DELETION_GROUP;
    }

    @Override
    public boolean isHandling(Event event) {
        return event instanceof MessageContentDeletionEvent;
    }

    @Override
    public Publisher<Void> reactiveEvent(Event event) {
        if (event instanceof MessageContentDeletionEvent contentDeletionEvent) {
            return forMessage(contentDeletionEvent);
        }

        return Mono.empty();
    }

    public Mono<Void> forMessage(MessageContentDeletionEvent messageContentDeletionEvent) {
        return fetchMessageHeaderBytes(messageContentDeletionEvent)
            .flatMap(bytes -> {
                Optional<Message> mimeMessage = parseMessage(new ByteArrayInputStream(bytes), messageContentDeletionEvent.messageId());
                DeletedMessage deletedMessage = DeletedMessage.builder()
                    .messageId(messageContentDeletionEvent.messageId())
                    .originMailboxes(messageContentDeletionEvent.mailboxId())
                    .user(messageContentDeletionEvent.getUsername())
                    .deliveryDate(ZonedDateTime.ofInstant(messageContentDeletionEvent.internalDate(), ZoneOffset.UTC))
                    .deletionDate(ZonedDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
                    .sender(retrieveSender(mimeMessage))
                    .recipients(retrieveRecipients(mimeMessage))
                    .hasAttachment(messageContentDeletionEvent.hasAttachments())
                    .size(messageContentDeletionEvent.size())
                    .subject(mimeMessage.map(Message::getSubject))
                    .build();

                return Mono.from(blobStore.readReactive(blobStore.getDefaultBucketName(), blobIdFactory.parse(messageContentDeletionEvent.bodyBlobId()), BlobStore.StoragePolicy.LOW_COST))
                    .map(bodyStream -> new SequenceInputStream(new ByteArrayInputStream(bytes), bodyStream))
                    .flatMap(bodyStream -> Mono.from(deletedMessageVault.append(deletedMessage, bodyStream)));
            });
    }

    private Mono<byte[]> fetchMessageHeaderBytes(MessageContentDeletionEvent messageContentDeletionEvent) {
        return Mono.justOrEmpty(messageContentDeletionEvent.headerBlobId())
            .flatMap(headerBlobId -> Mono.from(blobStore.readBytes(blobStore.getDefaultBucketName(), blobIdFactory.parse(headerBlobId), BlobStore.StoragePolicy.LOW_COST)))
            .switchIfEmpty(Mono.justOrEmpty(messageContentDeletionEvent.headerContent())
                .map(headerContent -> headerContent.getBytes(StandardCharsets.UTF_8))
                .switchIfEmpty(Mono.error(() -> new IllegalArgumentException("No header content nor header blob id provided"))));
    }

    private Optional<Message> parseMessage(InputStream inputStream, MessageId messageId) {
        DefaultMessageBuilder messageBuilder = new DefaultMessageBuilder();
        messageBuilder.setMimeEntityConfig(MimeConfig.PERMISSIVE);
        messageBuilder.setDecodeMonitor(DecodeMonitor.SILENT);
        try {
            return Optional.ofNullable(messageBuilder.parseMessage(inputStream));
        } catch (MimeIOException e) {
            LOGGER.warn("Can not parse the message {}", messageId, e);
            return Optional.empty();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private MaybeSender retrieveSender(Optional<Message> mimeMessage) {
        return mimeMessage
            .map(Message::getSender)
            .map(Mailbox::getAddress)
            .map(MaybeSender::getMailSender)
            .orElse(MaybeSender.nullSender());
    }

    private Set<MailAddress> retrieveRecipients(Optional<Message> maybeMessage) {
        return maybeMessage.map(message -> Envelope.fromMime4JMessage(message, Envelope.ValidationPolicy.IGNORE))
            .map(Envelope::getRecipients)
            .orElse(ImmutableSet.of());
    }
}
