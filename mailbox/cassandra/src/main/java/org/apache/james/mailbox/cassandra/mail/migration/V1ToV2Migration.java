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

package org.apache.james.mailbox.cassandra.mail.migration;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.backends.cassandra.CassandraConfiguration;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.mail.AttachmentLoader;
import org.apache.james.mailbox.cassandra.mail.CassandraAttachmentMapper;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageDAOV2;
import org.apache.james.mailbox.cassandra.mail.MessageAttachmentRepresentation;
import org.apache.james.mailbox.cassandra.mail.MessageWithoutAttachment;
import org.apache.james.mailbox.cassandra.mail.utils.Limit;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

public class V1ToV2Migration {
    private static final Logger LOGGER = LoggerFactory.getLogger(V1ToV2Migration.class);

    private final CassandraMessageDAO messageDAOV1;
    private final CassandraMessageDAOV2 messageDAOV2;
    private final AttachmentLoader attachmentLoader;
    private final CassandraConfiguration cassandraConfiguration;

    public V1ToV2Migration(CassandraMessageDAO messageDAOV1, CassandraMessageDAOV2 messageDAOV2,
                           CassandraAttachmentMapper attachmentMapper, CassandraConfiguration cassandraConfiguration) {
        this.messageDAOV1 = messageDAOV1;
        this.messageDAOV2 = messageDAOV2;
        this.attachmentLoader = new AttachmentLoader(attachmentMapper);
        this.cassandraConfiguration = cassandraConfiguration;
    }

    public CompletableFuture<Pair<MessageWithoutAttachment, Stream<MessageAttachmentRepresentation>>>
            moveFromV1toV2(CassandraMessageDAOV2.MessageResult result) {

        if (result.isFound()) {
            return CompletableFuture.completedFuture(result.message());
        }

        return messageDAOV1.retrieveMessages(ImmutableList.of(result.getMetadata()), MessageMapper.FetchType.Full, Limit.unlimited())
            .thenApply(results -> results.findAny()
                .orElseThrow(() -> new IllegalArgumentException("Message not found in DAO V1" + result.getMetadata())))
            .thenCompose(this::performV1ToV2Migration);
    }

    private CompletableFuture<Pair<MessageWithoutAttachment, Stream<MessageAttachmentRepresentation>>> performV1ToV2Migration(Pair<MessageWithoutAttachment, Stream<MessageAttachmentRepresentation>> messageV1) {
        return attachmentLoader.addAttachmentToMessages(Stream.of(messageV1), MessageMapper.FetchType.Full)
            .thenApply(stream -> stream.findAny().get())
            .thenCompose(this::performV1ToV2Migration)
            .thenApply(any -> messageV1);
    }

    private CompletableFuture<Void> performV1ToV2Migration(SimpleMailboxMessage message) {
        if (!cassandraConfiguration.isOnTheFlyV1ToV2Migration()) {
            return CompletableFuture.completedFuture(null);
        }
        return saveInV2FromV1(message)
            .thenCompose(this::deleteInV1);
    }

    private CompletableFuture<Void> deleteInV1(Optional<SimpleMailboxMessage> optional) {
        return optional.map(SimpleMailboxMessage::getMessageId)
            .map(messageId -> (CassandraMessageId) messageId)
            .map(messageDAOV1::delete)
            .orElse(CompletableFuture.completedFuture(null));
    }

    private CompletableFuture<Optional<SimpleMailboxMessage>> saveInV2FromV1(SimpleMailboxMessage message) {
        try {
            return messageDAOV2.save(message).thenApply(any -> Optional.of(message));
        } catch (MailboxException e) {
            LOGGER.error("Exception while saving message during migration", e);
            return CompletableFuture.completedFuture(Optional.<SimpleMailboxMessage>empty());
        }
    }
}
