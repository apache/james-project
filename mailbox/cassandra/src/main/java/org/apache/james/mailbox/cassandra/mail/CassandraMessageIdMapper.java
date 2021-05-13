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

import static org.apache.james.backends.cassandra.init.configuration.CassandraConsistenciesConfiguration.ConsistencyChoice.STRONG;
import static org.apache.james.backends.cassandra.init.configuration.CassandraConsistenciesConfiguration.ConsistencyChoice.WEAK;
import static org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import javax.mail.Flags;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.backends.cassandra.init.configuration.CassandraConsistenciesConfiguration.ConsistencyChoice;
import org.apache.james.mailbox.MessageManager;
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

import com.github.steveash.guavate.Guavate;
import com.google.common.collect.Multimap;

import reactor.core.publisher.Flux;
import reactor.core.publisher.GroupedFlux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

public class CassandraMessageIdMapper implements MessageIdMapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraMessageIdMapper.class);

    private static final int MAX_RETRY = 5;
    private static final Duration MIN_RETRY_BACKOFF = Duration.ofMillis(10);
    private static final Duration MAX_RETRY_BACKOFF = Duration.ofMillis(1000);

    private final MailboxMapper mailboxMapper;
    private final CassandraMailboxDAO mailboxDAO;
    private final CassandraMessageIdToImapUidDAO imapUidDAO;
    private final CassandraMessageIdDAO messageIdDAO;
    private final CassandraMessageDAO messageDAO;
    private final CassandraMessageDAOV3 messageDAOV3;
    private final CassandraIndexTableHandler indexTableHandler;
    private final ModSeqProvider modSeqProvider;
    private final AttachmentLoader attachmentLoader;
    private final CassandraConfiguration cassandraConfiguration;

    public CassandraMessageIdMapper(MailboxMapper mailboxMapper, CassandraMailboxDAO mailboxDAO, CassandraAttachmentMapper attachmentMapper,
                                    CassandraMessageIdToImapUidDAO imapUidDAO, CassandraMessageIdDAO messageIdDAO,
                                    CassandraMessageDAO messageDAO, CassandraMessageDAOV3 messageDAOV3, CassandraIndexTableHandler indexTableHandler,
                                    ModSeqProvider modSeqProvider, CassandraConfiguration cassandraConfiguration) {

        this.mailboxMapper = mailboxMapper;
        this.mailboxDAO = mailboxDAO;
        this.imapUidDAO = imapUidDAO;
        this.messageIdDAO = messageIdDAO;
        this.messageDAO = messageDAO;
        this.messageDAOV3 = messageDAOV3;
        this.indexTableHandler = indexTableHandler;
        this.modSeqProvider = modSeqProvider;
        this.attachmentLoader = new AttachmentLoader(attachmentMapper);
        this.cassandraConfiguration = cassandraConfiguration;
    }

    @Override
    public List<MailboxMessage> find(Collection<MessageId> messageIds, FetchType fetchType) {
        return findReactive(messageIds, fetchType)
            .collectList()
            .block();
    }

    @Override
    public Flux<MailboxMessage> findReactive(Collection<MessageId> messageIds, FetchType fetchType) {
        return Flux.fromIterable(messageIds)
            .flatMap(messageId -> imapUidDAO.retrieve((CassandraMessageId) messageId, Optional.empty(), chooseReadConsistency()),
                cassandraConfiguration.getMessageReadChunkSize())
            .flatMap(composedMessageId -> messageDAOV3.retrieveMessage(composedMessageId, fetchType)
                .switchIfEmpty(messageDAO.retrieveMessage(composedMessageId, fetchType))
                .map(messageRepresentation -> Pair.of(composedMessageId, messageRepresentation)), cassandraConfiguration.getMessageReadChunkSize())
            .flatMap(messageRepresentation -> attachmentLoader.addAttachmentToMessage(messageRepresentation, fetchType), cassandraConfiguration.getMessageReadChunkSize())
            .groupBy(MailboxMessage::getMailboxId)
            .flatMap(this::keepMessageIfMailboxExists, ReactorUtils.DEFAULT_CONCURRENCY);
    }

    @Override
    public Publisher<ComposedMessageIdWithMetaData> findMetadata(MessageId messageId) {
        return imapUidDAO.retrieve((CassandraMessageId) messageId, Optional.empty(), chooseReadConsistency());
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
            .map(ComposedMessageIdWithMetaData::getComposedMessageId)
            .map(ComposedMessageId::getMailboxId)
            .collectList()
            .block();
    }

    public ConsistencyChoice chooseReadConsistency() {
        if (cassandraConfiguration.isMessageReadStrongConsistency()) {
            return STRONG;
        } else {
            return WEAK;
        }
    }

    private ConsistencyChoice chooseReadConsistencyUponWrites() {
        if (cassandraConfiguration.isMessageWriteStrongConsistency()) {
            return STRONG;
        }
        return WEAK;
    }

    @Override
    public void save(MailboxMessage mailboxMessage) throws MailboxException {
        CassandraId mailboxId = (CassandraId) mailboxMessage.getMailboxId();
        MailboxReactorUtils.block(mailboxMapper.findMailboxById(mailboxId)
            .switchIfEmpty(Mono.error(() -> new MailboxNotFoundException(mailboxId)))
            .then(messageDAOV3.save(mailboxMessage))
            .thenEmpty(saveMessageMetadata(mailboxMessage, mailboxId)));
    }

    @Override
    public void copyInMailbox(MailboxMessage mailboxMessage, Mailbox mailbox) throws MailboxException {
        MailboxReactorUtils.block(copyInMailboxReactive(mailboxMessage, mailbox));
    }

    @Override
    public Mono<Void> copyInMailboxReactive(MailboxMessage mailboxMessage, Mailbox mailbox) {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();
        return saveMessageMetadata(mailboxMessage, mailboxId);
    }

    private Mono<Void> saveMessageMetadata(MailboxMessage mailboxMessage, CassandraId mailboxId) {
        ComposedMessageIdWithMetaData composedMessageIdWithMetaData = createMetadataFor(mailboxMessage);
        return imapUidDAO.insert(composedMessageIdWithMetaData)
            .thenEmpty(Flux.merge(
                messageIdDAO.insert(composedMessageIdWithMetaData)
                    .retryWhen(Retry.backoff(MAX_RETRY, MIN_RETRY_BACKOFF).maxBackoff(MAX_RETRY_BACKOFF)),
                indexTableHandler.updateIndexOnAdd(mailboxMessage, mailboxId))
            .then());
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


    public Mono<Void> deleteReactive(Multimap<MessageId, MailboxId> ids) {
        return Flux.fromIterable(ids.asMap()
            .entrySet())
            .publishOn(Schedulers.elastic())
            .flatMap(entry -> deleteReactive(entry.getKey(), entry.getValue()), cassandraConfiguration.getExpungeChunkSize(),
                DEFAULT_CONCURRENCY)
            .then();
    }

    private Mono<Void> retrieveAndDeleteIndices(CassandraMessageId messageId, Optional<CassandraId> mailboxId) {
        return imapUidDAO.retrieve(messageId, mailboxId, chooseReadConsistencyUponWrites())
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
    public Mono<Multimap<MailboxId, UpdatedFlags>> setFlags(MessageId messageId, List<MailboxId> mailboxIds, Flags newState, MessageManager.FlagsUpdateMode updateMode) throws MailboxException {
        return Flux.fromIterable(mailboxIds)
            .distinct()
            .map(mailboxId -> (CassandraId) mailboxId)
            .concatMap(mailboxId -> flagsUpdateWithRetry(newState, updateMode, mailboxId, messageId))
            .flatMap(this::updateCounts, ReactorUtils.DEFAULT_CONCURRENCY)
            .collect(Guavate.toImmutableListMultimap(Pair::getLeft, Pair::getRight));
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
                    modSeq))
            .flatMap(newComposedId -> updateFlags(oldComposedId, newComposedId));
        }
    }

    private boolean identicalFlags(ComposedMessageIdWithMetaData oldComposedId, Flags newFlags) {
        return oldComposedId.getFlags().equals(newFlags);
    }

    private Mono<Pair<Flags, ComposedMessageIdWithMetaData>> updateFlags(ComposedMessageIdWithMetaData oldComposedId, ComposedMessageIdWithMetaData newComposedId) {
        return imapUidDAO.updateMetadata(newComposedId, oldComposedId.getModSeq())
            .filter(FunctionalUtils.identityPredicate())
            .flatMap(any -> messageIdDAO.updateMetadata(newComposedId)
                .thenReturn(Pair.of(oldComposedId.getFlags(), newComposedId)))
            .single();
    }
}
