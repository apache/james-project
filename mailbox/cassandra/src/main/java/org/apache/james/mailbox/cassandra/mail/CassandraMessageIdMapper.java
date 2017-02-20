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

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.mail.Flags;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.backends.cassandra.utils.FunctionRunnerWithRetry;
import org.apache.james.backends.cassandra.utils.LightweightTransactionException;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.cassandra.CassandraId;
import org.apache.james.mailbox.cassandra.CassandraMessageId;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageAttachment;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.FlagsUpdateCalculator;
import org.apache.james.mailbox.store.mail.AttachmentMapper;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.MessageIdMapper;
import org.apache.james.mailbox.store.mail.MessageMapper.FetchType;
import org.apache.james.mailbox.store.mail.ModSeqProvider;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.github.fge.lambdas.functions.FunctionChainer;
import com.github.steveash.guavate.Guavate;
import com.google.common.base.Throwables;

public class CassandraMessageIdMapper implements MessageIdMapper {

    private static final int MAX_RETRY = 1000;
    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraMessageIdMapper.class);

    private final MailboxMapper mailboxMapper;
    private final AttachmentMapper attachmentMapper;
    private final CassandraMessageIdToImapUidDAO imapUidDAO;
    private final CassandraMessageIdDAO messageIdDAO;
    private final CassandraMessageDAO messageDAO;
    private final CassandraIndexTableHandler indexTableHandler;
    private final ModSeqProvider modSeqProvider;
    private final MailboxSession mailboxSession;

    public CassandraMessageIdMapper(MailboxMapper mailboxMapper, AttachmentMapper attachmentMapper,
                                    CassandraMessageIdToImapUidDAO imapUidDAO, CassandraMessageIdDAO messageIdDAO, CassandraMessageDAO messageDAO,
                                    CassandraIndexTableHandler indexTableHandler, ModSeqProvider modSeqProvider, MailboxSession mailboxSession) {
        this.mailboxMapper = mailboxMapper;
        this.attachmentMapper = attachmentMapper;
        this.imapUidDAO = imapUidDAO;
        this.messageIdDAO = messageIdDAO;
        this.messageDAO = messageDAO;
        this.indexTableHandler = indexTableHandler;
        this.modSeqProvider = modSeqProvider;
        this.mailboxSession = mailboxSession;
    }

    @Override
    public List<MailboxMessage> find(List<MessageId> messageIds, FetchType fetchType) {
        return findAsStream(messageIds, fetchType)
            .collect(Guavate.toImmutableList());
    }

    private Stream<SimpleMailboxMessage> findAsStream(List<MessageId> messageIds, FetchType fetchType) {
        List<ComposedMessageIdWithMetaData> composedMessageIds = messageIds.stream()
            .map(messageId -> imapUidDAO.retrieve((CassandraMessageId) messageId, Optional.empty()))
            .flatMap(CompletableFuture::join)
            .collect(Guavate.toImmutableList());
        return messageDAO.retrieveMessages(composedMessageIds, fetchType, Optional.empty()).join()
            .filter(pair -> mailboxExists(pair.getLeft()))
            .map(loadAttachments(fetchType))
            .map(toMailboxMessages())
            .sorted(Comparator.comparing(MailboxMessage::getUid));
    }

    private boolean mailboxExists(CassandraMessageDAO.MessageWithoutAttachment messageWithoutAttachment) {
        try {
            mailboxMapper.findMailboxById(messageWithoutAttachment.getMailboxId());
            return true;
        } catch (MailboxException e) {
            LOGGER.info("Mailbox {} have been deleted but message {} is still attached to it.", messageWithoutAttachment.getMailboxId(), messageWithoutAttachment.getMessageId());
            return false;
        }
    }

    private Function<Pair<CassandraMessageDAO.MessageWithoutAttachment, Stream<CassandraMessageDAO.MessageAttachmentRepresentation>>, Pair<CassandraMessageDAO.MessageWithoutAttachment, Stream<MessageAttachment>>> loadAttachments(FetchType fetchType) {
        if (fetchType == FetchType.Full || fetchType == FetchType.Body) {
            return pair -> Pair.of(pair.getLeft(),
                new AttachmentLoader(attachmentMapper)
                    .getAttachments(pair.getRight().collect(Guavate.toImmutableList()))
                    .stream());
        } else {
            return pair -> Pair.of(pair.getLeft(), Stream.of());
        }
    }

    private FunctionChainer<Pair<CassandraMessageDAO.MessageWithoutAttachment, Stream<MessageAttachment>>, SimpleMailboxMessage> toMailboxMessages() {
        return Throwing.function(pair -> pair.getLeft()
            .toMailboxMessage(pair.getRight()
                .collect(Guavate.toImmutableList())));
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
        CassandraMessageId messageId = (CassandraMessageId) mailboxMessage.getMessageId();
        ComposedMessageIdWithMetaData composedMessageIdWithMetaData = ComposedMessageIdWithMetaData.builder()
            .composedMessageId(new ComposedMessageId(mailboxId, messageId, mailboxMessage.getUid()))
            .flags(mailboxMessage.createFlags())
            .modSeq(mailboxMessage.getModSeq())
            .build();
        messageDAO.save(mailboxMessage)
            .thenCompose(voidValue -> CompletableFuture.allOf(
                imapUidDAO.insert(composedMessageIdWithMetaData),
                messageIdDAO.insert(composedMessageIdWithMetaData)))
            .thenCompose(voidValue -> indexTableHandler.updateIndexOnAdd(mailboxMessage, mailboxId))
            .join();
    }

    @Override
    public void delete(MessageId messageId, List<MailboxId> mailboxIds) {
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
            .thenCompose(voidValue -> messageDAO.delete(cassandraMessageId))
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
            Pair<Flags, ComposedMessageIdWithMetaData> pair = new FunctionRunnerWithRetry(MAX_RETRY)
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
        ComposedMessageIdWithMetaData newComposedId = new ComposedMessageIdWithMetaData(
            oldComposedId.getComposedMessageId(),
            new FlagsUpdateCalculator(newState, updateMode).buildNewFlags(oldComposedId.getFlags()),
            modSeqProvider.nextModSeq(mailboxSession, cassandraId));

        return updateFlags(oldComposedId, newComposedId);
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
