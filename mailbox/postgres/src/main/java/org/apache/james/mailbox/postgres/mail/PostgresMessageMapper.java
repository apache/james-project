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
import static org.apache.james.blob.api.BlobStore.StoragePolicy.SIZE_BASED;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageTable.BODY_BLOB_ID;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageTable.HEADER_CONTENT;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import javax.mail.Flags;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.backends.postgres.utils.PostgresUtils;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.mailbox.ApplicableFlagBuilder;
import org.apache.james.mailbox.FlagsBuilder;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.Content;
import org.apache.james.mailbox.model.HeaderAndBodyByteContent;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxCounters;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.postgres.PostgresMailboxId;
import org.apache.james.mailbox.postgres.mail.dao.PostgresMailboxDAO;
import org.apache.james.mailbox.postgres.mail.dao.PostgresMailboxMessageDAO;
import org.apache.james.mailbox.postgres.mail.dao.PostgresMessageDAO;
import org.apache.james.mailbox.store.FlagsUpdateCalculator;
import org.apache.james.mailbox.store.MailboxReactorUtils;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.apache.james.util.ReactorUtils;
import org.apache.james.util.streams.Limit;
import org.jooq.Record;

import com.google.common.io.ByteSource;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresMessageMapper implements MessageMapper {

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


    private final PostgresMessageDAO messageDAO;
    private final PostgresMailboxMessageDAO mailboxMessageDAO;
    private final PostgresMailboxDAO mailboxDAO;
    private final PostgresModSeqProvider modSeqProvider;
    private final PostgresUidProvider uidProvider;
    private final BlobStore blobStore;
    private final Clock clock;
    private final BlobId.Factory blobIdFactory;

    public PostgresMessageMapper(PostgresExecutor postgresExecutor,
                                 PostgresModSeqProvider modSeqProvider,
                                 PostgresUidProvider uidProvider,
                                 BlobStore blobStore,
                                 Clock clock,
                                 BlobId.Factory blobIdFactory) {
        this.messageDAO = new PostgresMessageDAO(postgresExecutor, blobIdFactory);
        this.mailboxMessageDAO = new PostgresMailboxMessageDAO(postgresExecutor);
        this.mailboxDAO = new PostgresMailboxDAO(postgresExecutor);
        this.modSeqProvider = modSeqProvider;
        this.uidProvider = uidProvider;
        this.blobStore = blobStore;
        this.clock = clock;
        this.blobIdFactory = blobIdFactory;
    }


    @Override
    public Iterator<MailboxMessage> findInMailbox(Mailbox mailbox, MessageRange set, FetchType type, int limit) {
        return findInMailboxReactive(mailbox, set, type, limit)
            .toIterable()
            .iterator();
    }

    @Override
    public Flux<ComposedMessageIdWithMetaData> listMessagesMetadata(Mailbox mailbox, MessageRange set) {
        return mailboxMessageDAO.findMessagesMetadata((PostgresMailboxId) mailbox.getMailboxId(), set);
    }

    @Override
    public Flux<MailboxMessage> findInMailboxReactive(Mailbox mailbox, MessageRange messageRange, FetchType fetchType, int limitAsInt) {
        Flux<Pair<SimpleMailboxMessage.Builder, Record>> fetchMessageWithoutFullContentPublisher = fetchMessageWithoutFullContent(mailbox, messageRange, fetchType, limitAsInt);
        if (fetchType == FetchType.FULL) {
            return fetchMessageWithoutFullContentPublisher
                .flatMap(messageBuilderAndRecord -> {
                    SimpleMailboxMessage.Builder messageBuilder = messageBuilderAndRecord.getLeft();
                    return retrieveFullContent(messageBuilderAndRecord.getRight())
                        .map(headerAndBodyContent -> messageBuilder.content(headerAndBodyContent).build());
                }, ReactorUtils.DEFAULT_CONCURRENCY)
                .sort(Comparator.comparing(MailboxMessage::getUid))
                .map(message -> message);
        } else {
            return fetchMessageWithoutFullContentPublisher
                .map(messageBuilderAndBlobId -> messageBuilderAndBlobId.getLeft().build());
        }
    }

    private Flux<Pair<SimpleMailboxMessage.Builder, Record>> fetchMessageWithoutFullContent(Mailbox mailbox, MessageRange messageRange, FetchType fetchType, int limitAsInt) {
        return Mono.just(messageRange)
            .flatMapMany(range -> {
                Limit limit = Limit.from(limitAsInt);
                switch (messageRange.getType()) {
                    case ALL:
                        return mailboxMessageDAO.findMessagesByMailboxId((PostgresMailboxId) mailbox.getMailboxId(), limit, fetchType);
                    case FROM:
                        return mailboxMessageDAO.findMessagesByMailboxIdAndAfterUID((PostgresMailboxId) mailbox.getMailboxId(), range.getUidFrom(), limit, fetchType);
                    case ONE:
                        return mailboxMessageDAO.findMessageByMailboxIdAndUid((PostgresMailboxId) mailbox.getMailboxId(), range.getUidFrom(), fetchType)
                            .flatMapMany(Flux::just);
                    case RANGE:
                        return mailboxMessageDAO.findMessagesByMailboxIdAndBetweenUIDs((PostgresMailboxId) mailbox.getMailboxId(), range.getUidFrom(), range.getUidTo(), limit, fetchType);
                    default:
                        throw new RuntimeException("Unknown MessageRange range " + range.getType());
                }
            });
    }

    private Mono<Content> retrieveFullContent(Record messageRecord) {
        byte[] headerBytes = messageRecord.get(HEADER_CONTENT);
        return Mono.from(blobStore.readBytes(blobStore.getDefaultBucketName(),
                blobIdFactory.from(messageRecord.get(BODY_BLOB_ID)),
                SIZE_BASED))
            .map(bodyBytes -> new HeaderAndBodyByteContent(headerBytes, bodyBytes));
    }

    @Override
    public List<MessageUid> retrieveMessagesMarkedForDeletion(Mailbox mailbox, MessageRange messageRange) {
        return retrieveMessagesMarkedForDeletionReactive(mailbox, messageRange)
            .collectList()
            .block();
    }

    @Override
    public Flux<MessageUid> retrieveMessagesMarkedForDeletionReactive(Mailbox mailbox, MessageRange messageRange) {
        return Mono.just(messageRange)
            .flatMapMany(range -> {
                switch (messageRange.getType()) {
                    case ALL:
                        return mailboxMessageDAO.findDeletedMessagesByMailboxId((PostgresMailboxId) mailbox.getMailboxId());
                    case FROM:
                        return mailboxMessageDAO.findDeletedMessagesByMailboxIdAndAfterUID((PostgresMailboxId) mailbox.getMailboxId(), range.getUidFrom());
                    case ONE:
                        return mailboxMessageDAO.findDeletedMessageByMailboxIdAndUid((PostgresMailboxId) mailbox.getMailboxId(), range.getUidFrom())
                            .flatMapMany(Flux::just);
                    case RANGE:
                        return mailboxMessageDAO.findDeletedMessagesByMailboxIdAndBetweenUIDs((PostgresMailboxId) mailbox.getMailboxId(), range.getUidFrom(), range.getUidTo());
                    default:
                        throw new RuntimeException("Unknown MessageRange type " + range.getType());
                }
            });
    }

    @Override
    public long countMessagesInMailbox(Mailbox mailbox) {
        return mailboxMessageDAO.countTotalMessagesByMailboxId((PostgresMailboxId) mailbox.getMailboxId())
            .block();
    }

    @Override
    public MailboxCounters getMailboxCounters(Mailbox mailbox) {
        return getMailboxCountersReactive(mailbox).block();
    }

    @Override
    public Mono<MailboxCounters> getMailboxCountersReactive(Mailbox mailbox) {
        return mailboxMessageDAO.countTotalAndUnseenMessagesByMailboxId((PostgresMailboxId) mailbox.getMailboxId())
            .map(pair -> MailboxCounters.builder()
                .mailboxId(mailbox.getMailboxId())
                .count(pair.getLeft())
                .unseen(pair.getRight())
                .build());
    }

    @Override
    public void delete(Mailbox mailbox, MailboxMessage message) throws MailboxException {
        deleteMessages(mailbox, List.of(message.getUid()));
    }

    @Override
    public Map<MessageUid, MessageMetaData> deleteMessages(Mailbox mailbox, List<MessageUid> uids) {
        return deleteMessagesReactive(mailbox, uids).block();
    }

    @Override
    public Mono<Map<MessageUid, MessageMetaData>> deleteMessagesReactive(Mailbox mailbox, List<MessageUid> uids) {
        return mailboxMessageDAO.findMessagesByMailboxIdAndUIDs((PostgresMailboxId) mailbox.getMailboxId(), uids)
            .map(SimpleMailboxMessage.Builder::build)
            .collectMap(MailboxMessage::getUid, MailboxMessage::metaData)
            .flatMap(map -> mailboxMessageDAO.deleteByMailboxIdAndMessageUids((PostgresMailboxId) mailbox.getMailboxId(), uids)
                .then(Mono.just(map)));
    }

    @Override
    public MessageUid findFirstUnseenMessageUid(Mailbox mailbox) {
        return mailboxMessageDAO.findFirstUnseenMessageUid((PostgresMailboxId) mailbox.getMailboxId()).block();
    }

    @Override
    public Mono<Optional<MessageUid>> findFirstUnseenMessageUidReactive(Mailbox mailbox) {
        return mailboxMessageDAO.findFirstUnseenMessageUid((PostgresMailboxId) mailbox.getMailboxId())
            .map(Optional::of)
            .switchIfEmpty(Mono.just(Optional.empty()));
    }

    @Override
    public List<MessageUid> findRecentMessageUidsInMailbox(Mailbox mailbox) {
        return findRecentMessageUidsInMailboxReactive(mailbox).block();
    }

    @Override
    public Mono<List<MessageUid>> findRecentMessageUidsInMailboxReactive(Mailbox mailbox) {
        return mailboxMessageDAO.findAllRecentMessageUid((PostgresMailboxId) mailbox.getMailboxId())
            .collectList();
    }

    @Override
    public MessageMetaData add(Mailbox mailbox, MailboxMessage message) throws MailboxException {
        return addReactive(mailbox, message).block();
    }

    @Override
    public Mono<MessageMetaData> addReactive(Mailbox mailbox, MailboxMessage message) {
        return Mono.fromCallable(() -> {
                message.setSaveDate(Date.from(clock.instant()));
                return message;
            })
            .flatMap(this::setNewUidAndModSeq)
            .then(saveBodyContent(message)
                .flatMap(bodyBlobId -> messageDAO.insert(message, bodyBlobId.asString())
                    .onErrorResume(PostgresUtils.UNIQUE_CONSTRAINT_VIOLATION_PREDICATE, e -> Mono.empty())))
            .then(Mono.defer(() -> mailboxMessageDAO.insert(message)))
            .then(Mono.fromCallable(message::metaData));
    }

    private Mono<BlobId> saveBodyContent(MailboxMessage message) {
        return Mono.fromCallable(() -> MESSAGE_BODY_CONTENT_LOADER.apply(message))
            .flatMap(bodyByteSource -> Mono.from(blobStore.save(blobStore.getDefaultBucketName(), bodyByteSource, LOW_COST)));
    }

    @Override
    public Iterator<UpdatedFlags> updateFlags(Mailbox mailbox, FlagsUpdateCalculator flagsUpdateCalculator, MessageRange range) {
        return updateFlagsPublisher(mailbox, flagsUpdateCalculator, range)
            .toIterable()
            .iterator();
    }

    @Override
    public Mono<List<UpdatedFlags>> updateFlagsReactive(Mailbox mailbox, FlagsUpdateCalculator flagsUpdateCalculator, MessageRange range) {
        return updateFlagsPublisher(mailbox, flagsUpdateCalculator, range)
            .collectList();
    }

    private Flux<UpdatedFlags> updateFlagsPublisher(Mailbox mailbox, FlagsUpdateCalculator flagsUpdateCalculator, MessageRange range) {
        return mailboxMessageDAO.findMessagesMetadata((PostgresMailboxId) mailbox.getMailboxId(), range)
            .collectList()
            .flatMapMany(listMessagesMetadata -> updatedFlags(listMessagesMetadata, mailbox, flagsUpdateCalculator));
    }

    private Flux<UpdatedFlags> updatedFlags(List<ComposedMessageIdWithMetaData> listMessagesMetaData,
                                            Mailbox mailbox,
                                            FlagsUpdateCalculator flagsUpdateCalculator) {
        return modSeqProvider.nextModSeqReactive(mailbox.getMailboxId())
            .flatMapMany(newModSeq -> Flux.fromIterable(listMessagesMetaData)
                .flatMap(messageMetaData -> updateFlags(messageMetaData, flagsUpdateCalculator, newModSeq)));
    }

    private Mono<UpdatedFlags> updateFlags(ComposedMessageIdWithMetaData currentMetaData,
                                           FlagsUpdateCalculator flagsUpdateCalculator,
                                           ModSeq newModSeq) {
        Flags oldFlags = currentMetaData.getFlags();
        ComposedMessageId composedMessageId = currentMetaData.getComposedMessageId();

        if (oldFlags.equals(flagsUpdateCalculator.buildNewFlags(oldFlags))) {
            return Mono.just(UpdatedFlags.builder()
                .messageId(composedMessageId.getMessageId())
                .oldFlags(oldFlags)
                .newFlags(oldFlags)
                .uid(composedMessageId.getUid())
                .modSeq(currentMetaData.getModSeq())
                .build());
        } else {
            return Mono.just(flagsUpdateCalculator.getMode())
                .flatMap(mode -> {
                    switch (mode) {
                        case ADD:
                            return mailboxMessageDAO.addFlags((PostgresMailboxId) composedMessageId.getMailboxId(), composedMessageId.getUid(), flagsUpdateCalculator.providedFlags(), newModSeq);
                        case REMOVE:
                            return mailboxMessageDAO.removeFlags((PostgresMailboxId) composedMessageId.getMailboxId(), composedMessageId.getUid(), flagsUpdateCalculator.providedFlags(), newModSeq);
                        case REPLACE:
                            return mailboxMessageDAO.replaceFlags((PostgresMailboxId) composedMessageId.getMailboxId(), composedMessageId.getUid(), flagsUpdateCalculator.providedFlags(), newModSeq);
                        default:
                            return Mono.error(() -> new RuntimeException("Unknown MessageRange type " + mode));
                    }
                }).map(updatedFlags -> UpdatedFlags.builder()
                    .messageId(composedMessageId.getMessageId())
                    .oldFlags(oldFlags)
                    .newFlags(updatedFlags)
                    .uid(composedMessageId.getUid())
                    .modSeq(newModSeq)
                    .build());
        }
    }

    @Override
    public List<UpdatedFlags> resetRecent(Mailbox mailbox) {
        return resetRecentReactive(mailbox).block();
    }

    @Override
    public Mono<List<UpdatedFlags>> resetRecentReactive(Mailbox mailbox) {
        return mailboxMessageDAO.findAllRecentMessageMetadata((PostgresMailboxId) mailbox.getMailboxId())
            .collectList()
            .flatMapMany(mailboxMessageList -> resetRecentFlag((PostgresMailboxId) mailbox.getMailboxId(), mailboxMessageList))
            .collectList();
    }

    private Flux<UpdatedFlags> resetRecentFlag(PostgresMailboxId mailboxId, List<ComposedMessageIdWithMetaData> messageIdWithMetaDataList) {
        return Flux.fromIterable(messageIdWithMetaDataList)
            .collectMap(m -> m.getComposedMessageId().getUid(), Function.identity())
            .flatMapMany(uidMapping -> modSeqProvider.nextModSeqReactive(mailboxId)
                .flatMapMany(newModSeq -> mailboxMessageDAO.resetRecentFlag(mailboxId, List.copyOf(uidMapping.keySet()), newModSeq))
                .map(newMetaData -> UpdatedFlags.builder()
                    .messageId(newMetaData.getMessageId())
                    .modSeq(newMetaData.getModSeq())
                    .oldFlags(uidMapping.get(newMetaData.getUid()).getFlags())
                    .newFlags(newMetaData.getFlags())
                    .uid(newMetaData.getUid())
                    .build()));
    }

    @Override
    public MessageMetaData copy(Mailbox mailbox, MailboxMessage original) throws MailboxException {
        return copyReactive(mailbox, original).block();
    }

    private Mono<Void> setNewUidAndModSeq(MailboxMessage mailboxMessage) {
        return mailboxDAO.incrementAndGetLastUidAndModSeq(mailboxMessage.getMailboxId())
            .defaultIfEmpty(Pair.of(MessageUid.MIN_VALUE, ModSeq.first()))
            .map(pair -> {
                mailboxMessage.setUid(pair.getLeft());
                mailboxMessage.setModSeq(pair.getRight());
                return pair;
            }).then();
    }


    @Override
    public Mono<MessageMetaData> copyReactive(Mailbox mailbox, MailboxMessage original) {
        return Mono.fromCallable(() -> {
                MailboxMessage copiedMessage = original.copy(mailbox);
                copiedMessage.setFlags(new FlagsBuilder().add(original.createFlags()).add(Flags.Flag.RECENT).build());
                copiedMessage.setSaveDate(Date.from(clock.instant()));
                return copiedMessage;
            })
            .flatMap(copiedMessage -> setNewUidAndModSeq(copiedMessage)
                .then(Mono.defer(() -> mailboxMessageDAO.insert(copiedMessage))
                    .thenReturn(copiedMessage))
                .map(MailboxMessage::metaData));
    }


    @Override
    public MessageMetaData move(Mailbox mailbox, MailboxMessage original) {
        return moveReactive(mailbox, original).block();
    }

    @Override
    public List<MessageMetaData> move(Mailbox mailbox, List<MailboxMessage> original) throws MailboxException {
        return MailboxReactorUtils.block(moveReactive(mailbox, original));
    }


    @Override
    public Mono<MessageMetaData> moveReactive(Mailbox mailbox, MailboxMessage original) {
        return copyReactive(mailbox, original)
            .flatMap(copiedResult -> mailboxMessageDAO.deleteByMailboxIdAndMessageUid((PostgresMailboxId) original.getMailboxId(), original.getUid())
                .thenReturn(copiedResult));
    }

    @Override
    public Optional<MessageUid> getLastUid(Mailbox mailbox) {
        return uidProvider.lastUid(mailbox);
    }

    @Override
    public Mono<Optional<MessageUid>> getLastUidReactive(Mailbox mailbox) {
        return uidProvider.lastUidReactive(mailbox);
    }

    @Override
    public ModSeq getHighestModSeq(Mailbox mailbox) {
        return modSeqProvider.highestModSeq(mailbox);
    }

    @Override
    public Mono<ModSeq> getHighestModSeqReactive(Mailbox mailbox) {
        return modSeqProvider.highestModSeqReactive(mailbox);
    }

    @Override
    public Flags getApplicableFlag(Mailbox mailbox) {
        return getApplicableFlagReactive(mailbox).block();
    }

    @Override
    public Mono<Flags> getApplicableFlagReactive(Mailbox mailbox) {
        return mailboxMessageDAO.listDistinctUserFlags((PostgresMailboxId) mailbox.getMailboxId())
            .map(flags -> ApplicableFlagBuilder.builder().add(flags).build());
    }

    @Override
    public Flux<MessageUid> listAllMessageUids(Mailbox mailbox) {
        return mailboxMessageDAO.listAllMessageUid((PostgresMailboxId) mailbox.getMailboxId());
    }

}
