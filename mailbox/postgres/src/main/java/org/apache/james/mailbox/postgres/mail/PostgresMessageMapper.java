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
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import javax.mail.Flags;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.mailbox.FlagsBuilder;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxCounters;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.postgres.PostgresMailboxId;
import org.apache.james.mailbox.postgres.mail.dao.PostgresMailboxMessageDAO;
import org.apache.james.mailbox.postgres.mail.dao.PostgresMessageDAO;
import org.apache.james.mailbox.store.FlagsUpdateCalculator;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;

import com.google.common.io.ByteSource;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresMessageMapper implements MessageMapper {

    private static final Function<MailboxMessage, ByteSource> MESSAGE_CONTENT_LOADER = (mailboxMessage) -> new ByteSource() {
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
    private final PostgresModSeqProvider modSeqProvider;
    private final PostgresUidProvider uidProvider;
    private final BlobStore blobStore;
    private final Clock clock;

    public PostgresMessageMapper(PostgresExecutor postgresExecutor,
                                 PostgresModSeqProvider modSeqProvider,
                                 PostgresUidProvider uidProvider, BlobStore blobStore, Clock clock) {
        this.messageDAO = new PostgresMessageDAO(postgresExecutor);
        this.mailboxMessageDAO = new PostgresMailboxMessageDAO(postgresExecutor);
        this.modSeqProvider = modSeqProvider;
        this.uidProvider = uidProvider;
        this.blobStore = blobStore;
        this.clock = clock;
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
    public Flux<MailboxMessage> findInMailboxReactive(Mailbox mailbox, MessageRange messageRange, FetchType type, int limit) {
        return Mono.just(messageRange)
            .flatMapMany(range -> {
                switch (messageRange.getType()) {
                    case ALL:
                        return mailboxMessageDAO.findMessagesByMailboxId((PostgresMailboxId) mailbox.getMailboxId(), limit);
                    case FROM:
                        return mailboxMessageDAO.findMessagesByMailboxIdAndAfterUID((PostgresMailboxId) mailbox.getMailboxId(), range.getUidFrom(), limit);
                    case ONE:
                        return mailboxMessageDAO.findMessageByMailboxIdAndUid((PostgresMailboxId) mailbox.getMailboxId(), range.getUidFrom())
                            .flatMapMany(Flux::just);
                    case RANGE:
                        return mailboxMessageDAO.findMessagesByMailboxIdAndBetweenUIDs((PostgresMailboxId) mailbox.getMailboxId(), range.getUidFrom(), range.getUidTo(), limit);
                    default:
                        throw new RuntimeException("Unknown MessageRange type " + range.getType());
                }
            });
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
        return mailboxMessageDAO.countTotalMessagesByMailboxId((PostgresMailboxId) mailbox.getMailboxId())
            .flatMap(totalMessage -> mailboxMessageDAO.countUnseenMessagesByMailboxId((PostgresMailboxId) mailbox.getMailboxId())
                .map(unseenMessage -> MailboxCounters.builder()
                    .mailboxId(mailbox.getMailboxId())
                    .count(totalMessage)
                    .unseen(unseenMessage)
                    .build()));
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
            .flatMap(mailboxMessage -> setNewMessageUid(mailboxMessage)
                .then(setNewMessageModSeq(mailboxMessage)))
            .then(mailboxMessageDAO.insert(message))
            .then(saveBodyContent(message)
                .flatMap(blobId -> messageDAO.insert(message, blobId.asString())))
            .thenReturn(message.metaData());
    }

    private Mono<BlobId> saveBodyContent(MailboxMessage message) {
        return Mono.fromCallable(() -> MESSAGE_CONTENT_LOADER.apply(message))
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
            .flatMap(currentMetaData -> updateFlags(currentMetaData, flagsUpdateCalculator));
    }

    private Mono<UpdatedFlags> updateFlags(ComposedMessageIdWithMetaData currentMetaData, FlagsUpdateCalculator flagsUpdateCalculator) {
        Flags oldFlags = currentMetaData.getFlags();
        Flags newFlags = flagsUpdateCalculator.buildNewFlags(oldFlags);

        if (oldFlags.equals(newFlags)) {
            return Mono.empty();
        }

        ComposedMessageId composedMessageId = currentMetaData.getComposedMessageId();

        UpdatedFlags updatedFlags = UpdatedFlags.builder()
            .messageId(composedMessageId.getMessageId())
            .modSeq(currentMetaData.getModSeq())
            .oldFlags(oldFlags)
            .newFlags(newFlags)
            .uid(composedMessageId.getUid())
            .build();

        return mailboxMessageDAO.updateFlag((PostgresMailboxId) composedMessageId.getMailboxId(), composedMessageId.getUid(), updatedFlags)
            .then(Mono.just(updatedFlags));

    }

    @Override
    public Mono<List<UpdatedFlags>> resetRecentReactive(Mailbox mailbox) {
        return mailboxMessageDAO.findAllRecentMessageMetadata((PostgresMailboxId) mailbox.getMailboxId())
            .flatMap(mailboxMessage -> updateFlags(mailboxMessage,
                new FlagsUpdateCalculator(new Flags(Flags.Flag.RECENT), MessageManager.FlagsUpdateMode.REMOVE)))
            .collectList();
    }

    @Override
    public MessageMetaData copy(Mailbox mailbox, MailboxMessage original) throws MailboxException {
        return copyReactive(mailbox, original).block();
    }

    private Mono<Void> setNewMessageUid(MailboxMessage mailboxMessage) {
        return uidProvider.nextUidReactive(mailboxMessage.getMailboxId())
            .doOnNext(mailboxMessage::setUid)
            .then();
    }

    private Mono<Void> setNewMessageModSeq(MailboxMessage mailboxMessage) {
        return modSeqProvider.nextModSeqReactive(mailboxMessage.getMailboxId())
            .doOnNext(mailboxMessage::setModSeq)
            .then();
    }

    @Override
    public Mono<MessageMetaData> copyReactive(Mailbox mailbox, MailboxMessage original) {
        return Mono.fromCallable(() -> {
                original.setFlags(new FlagsBuilder().add(original.createFlags()).add(Flags.Flag.RECENT).build());
                original.setSaveDate(Date.from(clock.instant()));
                return original;
            })
            .flatMap(mailboxMessage -> setNewMessageUid(mailboxMessage)
                .then(setNewMessageModSeq(mailboxMessage))
                .then(mailboxMessageDAO.insert(mailboxMessage))
                .thenReturn(mailboxMessage.metaData()));
    }

    @Override
    public MessageMetaData move(Mailbox mailbox, MailboxMessage original) {
        return moveReactive(mailbox, original).block();
    }


    @Override
    public Mono<MessageMetaData> moveReactive(Mailbox mailbox, MailboxMessage original) {
        return copyReactive(mailbox, original)
            .then(mailboxMessageDAO.deleteByMailboxIdAndMessageUid((PostgresMailboxId) original.getMailboxId(), original.getUid()))
            .then(Mono.just(original.metaData()));
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
        return mailboxMessageDAO.listDistinctUserFlags((PostgresMailboxId) mailbox.getMailboxId());
    }

    @Override
    public Flux<MessageUid> listAllMessageUids(Mailbox mailbox) {
        return mailboxMessageDAO.listAllMessageUid((PostgresMailboxId) mailbox.getMailboxId());
    }

}
