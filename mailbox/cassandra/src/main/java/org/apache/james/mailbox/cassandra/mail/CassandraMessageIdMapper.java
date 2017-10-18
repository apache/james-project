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
package org.apache.james.mailbox.cassandra.mail;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import javax.mail.Flags;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.backends.cassandra.init.CassandraConfiguration;
import org.apache.james.backends.cassandra.utils.FunctionRunnerWithRetry;
import org.apache.james.backends.cassandra.utils.LightweightTransactionException;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.mail.utils.Limit;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.FlagsUpdateCalculator;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.MessageIdMapper;
import org.apache.james.mailbox.store.mail.MessageMapper.FetchType;
import org.apache.james.mailbox.store.mail.ModSeqProvider;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.apache.james.util.FluentFutureStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.steveash.guavate.Guavate;
import com.google.common.base.Throwables;

public class CassandraMessageIdMapper implements MessageIdMapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraMessageIdMapper.class);

    private final MailboxMapper mailboxMapper;
    private final CassandraMailboxDAO mailboxDAO;
    private final CassandraMessageIdToImapUidDAO imapUidDAO;
    private final CassandraMessageIdDAO messageIdDAO;
    private final CassandraMessageDAO messageDAO;
    private final CassandraIndexTableHandler indexTableHandler;
    private final ModSeqProvider modSeqProvider;
    private final MailboxSession mailboxSession;
    private final AttachmentLoader attachmentLoader;
    private final CassandraConfiguration cassandraConfiguration;

    public CassandraMessageIdMapper(MailboxMapper mailboxMapper, CassandraMailboxDAO mailboxDAO, CassandraAttachmentMapper attachmentMapper,
                                    CassandraMessageIdToImapUidDAO imapUidDAO, CassandraMessageIdDAO messageIdDAO,
                                    CassandraMessageDAO messageDAO, CassandraIndexTableHandler indexTableHandler,
                                    ModSeqProvider modSeqProvider, MailboxSession mailboxSession, CassandraConfiguration cassandraConfiguration) {

        this.mailboxMapper = mailboxMapper;
        this.mailboxDAO = mailboxDAO;
        this.imapUidDAO = imapUidDAO;
        this.messageIdDAO = messageIdDAO;
        this.messageDAO = messageDAO;
        this.indexTableHandler = indexTableHandler;
        this.modSeqProvider = modSeqProvider;
        this.mailboxSession = mailboxSession;
        this.attachmentLoader = new AttachmentLoader(attachmentMapper);
        this.cassandraConfiguration = cassandraConfiguration;
    }

    @Override
    public List<MailboxMessage> find(Collection<MessageId> messageIds, FetchType fetchType) {
        return findAsStream(messageIds, fetchType)
            .collect(Guavate.toImmutableList());
    }

    private Stream<SimpleMailboxMessage> findAsStream(Collection<MessageId> messageIds, FetchType fetchType) {
        return FluentFutureStream.ofNestedStreams(
            messageIds.stream()
                .map(messageId -> imapUidDAO.retrieve((CassandraMessageId) messageId, Optional.empty())))
            .collect(Guavate.toImmutableList())
            .thenCompose(composedMessageIds -> messageDAO.retrieveMessages(composedMessageIds, fetchType, Limit.unlimited()))
            .thenApply(stream -> stream
                .filter(CassandraMessageDAO.MessageResult::isFound)
                .map(CassandraMessageDAO.MessageResult::message))
            .thenCompose(stream -> attachmentLoader.addAttachmentToMessages(stream, fetchType))
            .thenCompose(this::filterMessagesWithExistingMailbox)
            .join()
            .sorted(Comparator.comparing(MailboxMessage::getUid));
    }

    private CompletableFuture<Stream<SimpleMailboxMessage>> filterMessagesWithExistingMailbox(Stream<SimpleMailboxMessage> stream) {
        return FluentFutureStream.ofOptionals(stream.map(this::keepMessageIfMailboxExists))
            .completableFuture();
    }

    private CompletableFuture<Optional<SimpleMailboxMessage>> keepMessageIfMailboxExists(SimpleMailboxMessage message) {
        CassandraId cassandraId = (CassandraId) message.getMailboxId();
        return mailboxDAO.retrieveMailbox(cassandraId)
            .thenApply(optional -> {
                if (!optional.isPresent()) {
                    LOGGER.info("Mailbox {} have been deleted but message {} is still attached to it.",
                        cassandraId,
                        message.getMailboxId());
                    return Optional.empty();
                }

                return Optional.of(message);
            });
    }

    @Override
    public List<MailboxId> findMailboxes(MessageId messageId) {
        return imapUidDAO.retrieve((CassandraMessageId) messageId, Optional.empty()).join()
            .map(ComposedMessageIdWithMetaData::getComposedMessageId)
            .map(ComposedMessageId::getMailboxId)
            .collect(Guavate.toImmutableList());
    }

    @Override
    public void save(MailboxMessage mailboxMessage) throws MailboxException {
        CassandraId mailboxId = (CassandraId) mailboxMessage.getMailboxId();
        mailboxMapper.findMailboxById(mailboxId);
        ComposedMessageIdWithMetaData composedMessageIdWithMetaData = createMetadataFor(mailboxMessage);
        messageDAO.save(mailboxMessage)
            .thenCompose(voidValue -> CompletableFuture.allOf(
                imapUidDAO.insert(composedMessageIdWithMetaData),
                messageIdDAO.insert(composedMessageIdWithMetaData)))
            .thenCompose(voidValue -> indexTableHandler.updateIndexOnAdd(mailboxMessage, mailboxId))
            .join();
    }

    @Override
    public void copyInMailbox(MailboxMessage mailboxMessage) throws MailboxException {
        CassandraId mailboxId = (CassandraId) mailboxMessage.getMailboxId();
        mailboxMapper.findMailboxById(mailboxId);
        ComposedMessageIdWithMetaData composedMessageIdWithMetaData = createMetadataFor(mailboxMessage);
        CompletableFuture.allOf(
                        imapUidDAO.insert(composedMessageIdWithMetaData),
                        messageIdDAO.insert(composedMessageIdWithMetaData))
                .thenCompose(voidValue -> indexTableHandler.updateIndexOnAdd(mailboxMessage, mailboxId))
                .join();
    }

    private ComposedMessageIdWithMetaData createMetadataFor(MailboxMessage mailboxMessage) {
        ComposedMessageId composedMessageId = new ComposedMessageId(
            mailboxMessage.getMailboxId(),
            mailboxMessage.getMessageId(),
            mailboxMessage.getUid());

        return ComposedMessageIdWithMetaData.builder()
            .composedMessageId(composedMessageId)
            .flags(mailboxMessage.createFlags())
            .modSeq(mailboxMessage.getModSeq())
            .build();
    }

    @Override
    public void delete(MessageId messageId, Collection<MailboxId> mailboxIds) {
        CassandraMessageId cassandraMessageId = (CassandraMessageId) messageId;
        mailboxIds.stream()
            .map(mailboxId -> retrieveAndDeleteIndices(cassandraMessageId, Optional.of((CassandraId) mailboxId)))
            .reduce((f1, f2) -> CompletableFuture.allOf(f1, f2))
            .orElse(CompletableFuture.completedFuture(null))
            .join();
    }


    private CompletableFuture<Void> retrieveAndDeleteIndices(CassandraMessageId messageId, Optional<CassandraId> mailboxId) {
        return imapUidDAO.retrieve(messageId, mailboxId)
            .thenCompose(composedMessageIds -> composedMessageIds
                .map(this::deleteIds)
                .reduce((f1, f2) -> CompletableFuture.allOf(f1, f2))
                .orElse(CompletableFuture.completedFuture(null)));
    }

    @Override
    public void delete(MessageId messageId) {
        CassandraMessageId cassandraMessageId = (CassandraMessageId) messageId;
        retrieveAndDeleteIndices(cassandraMessageId, Optional.empty())
            .join();
    }

    private CompletableFuture<Void> deleteIds(ComposedMessageIdWithMetaData metaData) {
        CassandraMessageId messageId = (CassandraMessageId) metaData.getComposedMessageId().getMessageId();
        CassandraId mailboxId = (CassandraId) metaData.getComposedMessageId().getMailboxId();
        return CompletableFuture.allOf(
            imapUidDAO.delete(messageId, mailboxId),
            messageIdDAO.delete(mailboxId, metaData.getComposedMessageId().getUid()))
            .thenCompose(voidValue -> indexTableHandler.updateIndexOnDelete(metaData, mailboxId));
    }

    @Override
    public Map<MailboxId, UpdatedFlags> setFlags(MessageId messageId, List<MailboxId> mailboxIds, Flags newState, MessageManager.FlagsUpdateMode updateMode) throws MailboxException {
        return mailboxIds.stream()
            .distinct()
            .map(mailboxId -> (CassandraId) mailboxId)
            .filter(mailboxId -> imapUidDAO.retrieve((CassandraMessageId) messageId, Optional.of(mailboxId))
                .join()
                .findAny()
                .isPresent())
            .flatMap(mailboxId -> flagsUpdateWithRetry(newState, updateMode, mailboxId, messageId))
            .map(this::updateCounts)
            .map(CompletableFuture::join)
            .collect(Guavate.toImmutableMap(Pair::getLeft, Pair::getRight));
    }

    private Stream<Pair<MailboxId, UpdatedFlags>> flagsUpdateWithRetry(Flags newState, MessageManager.FlagsUpdateMode updateMode, MailboxId mailboxId, MessageId messageId) {
        try {
            Pair<Flags, ComposedMessageIdWithMetaData> pair = new FunctionRunnerWithRetry(cassandraConfiguration.getFlagsUpdateMessageIdMaxRetry())
                .executeAndRetrieveObject(() -> tryFlagsUpdate(newState, updateMode, mailboxId, messageId));
            ComposedMessageIdWithMetaData composedMessageIdWithMetaData = pair.getRight();
            Flags oldFlags = pair.getLeft();
            return Stream.of(Pair.of(composedMessageIdWithMetaData.getComposedMessageId().getMailboxId(),
                    UpdatedFlags.builder()
                        .uid(composedMessageIdWithMetaData.getComposedMessageId().getUid())
                        .modSeq(composedMessageIdWithMetaData.getModSeq())
                        .oldFlags(oldFlags)
                        .newFlags(composedMessageIdWithMetaData.getFlags())
                        .build()));
        } catch (LightweightTransactionException e) {
            throw Throwables.propagate(e);
        } catch (MailboxDeleteDuringUpdateException e) {
            LOGGER.info("Mailbox {} was deleted during flag update", mailboxId);
            return Stream.of();
        }
    }

    private CompletableFuture<Pair<MailboxId, UpdatedFlags>> updateCounts(Pair<MailboxId, UpdatedFlags> pair) {
        CassandraId cassandraId = (CassandraId) pair.getLeft();
        return indexTableHandler.updateIndexOnFlagsUpdate(cassandraId, pair.getRight())
            .thenApply(voidValue -> pair);
    }

    private Optional<Pair<Flags, ComposedMessageIdWithMetaData>> tryFlagsUpdate(Flags newState, MessageManager.FlagsUpdateMode updateMode, MailboxId mailboxId, MessageId messageId) {
        try {
            return updateFlags(mailboxId, messageId, newState, updateMode);
        } catch (MailboxException e) {
            LOGGER.error("Error while updating flags on mailbox: ", mailboxId);
            return Optional.empty();
        }
    }

    private Optional<Pair<Flags, ComposedMessageIdWithMetaData>> updateFlags(MailboxId mailboxId, MessageId messageId, Flags newState, MessageManager.FlagsUpdateMode updateMode) throws MailboxException {
        CassandraId cassandraId = (CassandraId) mailboxId;
        ComposedMessageIdWithMetaData oldComposedId = imapUidDAO.retrieve((CassandraMessageId) messageId, Optional.of(cassandraId))
            .join()
            .findFirst()
            .orElseThrow(MailboxDeleteDuringUpdateException::new);
        Flags newFlags = new FlagsUpdateCalculator(newState, updateMode).buildNewFlags(oldComposedId.getFlags());
        if (identicalFlags(oldComposedId, newFlags)) {
            return Optional.of(Pair.of(oldComposedId.getFlags(), oldComposedId));
        }
        ComposedMessageIdWithMetaData newComposedId = new ComposedMessageIdWithMetaData(
            oldComposedId.getComposedMessageId(),
            newFlags,
            modSeqProvider.nextModSeq(mailboxSession, cassandraId));

        return updateFlags(oldComposedId, newComposedId);
    }

    private boolean identicalFlags(ComposedMessageIdWithMetaData oldComposedId, Flags newFlags) {
        return oldComposedId.getFlags().equals(newFlags);
    }

    private Optional<Pair<Flags, ComposedMessageIdWithMetaData>> updateFlags(ComposedMessageIdWithMetaData oldComposedId, ComposedMessageIdWithMetaData newComposedId) {
        return imapUidDAO.updateMetadata(newComposedId, oldComposedId.getModSeq())
            .thenCompose(updateSuccess -> Optional.of(updateSuccess)
                .filter(b -> b)
                .map((Boolean any) -> messageIdDAO.updateMetadata(newComposedId).thenApply(v -> updateSuccess))
                .orElse(CompletableFuture.completedFuture(updateSuccess)))
            .thenApply(success -> Optional.of(success)
                .filter(b -> b)
                .map(any -> Pair.of(oldComposedId.getFlags(), newComposedId)))
            .join();
    }
}
