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

package org.apache.james.vault.metadata;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;

import javax.inject.Inject;

import org.apache.james.blob.api.BlobStore;
import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.core.Username;
import org.apache.james.mailbox.cassandra.DeleteMessageListener;
import org.apache.james.mailbox.cassandra.mail.MessageRepresentation;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mime4j.MimeIOException;
import org.apache.james.mime4j.codec.DecodeMonitor;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.address.Mailbox;
import org.apache.james.mime4j.message.DefaultMessageBuilder;
import org.apache.james.mime4j.stream.MimeConfig;
import org.apache.james.server.core.Envelope;
import org.apache.james.vault.DeletedMessage;
import org.apache.james.vault.DeletedMessageVault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Mono;

public class DeletedMessageVaultDeletionCallback implements DeleteMessageListener.DeletionCallback {
    private static final Logger LOGGER = LoggerFactory.getLogger(DeletedMessageVaultDeletionCallback.class);


    private final DeletedMessageVault deletedMessageVault;
    private final BlobStore blobStore;
    private final Clock clock;

    @Inject
    public DeletedMessageVaultDeletionCallback(DeletedMessageVault deletedMessageVault, BlobStore blobStore, Clock clock) {
        this.deletedMessageVault = deletedMessageVault;
        this.blobStore = blobStore;
        this.clock = clock;
    }

    @Override
    public Mono<Void> forMessage(MessageRepresentation message, MailboxId mailboxId, Username owner) {
        return Mono.from(blobStore.readBytes(blobStore.getDefaultBucketName(), message.getHeaderId(), BlobStore.StoragePolicy.LOW_COST))
            .flatMap(bytes -> {
                Optional<Message> mimeMessage = parseMessage(new ByteArrayInputStream(bytes), message.getMessageId());
                DeletedMessage deletedMessage = DeletedMessage.builder()
                    .messageId(message.getMessageId())
                    .originMailboxes(mailboxId)
                    .user(owner)
                    .deliveryDate(ZonedDateTime.ofInstant(message.getInternalDate().toInstant(), ZoneOffset.UTC))
                    .deletionDate(ZonedDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
                    .sender(retrieveSender(mimeMessage))
                    .recipients(retrieveRecipients(mimeMessage))
                    .hasAttachment(!message.getAttachments().isEmpty())
                    .size(message.getSize())
                    .subject(mimeMessage.map(Message::getSubject))
                    .build();

                return Mono.from(blobStore.readReactive(blobStore.getDefaultBucketName(), message.getBodyId(), BlobStore.StoragePolicy.LOW_COST))
                    .map(bodyStream -> new SequenceInputStream(new ByteArrayInputStream(bytes), bodyStream))
                    .flatMap(bodyStream -> Mono.from(deletedMessageVault.append(deletedMessage, bodyStream)));
            });
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
