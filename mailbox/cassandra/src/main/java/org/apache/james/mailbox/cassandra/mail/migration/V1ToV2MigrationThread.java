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

import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.mail.AttachmentLoader;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageDAOV2;
import org.apache.james.mailbox.cassandra.mail.MessageAttachmentRepresentation;
import org.apache.james.mailbox.cassandra.mail.MessageWithoutAttachment;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class V1ToV2MigrationThread implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(V1ToV2MigrationThread.class);

    private final BlockingQueue<Pair<MessageWithoutAttachment, List<MessageAttachmentRepresentation>>> messagesToBeMigrated;
    private final CassandraMessageDAO messageDAOV1;
    private final CassandraMessageDAOV2 messageDAOV2;
    private final AttachmentLoader attachmentLoader;

    public V1ToV2MigrationThread(BlockingQueue<Pair<MessageWithoutAttachment, List<MessageAttachmentRepresentation>>> messagesToBeMigrated,
                                 CassandraMessageDAO messageDAOV1, CassandraMessageDAOV2 messageDAOV2, AttachmentLoader attachmentLoader) {
        this.messagesToBeMigrated = messagesToBeMigrated;
        this.messageDAOV1 = messageDAOV1;
        this.messageDAOV2 = messageDAOV2;
        this.attachmentLoader = attachmentLoader;
    }

    @Override
    public void run() {
        while (true) {
            try {
                Pair<MessageWithoutAttachment, List<MessageAttachmentRepresentation>> message = messagesToBeMigrated.take();
                performV1ToV2Migration(Pair.of(message.getLeft(), message.getRight().stream())).join();
            } catch (Exception e) {
                LOGGER.error("Error occured in migration thread", e);
            }
        }
    }

    private CompletableFuture<Void> performV1ToV2Migration(Pair<MessageWithoutAttachment, Stream<MessageAttachmentRepresentation>> messageV1) {
        return attachmentLoader.addAttachmentToMessages(Stream.of(messageV1), MessageMapper.FetchType.Full)
            .thenApply(stream -> stream.findAny().get())
            .thenCompose(this::performV1ToV2Migration);
    }

    private CompletableFuture<Void> performV1ToV2Migration(SimpleMailboxMessage message) {
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
