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
    private final CassandraMailboxCounterDAO mailboxCounterDAO;
    private final ModSeqProvider modSeqProvider;
    private final MailboxSession mailboxSession;

    public CassandraMessageIdMapper(MailboxMapper mailboxMapper, AttachmentMapper attachmentMapper,
                                    CassandraMessageIdToImapUidDAO imapUidDAO, CassandraMessageIdDAO messageIdDAO, CassandraMessageDAO messageDAO,
                                    CassandraMailboxCounterDAO cassandraMailboxCounterDAO, ModSeqProvider modSeqProvider, MailboxSession mailboxSession) {
        this.mailboxMapper = mailboxMapper;
        this.attachmentMapper = attachmentMapper;
        this.imapUidDAO = imapUidDAO;
        this.messageIdDAO = messageIdDAO;
        this.messageDAO = messageDAO;
        this.mailboxCounterDAO = cassandraMailboxCounterDAO;
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
            .map(loadAttachments())
            .map(toMailboxMessages())
            .sorted(Comparator.comparing(MailboxMessage::getUid));
    }

    private Function<Pair<MailboxMessage, Stream<CassandraMessageDAO.MessageAttachmentRepresentation>>, Pair<MailboxMessage, Stream<MessageAttachment>>> loadAttachments() {
        return pair -> Pair.of(pair.getLeft(),
            new AttachmentLoader(attachmentMapper).getAttachments(pair.getRight().collect(Guavate.toImmutableSet())).stream());
    }

    private FunctionChainer<Pair<MailboxMessage, Stream<MessageAttachment>>, SimpleMailboxMessage> toMailboxMessages() {
        return Throwing.function(pair -> SimpleMailboxMessage.cloneWithAttachments(pair.getLeft(),
                pair.getRight().collect(Guavate.toImmutableList())));
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
        messageDAO.save(mailboxMapper.findMailboxById(mailboxId), mailboxMessage).join();
        CassandraMessageId messageId = (CassandraMessageId) mailboxMessage.getMessageId();
        ComposedMessageIdWithMetaData composedMessageIdWithMetaData = ComposedMessageIdWithMetaData.builder()
            .composedMessageId(new ComposedMessageId(mailboxId, messageId, mailboxMessage.getUid()))
            .flags(mailboxMessage.createFlags())
            .modSeq(mailboxMessage.getModSeq())
            .build();
        CompletableFuture.allOf(imapUidDAO.insert(composedMessageIdWithMetaData),
            messageIdDAO.insert(composedMessageIdWithMetaData),
            mailboxCounterDAO.incrementCount(mailboxId),
            incrementUnseenOnSave(mailboxId, mailboxMessage.createFlags()))
        .join();
    }

    private CompletableFuture<Void> incrementUnseenOnSave(CassandraId mailboxId, Flags flags) {
        if (flags.contains(Flags.Flag.SEEN)) {
            return CompletableFuture.completedFuture(null);
        }
        return mailboxCounterDAO.incrementUnseen(mailboxId);
    }

    @Override
    public void delete(MessageId messageId, List<MailboxId> mailboxIds) {
        CassandraMessageId cassandraMessageId = (CassandraMessageId) messageId;
        mailboxIds.forEach(mailboxId -> retrieveAndDeleteIndices(cassandraMessageId, Optional.of((CassandraId) mailboxId)).join());
    }


    private CompletableFuture<Void> retrieveAndDeleteIndices(CassandraMessageId messageId, Optional<CassandraId> mailboxId) {
        return imapUidDAO.retrieve(messageId, mailboxId)
            .thenAccept(composedMessageIds -> composedMessageIds
                .map(this::deleteIds)
                .reduce((f1, f2) -> CompletableFuture.allOf(f1, f2)));
    }

    @Override
    public void delete(MessageId messageId) {
        CassandraMessageId cassandraMessageId = (CassandraMessageId) messageId;
        messageDAO.delete(cassandraMessageId).join();
        retrieveAndDeleteIndices(cassandraMessageId, Optional.empty()).join();
    }

    private CompletableFuture<Void> deleteIds(ComposedMessageIdWithMetaData metaData) {
        CassandraMessageId messageId = (CassandraMessageId) metaData.getComposedMessageId().getMessageId();
        CassandraId mailboxId = (CassandraId) metaData.getComposedMessageId().getMailboxId();
        return CompletableFuture.allOf(imapUidDAO.delete(messageId, mailboxId),
            messageIdDAO.delete(mailboxId, metaData.getComposedMessageId().getUid()),
            mailboxCounterDAO.decrementCount(mailboxId),
            decrementUnseenOnDelete(mailboxId, metaData.getFlags()));
    }

    private CompletableFuture<Void> decrementUnseenOnDelete(CassandraId mailboxId, Flags flags) {
        if (flags.contains(Flags.Flag.SEEN)) {
            return CompletableFuture.completedFuture(null);
        }
        return mailboxCounterDAO.decrementUnseen(mailboxId);
    }

    @Override
    public Map<MailboxId, UpdatedFlags> setFlags(MessageId messageId, List<MailboxId> mailboxIds, Flags newState, MessageManager.FlagsUpdateMode updateMode) throws MailboxException {
        CassandraMessageId cassandraMessageId = (CassandraMessageId) messageId;

        return mailboxIds.stream()
            .map(mailboxId -> (CassandraId) mailboxId)
            .flatMap(mailboxId -> imapUidDAO.retrieve(cassandraMessageId, Optional.of(mailboxId)).join())
            .map(composedMessageId -> flagsUpdateWithRetry(newState, updateMode, composedMessageId))
            .map(this::updateCounts)
            .map(CompletableFuture::join)
            .collect(Guavate.toImmutableMap(Pair::getLeft, Pair::getRight));
    }

    private Pair<MailboxId, UpdatedFlags> flagsUpdateWithRetry(Flags newState, MessageManager.FlagsUpdateMode updateMode, ComposedMessageIdWithMetaData composedMessageId) {
        try {
            Pair<Flags, Long> newFlagsWithModSeq = new FunctionRunnerWithRetry(MAX_RETRY)
                .executeAndRetrieveObject(() -> tryFlagsUpdate(newState, updateMode, composedMessageId, oldModSeq(composedMessageId.getComposedMessageId())));
            return Pair.of(composedMessageId.getComposedMessageId().getMailboxId(),
                    new UpdatedFlags(composedMessageId.getComposedMessageId().getUid(),
                        newFlagsWithModSeq.getRight(),
                        composedMessageId.getFlags(),
                        newFlagsWithModSeq.getLeft()));
        } catch (LightweightTransactionException e) {
            throw Throwables.propagate(e);
        }
    }

    private CompletableFuture<Pair<MailboxId, UpdatedFlags>> updateCounts(Pair<MailboxId, UpdatedFlags> pair) {
        CassandraId cassandraId = (CassandraId) pair.getLeft();
        return CompletableFuture.allOf(
            incrementCountIfNeeded(pair.getRight().getOldFlags(), pair.getRight().getNewFlags(), cassandraId),
            decrementCountIfNeeded(pair.getRight().getOldFlags(), pair.getRight().getNewFlags(), cassandraId))
            .thenApply(voidValue -> pair);
    }

    private CompletableFuture<Void> incrementCountIfNeeded(Flags oldFlags, Flags newFlags, CassandraId cassandraId) {
        if (oldFlags.contains(Flags.Flag.SEEN) && !newFlags.contains(Flags.Flag.SEEN)) {
            mailboxCounterDAO.incrementUnseen(cassandraId).join();
        }
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Void> decrementCountIfNeeded(Flags oldFlags, Flags newFlags, CassandraId cassandraId) {
        if (!oldFlags.contains(Flags.Flag.SEEN) && newFlags.contains(Flags.Flag.SEEN)) {
            mailboxCounterDAO.decrementUnseen(cassandraId).join();
        }
        return CompletableFuture.completedFuture(null);
    }

    private long oldModSeq(ComposedMessageId composedMessageId) {
        try {
            return modSeqProvider.highestModSeq(mailboxSession, composedMessageId.getMailboxId());
        } catch (MailboxException e) {
            throw Throwables.propagate(e);
        }
    }

    private Optional<Pair<Flags, Long>> tryFlagsUpdate(Flags newState, MessageManager.FlagsUpdateMode updateMode, ComposedMessageIdWithMetaData composedMessageId, long oldModSeq) {
        MailboxId mailboxId = composedMessageId.getComposedMessageId().getMailboxId();
        try {
            long newModSeq = modSeqProvider.nextModSeq(mailboxSession, mailboxId);
            Flags newFlags = new FlagsUpdateCalculator(composedMessageId.getFlags(), updateMode).buildNewFlags(newState);
            ComposedMessageIdWithMetaData composedMessageIdWithMetaData = new ComposedMessageIdWithMetaData(
                        composedMessageId.getComposedMessageId(),
                        newFlags,
                        newModSeq);
            if (updateFlags(composedMessageIdWithMetaData, oldModSeq)) {
                return Optional.of(Pair.of(newFlags, newModSeq));
            }
            return Optional.empty();
        } catch (MailboxException e) {
            LOGGER.error("Error while getting next ModSeq on mailbox: ", mailboxId);
            return Optional.empty();
        }
    }

    private boolean updateFlags(ComposedMessageIdWithMetaData composedMessageIdWithMetaData, long oldModSeq) {
        return imapUidDAO.updateMetadata(composedMessageIdWithMetaData, oldModSeq).join()
                && messageIdDAO.updateMetadata(composedMessageIdWithMetaData, oldModSeq).join();
    }
}
