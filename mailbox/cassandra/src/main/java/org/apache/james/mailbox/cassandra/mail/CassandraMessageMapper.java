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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import javax.mail.Flags;
import javax.mail.Flags.Flag;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.backends.cassandra.CassandraConfiguration;
import org.apache.james.mailbox.ApplicableFlagBuilder;
import org.apache.james.mailbox.FlagsBuilder;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.mail.migration.V1ToV2Migration;
import org.apache.james.mailbox.cassandra.mail.utils.FlagsUpdateStageResult;
import org.apache.james.mailbox.cassandra.mail.utils.Limit;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.MailboxCounters;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.FlagsUpdateCalculator;
import org.apache.james.mailbox.store.SimpleMessageMetaData;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.apache.james.util.FluentFutureStream;
import org.apache.james.util.OptionalConverter;
import org.apache.james.util.streams.JamesCollectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableList;

public class CassandraMessageMapper implements MessageMapper {
    public static final MailboxCounters INITIAL_COUNTERS =  MailboxCounters.builder()
        .count(0L)
        .unseen(0L)
        .build();
    public static final Logger LOGGER = LoggerFactory.getLogger(CassandraMessageMapper.class);

    private final CassandraModSeqProvider modSeqProvider;
    private final MailboxSession mailboxSession;
    private final CassandraUidProvider uidProvider;
    private final CassandraMessageDAOV2 messageDAOV2;
    private final CassandraMessageIdDAO messageIdDAO;
    private final CassandraMessageIdToImapUidDAO imapUidDAO;
    private final CassandraMailboxCounterDAO mailboxCounterDAO;
    private final CassandraMailboxRecentsDAO mailboxRecentDAO;
    private final CassandraApplicableFlagDAO applicableFlagDAO;
    private final CassandraIndexTableHandler indexTableHandler;
    private final CassandraFirstUnseenDAO firstUnseenDAO;
    private final AttachmentLoader attachmentLoader;
    private final CassandraDeletedMessageDAO deletedMessageDAO;
    private final V1ToV2Migration v1ToV2Migration;
    private final CassandraConfiguration cassandraConfiguration;

    public CassandraMessageMapper(CassandraUidProvider uidProvider, CassandraModSeqProvider modSeqProvider,
                                  MailboxSession mailboxSession, CassandraAttachmentMapper attachmentMapper,
                                  CassandraMessageDAOV2 messageDAOV2, CassandraMessageIdDAO messageIdDAO,
                                  CassandraMessageIdToImapUidDAO imapUidDAO, CassandraMailboxCounterDAO mailboxCounterDAO,
                                  CassandraMailboxRecentsDAO mailboxRecentDAO, CassandraApplicableFlagDAO applicableFlagDAO,
                                  CassandraIndexTableHandler indexTableHandler, CassandraFirstUnseenDAO firstUnseenDAO,
                                  CassandraDeletedMessageDAO deletedMessageDAO, V1ToV2Migration v1ToV2Migration,
                                  CassandraConfiguration cassandraConfiguration) {
        this.uidProvider = uidProvider;
        this.modSeqProvider = modSeqProvider;
        this.mailboxSession = mailboxSession;
        this.messageDAOV2 = messageDAOV2;
        this.messageIdDAO = messageIdDAO;
        this.imapUidDAO = imapUidDAO;
        this.mailboxCounterDAO = mailboxCounterDAO;
        this.mailboxRecentDAO = mailboxRecentDAO;
        this.indexTableHandler = indexTableHandler;
        this.firstUnseenDAO = firstUnseenDAO;
        this.attachmentLoader = new AttachmentLoader(attachmentMapper);
        this.applicableFlagDAO = applicableFlagDAO;
        this.deletedMessageDAO = deletedMessageDAO;
        this.v1ToV2Migration = v1ToV2Migration;
        this.cassandraConfiguration = cassandraConfiguration;
    }

    @Override
    public Iterator<MessageUid> listAllMessageUids(Mailbox mailbox) throws MailboxException {
        CassandraId cassandraId = (CassandraId) mailbox.getMailboxId();
        return messageIdDAO.retrieveMessages(cassandraId, MessageRange.all())
            .join()
            .map(metaData -> metaData.getComposedMessageId().getUid())
            .iterator();
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

        deleteAsFuture(message, mailboxId)
            .join();
    }

    private CompletableFuture<Void> deleteAsFuture(MailboxMessage message, CassandraId mailboxId) {
        return messageIdDAO.retrieve(mailboxId, message.getUid())
            .thenCompose(optional -> optional
                .map(this::deleteUsingMailboxId)
                .orElse(CompletableFuture.completedFuture(null)));
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
        return retrieveMessages(retrieveMessageIds(mailboxId, messageRange), ftype, Limit.from(max))
            .join()
            .map(SimpleMailboxMessage -> (MailboxMessage) SimpleMailboxMessage)
            .sorted(Comparator.comparing(MailboxMessage::getUid))
            .iterator();
    }

    private List<ComposedMessageIdWithMetaData> retrieveMessageIds(CassandraId mailboxId, MessageRange messageRange) {
        return messageIdDAO.retrieveMessages(mailboxId, messageRange)
            .join()
            .collect(Guavate.toImmutableList());
    }

    private CompletableFuture<Stream<SimpleMailboxMessage>> retrieveMessages(List<ComposedMessageIdWithMetaData> messageIds, FetchType fetchType, Limit limit) {
        CompletableFuture<Stream<Pair<MessageWithoutAttachment, Stream<MessageAttachmentRepresentation>>>>
            messageRepresentations = retrieveMessagesAndDoMigrationIfNeeded(messageIds, fetchType, limit);

        return messageRepresentations
            .thenCompose(stream -> attachmentLoader.addAttachmentToMessages(stream, fetchType));
    }

    @Override
    public List<MessageUid> findRecentMessageUidsInMailbox(Mailbox mailbox) throws MailboxException {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();
        return mailboxRecentDAO.getRecentMessageUidsInMailbox(mailboxId)
            .join()
            .collect(Guavate.toImmutableList());
    }

    @Override
    public MessageUid findFirstUnseenMessageUid(Mailbox mailbox) throws MailboxException {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();
        return firstUnseenDAO.retrieveFirstUnread(mailboxId)
            .join()
            .orElse(null);
    }

    @Override
    public Map<MessageUid, MessageMetaData> expungeMarkedForDeletionInMailbox(Mailbox mailbox, MessageRange messageRange) throws MailboxException {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();

        return deletedMessageDAO.retrieveDeletedMessage(mailboxId, messageRange)
            .join()
            .collect(JamesCollectors.chunker(cassandraConfiguration.getExpungeChunkSize()))
            .map(uidChunk -> expungeUidChunk(mailboxId, uidChunk))
            .flatMap(CompletableFuture::join)
            .collect(Guavate.toImmutableMap(MailboxMessage::getUid, SimpleMessageMetaData::new));
    }

    private CompletableFuture<Stream<SimpleMailboxMessage>> expungeUidChunk(CassandraId mailboxId, Collection<MessageUid> uidChunk) {
        return FluentFutureStream.ofOptionals(
                uidChunk.stream().map(uid -> retrieveComposedId(mailboxId, uid)))
            .performOnAll(this::deleteUsingMailboxId)
            .thenFlatCompose(idWithMetadata -> retrieveMessagesAndDoMigrationIfNeeded(ImmutableList.of(idWithMetadata), FetchType.Metadata, Limit.unlimited()))
            .map(pair -> pair.getKey().toMailboxMessage(ImmutableList.of()))
            .completableFuture();
    }

    private CompletableFuture<Optional<ComposedMessageIdWithMetaData>> retrieveComposedId(CassandraId mailboxId, MessageUid uid) {
        return messageIdDAO.retrieve(mailboxId, uid)
            .thenApply(optional -> OptionalConverter.ifEmpty(optional,
                () -> LOGGER.warn("Could not retrieve message {} {}", mailboxId, uid)));
    }

    private CompletableFuture<Stream<Pair<MessageWithoutAttachment, Stream<MessageAttachmentRepresentation>>>> retrieveMessagesAndDoMigrationIfNeeded(
        List<ComposedMessageIdWithMetaData> messageIds, FetchType fetchType, Limit limit) {

        return FluentFutureStream.of(messageDAOV2.retrieveMessages(messageIds, fetchType, limit))
            .thenComposeOnAll(v1ToV2Migration::getFromV2orElseFromV1AfterMigration)
            .completableFuture();
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
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();

        save(mailbox, addUidAndModseq(message, mailboxId))
            .thenCompose(voidValue -> indexTableHandler.updateIndexOnAdd(message, mailboxId))
            .join();
        return new SimpleMessageMetaData(message);
    }

    private MailboxMessage addUidAndModseq(MailboxMessage message, CassandraId mailboxId) throws MailboxException {
        CompletableFuture<Optional<MessageUid>> uidFuture = uidProvider.nextUid(mailboxId);
        CompletableFuture<Optional<Long>> modseqFuture = modSeqProvider.nextModSeq(mailboxId);
        CompletableFuture.allOf(uidFuture, modseqFuture).join();

        message.setUid(uidFuture.join()
            .orElseThrow(() -> new MailboxException("Can not find a UID to save " + message.getMessageId() + " in " + mailboxId)));
        message.setModSeq(modseqFuture.join()
            .orElseThrow(() -> new MailboxException("Can not find a MODSEQ to save " + message.getMessageId() + " in " + mailboxId)));

        return message;
    }

    @Override
    public Iterator<UpdatedFlags> updateFlags(Mailbox mailbox, FlagsUpdateCalculator flagUpdateCalculator, MessageRange range) throws MailboxException {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();

        Stream<ComposedMessageIdWithMetaData> toBeUpdated = messageIdDAO.retrieveMessages(mailboxId, range).join();

        FlagsUpdateStageResult firstResult = runUpdateStage(mailboxId, toBeUpdated, flagUpdateCalculator);
        FlagsUpdateStageResult finalResult = handleUpdatesStagedRetry(mailboxId, flagUpdateCalculator, firstResult);
        if (finalResult.containsFailedResults()) {
            LOGGER.error("Can not update following UIDs {} for mailbox {}", finalResult.getFailed(), mailboxId.asUuid());
        }
        return finalResult.getSucceeded().iterator();
    }

    private FlagsUpdateStageResult handleUpdatesStagedRetry(CassandraId mailboxId, FlagsUpdateCalculator flagUpdateCalculator, FlagsUpdateStageResult firstResult) {
        FlagsUpdateStageResult globalResult = firstResult;
        int retryCount = 0;
        while (retryCount < cassandraConfiguration.getFlagsUpdateMessageMaxRetry() && globalResult.containsFailedResults()) {
            retryCount++;
            FlagsUpdateStageResult stageResult = retryUpdatesStage(mailboxId, flagUpdateCalculator, globalResult.getFailed());
            globalResult = globalResult.keepSucceded().merge(stageResult);
        }
        return globalResult;
    }

    private FlagsUpdateStageResult retryUpdatesStage(CassandraId mailboxId, FlagsUpdateCalculator flagsUpdateCalculator, List<MessageUid> failed) {
        Stream<ComposedMessageIdWithMetaData> idsFailed = FluentFutureStream.ofOptionals(
            failed.stream().map(uid -> messageIdDAO.retrieve(mailboxId, uid)))
            .join();

        return runUpdateStage(mailboxId, idsFailed, flagsUpdateCalculator);
    }

    private FlagsUpdateStageResult runUpdateStage(CassandraId mailboxId, Stream<ComposedMessageIdWithMetaData> toBeUpdated, FlagsUpdateCalculator flagsUpdateCalculator) {
        Long newModSeq = modSeqProvider.nextModSeq(mailboxId).join().orElseThrow(() -> new RuntimeException("ModSeq generation failed for mailbox " + mailboxId.asUuid()));

        return toBeUpdated.collect(JamesCollectors.chunker(cassandraConfiguration.getFlagsUpdateChunkSize()))
            .map(uidChunk -> performUpdatesForChunk(mailboxId, flagsUpdateCalculator, newModSeq, uidChunk))
            .map(CompletableFuture::join)
            .reduce(FlagsUpdateStageResult.none(), FlagsUpdateStageResult::merge);
    }

    private CompletableFuture<FlagsUpdateStageResult> performUpdatesForChunk(CassandraId mailboxId, FlagsUpdateCalculator flagsUpdateCalculator, Long newModSeq, Collection<ComposedMessageIdWithMetaData> uidChunk) {
        Stream<CompletableFuture<FlagsUpdateStageResult>> updateMetaDataFuture =
            uidChunk.stream().map(oldMetadata -> tryFlagsUpdate(flagsUpdateCalculator, newModSeq, oldMetadata));

        return FluentFutureStream.of(updateMetaDataFuture)
            .reduce(FlagsUpdateStageResult.none(), FlagsUpdateStageResult::merge)
            .thenCompose(result -> updateIndexesForUpdatesResult(mailboxId, result));
    }

    private CompletableFuture<FlagsUpdateStageResult> updateIndexesForUpdatesResult(CassandraId mailboxId, FlagsUpdateStageResult result) {
        return FluentFutureStream.of(
            result.getSucceeded().stream()
                .map(Throwing
                    .function((UpdatedFlags updatedFlags) -> indexTableHandler.updateIndexOnFlagsUpdate(mailboxId, updatedFlags))
                    .fallbackTo(failedindex -> {
                        LOGGER.error("Could not update flag indexes for mailboxId {} UID {}. This will lead to inconsistencies across Cassandra tables");
                        return CompletableFuture.completedFuture(null);
                    })))
            .completableFuture()
            .thenApply(any -> result);
    }

    @Override
    public <T> T execute(Transaction<T> transaction) throws MailboxException {
        return transaction.run();
    }

    @Override
    public MessageMetaData copy(Mailbox mailbox, MailboxMessage original) throws MailboxException {
        original.setFlags(new FlagsBuilder().add(original.createFlags()).add(Flag.RECENT).build());
        return setInMailbox(mailbox, original);
    }

    @Override
    public com.google.common.base.Optional<MessageUid> getLastUid(Mailbox mailbox) throws MailboxException {
        return uidProvider.lastUid(mailboxSession, mailbox);
    }

    @Override
    public Flags getApplicableFlag(Mailbox mailbox) throws MailboxException {
        return ApplicableFlagBuilder.builder()
            .add(applicableFlagDAO.retrieveApplicableFlag((CassandraId) mailbox.getMailboxId())
                .join()
                .orElse(new Flags()))
            .build();
    }

    private MessageMetaData setInMailbox(Mailbox mailbox, MailboxMessage message) throws MailboxException {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();

        insertIds(addUidAndModseq(message, mailboxId), mailboxId)
                .thenCompose(voidValue -> indexTableHandler.updateIndexOnAdd(message, mailboxId))
                .join();
        return new SimpleMessageMetaData(message);
    }

    private CompletableFuture<Void> save(Mailbox mailbox, MailboxMessage message) throws MailboxException {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();
        return messageDAOV2.save(message)
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


    private CompletableFuture<FlagsUpdateStageResult> tryFlagsUpdate(FlagsUpdateCalculator flagUpdateCalculator, long newModSeq, ComposedMessageIdWithMetaData oldMetaData) {
        Flags oldFlags = oldMetaData.getFlags();
        Flags newFlags = flagUpdateCalculator.buildNewFlags(oldFlags);

        if (identicalFlags(oldFlags, newFlags)) {
            return CompletableFuture.completedFuture(FlagsUpdateStageResult.success(UpdatedFlags.builder()
                .uid(oldMetaData.getComposedMessageId().getUid())
                .modSeq(oldMetaData.getModSeq())
                .oldFlags(oldFlags)
                .newFlags(newFlags)
                .build()));
        }

        return updateFlags(oldMetaData, newFlags, newModSeq)
            .thenApply(success -> {
                if (success) {
                    return FlagsUpdateStageResult.success(UpdatedFlags.builder()
                        .uid(oldMetaData.getComposedMessageId().getUid())
                        .modSeq(newModSeq)
                        .oldFlags(oldFlags)
                        .newFlags(newFlags)
                        .build());
                } else {
                    return FlagsUpdateStageResult.fail(oldMetaData.getComposedMessageId().getUid());
                }
            });
    }

    private boolean identicalFlags(Flags oldFlags, Flags newFlags) {
        return oldFlags.equals(newFlags);
    }

    private CompletableFuture<Boolean> updateFlags(ComposedMessageIdWithMetaData oldMetadata, Flags newFlags, long newModSeq) {
        ComposedMessageIdWithMetaData newMetadata = ComposedMessageIdWithMetaData.builder()
                .composedMessageId(oldMetadata.getComposedMessageId())
                .modSeq(newModSeq)
                .flags(newFlags)
                .build();
        return imapUidDAO.updateMetadata(newMetadata, oldMetadata.getModSeq())
            .thenCompose(success -> Optional.of(success)
                .filter(b -> b)
                .map((Boolean any) -> messageIdDAO.updateMetadata(newMetadata)
                    .thenApply(v -> success))
                .orElse(CompletableFuture.completedFuture(success)));
    }
}
