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

import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import jakarta.mail.Flags;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.backends.cassandra.init.configuration.JamesExecutionProfiles;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.BatchSizes;
import org.apache.james.mailbox.store.FlagsUpdateCalculator;
import org.apache.james.mailbox.store.MailboxReactorUtils;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.MessageIdMapper;
import org.apache.james.mailbox.store.mail.MessageMapper.FetchType;
import org.apache.james.mailbox.store.mail.ModSeqProvider;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.util.FunctionalUtils;
import org.apache.james.util.ReactorUtils;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Multimap;

import reactor.core.publisher.Flux;
import reactor.core.publisher.GroupedFlux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

public class CassandraMessageIdMapper implements MessageIdMapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraMessageIdMapper.class);
    private static final byte[] EMPTY_BYTE_ARRAY = {};

    private static final int MAX_RETRY = 5;
    private static final Duration MIN_RETRY_BACKOFF = Duration.ofMillis(10);
    private static final Duration MAX_RETRY_BACKOFF = Duration.ofMillis(1000);

    private final MailboxMapper mailboxMapper;
    private final CassandraMailboxDAO mailboxDAO;
    private final CassandraMessageIdToImapUidDAO imapUidDAO;
    private final CassandraMessageIdDAO messageIdDAO;
    private final CassandraMessageDAOV3 messageDAOV3;
    private final CassandraIndexTableHandler indexTableHandler;
    private final ModSeqProvider modSeqProvider;
    private final AttachmentLoader attachmentLoader;
    private final BlobStore blobStore;
    private final CassandraConfiguration cassandraConfiguration;
    private final BatchSizes batchSizes;
    private final Clock clock;

    public CassandraMessageIdMapper(MailboxMapper mailboxMapper, CassandraMailboxDAO mailboxDAO, CassandraAttachmentMapper attachmentMapper,
                                    CassandraMessageIdToImapUidDAO imapUidDAO, CassandraMessageIdDAO messageIdDAO,
                                    CassandraMessageDAOV3 messageDAOV3, CassandraIndexTableHandler indexTableHandler,
                                    ModSeqProvider modSeqProvider, BlobStore blobStore, CassandraConfiguration cassandraConfiguration, BatchSizes batchSizes, Clock clock) {

        this.mailboxMapper = mailboxMapper;
        this.mailboxDAO = mailboxDAO;
        this.imapUidDAO = imapUidDAO;
        this.messageIdDAO = messageIdDAO;
        this.messageDAOV3 = messageDAOV3;
        this.indexTableHandler = indexTableHandler;
        this.modSeqProvider = modSeqProvider;
        this.attachmentLoader = new AttachmentLoader(attachmentMapper);
        this.blobStore = blobStore;
        this.cassandraConfiguration = cassandraConfiguration;
        this.batchSizes = batchSizes;
        this.clock = clock;
    }

    @Override
    public List<MailboxMessage> find(Collection<MessageId> messageIds, FetchType fetchType) {
        return findReactive(messageIds, fetchType)
            .collectList()
            .block();
    }

    @Override
    public Flux<MailboxMessage> findReactive(Collection<MessageId> messageIds, FetchType fetchType) {
        int concurrency = batchSizes.forFetchType(fetchType);
        return Flux.fromIterable(messageIds)
            .flatMap(messageId -> imapUidDAO.retrieve((CassandraMessageId) messageId, Optional.empty(), chooseReadConsistency()),
                concurrency, concurrency)
            .flatMap(metadata -> toMailboxMessage(metadata, fetchType), concurrency, concurrency)
            .groupBy(MailboxMessage::getMailboxId)
            .flatMap(this::keepMessageIfMailboxExists, ReactorUtils.DEFAULT_CONCURRENCY);
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
    public Publisher<ComposedMessageIdWithMetaData> findMetadata(MessageId messageId) {
        return imapUidDAO.retrieve((CassandraMessageId) messageId, Optional.empty(), chooseReadConsistency())
            .map(CassandraMessageMetadata::getComposedMessageId);
    }

    private Flux<MailboxMessage> keepMessageIfMailboxExists(GroupedFlux<MailboxId, MailboxMessage> groupedFlux) {
        CassandraId cassandraId = (CassandraId) groupedFlux.key();
        return mailboxDAO.retrieveMailbox(cassandraId)
            .flatMapMany(any -> groupedFlux)
            .switchIfEmpty(groupedFlux.map(message -> {
                LOGGER.info("Mailbox {} have been deleted but message {} is still attached to it.",
                    cassandraId.serialize(),
                    message.getMessageId().serialize());
                return message;
            }).then(Mono.empty()));
    }

    @Override
    public List<MailboxId> findMailboxes(MessageId messageId) {
        return imapUidDAO.retrieve((CassandraMessageId) messageId, Optional.empty(), chooseReadConsistency())
            .map(CassandraMessageMetadata::getComposedMessageId)
            .map(ComposedMessageIdWithMetaData::getComposedMessageId)
            .map(ComposedMessageId::getMailboxId)
            .collectList()
            .block();
    }

    public JamesExecutionProfiles.ConsistencyChoice chooseReadConsistency() {
        if (cassandraConfiguration.isMessageReadStrongConsistency()) {
            return STRONG;
        } else {
            return WEAK;
        }
    }

    private JamesExecutionProfiles.ConsistencyChoice chooseReadConsistencyUponWrites() {
        if (cassandraConfiguration.isMessageWriteStrongConsistency()) {
            return STRONG;
        }
        return WEAK;
    }

    @Override
    public void save(MailboxMessage mailboxMessage) throws MailboxException {
        CassandraId mailboxId = (CassandraId) mailboxMessage.getMailboxId();
        mailboxMessage.setSaveDate(Date.from(clock.instant()));
        MailboxReactorUtils.block(mailboxMapper.findMailboxById(mailboxId)
            .switchIfEmpty(Mono.error(() -> new MailboxNotFoundException(mailboxId)))
            .then(messageDAOV3.save(mailboxMessage))
            .flatMap(headerAndBody -> saveMessageMetadata(mailboxMessage, mailboxId, headerAndBody.getT1())));
    }

    @Override
    public void copyInMailbox(MailboxMessage mailboxMessage, Mailbox mailbox) throws MailboxException {
        MailboxReactorUtils.block(copyInMailboxReactive(mailboxMessage, mailbox));
    }

    @Override
    public Mono<Void> copyInMailboxReactive(MailboxMessage mailboxMessage, Mailbox mailbox) {
        mailboxMessage.setSaveDate(Date.from(clock.instant()));
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();
        return insertMetadata(mailboxMessage, mailboxId, CassandraMessageMetadata.from(mailboxMessage)
            .withMailboxId(mailboxId));
    }

    private Mono<Void> saveMessageMetadata(MailboxMessage mailboxMessage, CassandraId mailboxId, BlobId headerBlobId) {
        CassandraMessageMetadata metadata = CassandraMessageMetadata.from(mailboxMessage, headerBlobId)
            .withMailboxId(mailboxId);

        return insertMetadata(mailboxMessage, mailboxId, metadata);
    }

    private Mono<Void> insertMetadata(MailboxMessage mailboxMessage, CassandraId mailboxId, CassandraMessageMetadata metadata) {
        return imapUidDAO.insert(metadata)
            .thenEmpty(Flux.merge(
                messageIdDAO.insert(metadata)
                    .retryWhen(Retry.backoff(MAX_RETRY, MIN_RETRY_BACKOFF).maxBackoff(MAX_RETRY_BACKOFF)),
                indexTableHandler.updateIndexOnAdd(mailboxMessage, mailboxId))
            .then());
    }

    @Override
    public void delete(MessageId messageId, Collection<MailboxId> mailboxIds) {
        deleteReactive(messageId, mailboxIds).block();
    }

    @Override
    public Mono<Void> deleteReactive(MessageId messageId, Collection<MailboxId> mailboxIds) {
        CassandraMessageId cassandraMessageId = (CassandraMessageId) messageId;
        return Flux.fromStream(mailboxIds.stream())
            .flatMap(mailboxId -> retrieveAndDeleteIndices(cassandraMessageId, Optional.of((CassandraId) mailboxId)),
                DEFAULT_CONCURRENCY)
            .then();
    }

    @Override
    public void delete(Multimap<MessageId, MailboxId> ids) {
        deleteReactive(ids)
            .block();
    }

    @Override
    public Mono<Void> deleteReactive(Multimap<MessageId, MailboxId> ids) {
        return Flux.fromIterable(ids.asMap()
            .entrySet())
            .flatMap(entry -> deleteReactive(entry.getKey(), entry.getValue()), cassandraConfiguration.getExpungeChunkSize(),
                DEFAULT_CONCURRENCY)
            .then();
    }

    private Mono<Void> retrieveAndDeleteIndices(CassandraMessageId messageId, Optional<CassandraId> mailboxId) {
        return imapUidDAO.retrieve(messageId, mailboxId, chooseReadConsistencyUponWrites())
            .map(CassandraMessageMetadata::getComposedMessageId)
            .flatMap(this::deleteIds, ReactorUtils.DEFAULT_CONCURRENCY)
            .then();
    }

    @Override
    public void delete(MessageId messageId) {
        CassandraMessageId cassandraMessageId = (CassandraMessageId) messageId;
        retrieveAndDeleteIndices(cassandraMessageId, Optional.empty())
            .block();
    }

    private Mono<Void> deleteIds(ComposedMessageIdWithMetaData metaData) {
        CassandraMessageId messageId = (CassandraMessageId) metaData.getComposedMessageId().getMessageId();
        CassandraId mailboxId = (CassandraId) metaData.getComposedMessageId().getMailboxId();
        return Flux.merge(
                imapUidDAO.delete(messageId, mailboxId),
                messageIdDAO.delete(mailboxId, metaData.getComposedMessageId().getUid()))
            .then(indexTableHandler.updateIndexOnDelete(metaData, mailboxId));
    }

    @Override
    public Mono<Multimap<MailboxId, UpdatedFlags>> setFlags(MessageId messageId, List<MailboxId> mailboxIds, Flags newState, MessageManager.FlagsUpdateMode updateMode) {
        return Flux.fromIterable(mailboxIds)
            .distinct()
            .map(CassandraId.class::cast)
            .concatMap(mailboxId -> flagsUpdateWithRetry(newState, updateMode, mailboxId, messageId))
            .flatMap(this::updateCounts, ReactorUtils.DEFAULT_CONCURRENCY)
            .collect(ImmutableListMultimap.toImmutableListMultimap(Pair::getLeft, Pair::getRight));
    }

    private Flux<Pair<MailboxId, UpdatedFlags>> flagsUpdateWithRetry(Flags newState, MessageManager.FlagsUpdateMode updateMode, MailboxId mailboxId, MessageId messageId) {
        return updateFlags(mailboxId, messageId, newState, updateMode)
            .retry(cassandraConfiguration.getFlagsUpdateMessageIdMaxRetry())
            .onErrorResume(MailboxDeleteDuringUpdateException.class, e -> {
                LOGGER.info("Mailbox {} was deleted during flag update", mailboxId);
                return Mono.empty();
            })
            .flux()
            .flatMapIterable(Function.identity())
            .map(pair -> buildUpdatedFlags(pair.getRight(), pair.getLeft()));
    }

    private Pair<MailboxId, UpdatedFlags> buildUpdatedFlags(ComposedMessageIdWithMetaData composedMessageIdWithMetaData, Flags oldFlags) {
        return Pair.of(composedMessageIdWithMetaData.getComposedMessageId().getMailboxId(),
                UpdatedFlags.builder()
                    .uid(composedMessageIdWithMetaData.getComposedMessageId().getUid())
                    .messageId(composedMessageIdWithMetaData.getComposedMessageId().getMessageId())
                    .modSeq(composedMessageIdWithMetaData.getModSeq())
                    .oldFlags(oldFlags)
                    .newFlags(composedMessageIdWithMetaData.getFlags())
                    .build());
    }

    private Mono<Pair<MailboxId, UpdatedFlags>> updateCounts(Pair<MailboxId, UpdatedFlags> pair) {
        CassandraId cassandraId = (CassandraId) pair.getLeft();
        return indexTableHandler.updateIndexOnFlagsUpdate(cassandraId, pair.getRight())
            .thenReturn(pair);
    }

    private Mono<List<Pair<Flags, ComposedMessageIdWithMetaData>>> updateFlags(MailboxId mailboxId, MessageId messageId, Flags newState, MessageManager.FlagsUpdateMode updateMode) {
        CassandraId cassandraId = (CassandraId) mailboxId;
        return imapUidDAO.retrieve((CassandraMessageId) messageId, Optional.of(cassandraId), chooseReadConsistencyUponWrites())
            .map(CassandraMessageMetadata::getComposedMessageId)
            .flatMap(oldComposedId -> updateFlags(newState, updateMode, cassandraId, oldComposedId), ReactorUtils.DEFAULT_CONCURRENCY)
            .switchIfEmpty(Mono.error(MailboxDeleteDuringUpdateException::new))
            .collectList();
    }

    private Mono<Pair<Flags, ComposedMessageIdWithMetaData>> updateFlags(Flags newState, MessageManager.FlagsUpdateMode updateMode, CassandraId cassandraId, ComposedMessageIdWithMetaData oldComposedId) {
        Flags newFlags = new FlagsUpdateCalculator(newState, updateMode).buildNewFlags(oldComposedId.getFlags());
        if (identicalFlags(oldComposedId, newFlags)) {
            return Mono.just(Pair.of(oldComposedId.getFlags(), oldComposedId));
        } else {
            return modSeqProvider.nextModSeqReactive(cassandraId)
                .map(modSeq -> new ComposedMessageIdWithMetaData(
                    oldComposedId.getComposedMessageId(),
                    newFlags,
                    modSeq,
                    oldComposedId.getThreadId()))
            .flatMap(newComposedId -> updateFlags(oldComposedId, newComposedId));
        }
    }

    private boolean identicalFlags(ComposedMessageIdWithMetaData oldComposedId, Flags newFlags) {
        return oldComposedId.getFlags().equals(newFlags);
    }

    private Mono<Pair<Flags, ComposedMessageIdWithMetaData>> updateFlags(ComposedMessageIdWithMetaData oldComposedId, ComposedMessageIdWithMetaData newComposedId) {
        ComposedMessageId composedMessageId = newComposedId.getComposedMessageId();
        ModSeq previousModseq = oldComposedId.getModSeq();
        UpdatedFlags updatedFlags = UpdatedFlags.builder()
            .messageId(composedMessageId.getMessageId())
            .modSeq(newComposedId.getModSeq())
            .oldFlags(oldComposedId.getFlags())
            .newFlags(newComposedId.getFlags())
            .uid(composedMessageId.getUid())
            .build();

        return imapUidDAO.updateMetadata(composedMessageId, updatedFlags, previousModseq)
            .filter(FunctionalUtils.identityPredicate())
            .flatMap(any -> messageIdDAO.updateMetadata(composedMessageId, updatedFlags)
                .thenReturn(Pair.of(oldComposedId.getFlags(), newComposedId)))
            .single();
    }
}
