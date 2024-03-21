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

package org.apache.james.pop3server.mailbox.task;

import java.time.Duration;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.inject.Inject;

import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageDAOV3;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdToImapUidDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageMetadata;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.mail.MessageMapper.FetchType;
import org.apache.james.pop3server.mailbox.Pop3MetadataStore;
import org.apache.james.task.Task;
import org.apache.james.util.ReactorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class MetaDataFixInconsistenciesService {
    private enum Presence {
        PRESENT,
        ABSENT
    }

    interface Inconsistency {
        Mono<Task.Result> fix(Context context, CassandraMessageIdToImapUidDAO imapUidDAO, Pop3MetadataStore pop3MetadataStore);

        Mono<Inconsistency> confirm(CassandraMessageIdToImapUidDAO imapUidDAO, Pop3MetadataStore pop3MetadataStore);
    }

    static final Inconsistency NO_INCONSISTENCY = new Inconsistency() {
        @Override
        public Mono<Task.Result> fix(Context context, CassandraMessageIdToImapUidDAO imapUidDAO, Pop3MetadataStore pop3MetadataStore) {
            return Mono.just(Task.Result.COMPLETED);
        }

        @Override
        public Mono<Inconsistency> confirm(CassandraMessageIdToImapUidDAO imapUidDAO, Pop3MetadataStore pop3MetadataStore) {
            return Mono.just(this);
        }
    };

    private static class StalePOP3EntryConsistency implements Inconsistency {
        private final MailboxId mailboxId;
        private final MessageId messageId;

        private StalePOP3EntryConsistency(MailboxId mailboxId, MessageId messageId) {
            this.mailboxId = mailboxId;
            this.messageId = messageId;
        }

        @Override
        public Mono<Task.Result> fix(Context context,
                                     CassandraMessageIdToImapUidDAO imapUidDAO,
                                     Pop3MetadataStore pop3MetadataStore) {
            return Mono.from(pop3MetadataStore.remove(mailboxId, messageId))
                .doOnSuccess(any -> notifySuccess(context))
                .thenReturn(Task.Result.COMPLETED)
                .onErrorResume(error -> {
                    notifyFailure(context, error);
                    return Mono.just(Task.Result.PARTIAL);
                });
        }

        @Override
        public Mono<Inconsistency> confirm(CassandraMessageIdToImapUidDAO imapUidDAO, Pop3MetadataStore pop3MetadataStore) {
            Mono<Presence> imapView = imapUidDAO.retrieve((CassandraMessageId) messageId, Optional.of((CassandraId) mailboxId))
                .next()
                .map(any -> Presence.PRESENT)
                .switchIfEmpty(Mono.just(Presence.ABSENT));
            Mono<Presence> pop3View = Mono.from(pop3MetadataStore.retrieve(mailboxId, messageId))
                .map(any -> Presence.PRESENT)
                .switchIfEmpty(Mono.just(Presence.ABSENT));

            return imapView.zipWith(pop3View)
                .map(t2 -> {
                    if (t2.getT1() == Presence.ABSENT && t2.getT2() == Presence.PRESENT) {
                        return this;
                    }
                    return NO_INCONSISTENCY;
                });
        }

        private void notifyFailure(Context context, Throwable e) {
            context.addErrors(MessageInconsistenciesEntry.builder()
                .mailboxId(mailboxId.serialize())
                .messageId(messageId.serialize()));
            LOGGER.error("Failed to fix inconsistency for stale POP3 entry: {}", messageId, e);
        }

        private void notifySuccess(Context context) {
            context.incrementStalePOP3Entries();
            context.addFixedInconsistency(MessageInconsistenciesEntry.builder()
                .mailboxId(mailboxId.serialize())
                .messageId(messageId.serialize()));
            LOGGER.info("Inconsistency fixed for stale POP3 entry: {}", messageId);
        }
    }

    private static class MissingPOP3EntryInconsistency implements Inconsistency {
        private final MailboxId mailboxId;
        private final CassandraMessageId messageId;
        private final CassandraMessageDAOV3 cassandraMessageDAOV3;

        private MissingPOP3EntryInconsistency(MailboxId mailboxId,
                                              CassandraMessageId messageId,
                                              CassandraMessageDAOV3 cassandraMessageDAOV3) {
            this.mailboxId = mailboxId;
            this.messageId = messageId;
            this.cassandraMessageDAOV3 = cassandraMessageDAOV3;
        }

        @Override
        public Mono<Task.Result> fix(Context context,
                                     CassandraMessageIdToImapUidDAO imapUidDAO,
                                     Pop3MetadataStore pop3MetadataStore) {
            return buildStatMetadata()
                .flatMap(statMetadata -> Mono.from(pop3MetadataStore.add(mailboxId, statMetadata)))
                .doOnSuccess(any -> notifySuccess(context))
                .thenReturn(Task.Result.COMPLETED)
                .onErrorResume(error -> {
                    notifyFailure(context, error);
                    return Mono.just(Task.Result.PARTIAL);
                });
        }

        @Override
        public Mono<Inconsistency> confirm(CassandraMessageIdToImapUidDAO imapUidDAO, Pop3MetadataStore pop3MetadataStore) {
            Mono<Presence> imapView = imapUidDAO.retrieve(messageId, Optional.of((CassandraId) mailboxId))
                .next()
                .map(any -> Presence.PRESENT)
                .switchIfEmpty(Mono.just(Presence.ABSENT));
            Mono<Presence> pop3View = Mono.from(pop3MetadataStore.retrieve(mailboxId, messageId))
                .map(any -> Presence.PRESENT)
                .switchIfEmpty(Mono.just(Presence.ABSENT));

            return imapView.zipWith(pop3View)
                .map(t2 -> {
                    if (t2.getT1() == Presence.PRESENT && t2.getT2() == Presence.ABSENT) {
                        return this;
                    }
                    return NO_INCONSISTENCY;
                });
        }

        private Mono<Pop3MetadataStore.StatMetadata> buildStatMetadata() {
            return cassandraMessageDAOV3.retrieveMessage(messageId, FetchType.METADATA)
                .switchIfEmpty(Mono.error(new MailboxException("Message not found: " + messageId)))
                .map(messageRepresentation -> new Pop3MetadataStore.StatMetadata(messageId, messageRepresentation.getSize()));
        }


        private void notifyFailure(Context context, Throwable e) {
            context.addErrors(MessageInconsistenciesEntry.builder()
                .mailboxId(mailboxId.serialize())
                .messageId(messageId.serialize()));
            LOGGER.error("Failed to fix inconsistency for missing POP3 entry: {}", messageId, e);
        }

        private void notifySuccess(Context context) {
            context.incrementMissingPOP3Entries();
            context.addFixedInconsistency(MessageInconsistenciesEntry.builder()
                .mailboxId(mailboxId.serialize())
                .messageId(messageId.serialize()));
            LOGGER.info("Inconsistency fixed for missing POP3 entry: {}", messageId);
        }
    }

    private static class FailToDetectInconsistency implements Inconsistency {
        private final MailboxId mailboxId;
        private final MessageId messageId;

        private FailToDetectInconsistency(MailboxId mailboxId, MessageId messageId) {
            this.mailboxId = mailboxId;
            this.messageId = messageId;
        }

        @Override
        public Mono<Task.Result> fix(Context context,
                                     CassandraMessageIdToImapUidDAO imapUidDAO,
                                     Pop3MetadataStore pop3MetadataStore) {
            context.addErrors(MessageInconsistenciesEntry.builder()
                .mailboxId(mailboxId.serialize())
                .messageId(messageId.serialize()));
            LOGGER.error("Failed to detect inconsistency: {}", messageId);
            return Mono.just(Task.Result.PARTIAL);
        }

        @Override
        public Mono<Inconsistency> confirm(CassandraMessageIdToImapUidDAO imapUidDAO, Pop3MetadataStore pop3MetadataStore) {
            return Mono.just(this);
        }
    }

    public static class Context {
        static class Snapshot {
            public static Builder builder() {
                return new Builder();
            }

            static class Builder {
                private Optional<Long> processedImapUidEntries;
                private Optional<Long> processedPop3MetaDataStoreEntries;
                private Optional<Long> stalePOP3Entries;
                private Optional<Long> missingPOP3Entries;
                private ImmutableList.Builder<MessageInconsistenciesEntry> fixedInconsistencies;
                private ImmutableList.Builder<MessageInconsistenciesEntry> errors;

                Builder() {
                    processedImapUidEntries = Optional.empty();
                    processedPop3MetaDataStoreEntries = Optional.empty();
                    stalePOP3Entries = Optional.empty();
                    missingPOP3Entries = Optional.empty();
                    fixedInconsistencies = ImmutableList.builder();
                    errors = ImmutableList.builder();
                }

                public Builder processedImapUidEntries(long count) {
                    this.processedImapUidEntries = Optional.of(count);
                    return this;
                }

                public Builder processedPop3MetaDataStoreEntries(long count) {
                    this.processedPop3MetaDataStoreEntries = Optional.of(count);
                    return this;
                }

                public Builder stalePOP3Entries(long count) {
                    this.stalePOP3Entries = Optional.of(count);
                    return this;
                }

                public Builder missingPOP3Entries(long count) {
                    this.missingPOP3Entries = Optional.of(count);
                    return this;
                }

                public Builder addFixedInconsistencies(MessageInconsistenciesEntry messageInconsistenciesEntry) {
                    fixedInconsistencies.add(messageInconsistenciesEntry);
                    return this;
                }

                public Builder errors(MessageInconsistenciesEntry messageInconsistenciesEntry) {
                    errors.add(messageInconsistenciesEntry);
                    return this;
                }

                public MetaDataFixInconsistenciesService.Context.Snapshot build() {
                    return new MetaDataFixInconsistenciesService.Context.Snapshot(
                        processedImapUidEntries.orElse(0L),
                        processedPop3MetaDataStoreEntries.orElse(0L),
                        stalePOP3Entries.orElse(0L),
                        missingPOP3Entries.orElse(0L),
                        fixedInconsistencies.build(),
                        errors.build());
                }
            }

            private final long processedImapUidEntries;
            private final long processedPop3MetaDataStoreEntries;
            private final long stalePOP3Entries;
            private final long missingPOP3Entries;
            private final ImmutableList<MessageInconsistenciesEntry> fixedInconsistencies;
            private final ImmutableList<MessageInconsistenciesEntry> errors;

            public Snapshot(long processedImapUidEntries,
                            long processedPop3MetaDataStoreEntries,
                            long stalePOP3Entries,
                            long missingPOP3Entries,
                            ImmutableList<MessageInconsistenciesEntry> fixedInconsistencies,
                            ImmutableList<MessageInconsistenciesEntry> errors) {
                this.processedImapUidEntries = processedImapUidEntries;
                this.processedPop3MetaDataStoreEntries = processedPop3MetaDataStoreEntries;
                this.stalePOP3Entries = stalePOP3Entries;
                this.missingPOP3Entries = missingPOP3Entries;
                this.fixedInconsistencies = fixedInconsistencies;
                this.errors = errors;
            }

            @Override
            public final int hashCode() {
                return Objects.hash(processedPop3MetaDataStoreEntries, processedImapUidEntries, errors, fixedInconsistencies);
            }

            @Override
            public final boolean equals(Object obj) {
                if (obj instanceof Snapshot) {
                    Snapshot snapshot = (Snapshot) obj;
                    return Objects.equals(this.processedPop3MetaDataStoreEntries, snapshot.processedPop3MetaDataStoreEntries)
                        && Objects.equals(this.processedImapUidEntries, snapshot.processedImapUidEntries)
                        && Objects.equals(this.fixedInconsistencies, snapshot.fixedInconsistencies)
                        && Objects.equals(this.errors, snapshot.errors);
                }
                return false;
            }

            @Override
            public String toString() {
                return MoreObjects.toStringHelper(this)
                    .add("processedPop3MetaDataStoreEntries", processedPop3MetaDataStoreEntries)
                    .add("processedImapUidEntries", processedImapUidEntries)
                    .add("stalePOP3Entries", stalePOP3Entries)
                    .add("missingPOP3Entries", missingPOP3Entries)
                    .add("fixedInconsistencies", fixedInconsistencies)
                    .add("errors", errors)
                    .toString();
            }

            public long getProcessedImapUidEntries() {
                return processedImapUidEntries;
            }

            public long getProcessedPop3MetaDataStoreEntries() {
                return processedPop3MetaDataStoreEntries;
            }

            public long getStalePOP3Entries() {
                return stalePOP3Entries;
            }

            public long getMissingPOP3Entries() {
                return missingPOP3Entries;
            }

            public ImmutableList<MessageInconsistenciesEntry> getFixedInconsistencies() {
                return fixedInconsistencies;
            }

            public ImmutableList<MessageInconsistenciesEntry> getErrors() {
                return errors;
            }
        }

        private final AtomicLong processedImapUidEntries;
        private final AtomicLong processedPop3MetaDataStoreEntries;
        private final AtomicLong stalePOP3Entries;
        private final AtomicLong missingPOP3Entries;
        private final ConcurrentLinkedDeque<MessageInconsistenciesEntry> fixedInconsistencies;
        private final ConcurrentLinkedDeque<MessageInconsistenciesEntry> errors;

        Context() {
            this(new AtomicLong(), new AtomicLong(), new AtomicLong(), new AtomicLong(), ImmutableList.of(), ImmutableList.of());
        }

        private Context(AtomicLong processedImapUidEntries,
                        AtomicLong processedPop3MetaDataStoreEntries,
                        AtomicLong stalePOP3Entries,
                        AtomicLong missingPOP3Entries,
                        Collection<MessageInconsistenciesEntry> fixedInconsistencies,
                        Collection<MessageInconsistenciesEntry> errors) {
            this.processedImapUidEntries = processedImapUidEntries;
            this.processedPop3MetaDataStoreEntries = processedPop3MetaDataStoreEntries;
            this.stalePOP3Entries = stalePOP3Entries;
            this.missingPOP3Entries = missingPOP3Entries;
            this.fixedInconsistencies = new ConcurrentLinkedDeque<>(fixedInconsistencies);
            this.errors = new ConcurrentLinkedDeque<>(errors);
        }

        void incrementProcessedImapUidEntries() {
            processedImapUidEntries.incrementAndGet();
        }

        void incrementProcessedPop3MetaDataStoreEntries() {
            processedPop3MetaDataStoreEntries.incrementAndGet();
        }

        void incrementStalePOP3Entries() {
            stalePOP3Entries.getAndIncrement();
        }

        void incrementMissingPOP3Entries() {
            missingPOP3Entries.incrementAndGet();
        }

        void addFixedInconsistency(MessageInconsistenciesEntry messageInconsistenciesEntry) {
            fixedInconsistencies.add(messageInconsistenciesEntry);
        }

        void addErrors(MessageInconsistenciesEntry messageInconsistenciesEntry) {
            errors.add(messageInconsistenciesEntry);
        }

        Snapshot snapshot() {
            return new Snapshot(
                processedImapUidEntries.get(),
                processedPop3MetaDataStoreEntries.get(),
                stalePOP3Entries.get(),
                missingPOP3Entries.get(),
                ImmutableList.copyOf(fixedInconsistencies),
                ImmutableList.copyOf(errors));
        }
    }

    public static class RunningOptions {
        public static RunningOptions withMessageRatePerSecond(int messageRatePerSecond) {
            return new RunningOptions(messageRatePerSecond);
        }

        public static final RunningOptions DEFAULT = new RunningOptions(100);

        private final int messagesPerSecond;

        @JsonCreator
        public RunningOptions(@JsonProperty("messagesPerSecond") int messagesPerSecond) {
            Preconditions.checkArgument(messagesPerSecond > 0, "'messagesPerSecond' must be strictly positive");

            this.messagesPerSecond = messagesPerSecond;
        }

        public int getMessagesPerSecond() {
            return this.messagesPerSecond;
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(MetaDataFixInconsistenciesService.class);
    private static final Duration PERIOD = Duration.ofSeconds(1);

    private final CassandraMessageIdToImapUidDAO imapUidDAO;
    private final Pop3MetadataStore pop3MetadataStore;
    private final CassandraMessageDAOV3 cassandraMessageDAOV3;

    @Inject
    public MetaDataFixInconsistenciesService(CassandraMessageIdToImapUidDAO imapUidDAO,
                                             Pop3MetadataStore pop3MetadataStore,
                                             CassandraMessageDAOV3 cassandraMessageDAOV3) {
        this.imapUidDAO = imapUidDAO;
        this.pop3MetadataStore = pop3MetadataStore;
        this.cassandraMessageDAOV3 = cassandraMessageDAOV3;
    }

    public Mono<Task.Result> fixInconsistencies(Context context, RunningOptions runningOptions) {
        return Flux.concat(
            fixInconsistenciesInPop3MetaDataStore(context, runningOptions),
            fixInconsistenciesInImapUid(context, runningOptions))
            .reduce(Task.Result.COMPLETED, Task::combine);

    }

    private Flux<Task.Result> fixInconsistenciesInPop3MetaDataStore(Context context, RunningOptions runningOptions) {
        return Flux.from(pop3MetadataStore.listAllEntries())
            .transform(ReactorUtils.<Pop3MetadataStore.FullMetadata, Task.Result>throttle()
                .elements(runningOptions.getMessagesPerSecond())
                .per(PERIOD)
                .forOperation(fullMetadata -> detectStaleEntriesInPop3MetaDataStore(fullMetadata)
                    .doOnNext(any -> context.incrementProcessedPop3MetaDataStoreEntries())
                    .flatMap(inconsistency -> inconsistency.confirm(imapUidDAO, pop3MetadataStore))
                    .flatMap(inconsistency -> inconsistency.fix(context, imapUidDAO, pop3MetadataStore))));
    }

    private Mono<Inconsistency> detectStaleEntriesInPop3MetaDataStore(Pop3MetadataStore.FullMetadata fullMetadata) {
        CassandraId mailboxId = (CassandraId) fullMetadata.getMailboxId();
        CassandraMessageId messageId = (CassandraMessageId) fullMetadata.getMessageId();
        return imapUidDAO.retrieve(messageId, Optional.of(mailboxId))
            .next()
            .flatMap(any -> Mono.just(NO_INCONSISTENCY))
            .switchIfEmpty(Mono.just(new StalePOP3EntryConsistency(mailboxId, messageId)))
            .onErrorResume(error -> Mono.just(new FailToDetectInconsistency(mailboxId, messageId)));
    }

    private Flux<Task.Result> fixInconsistenciesInImapUid(Context context, RunningOptions runningOptions) {
        return imapUidDAO.retrieveAllMessages()
            .map(CassandraMessageMetadata::getComposedMessageId)
            .transform(ReactorUtils.<ComposedMessageIdWithMetaData, Task.Result>throttle()
                .elements(runningOptions.getMessagesPerSecond())
                .per(PERIOD)
                .forOperation(metaData -> detectMissingEntriesInPop3MetaDataStore(metaData)
                    .doOnNext(any -> context.incrementProcessedImapUidEntries())
                    .flatMap(inconsistency -> inconsistency.confirm(imapUidDAO, pop3MetadataStore))
                    .flatMap(inconsistency -> inconsistency.fix(context, imapUidDAO, pop3MetadataStore))));
    }

    private Mono<Inconsistency> detectMissingEntriesInPop3MetaDataStore(ComposedMessageIdWithMetaData messageFromImapUid) {
        CassandraId mailboxId = (CassandraId) messageFromImapUid.getComposedMessageId().getMailboxId();
        CassandraMessageId messageId = (CassandraMessageId) messageFromImapUid.getComposedMessageId().getMessageId();

        return Flux.from(pop3MetadataStore.retrieve(mailboxId, messageId))
            .next()
            .flatMap(any -> Mono.just(NO_INCONSISTENCY))
            .switchIfEmpty(Mono.just(new MissingPOP3EntryInconsistency(mailboxId, messageId, this.cassandraMessageDAOV3)))
            .onErrorResume(error -> Mono.just(new FailToDetectInconsistency(mailboxId, messageId)));
    }
}
