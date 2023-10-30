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

package org.apache.james.mailbox.postgres.mail;

import static org.apache.james.blob.api.BlobStore.StoragePolicy.LOW_COST;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.function.Function;

import jakarta.mail.Flags;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.backends.postgres.utils.PostgresUtils;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.postgres.PostgresMailboxId;
import org.apache.james.mailbox.postgres.PostgresMessageId;
import org.apache.james.mailbox.postgres.mail.dao.PostgresMailboxDAO;
import org.apache.james.mailbox.postgres.mail.dao.PostgresMailboxMessageDAO;
import org.apache.james.mailbox.postgres.mail.dao.PostgresMessageDAO;
import org.apache.james.mailbox.store.FlagsUpdateCalculator;
import org.apache.james.mailbox.store.MailboxReactorUtils;
import org.apache.james.mailbox.store.mail.MessageIdMapper;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.apache.james.util.ReactorUtils;
import org.jooq.Record;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.io.ByteSource;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresMessageIdMapper implements MessageIdMapper {
    private static final Function<MailboxMessage, ByteSource> MESSAGE_BODY_CONTENT_LOADER = (mailboxMessage) -> new ByteSource() {
        @Override
        public InputStream openStream() {
            try {
                return mailboxMessage.getBodyContent();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public long size() {
            return mailboxMessage.getBodyOctets();
        }
    };

    public static final int NUM_RETRIES = 5;
    public static final Logger LOGGER = LoggerFactory.getLogger(PostgresMessageIdMapper.class);

    private final PostgresMailboxDAO mailboxDAO;
    private final PostgresMessageDAO messageDAO;
    private final PostgresMailboxMessageDAO mailboxMessageDAO;
    private final PostgresModSeqProvider modSeqProvider;
    private final BlobStore blobStore;
    private final Clock clock;
    private final PostgresMessageRetriever messageRetriever;

    public PostgresMessageIdMapper(PostgresMailboxDAO mailboxDAO,
                                   PostgresMessageDAO messageDAO,
                                   PostgresMailboxMessageDAO mailboxMessageDAO,
                                   PostgresModSeqProvider modSeqProvider,
                                   PostgresAttachmentMapper attachmentMapper,
                                   BlobStore blobStore,
                                   BlobId.Factory blobIdFactory,
                                   Clock clock) {
        this.mailboxDAO = mailboxDAO;
        this.messageDAO = messageDAO;
        this.mailboxMessageDAO = mailboxMessageDAO;
        this.modSeqProvider = modSeqProvider;
        this.blobStore = blobStore;
        this.clock = clock;
        this.messageRetriever = new PostgresMessageRetriever(blobStore, blobIdFactory, attachmentMapper);
    }

    @Override
    public List<MailboxMessage> find(Collection<MessageId> messageIds, MessageMapper.FetchType fetchType) {
        return findReactive(messageIds, fetchType)
            .collectList()
            .block();
    }

    @Override
    public Publisher<ComposedMessageIdWithMetaData> findMetadata(MessageId messageId) {
        return mailboxMessageDAO.findMetadataByMessageId(PostgresMessageId.class.cast(messageId));
    }

    @Override
    public Flux<MailboxMessage> findReactive(Collection<MessageId> messageIds, MessageMapper.FetchType fetchType) {
        Flux<Pair<SimpleMailboxMessage.Builder, Record>> fetchMessagePublisher = mailboxMessageDAO.findMessagesByMessageIds(messageIds.stream().map(PostgresMessageId.class::cast).collect(ImmutableList.toImmutableList()), fetchType);
        return messageRetriever.get(fetchType, fetchMessagePublisher);
    }

    @Override
    public List<MailboxId> findMailboxes(MessageId messageId) {
        return mailboxMessageDAO.findMailboxes(PostgresMessageId.class.cast(messageId))
            .collect(ImmutableList.toImmutableList())
            .block();
    }

    @Override
    public void save(MailboxMessage mailboxMessage) throws MailboxException {
        PostgresMailboxId mailboxId = PostgresMailboxId.class.cast(mailboxMessage.getMailboxId());
        mailboxMessage.setSaveDate(Date.from(clock.instant()));
        MailboxReactorUtils.block(mailboxDAO.findMailboxById(mailboxId)
            .switchIfEmpty(Mono.error(() -> new MailboxNotFoundException(mailboxId)))
            .then(saveBodyContent(mailboxMessage))
            .flatMap(blobId -> messageDAO.insert(mailboxMessage, blobId.asString())
                .onErrorResume(PostgresUtils.UNIQUE_CONSTRAINT_VIOLATION_PREDICATE, e -> Mono.empty()))
            .then(mailboxMessageDAO.insert(mailboxMessage)));
    }

    @Override
    public void copyInMailbox(MailboxMessage mailboxMessage, Mailbox mailbox) throws MailboxException {
        MailboxReactorUtils.block(copyInMailboxReactive(mailboxMessage, mailbox));
    }

    @Override
    public Mono<Void> copyInMailboxReactive(MailboxMessage mailboxMessage, Mailbox mailbox) {
        mailboxMessage.setSaveDate(Date.from(clock.instant()));
        PostgresMailboxId mailboxId = (PostgresMailboxId) mailbox.getMailboxId();
        return mailboxMessageDAO.insert(mailboxMessage, mailboxId)
            .onErrorResume(PostgresUtils.UNIQUE_CONSTRAINT_VIOLATION_PREDICATE, e -> Mono.empty());
    }

    @Override
    public void delete(MessageId messageId) {
        mailboxMessageDAO.deleteByMessageId((PostgresMessageId) messageId).block();
    }

    @Override
    public void delete(MessageId messageId, Collection<MailboxId> mailboxIds) {
        mailboxMessageDAO.deleteByMessageIdAndMailboxIds((PostgresMessageId) messageId,
            mailboxIds.stream().map(PostgresMailboxId.class::cast).collect(ImmutableList.toImmutableList())).block();
    }

    @Override
    public Mono<Multimap<MailboxId, UpdatedFlags>> setFlags(MessageId messageId, List<MailboxId> mailboxIds, Flags newState, MessageManager.FlagsUpdateMode updateMode) {
        return Flux.fromIterable(mailboxIds)
            .distinct()
            .map(PostgresMailboxId.class::cast)
            .concatMap(mailboxId -> flagsUpdateWithRetry(newState, updateMode, mailboxId, messageId))
            .collect(ImmutableListMultimap.toImmutableListMultimap(Pair::getLeft, Pair::getRight));
    }

    private Flux<Pair<MailboxId, UpdatedFlags>> flagsUpdateWithRetry(Flags newState, MessageManager.FlagsUpdateMode updateMode, MailboxId mailboxId, MessageId messageId) {
        return updateFlags(mailboxId, messageId, newState, updateMode)
            .retry(NUM_RETRIES)
            .onErrorResume(MailboxDeleteDuringUpdateException.class, e -> {
                LOGGER.info("Mailbox {} was deleted during flag update", mailboxId);
                return Mono.empty();
            })
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

    private Mono<List<Pair<Flags, ComposedMessageIdWithMetaData>>> updateFlags(MailboxId mailboxId, MessageId messageId, Flags newState, MessageManager.FlagsUpdateMode updateMode) {
        PostgresMailboxId postgresMailboxId = (PostgresMailboxId) mailboxId;
        PostgresMessageId postgresMessageId = (PostgresMessageId) messageId;
        return mailboxMessageDAO.findMetadataByMessageId(postgresMessageId, postgresMailboxId)
            .flatMap(oldComposedId -> updateFlags(newState, updateMode, postgresMailboxId, oldComposedId), ReactorUtils.DEFAULT_CONCURRENCY)
            .switchIfEmpty(Mono.error(MailboxDeleteDuringUpdateException::new))
            .collectList();
    }

    private Mono<Pair<Flags, ComposedMessageIdWithMetaData>> updateFlags(Flags newState, MessageManager.FlagsUpdateMode updateMode, PostgresMailboxId mailboxId, ComposedMessageIdWithMetaData oldComposedId) {
        FlagsUpdateCalculator flagsUpdateCalculator = new FlagsUpdateCalculator(newState, updateMode);
        Flags newFlags = flagsUpdateCalculator.buildNewFlags(oldComposedId.getFlags());
        if (identicalFlags(oldComposedId, newFlags)) {
            return Mono.just(Pair.of(oldComposedId.getFlags(), oldComposedId));
        } else {
            return modSeqProvider.nextModSeqReactive(mailboxId)
                .flatMap(newModSeq -> updateFlags(mailboxId, flagsUpdateCalculator, newModSeq, oldComposedId.getComposedMessageId().getUid())
                    .map(flags -> Pair.of(oldComposedId.getFlags(), new ComposedMessageIdWithMetaData(
                        oldComposedId.getComposedMessageId(),
                        flags,
                        newModSeq,
                        oldComposedId.getThreadId()))));
        }
    }

    private Mono<Flags> updateFlags(PostgresMailboxId mailboxId, FlagsUpdateCalculator flagsUpdateCalculator, ModSeq newModSeq, MessageUid uid) {

        switch (flagsUpdateCalculator.getMode()) {
            case ADD:
                return mailboxMessageDAO.addFlags(mailboxId, uid, flagsUpdateCalculator.providedFlags(), newModSeq);
            case REMOVE:
                return mailboxMessageDAO.removeFlags(mailboxId, uid, flagsUpdateCalculator.providedFlags(), newModSeq);
            case REPLACE:
                return mailboxMessageDAO.replaceFlags(mailboxId, uid, flagsUpdateCalculator.providedFlags(), newModSeq);
            default:
                return Mono.error(() -> new RuntimeException("Unknown MessageRange type " + flagsUpdateCalculator.getMode()));
        }
    }

    private boolean identicalFlags(ComposedMessageIdWithMetaData oldComposedId, Flags newFlags) {
        return oldComposedId.getFlags().equals(newFlags);
    }

    private Mono<BlobId> saveBodyContent(MailboxMessage message) {
        return Mono.fromCallable(() -> MESSAGE_BODY_CONTENT_LOADER.apply(message))
            .flatMap(bodyByteSource -> Mono.from(blobStore.save(blobStore.getDefaultBucketName(), bodyByteSource, LOW_COST)));
    }
}
