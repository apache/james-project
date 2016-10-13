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

import static com.datastax.driver.core.querybuilder.QueryBuilder.decr;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.incr;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static com.datastax.driver.core.querybuilder.QueryBuilder.update;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.mail.Flags;
import javax.mail.Flags.Flag;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.backends.cassandra.utils.FunctionRunnerWithRetry;
import org.apache.james.mailbox.FlagsBuilder;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.cassandra.CassandraId;
import org.apache.james.mailbox.cassandra.CassandraMessageId;
import org.apache.james.mailbox.cassandra.mail.utils.MessageDeletedDuringFlagsUpdateException;
import org.apache.james.mailbox.cassandra.table.CassandraMailboxCountersTable;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Attachment;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MessageAttachment;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.FlagsUpdateCalculator;
import org.apache.james.mailbox.store.SimpleMessageMetaData;
import org.apache.james.mailbox.store.mail.AttachmentMapper;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.ModSeqProvider;
import org.apache.james.mailbox.store.mail.UidProvider;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;

import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.Assignment;
import com.github.fge.lambdas.Throwing;
import com.github.steveash.guavate.Guavate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;

public class CassandraMessageMapper implements MessageMapper {

    private final Session session;
    private final ModSeqProvider modSeqProvider;
    private final MailboxSession mailboxSession;
    private final UidProvider uidProvider;
    private final int maxRetries;
    private final AttachmentMapper attachmentMapper;
    private final CassandraMessageDAO messageDAO;
    private final CassandraMessageIdDAO messageIdDAO;
    private final CassandraMessageIdToImapUidDAO imapUidDAO;

    public CassandraMessageMapper(Session session, UidProvider uidProvider, ModSeqProvider modSeqProvider, 
            MailboxSession mailboxSession, int maxRetries, AttachmentMapper attachmentMapper,
            CassandraMessageDAO messageDAO, CassandraMessageIdDAO messageIdDAO, CassandraMessageIdToImapUidDAO imapUidDAO) {
        this.session = session;
        this.uidProvider = uidProvider;
        this.modSeqProvider = modSeqProvider;
        this.mailboxSession = mailboxSession;
        this.maxRetries = maxRetries;
        this.attachmentMapper = attachmentMapper;
        this.messageDAO = messageDAO;
        this.messageIdDAO = messageIdDAO;
        this.imapUidDAO = imapUidDAO;
    }

    @Override
    public long countMessagesInMailbox(Mailbox mailbox) throws MailboxException {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();
        ResultSet results = session.execute(
            select(CassandraMailboxCountersTable.COUNT)
                .from(CassandraMailboxCountersTable.TABLE_NAME)
                .where(eq(CassandraMailboxCountersTable.MAILBOX_ID, mailboxId.asUuid())));
        if (!results.isExhausted()) {
            return results.one().getLong(CassandraMailboxCountersTable.COUNT);
        }
        return 0;
    }

    @Override
    public long countUnseenMessagesInMailbox(Mailbox mailbox) throws MailboxException {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();
        ResultSet results = session.execute(
            select(CassandraMailboxCountersTable.UNSEEN)
                .from(CassandraMailboxCountersTable.TABLE_NAME)
                .where(eq(CassandraMailboxCountersTable.MAILBOX_ID, mailboxId.asUuid())));
        if (!results.isExhausted()) {
            Row row = results.one();
            if (row.getColumnDefinitions().contains(CassandraMailboxCountersTable.UNSEEN)) {
                return row.getLong(CassandraMailboxCountersTable.UNSEEN);
            }
        }
        return 0;
    }

    @Override
    public void delete(Mailbox mailbox, MailboxMessage message) {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();
        retrieveMessageId(mailboxId, message)
                .ifPresent(messageId -> deleteUsingMailboxId(messageId, mailboxId, message));
    }

    private void deleteUsingMailboxId(CassandraMessageId messageId, CassandraId mailboxId, MailboxMessage message) {
        CompletableFuture.allOf(imapUidDAO.delete(messageId, mailboxId),
                messageIdDAO.delete(mailboxId, message.getUid()))
            .join();
        
        decrementCount(mailboxId);
        if (!message.isSeen()) {
            decrementUnseen(mailboxId);
        }
    }

    private Optional<CassandraMessageId> retrieveMessageId(CassandraId mailboxId, MailboxMessage message) {
        return messageIdDAO.retrieve(mailboxId, message.getUid())
                .join();
    }

    @Override
    public Iterator<MailboxMessage> findInMailbox(Mailbox mailbox, MessageRange messageRange, FetchType ftype, int max) throws MailboxException {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();
        return retrieveMessages(retrieveMessageIds(mailboxId, messageRange), ftype, Optional.of(max))
                .map(SimpleMailboxMessage -> (MailboxMessage) SimpleMailboxMessage)
                .sorted(Comparator.comparing(MailboxMessage::getUid))
                .iterator();
    }

    private List<CassandraMessageId> retrieveMessageIds(CassandraId mailboxId, MessageRange messageRange) {
        return messageIdDAO.retrieveMessageIds(mailboxId, messageRange)
                .join()
                .collect(Guavate.toImmutableList());
    }

    private Stream<SimpleMailboxMessage> retrieveMessages(List<CassandraMessageId> messageIds, FetchType fetchType, Optional<Integer> limit) {
        return messageDAO.retrieveMessages(messageIds, fetchType, limit).join()
                .map(pair -> Pair.of(pair.getLeft(), getAttachments(pair.getRight().collect(Guavate.toImmutableList()))))
                .map(Throwing.function(pair -> {
                    return SimpleMailboxMessage.cloneWithAttachments(pair.getLeft(), 
                            pair.getRight().collect(Guavate.toImmutableList()));
                }));
    }

    private Stream<MessageAttachment> getAttachments(List<MessageAttachmentById> attachmentsById) {
        Map<AttachmentId, Attachment> attachmentByIdMap = attachmentsById(attachmentsById.stream()
                .map(MessageAttachmentById::getAttachmentId)
                .collect(Guavate.toImmutableList()));
        return attachmentsById.stream()
                .map(Throwing.function(attachment ->
                    MessageAttachment.builder()
                        .attachment(attachmentByIdMap.get(attachment.getAttachmentId()))
                        .name(attachment.getName().orElse(null))
                        .cid(com.google.common.base.Optional.fromNullable(attachment.getCid().orElse(null)))
                        .isInline(attachment.isInline())
                        .build())
                );
    }

    @VisibleForTesting Map<AttachmentId,Attachment> attachmentsById(List<AttachmentId> attachmentIds) {
        return attachmentMapper.getAttachments(attachmentIds).stream()
            .collect(toMapRemovingDuplicateKeys(Attachment::getAttachmentId, Function.identity()));
    }

    private Collector<Attachment, Map<AttachmentId, Attachment>, Map<AttachmentId, Attachment>> toMapRemovingDuplicateKeys(
            Function<Attachment, AttachmentId> keyMapper,
            Function<Attachment, Attachment> valueMapper) {
        return Collector.of(HashMap::new,
                (acc, v) -> acc.put(keyMapper.apply(v), valueMapper.apply(v)),
                (map1, map2) -> {
                    map1.putAll(map2);
                    return map1;
                },
                Function.identity()
                );
    }

    @Override
    public List<MessageUid> findRecentMessageUidsInMailbox(Mailbox mailbox) throws MailboxException {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();
        return retrieveMessages(retrieveMessageIds(mailboxId, MessageRange.all()), FetchType.Metadata, Optional.empty())
                .filter(MailboxMessage::isRecent)
                .flatMap(message -> imapUidDAO.retrieve((CassandraMessageId) message.getMessageId(), Optional.ofNullable(mailboxId)).join())
                .map(ComposedMessageId::getUid)
                .sorted()
                .collect(Collectors.toList());
    }

    @Override
    public MessageUid findFirstUnseenMessageUid(Mailbox mailbox) throws MailboxException {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();
        return retrieveMessages(retrieveMessageIds(mailboxId, MessageRange.all()), FetchType.Metadata, Optional.empty())
                .filter(message -> !message.isSeen())
                .flatMap(message -> imapUidDAO.retrieve((CassandraMessageId) message.getMessageId(), Optional.ofNullable(mailboxId)).join())
                .map(ComposedMessageId::getUid)
                .sorted()
                .findFirst()
                .orElse(null);
    }

    @Override
    public Map<MessageUid, MessageMetaData> expungeMarkedForDeletionInMailbox(Mailbox mailbox, MessageRange set) throws MailboxException {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();
        return retrieveMessages(retrieveMessageIds(mailboxId, set), FetchType.Metadata, Optional.empty())
                .filter(MailboxMessage::isDeleted)
                .peek(message -> delete(mailbox, message))
                .collect(Collectors.toMap(MailboxMessage::getUid, SimpleMessageMetaData::new));
    }

    @Override
    public MessageMetaData move(Mailbox destinationMailbox, MailboxMessage original) throws MailboxException {
        CassandraId originalMailboxId = (CassandraId) original.getMailboxId();
        MessageMetaData messageMetaData = copy(destinationMailbox, original);
        CassandraId mailboxId = (CassandraId) destinationMailbox.getMailboxId();
        retrieveMessageId(mailboxId, original)
                .ifPresent(messageId -> deleteUsingMailboxId(messageId, originalMailboxId, original));
        return messageMetaData;
    }

    @Override
    public void endRequest() {
        // Do nothing
    }

    @Override
    public long getHighestModSeq(Mailbox mailbox) throws MailboxException {
        return modSeqProvider.highestModSeq(mailboxSession, mailbox);
    }

    @Override
    public MessageMetaData add(Mailbox mailbox, MailboxMessage message) throws MailboxException {
        message.setUid(uidProvider.nextUid(mailboxSession, mailbox));
        message.setModSeq(modSeqProvider.nextModSeq(mailboxSession, mailbox));
        MessageMetaData messageMetaData = save(mailbox, message);
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();
        if (!message.isSeen()) {
            incrementUnseen(mailboxId);
        }
        incrementCount(mailboxId);
        return messageMetaData;
    }

    @Override
    public Iterator<UpdatedFlags> updateFlags(Mailbox mailbox, FlagsUpdateCalculator flagUpdateCalculator, MessageRange set) throws MailboxException {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();
        return retrieveMessages(retrieveMessageIds(mailboxId, set), FetchType.Metadata, Optional.empty())
                .map(message -> updateFlagsOnMessage(mailbox, flagUpdateCalculator, message))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .peek((updatedFlags) -> manageUnseenMessageCounts(mailbox, updatedFlags.getOldFlags(), updatedFlags.getNewFlags()))
                .collect(Collectors.toList()) // This collect is here as we need to consume all the stream before returning result
                .iterator();
    }

    @Override
    public <T> T execute(Transaction<T> transaction) throws MailboxException {
        return transaction.run();
    }

    @Override
    public MessageMetaData copy(Mailbox mailbox, MailboxMessage original) throws MailboxException {
        original.setFlags(new FlagsBuilder().add(original.createFlags()).add(Flag.RECENT).build());
        return add(mailbox, original);
    }

    @Override
    public com.google.common.base.Optional<MessageUid> getLastUid(Mailbox mailbox) throws MailboxException {
        return uidProvider.lastUid(mailboxSession, mailbox);
    }

    private void decrementCount(CassandraId mailboxId) {
        updateMailbox(mailboxId, decr(CassandraMailboxCountersTable.COUNT));
    }

    private void incrementCount(CassandraId mailboxId) {
        updateMailbox(mailboxId, incr(CassandraMailboxCountersTable.COUNT));
    }

    private void decrementUnseen(CassandraId mailboxId) {
        updateMailbox(mailboxId, decr(CassandraMailboxCountersTable.UNSEEN));
    }

    private void incrementUnseen(CassandraId mailboxId) {
        updateMailbox(mailboxId, incr(CassandraMailboxCountersTable.UNSEEN));
    }

    private void updateMailbox(CassandraId mailboxId, Assignment operation) {
        session.execute(update(CassandraMailboxCountersTable.TABLE_NAME).with(operation).where(eq(CassandraMailboxCountersTable.MAILBOX_ID, mailboxId.asUuid())));
    }

    private MessageMetaData save(Mailbox mailbox, MailboxMessage message) throws MailboxException {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();
        insertIds(message, mailboxId).join();
        messageDAO.save(mailbox, message).join();
        return new SimpleMessageMetaData(message);
    }

    private CompletableFuture<Void> insertIds(MailboxMessage message, CassandraId mailboxId) {
        CassandraMessageId messageId = (CassandraMessageId) message.getMessageId();
        return CompletableFuture.allOf(messageIdDAO.insert(mailboxId, message.getUid(), messageId),
                imapUidDAO.insert(messageId, mailboxId, message.getUid()));
    }

    private void manageUnseenMessageCounts(Mailbox mailbox, Flags oldFlags, Flags newFlags) {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();
        if (oldFlags.contains(Flag.SEEN) && !newFlags.contains(Flag.SEEN)) {
            incrementUnseen(mailboxId);
        }
        if (!oldFlags.contains(Flag.SEEN) && newFlags.contains(Flag.SEEN)) {
            decrementUnseen(mailboxId);
        }
    }

    private Optional<UpdatedFlags> updateFlagsOnMessage(Mailbox mailbox, FlagsUpdateCalculator flagUpdateCalculator, MailboxMessage message) {
        return tryMessageFlagsUpdate(flagUpdateCalculator, mailbox, message)
            .map(Optional::of)
            .orElse(handleRetries(mailbox, flagUpdateCalculator, (CassandraMessageId) message.getMessageId()));
    }

    private Optional<UpdatedFlags> tryMessageFlagsUpdate(FlagsUpdateCalculator flagUpdateCalculator, Mailbox mailbox, MailboxMessage message) {
        try {
            long oldModSeq = message.getModSeq();
            Flags oldFlags = message.createFlags();
            Flags newFlags = flagUpdateCalculator.buildNewFlags(oldFlags);
            message.setFlags(newFlags);
            message.setModSeq(modSeqProvider.nextModSeq(mailboxSession, mailbox));
            if (messageDAO.conditionalSave(message, oldModSeq).join()) {
                return Optional.of(new UpdatedFlags(message.getUid(), message.getModSeq(), oldFlags, newFlags));
            } else {
                return Optional.empty();
            }
        } catch (MailboxException e) {
            throw Throwables.propagate(e);
        }
    }

    private Optional<UpdatedFlags> handleRetries(Mailbox mailbox, FlagsUpdateCalculator flagUpdateCalculator, CassandraMessageId messageId) {
        try {
            return Optional.of(
                new FunctionRunnerWithRetry(maxRetries)
                    .executeAndRetrieveObject(() -> retryMessageFlagsUpdate(mailbox, messageId, flagUpdateCalculator)));
        } catch (MessageDeletedDuringFlagsUpdateException e) {
            mailboxSession.getLog().warn(e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    private Optional<UpdatedFlags> retryMessageFlagsUpdate(Mailbox mailbox, CassandraMessageId messageId, FlagsUpdateCalculator flagUpdateCalculator) {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();
        return tryMessageFlagsUpdate(flagUpdateCalculator,
            mailbox,
            retrieveMessages(ImmutableList.of(messageId), FetchType.Metadata, Optional.empty())
                .findFirst()
                .orElseThrow(() -> new MessageDeletedDuringFlagsUpdateException(mailboxId, messageId)));
    }
}
