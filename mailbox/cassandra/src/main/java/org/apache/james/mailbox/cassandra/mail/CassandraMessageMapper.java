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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
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
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.MailboxCounters;
import org.apache.james.mailbox.model.MessageId;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.github.steveash.guavate.Guavate;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;

public class CassandraMessageMapper implements MessageMapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraMessageMapper.class);
    public static final MailboxCounters INITIAL_COUNTERS =  MailboxCounters.builder()
        .count(0L)
        .unseen(0L)
        .build();

    private final ModSeqProvider modSeqProvider;
    private final MailboxSession mailboxSession;
    private final UidProvider uidProvider;
    private final int maxRetries;
    private final AttachmentMapper attachmentMapper;
    private final CassandraMessageDAO messageDAO;
    private final CassandraMessageIdDAO messageIdDAO;
    private final CassandraMessageIdToImapUidDAO imapUidDAO;
    private final CassandraMailboxCounterDAO mailboxCounterDAO;
    private final CassandraMailboxRecentsDAO mailboxRecentDAO;
    private final CassandraIndexTableHandler indexTableHandler;

    public CassandraMessageMapper(UidProvider uidProvider, ModSeqProvider modSeqProvider,
                                  MailboxSession mailboxSession, int maxRetries, AttachmentMapper attachmentMapper,
                                  CassandraMessageDAO messageDAO, CassandraMessageIdDAO messageIdDAO, CassandraMessageIdToImapUidDAO imapUidDAO,
                                  CassandraMailboxCounterDAO mailboxCounterDAO, CassandraMailboxRecentsDAO mailboxRecentDAO, CassandraIndexTableHandler indexTableHandler) {
        this.uidProvider = uidProvider;
        this.modSeqProvider = modSeqProvider;
        this.mailboxSession = mailboxSession;
        this.maxRetries = maxRetries;
        this.attachmentMapper = attachmentMapper;
        this.messageDAO = messageDAO;
        this.messageIdDAO = messageIdDAO;
        this.imapUidDAO = imapUidDAO;
        this.mailboxCounterDAO = mailboxCounterDAO;
        this.mailboxRecentDAO = mailboxRecentDAO;
        this.indexTableHandler = indexTableHandler;
    }

    @Override
    public long countMessagesInMailbox(Mailbox mailbox) throws MailboxException {
        return mailboxCounterDAO.countMessagesInMailbox(mailbox)
            .join()
            .orElse(0L);
    }

    @Override
    public long countUnseenMessagesInMailbox(Mailbox mailbox) throws MailboxException {
        return mailboxCounterDAO.countUnseenMessagesInMailbox(mailbox)
            .join()
            .orElse(0L);
    }

    @Override
    public MailboxCounters getMailboxCounters(Mailbox mailbox) throws MailboxException {
        return mailboxCounterDAO.retrieveMailboxCounters(mailbox)
            .join()
            .orElse(INITIAL_COUNTERS);
    }

    @Override
    public void delete(Mailbox mailbox, MailboxMessage message) {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();

        messageIdDAO.retrieve(mailboxId, message.getUid())
            .thenCompose(optional -> optional.map(this::deleteUsingMailboxId)
                .orElse(CompletableFuture.completedFuture(null)))
            .join();
    }

    private CompletableFuture<Void> deleteUsingMailboxId(ComposedMessageIdWithMetaData composedMessageIdWithMetaData) {
        ComposedMessageId composedMessageId = composedMessageIdWithMetaData.getComposedMessageId();
        CassandraMessageId messageId = (CassandraMessageId) composedMessageId.getMessageId();
        CassandraId mailboxId = (CassandraId) composedMessageId.getMailboxId();
        MessageUid uid = composedMessageId.getUid();
        return CompletableFuture.allOf(
            imapUidDAO.delete(messageId, mailboxId),
            messageIdDAO.delete(mailboxId, uid)
        ).thenCompose(voidValue -> indexTableHandler.updateIndexOnDelete(composedMessageIdWithMetaData, mailboxId));
    }

    private CompletableFuture<Optional<ComposedMessageIdWithMetaData>> retrieveMessageId(CassandraId mailboxId, MailboxMessage message) {
        return messageIdDAO.retrieve(mailboxId, message.getUid());
    }

    @Override
    public Iterator<MailboxMessage> findInMailbox(Mailbox mailbox, MessageRange messageRange, FetchType ftype, int max) throws MailboxException {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();
        return retrieveMessages(retrieveMessageIds(mailboxId, messageRange), ftype, Optional.of(max))
                .map(SimpleMailboxMessage -> (MailboxMessage) SimpleMailboxMessage)
                .sorted(Comparator.comparing(MailboxMessage::getUid))
                .iterator();
    }

    private List<ComposedMessageIdWithMetaData> retrieveMessageIds(CassandraId mailboxId, MessageRange messageRange) {
        return messageIdDAO.retrieveMessages(mailboxId, messageRange)
                .join()
                .collect(Guavate.toImmutableList());
    }

    private Stream<SimpleMailboxMessage> retrieveMessages(List<ComposedMessageIdWithMetaData> messageIds, FetchType fetchType, Optional<Integer> limit) {
        Stream<Pair<CassandraMessageDAO.MessageWithoutAttachment, Stream<CassandraMessageDAO.MessageAttachmentRepresentation>>>
            messageRepresentions = messageDAO.retrieveMessages(messageIds, fetchType, limit).join();
        if (fetchType == FetchType.Body || fetchType == FetchType.Full) {
            return messageRepresentions
                .map(pair -> Pair.of(pair.getLeft(), new AttachmentLoader(attachmentMapper)
                    .getAttachments(pair.getRight()
                        .collect(Guavate.toImmutableList()))))
                .map(Throwing.function(pair -> pair.getLeft()
                    .toMailboxMessage(pair.getRight()
                        .stream()
                        .collect(Guavate.toImmutableList()))));
        } else {
            return messageRepresentions.map(pair -> pair.getLeft().toMailboxMessage(ImmutableList.of()));
        }
    }

    @Override
    public List<MessageUid> findRecentMessageUidsInMailbox(Mailbox mailbox) throws MailboxException {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();
        return mailboxRecentDAO.getRecentMessageUidsInMailbox(mailboxId)
                .join();
    }

    @Override
    public MessageUid findFirstUnseenMessageUid(Mailbox mailbox) throws MailboxException {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();
        return retrieveMessages(retrieveMessageIds(mailboxId, MessageRange.all()), FetchType.Metadata, Optional.empty())
                .filter(message -> !message.isSeen())
                .flatMap(message -> imapUidDAO.retrieve((CassandraMessageId) message.getMessageId(), Optional.ofNullable(mailboxId)).join())
                .map(ComposedMessageIdWithMetaData::getComposedMessageId)
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
        retrieveMessageId(originalMailboxId, original)
            .thenCompose(optional -> optional.map(this::deleteUsingMailboxId).orElse(CompletableFuture.completedFuture(null)))
            .join();
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
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();
        save(mailbox, message)
            .thenCompose(voidValue -> indexTableHandler.updateIndexOnAdd(message, mailboxId))
            .join();
        return new SimpleMessageMetaData(message);
    }



    @Override
    public Iterator<UpdatedFlags> updateFlags(Mailbox mailbox, FlagsUpdateCalculator flagUpdateCalculator, MessageRange set) throws MailboxException {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();
        return retrieveMessages(retrieveMessageIds(mailboxId, set), FetchType.Metadata, Optional.empty())
                .flatMap(message -> updateFlagsOnMessage(mailbox, flagUpdateCalculator, message))
                .map((UpdatedFlags updatedFlags) -> indexTableHandler.updateIndexOnFlagsUpdate(mailboxId, updatedFlags)
                    .thenApply(voidValue -> updatedFlags))
                .map(CompletableFuture::join)
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

    private CompletableFuture<Void> save(Mailbox mailbox, MailboxMessage message) throws MailboxException {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();
        return messageDAO.save(message)
            .thenCompose(aVoid -> insertIds(message, mailboxId));
    }

    private CompletableFuture<Void> insertIds(MailboxMessage message, CassandraId mailboxId) {
        ComposedMessageIdWithMetaData composedMessageIdWithMetaData = ComposedMessageIdWithMetaData.builder()
                .composedMessageId(new ComposedMessageId(mailboxId, message.getMessageId(), message.getUid()))
                .flags(message.createFlags())
                .modSeq(message.getModSeq())
                .build();
        return CompletableFuture.allOf(messageIdDAO.insert(composedMessageIdWithMetaData),
                imapUidDAO.insert(composedMessageIdWithMetaData));
    }

    private Stream<UpdatedFlags> updateFlagsOnMessage(Mailbox mailbox, FlagsUpdateCalculator flagUpdateCalculator, MailboxMessage message) {
        return tryMessageFlagsUpdate(flagUpdateCalculator, mailbox, message)
            .map(Stream::of)
            .orElse(handleRetries(mailbox, flagUpdateCalculator, message));
    }

    private Optional<UpdatedFlags> tryMessageFlagsUpdate(FlagsUpdateCalculator flagUpdateCalculator, Mailbox mailbox, MailboxMessage message) {
        try {
            long oldModSeq = message.getModSeq();
            Flags oldFlags = message.createFlags();
            Flags newFlags = flagUpdateCalculator.buildNewFlags(oldFlags);
            message.setFlags(newFlags);
            message.setModSeq(modSeqProvider.nextModSeq(mailboxSession, mailbox));
            if (updateFlags(message, oldModSeq)) {
                return Optional.of(UpdatedFlags.builder()
                    .uid(message.getUid())
                    .modSeq(message.getModSeq())
                    .oldFlags(oldFlags)
                    .newFlags(newFlags)
                    .build());
            } else {
                return Optional.empty();
            }
        } catch (MailboxException e) {
            throw Throwables.propagate(e);
        }
    }

    private boolean updateFlags(MailboxMessage message, long oldModSeq) {
        ComposedMessageIdWithMetaData composedMessageIdWithMetaData = ComposedMessageIdWithMetaData.builder()
                .composedMessageId(new ComposedMessageId(message.getMailboxId(), message.getMessageId(), message.getUid()))
                .modSeq(message.getModSeq())
                .flags(message.createFlags())
                .build();
        return imapUidDAO.updateMetadata(composedMessageIdWithMetaData, oldModSeq)
            .thenCompose(success -> Optional.of(success)
                .filter(b -> b)
                .map((Boolean any) -> messageIdDAO.updateMetadata(composedMessageIdWithMetaData)
                    .thenApply(v -> success))
                .orElse(CompletableFuture.completedFuture(success)))
            .join();
    }

    private Stream<UpdatedFlags> handleRetries(Mailbox mailbox, FlagsUpdateCalculator flagUpdateCalculator, MailboxMessage message) {
        try {
            return Stream.of(
                new FunctionRunnerWithRetry(maxRetries)
                    .executeAndRetrieveObject(() -> retryMessageFlagsUpdate(mailbox,
                            message.getMessageId(),
                            flagUpdateCalculator)));
        } catch (MessageDeletedDuringFlagsUpdateException e) {
            mailboxSession.getLog().warn(e.getMessage());
            return Stream.of();
        } catch (MailboxDeleteDuringUpdateException e) {
            LOGGER.info("Mailbox {} was deleted during flag update", mailbox.getMailboxId());
            return Stream.of();
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    private Optional<UpdatedFlags> retryMessageFlagsUpdate(Mailbox mailbox, MessageId messageId, FlagsUpdateCalculator flagUpdateCalculator) {
        CassandraId cassandraId = (CassandraId) mailbox.getMailboxId();
        ComposedMessageIdWithMetaData composedMessageIdWithMetaData = imapUidDAO.retrieve((CassandraMessageId) messageId, Optional.of(cassandraId))
            .join()
            .findFirst()
            .orElseThrow(MailboxDeleteDuringUpdateException::new);
        return tryMessageFlagsUpdate(flagUpdateCalculator,
                mailbox,
                messageDAO.retrieveMessages(ImmutableList.of(composedMessageIdWithMetaData), FetchType.Metadata, Optional.empty()).join()
                    .findFirst()
                    .map(pair -> pair.getLeft().toMailboxMessage(ImmutableList.of()))
                    .orElseThrow(() -> new MessageDeletedDuringFlagsUpdateException(cassandraId, (CassandraMessageId) messageId)));
    }
}
