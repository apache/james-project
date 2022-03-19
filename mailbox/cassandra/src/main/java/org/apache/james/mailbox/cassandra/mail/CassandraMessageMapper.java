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

import static org.apache.james.backends.cassandra.init.configuration.JamesExecutionProfiles.ConsistencyChoice.STRONG;
import static org.apache.james.backends.cassandra.init.configuration.JamesExecutionProfiles.ConsistencyChoice.WEAK;
import static org.apache.james.blob.api.BlobStore.StoragePolicy.SIZE_BASED;
import static org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.mail.Flags;
import jakarta.mail.Flags.Flag;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.backends.cassandra.init.configuration.JamesExecutionProfiles;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.mailbox.ApplicableFlagBuilder;
import org.apache.james.mailbox.FlagsBuilder;
import org.apache.james.mailbox.MessageManager.FlagsUpdateMode;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.mail.task.RecomputeMailboxCountersService;
import org.apache.james.mailbox.cassandra.mail.utils.FlagsUpdateStageResult;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxCounters;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.BatchSizes;
import org.apache.james.mailbox.store.FlagsUpdateCalculator;
import org.apache.james.mailbox.store.MailboxReactorUtils;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.ModSeqProvider;
import org.apache.james.mailbox.store.mail.UidProvider;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.apache.james.task.Task;
import org.apache.james.util.ReactorUtils;
import org.apache.james.util.streams.Limit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

public class CassandraMessageMapper implements MessageMapper {
    public static final Logger LOGGER = LoggerFactory.getLogger(CassandraMessageMapper.class);
    private static final byte[] EMPTY_BYTE_ARRAY = {};

    private static final int MAX_RETRY = 5;
    private static final Duration MIN_RETRY_BACKOFF = Duration.ofMillis(10);
    private static final Duration MAX_RETRY_BACKOFF = Duration.ofMillis(1000);

    private final ModSeqProvider modSeqProvider;
    private final UidProvider uidProvider;
    private final CassandraMessageDAOV3 messageDAOV3;
    private final CassandraMessageIdDAO messageIdDAO;
    private final CassandraMessageIdToImapUidDAO imapUidDAO;
    private final CassandraMailboxCounterDAO mailboxCounterDAO;
    private final CassandraMailboxRecentsDAO mailboxRecentDAO;
    private final CassandraApplicableFlagDAO applicableFlagDAO;
    private final CassandraIndexTableHandler indexTableHandler;
    private final CassandraFirstUnseenDAO firstUnseenDAO;
    private final AttachmentLoader attachmentLoader;
    private final CassandraDeletedMessageDAO deletedMessageDAO;
    private final BlobStore blobStore;
    private final CassandraConfiguration cassandraConfiguration;
    private final BatchSizes batchSizes;
    private final RecomputeMailboxCountersService recomputeMailboxCountersService;
    private final SecureRandom secureRandom;
    private final int reactorConcurrency;
    private final Clock clock;

    public CassandraMessageMapper(UidProvider uidProvider, ModSeqProvider modSeqProvider,
                                  CassandraAttachmentMapper attachmentMapper,
                                  CassandraMessageDAOV3 messageDAOV3, CassandraMessageIdDAO messageIdDAO,
                                  CassandraMessageIdToImapUidDAO imapUidDAO, CassandraMailboxCounterDAO mailboxCounterDAO,
                                  CassandraMailboxRecentsDAO mailboxRecentDAO, CassandraApplicableFlagDAO applicableFlagDAO,
                                  CassandraIndexTableHandler indexTableHandler, CassandraFirstUnseenDAO firstUnseenDAO,
                                  CassandraDeletedMessageDAO deletedMessageDAO, BlobStore blobStore, CassandraConfiguration cassandraConfiguration,
                                  BatchSizes batchSizes, RecomputeMailboxCountersService recomputeMailboxCountersService, Clock clock) {
        this.uidProvider = uidProvider;
        this.modSeqProvider = modSeqProvider;
        this.messageDAOV3 = messageDAOV3;
        this.messageIdDAO = messageIdDAO;
        this.imapUidDAO = imapUidDAO;
        this.mailboxCounterDAO = mailboxCounterDAO;
        this.mailboxRecentDAO = mailboxRecentDAO;
        this.indexTableHandler = indexTableHandler;
        this.firstUnseenDAO = firstUnseenDAO;
        this.attachmentLoader = new AttachmentLoader(attachmentMapper);
        this.applicableFlagDAO = applicableFlagDAO;
        this.deletedMessageDAO = deletedMessageDAO;
        this.blobStore = blobStore;
        this.cassandraConfiguration = cassandraConfiguration;
        this.batchSizes = batchSizes;
        this.recomputeMailboxCountersService = recomputeMailboxCountersService;
        this.secureRandom = new SecureRandom();
        this.reactorConcurrency = evaluateReactorConcurrency();
        this.clock = clock;
    }

    @Override
    public Flux<MessageUid> listAllMessageUids(Mailbox mailbox) {
        CassandraId cassandraId = (CassandraId) mailbox.getMailboxId();
        return messageIdDAO.listUids(cassandraId);
    }

    @Override
    public long countMessagesInMailbox(Mailbox mailbox) {
        return getMailboxCounters(mailbox).getCount();
    }

    @Override
    public MailboxCounters getMailboxCounters(Mailbox mailbox) {
        return getMailboxCountersReactive(mailbox).block();
    }

    @Override
    public Mono<MailboxCounters> getMailboxCountersReactive(Mailbox mailbox) {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();
        return readMailboxCounters(mailboxId)
            .flatMap(counters -> {
                if (!counters.isValid()) {
                    return fixCounters(mailbox)
                        .then(readMailboxCounters(mailboxId));
                }
                return Mono.just(counters);
            })
            .doOnNext(counters -> readRepair(mailbox, counters));
    }

    public Mono<MailboxCounters> readMailboxCounters(CassandraId mailboxId) {
        return mailboxCounterDAO.retrieveMailboxCounters(mailboxId)
            .defaultIfEmpty(MailboxCounters.empty(mailboxId));
    }

    private void readRepair(Mailbox mailbox, MailboxCounters counters) {
        if (shouldReadRepair(counters)) {
            fixCounters(mailbox)
                .subscribeOn(Schedulers.parallel())
                .subscribe();
        }
    }

    private Mono<Task.Result> fixCounters(Mailbox mailbox) {
        return recomputeMailboxCountersService.recomputeMailboxCounter(
                new RecomputeMailboxCountersService.Context(),
                mailbox,
                RecomputeMailboxCountersService.Options.trustMessageProjection());
    }

    private boolean shouldReadRepair(MailboxCounters counters) {
        boolean activated = cassandraConfiguration.getMailboxCountersReadRepairChanceMax() != 0 || cassandraConfiguration.getMailboxCountersReadRepairChanceOneHundred() != 0;
        double ponderedReadRepairChance = cassandraConfiguration.getMailboxCountersReadRepairChanceOneHundred() * (100.0 / counters.getUnseen());
        return activated &&
            secureRandom.nextFloat() < Math.min(
                cassandraConfiguration.getMailboxCountersReadRepairChanceMax(),
                ponderedReadRepairChance);
    }

    @Override
    public void delete(Mailbox mailbox, MailboxMessage message) {
        ComposedMessageIdWithMetaData metaData = message.getComposedMessageIdWithMetaData();

        deleteAndHandleIndexUpdates(metaData)
            .block();
    }

    private Mono<Void> deleteAndHandleIndexUpdates(ComposedMessageIdWithMetaData composedMessageIdWithMetaData) {
        ComposedMessageId composedMessageId = composedMessageIdWithMetaData.getComposedMessageId();
        CassandraId mailboxId = (CassandraId) composedMessageId.getMailboxId();

        return delete(composedMessageIdWithMetaData)
             .then(indexTableHandler.updateIndexOnDelete(composedMessageIdWithMetaData, mailboxId));
    }

    private Mono<Void> deleteAndHandleIndexUpdates(Collection<ComposedMessageIdWithMetaData> composedMessageIdWithMetaData) {
        if (composedMessageIdWithMetaData.isEmpty()) {
            return Mono.empty();
        }
        ComposedMessageId composedMessageId = composedMessageIdWithMetaData.iterator().next().getComposedMessageId();
        CassandraId mailboxId = (CassandraId) composedMessageId.getMailboxId();

        return Flux.fromIterable(composedMessageIdWithMetaData)
             .flatMap(this::delete, reactorConcurrency)
             .then(indexTableHandler.updateIndexOnDeleteComposedId(mailboxId, composedMessageIdWithMetaData));
    }

    private Mono<Void> delete(ComposedMessageIdWithMetaData composedMessageIdWithMetaData) {
        ComposedMessageId composedMessageId = composedMessageIdWithMetaData.getComposedMessageId();
        CassandraMessageId messageId = (CassandraMessageId) composedMessageId.getMessageId();
        CassandraId mailboxId = (CassandraId) composedMessageId.getMailboxId();
        MessageUid uid = composedMessageId.getUid();

        return Flux.merge(
                imapUidDAO.delete(messageId, mailboxId),
                messageIdDAO.delete(mailboxId, uid))
                .then();
    }

    @Override
    public Iterator<MailboxMessage> findInMailbox(Mailbox mailbox, MessageRange messageRange, FetchType ftype, int max) {
        return findInMailboxReactive(mailbox, messageRange, ftype, max)
            .toIterable()
            .iterator();
    }

    @Override
    public Flux<ComposedMessageIdWithMetaData> listMessagesMetadata(Mailbox mailbox, MessageRange set) {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();
        return messageIdDAO.listMessagesMetadata(mailboxId, set);
    }

    @Override
    public Flux<MailboxMessage> findInMailboxReactive(Mailbox mailbox, MessageRange messageRange, FetchType ftype, int limitAsInt) {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();

        Limit limit = Limit.from(limitAsInt);
        int concurrency = batchSizes.forFetchType(ftype);
        return limit.applyOnFlux(messageIdDAO.retrieveMessages(mailboxId, messageRange, limit))
            .flatMapSequential(metadata -> toMailboxMessage(metadata, ftype), concurrency, concurrency);
    }

    private Mono<MailboxMessage> toMailboxMessage(CassandraMessageMetadata metadata, FetchType fetchType) {
        if (fetchType == FetchType.METADATA && metadata.isComplete()) {
            return Mono.just(metadata.asMailboxMessage(EMPTY_BYTE_ARRAY));
        }
        if (fetchType == FetchType.HEADERS && metadata.isComplete()) {
            return Mono.from(blobStore.readBytes(blobStore.getDefaultBucketName(), metadata.getHeaderContent().get(), SIZE_BASED))
                .map(metadata::asMailboxMessage);
        }
        return messageDAOV3.retrieveMessage(metadata.getComposedMessageId(), fetchType)
            .map(messageRepresentation -> Pair.of(metadata.getComposedMessageId(), messageRepresentation))
            .flatMap(messageRepresentation -> attachmentLoader.addAttachmentToMessage(messageRepresentation, metadata.getSaveDate(), fetchType));
    }

    @Override
    public List<MessageUid> findRecentMessageUidsInMailbox(Mailbox mailbox) {
        return findRecentMessageUidsInMailboxReactive(mailbox)
            .block();
    }

    @Override
    public Mono<List<MessageUid>> findRecentMessageUidsInMailboxReactive(Mailbox mailbox) {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();
        return mailboxRecentDAO.getRecentMessageUidsInMailbox(mailboxId)
            .collectList();
    }

    @Override
    public MessageUid findFirstUnseenMessageUid(Mailbox mailbox) {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();
        return firstUnseenDAO.retrieveFirstUnread(mailboxId)
                .blockOptional()
                .orElse(null);
    }

    @Override
    public Mono<Optional<MessageUid>> findFirstUnseenMessageUidReactive(Mailbox mailbox) {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();
        return firstUnseenDAO.retrieveFirstUnread(mailboxId)
            .map(Optional::of)
            .switchIfEmpty(Mono.just(Optional.empty()));
    }

    @Override
    public List<MessageUid> retrieveMessagesMarkedForDeletion(Mailbox mailbox, MessageRange messageRange) {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();

        return deletedMessageDAO.retrieveDeletedMessage(mailboxId, messageRange)
            .collect(ImmutableList.toImmutableList())
            .block();
    }

    @Override
    public Map<MessageUid, MessageMetaData> deleteMessages(Mailbox mailbox, List<MessageUid> uids) {
        return deleteMessagesReactive(mailbox, uids)
            .block();
    }

    @Override
    public Flux<MessageUid> retrieveMessagesMarkedForDeletionReactive(Mailbox mailbox, MessageRange messageRange) {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();

        return deletedMessageDAO.retrieveDeletedMessage(mailboxId, messageRange);
    }

    @Override
    public Mono<Map<MessageUid, MessageMetaData>> deleteMessagesReactive(Mailbox mailbox, List<MessageUid> uids) {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();

        return Flux.fromIterable(MessageRange.toRanges(uids))
            .concatMap(range -> messageIdDAO.retrieveMessages(mailboxId, range, Limit.unlimited()))
            .flatMap(cassandraMessageMetadata -> expungeOne(cassandraMessageMetadata.getComposedMessageId(), cassandraMessageMetadata.getSaveDate()), cassandraConfiguration.getExpungeChunkSize())
            .collect(ImmutableMap.toImmutableMap(MailboxMessage::getUid, MailboxMessage::metaData))
            .flatMap(messageMap -> indexTableHandler.updateIndexOnDelete(mailboxId, messageMap.values())
                .thenReturn(messageMap));
    }

    private Mono<SimpleMailboxMessage> expungeOne(ComposedMessageIdWithMetaData metaData, Optional<Date> saveDate) {
        return delete(metaData)
            .then(messageDAOV3.retrieveMessage(metaData, FetchType.METADATA))
            .map(pair -> pair.toMailboxMessage(metaData, ImmutableList.of(), saveDate));
    }

    @Override
    public MessageMetaData move(Mailbox destinationMailbox, MailboxMessage original) throws MailboxException {
        return MailboxReactorUtils.block(moveReactive(destinationMailbox, original));
    }

    @Override
    public List<MessageMetaData> move(Mailbox mailbox, List<MailboxMessage> original) throws MailboxException {
        return MailboxReactorUtils.block(moveReactive(mailbox, original));
    }

    @Override
    public Mono<MessageMetaData> moveReactive(Mailbox destinationMailbox, MailboxMessage original) {
        ComposedMessageIdWithMetaData composedMessageIdWithMetaData = original.getComposedMessageIdWithMetaData();

        return copyReactive(destinationMailbox, original)
            .flatMap(messageMetaData -> deleteAndHandleIndexUpdates(composedMessageIdWithMetaData)
                .thenReturn(messageMetaData));
    }

    @Override
    public Mono<List<MessageMetaData>> moveReactive(Mailbox destinationMailbox, List<MailboxMessage> original) {
        List<ComposedMessageIdWithMetaData> beforeCopy = original.stream()
            .map(MailboxMessage::getComposedMessageIdWithMetaData)
            .collect(ImmutableList.toImmutableList());

        return copyReactive(destinationMailbox, original)
            .flatMap(messageMetaData -> deleteAndHandleIndexUpdates(beforeCopy)
                .thenReturn(messageMetaData));
    }

    @Override
    public ModSeq getHighestModSeq(Mailbox mailbox) throws MailboxException {
        return modSeqProvider.highestModSeq(mailbox);
    }

    @Override
    public Mono<Optional<MessageUid>> getLastUidReactive(Mailbox mailbox) {
        return uidProvider.lastUidReactive(mailbox);
    }

    @Override
    public Mono<ModSeq> getHighestModSeqReactive(Mailbox mailbox) {
        return modSeqProvider.highestModSeqReactive(mailbox);
    }

    @Override
    public MessageMetaData add(Mailbox mailbox, MailboxMessage message) throws MailboxException {
        return block(addReactive(mailbox, message));
    }

    @Override
    public Mono<MessageMetaData> addReactive(Mailbox mailbox, MailboxMessage message) {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();

        return addUidAndModseqAndSaveDate(message, mailboxId)
            .flatMap(messageWithUidAndModSeq -> save(mailbox, messageWithUidAndModSeq)
                .thenReturn(messageWithUidAndModSeq.metaData()));
    }

    private Mono<MailboxMessage> addUidAndModseqAndSaveDate(MailboxMessage message, CassandraId mailboxId) {
        Mono<MessageUid> messageUidMono = uidProvider
            .nextUidReactive(mailboxId)
            .switchIfEmpty(Mono.error(() -> new MailboxException("Can not find a UID to save " + message.getMessageId() + " in " + mailboxId)));

        Mono<ModSeq> nextModSeqMono = modSeqProvider.nextModSeqReactive(mailboxId)
            .switchIfEmpty(Mono.error(() -> new MailboxException("Can not find a MODSEQ to save " + message.getMessageId() + " in " + mailboxId)));

        return Mono.zip(messageUidMono, nextModSeqMono)
                .doOnNext(tuple -> {
                    message.setUid(tuple.getT1());
                    message.setModSeq(tuple.getT2());
                    message.setSaveDate(Date.from(clock.instant()));
                })
                .thenReturn(message);
    }

    private <T> T block(Mono<T> mono) throws MailboxException {
        try {
            return mono.block();
        } catch (RuntimeException e) {
            if (e.getCause() instanceof MailboxException) {
                throw (MailboxException) e.getCause();
            }
            throw e;
        }
    }

    @Override
    public Iterator<UpdatedFlags> updateFlags(Mailbox mailbox, FlagsUpdateCalculator flagUpdateCalculator, MessageRange range) {
        return updateFlagsReactive(mailbox, flagUpdateCalculator, range).block().iterator();
    }

    @Override
    public Mono<List<UpdatedFlags>> updateFlagsReactive(Mailbox mailbox, FlagsUpdateCalculator flagsUpdateCalculator, MessageRange set) {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();

        Flux<ComposedMessageIdWithMetaData> toBeUpdated = messageIdDAO.retrieveMessages(mailboxId, set, Limit.unlimited())
            .map(CassandraMessageMetadata::getComposedMessageId);

        return updateFlags(flagsUpdateCalculator, mailboxId, toBeUpdated);
    }

    private Mono<List<UpdatedFlags>> updateFlags(FlagsUpdateCalculator flagUpdateCalculator, CassandraId mailboxId, Flux<ComposedMessageIdWithMetaData> toBeUpdated) {
        return runUpdateStage(mailboxId, toBeUpdated, flagUpdateCalculator)
            .flatMap(firstResult ->
                handleUpdatesStagedRetry(mailboxId, flagUpdateCalculator, firstResult)
                    .doOnNext(finalResult -> {
                        if (finalResult.containsFailedResults()) {
                            LOGGER.error("Can not update following UIDs {} for mailbox {}", finalResult.getFailed(), mailboxId.asUuid());
                        }
                    }).map(FlagsUpdateStageResult::getSucceeded));
    }

    @Override
    public List<UpdatedFlags> resetRecent(Mailbox mailbox) {
        return resetRecentReactive(mailbox).block();
    }

    @Override
    public Mono<List<UpdatedFlags>> resetRecentReactive(Mailbox mailbox) {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();

        Flux<ComposedMessageIdWithMetaData> toBeUpdated = mailboxRecentDAO.getRecentMessageUidsInMailbox(mailboxId)
            .collectList()
            .flatMapIterable(MessageRange::toRanges)
            .concatMap(range -> messageIdDAO.retrieveMessages(mailboxId, range, Limit.unlimited()))
            .map(CassandraMessageMetadata::getComposedMessageId)
            .filter(message -> message.getFlags().contains(Flag.RECENT));
        FlagsUpdateCalculator calculator = new FlagsUpdateCalculator(new Flags(Flag.RECENT), FlagsUpdateMode.REMOVE);

        return updateFlags(calculator, mailboxId, toBeUpdated);
    }

    private Mono<FlagsUpdateStageResult> handleUpdatesStagedRetry(CassandraId mailboxId, FlagsUpdateCalculator flagUpdateCalculator, FlagsUpdateStageResult firstResult) {
        AtomicReference<FlagsUpdateStageResult> globalResult = new AtomicReference<>(firstResult);

        return Flux.range(0, cassandraConfiguration.getFlagsUpdateMessageMaxRetry())
            .takeUntil(i -> globalResult.get().containsFailedResults())
            .concatMap(i -> retryUpdatesStage(mailboxId, flagUpdateCalculator, globalResult.get().getFailed())
                .doOnNext(next -> globalResult.set(globalResult.get().keepSucceded().merge(next))))
            .then(Mono.fromCallable(globalResult::get));
    }

    private Mono<FlagsUpdateStageResult> retryUpdatesStage(CassandraId mailboxId, FlagsUpdateCalculator flagsUpdateCalculator, List<ComposedMessageId> failed) {
        if (!failed.isEmpty()) {
            Flux<ComposedMessageIdWithMetaData> toUpdate = Flux.fromIterable(failed)
                .flatMap(ids -> imapUidDAO.retrieve((CassandraMessageId) ids.getMessageId(), Optional.of((CassandraId) ids.getMailboxId()), chooseReadConsistencyUponWrites())
                        .map(CassandraMessageMetadata::getComposedMessageId),
                    DEFAULT_CONCURRENCY);
            return runUpdateStage(mailboxId, toUpdate, flagsUpdateCalculator);
        } else {
            return Mono.empty();
        }
    }

    private JamesExecutionProfiles.ConsistencyChoice chooseReadConsistencyUponWrites() {
        if (cassandraConfiguration.isMessageWriteStrongConsistency()) {
            return STRONG;
        }
        return WEAK;
    }

    private Mono<FlagsUpdateStageResult> runUpdateStage(CassandraId mailboxId, Flux<ComposedMessageIdWithMetaData> toBeUpdated, FlagsUpdateCalculator flagsUpdateCalculator) {
        return computeNewModSeq(mailboxId)
            .flatMapMany(newModSeq -> toBeUpdated
            .flatMap(metadata -> tryFlagsUpdate(flagsUpdateCalculator, newModSeq, metadata), reactorConcurrency))
            .reduce(FlagsUpdateStageResult.none(), FlagsUpdateStageResult::merge)
            .flatMap(result -> updateIndexesForUpdatesResult(mailboxId, result));
    }

    private Mono<ModSeq> computeNewModSeq(CassandraId mailboxId) {
        return modSeqProvider.nextModSeqReactive(mailboxId)
            .switchIfEmpty(ReactorUtils.executeAndEmpty(() -> new RuntimeException("ModSeq generation failed for mailbox " + mailboxId.asUuid())));
    }

    private Mono<FlagsUpdateStageResult> updateIndexesForUpdatesResult(CassandraId mailboxId, FlagsUpdateStageResult result) {
        return indexTableHandler.updateIndexOnFlagsUpdate(mailboxId, result.getSucceeded())
            .onErrorResume(e -> {
                LOGGER.error("Could not update flag indexes for mailboxId {}. This will lead to inconsistencies across Cassandra tables", mailboxId, e);
                return Mono.empty();
            })
            .thenReturn(result);
    }

    @Override
    public MessageMetaData copy(Mailbox mailbox, MailboxMessage original) throws MailboxException {
        return MailboxReactorUtils.block(copyReactive(mailbox, original));
    }

    @Override
    public List<MessageMetaData> copy(Mailbox mailbox, List<MailboxMessage> originals) throws MailboxException {
        return MailboxReactorUtils.block(copyReactive(mailbox, originals));
    }

    @Override
    public Mono<MessageMetaData> copyReactive(Mailbox mailbox, MailboxMessage original) {
        original.setFlags(new FlagsBuilder().add(original.createFlags()).add(Flag.RECENT).build());
        original.setSaveDate(Date.from(clock.instant()));
        return setInMailboxReactive(mailbox, original);
    }

    @Override
    public Mono<List<MessageMetaData>> copyReactive(Mailbox mailbox, List<MailboxMessage> originals) {
        if (originals.isEmpty()) {
            return Mono.empty();
        }
        return setMessagesInMailboxReactive(mailbox, originals.stream()
            .map(original -> {
                original.setFlags(new FlagsBuilder().add(original.createFlags()).add(Flag.RECENT).build());
                original.setSaveDate(Date.from(clock.instant()));
                return original;
            }).collect(ImmutableList.toImmutableList()))
            .collectList();
    }

    @Override
    public Optional<MessageUid> getLastUid(Mailbox mailbox) throws MailboxException {
        return uidProvider.lastUid(mailbox);
    }

    @Override
    public Flags getApplicableFlag(Mailbox mailbox) {
        return ApplicableFlagBuilder.builder()
            .add(applicableFlagDAO.retrieveApplicableFlag((CassandraId) mailbox.getMailboxId())
                .defaultIfEmpty(new Flags())
                .block())
            .build();
    }

    @Override
    public Mono<Flags> getApplicableFlagReactive(Mailbox mailbox) {
        return applicableFlagDAO.retrieveApplicableFlag((CassandraId) mailbox.getMailboxId())
            .defaultIfEmpty(new Flags())
            .map(flags -> ApplicableFlagBuilder.builder().add(flags).build());
    }

    private Mono<MessageMetaData> setInMailboxReactive(Mailbox mailbox, MailboxMessage message) {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();
        return addUidAndModseqAndSaveDate(message, mailboxId)
            .flatMap(messageWithUidAndModseq ->
                insertMetadata(messageWithUidAndModseq, mailboxId,
                    CassandraMessageMetadata.from(messageWithUidAndModseq)
                        .withMailboxId(mailboxId))
                .thenReturn(messageWithUidAndModseq))
            .map(MailboxMessage::metaData);
    }

    private Flux<MessageMetaData> setMessagesInMailboxReactive(Mailbox mailbox, List<MailboxMessage> messages) {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();

        Mono<List<MessageUid>> uids = uidProvider.nextUids(mailboxId, messages.size());
        Mono<ModSeq> nextModSeq = modSeqProvider.nextModSeqReactive(mailboxId);

        Mono<List<MailboxMessage>> messagesWithUidAndModSeq = nextModSeq.flatMap(modSeq -> uids.map(uidList -> Pair.of(uidList, modSeq)))
            .map(pair -> pair.getKey().stream()
                .map(uid -> Pair.of(uid, pair.getRight())))
            .map(uidsAndModSeq -> Streams.zip(uidsAndModSeq, messages.stream(),
                (uidAndModseq, aMessage) -> {
                    aMessage.setUid(uidAndModseq.getKey());
                    aMessage.setModSeq((uidAndModseq.getValue()));
                    return aMessage;
                }).collect(ImmutableList.toImmutableList()));

        return messagesWithUidAndModSeq
            .flatMapMany(list -> insertIds(list, mailboxId).thenMany(Flux.fromIterable(list)))
            .map(MailboxMessage::metaData);
    }

    private Mono<Void> save(Mailbox mailbox, MailboxMessage message) {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();
        return messageDAOV3.save(message)
            .flatMap(headerAndBodyBlobIds -> insertIds(message, mailboxId, headerAndBodyBlobIds.getT1()));
    }

    private Mono<Void> insertIds(MailboxMessage message, CassandraId mailboxId, BlobId headerBlobId) {
        CassandraMessageMetadata metadata = CassandraMessageMetadata.from(message, headerBlobId);

        return insertMetadata(message, mailboxId, metadata);
    }

    private Mono<Void> insertMetadata(MailboxMessage message, CassandraId mailboxId, CassandraMessageMetadata metadata) {
        return imapUidDAO.insert(metadata)
            .then(Flux.merge(
                messageIdDAO.insert(metadata)
                    .retryWhen(Retry.backoff(MAX_RETRY, MIN_RETRY_BACKOFF).maxBackoff(MAX_RETRY_BACKOFF)),
                indexTableHandler.updateIndexOnAdd(message, mailboxId))
            .then());
    }


    private CassandraMessageMetadata computeId(MailboxMessage message, CassandraId mailboxId) {
        return CassandraMessageMetadata.from(message)
            .withMailboxId(mailboxId);
    }

    private Mono<Void> insertIds(Collection<MailboxMessage> messages, CassandraId mailboxId) {
        return Flux.fromIterable(messages)
            .map(message -> computeId(message, mailboxId))
            .concatMap(id -> imapUidDAO.insert(id).thenReturn(id))
            .flatMap(id -> messageIdDAO.insert(id)
                .retryWhen(Retry.backoff(MAX_RETRY, MIN_RETRY_BACKOFF).maxBackoff(MAX_RETRY_BACKOFF)), reactorConcurrency)
            .then(indexTableHandler.updateIndexOnAdd(messages, mailboxId));
    }

    private Mono<FlagsUpdateStageResult> tryFlagsUpdate(FlagsUpdateCalculator flagUpdateCalculator, ModSeq newModSeq, ComposedMessageIdWithMetaData oldMetaData) {
        Flags oldFlags = oldMetaData.getFlags();
        Flags newFlags = flagUpdateCalculator.buildNewFlags(oldFlags);

        if (identicalFlags(oldFlags, newFlags)) {
            return Mono.just(FlagsUpdateStageResult.success(UpdatedFlags.builder()
                .uid(oldMetaData.getComposedMessageId().getUid())
                .messageId(oldMetaData.getComposedMessageId().getMessageId())
                .modSeq(oldMetaData.getModSeq())
                .oldFlags(oldFlags)
                .newFlags(newFlags)
                .build()));
        }

        return updateFlags(oldMetaData, newFlags, newModSeq)
            .map(success -> {
                if (success) {
                    return FlagsUpdateStageResult.success(UpdatedFlags.builder()
                        .uid(oldMetaData.getComposedMessageId().getUid())
                        .messageId(oldMetaData.getComposedMessageId().getMessageId())
                        .modSeq(newModSeq)
                        .oldFlags(oldFlags)
                        .newFlags(newFlags)
                        .build());
                } else {
                    return FlagsUpdateStageResult.fail(oldMetaData.getComposedMessageId());
                }
            });
    }

    private boolean identicalFlags(Flags oldFlags, Flags newFlags) {
        return oldFlags.equals(newFlags);
    }

    private Mono<Boolean> updateFlags(ComposedMessageIdWithMetaData oldMetadata, Flags newFlags, ModSeq newModSeq) {
        ComposedMessageIdWithMetaData newMetadata = ComposedMessageIdWithMetaData.builder()
            .composedMessageId(oldMetadata.getComposedMessageId())
            .modSeq(newModSeq)
            .flags(newFlags)
            .threadId(oldMetadata.getThreadId())
            .build();

        ComposedMessageId composedMessageId = newMetadata.getComposedMessageId();
        ModSeq previousModseq = oldMetadata.getModSeq();
        UpdatedFlags updatedFlags = UpdatedFlags.builder()
            .messageId(composedMessageId.getMessageId())
            .modSeq(newMetadata.getModSeq())
            .oldFlags(oldMetadata.getFlags())
            .newFlags(newMetadata.getFlags())
            .uid(composedMessageId.getUid())
            .build();

        return imapUidDAO.updateMetadata(composedMessageId, updatedFlags, previousModseq)
            .flatMap(success -> {
                if (success) {
                    return messageIdDAO.updateMetadata(composedMessageId, updatedFlags).thenReturn(true);
                } else {
                    return Mono.just(false);
                }
            });
    }

    private int evaluateReactorConcurrency() {
        if (cassandraConfiguration.isMessageWriteStrongConsistency()) {
            // Prevent parallel execution to prevent CAS contention because of LightWeight transactions
            return 1;
        }
        return 4;
    }
}
