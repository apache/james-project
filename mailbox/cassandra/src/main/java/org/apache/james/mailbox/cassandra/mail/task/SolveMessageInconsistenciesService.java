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

package org.apache.james.mailbox.cassandra.mail.task;

import static org.apache.james.backends.cassandra.init.configuration.JamesExecutionProfiles.ConsistencyChoice.STRONG;
import static org.apache.james.backends.cassandra.init.configuration.JamesExecutionProfiles.ConsistencyChoice.WEAK;
import static org.apache.james.util.ReactorUtils.publishIfPresent;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

import jakarta.inject.Inject;

import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.backends.cassandra.init.configuration.JamesExecutionProfiles.ConsistencyChoice;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageDAOV3;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdToImapUidDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageMetadata;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.mail.MessageMapper.FetchType;
import org.apache.james.task.Task;
import org.apache.james.util.ReactorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class SolveMessageInconsistenciesService {

    @FunctionalInterface
    interface Inconsistency {
        Mono<Task.Result> fix(Context context, CassandraMessageIdToImapUidDAO imapUidDAO, CassandraMessageIdDAO messageIdDAO);
    }

    private static final Inconsistency NO_INCONSISTENCY = (context, imapUidDAO, messageIdDAO) -> Mono.just(Task.Result.COMPLETED);

    private static class FailedToRetrieveRecord implements Inconsistency {
        private final CassandraMessageMetadata message;

        private FailedToRetrieveRecord(CassandraMessageMetadata message) {
            this.message = message;
        }

        @Override
        public Mono<Task.Result> fix(Context context, CassandraMessageIdToImapUidDAO imapUidDAO, CassandraMessageIdDAO messageIdDAO) {
            context.addErrors(message.getComposedMessageId().getComposedMessageId());
            LOGGER.error("Failed to retrieve record: {}", message.getComposedMessageId());
            return Mono.just(Task.Result.PARTIAL);
        }
    }

    private static class OrphanImapUidEntry implements Inconsistency {
        private final CassandraMessageMetadata message;

        private OrphanImapUidEntry(CassandraMessageMetadata message) {
            this.message = message;
        }

        @Override
        public Mono<Task.Result> fix(Context context, CassandraMessageIdToImapUidDAO imapUidDAO, CassandraMessageIdDAO messageIdDAO) {
            return messageIdDAO.insert(message)
                .doOnSuccess(any -> notifySuccess(context))
                .thenReturn(Task.Result.COMPLETED)
                .onErrorResume(error -> {
                    notifyFailure(context);
                    return Mono.just(Task.Result.PARTIAL);
                });
        }

        private void notifyFailure(Context context) {
            context.addErrors(message.getComposedMessageId().getComposedMessageId());
            LOGGER.error("Failed to fix inconsistency for orphan message in ImapUid: {}", message.getComposedMessageId());
        }

        private void notifySuccess(Context context) {
            LOGGER.info("Inconsistency fixed for orphan message in ImapUid: {}", message.getComposedMessageId());
            context.incrementAddedMessageIdEntries();
            context.addFixedInconsistency(message.getComposedMessageId().getComposedMessageId());
        }
    }

    /**
     * The message is referenced in ImapUid but its content is missing in MessageV3.
     *
     * This happens when deleted index entries are resurrected (eg: tombstones purged before being repaired)
     * while the message content is not. The entry is not propagated to MessageId: doing so would expose
     * a message that can not be read. It is reported instead.
     */
    private static class ImapUidEntryWithoutContent implements Inconsistency {
        private final CassandraMessageMetadata message;

        private ImapUidEntryWithoutContent(CassandraMessageMetadata message) {
            this.message = message;
        }

        @Override
        public Mono<Task.Result> fix(Context context, CassandraMessageIdToImapUidDAO imapUidDAO, CassandraMessageIdDAO messageIdDAO) {
            context.addErrors(message.getComposedMessageId().getComposedMessageId());
            LOGGER.warn("Skipping message in ImapUid as its content is missing in MessageV3: {}", message.getComposedMessageId());
            return Mono.just(Task.Result.PARTIAL);
        }
    }

    /**
     * Same as {@link ImapUidEntryWithoutContent} but the admin explicitly asked for such entries to be removed.
     *
     * The entry is removed from both ImapUid and MessageId. Mailbox counters, quotas and search indexes are not
     * updated and need to be recomputed.
     *
     * The content is checked again right before removal, so that removals are only based on up to date reads.
     */
    private static class RemovableImapUidEntryWithoutContent implements Inconsistency {
        private final CassandraMessageMetadata message;
        private final Mono<Boolean> hasContent;

        private RemovableImapUidEntryWithoutContent(CassandraMessageMetadata message, Mono<Boolean> hasContent) {
            this.message = message;
            this.hasContent = hasContent;
        }

        @Override
        public Mono<Task.Result> fix(Context context, CassandraMessageIdToImapUidDAO imapUidDAO, CassandraMessageIdDAO messageIdDAO) {
            return hasContent
                .flatMap(contentFound -> {
                    if (contentFound) {
                        LOGGER.warn("Content found in MessageV3 upon re-check, skipping removal of {}", message.getComposedMessageId());
                        return Mono.just(Task.Result.COMPLETED);
                    }
                    return remove(context, imapUidDAO, messageIdDAO);
                })
                .onErrorResume(error -> {
                    notifyFailure(context, error);
                    return Mono.just(Task.Result.PARTIAL);
                });
        }

        private Mono<Task.Result> remove(Context context, CassandraMessageIdToImapUidDAO imapUidDAO, CassandraMessageIdDAO messageIdDAO) {
            ComposedMessageId id = message.getComposedMessageId().getComposedMessageId();
            return imapUidDAO.delete((CassandraMessageId) id.getMessageId(), (CassandraId) id.getMailboxId())
                .then(messageIdDAO.delete((CassandraId) id.getMailboxId(), id.getUid()))
                .then(Mono.fromRunnable(() -> notifySuccess(context)))
                .thenReturn(Task.Result.COMPLETED);
        }

        private void notifyFailure(Context context, Throwable error) {
            context.addErrors(message.getComposedMessageId().getComposedMessageId());
            LOGGER.error("Failed to remove message without content in MessageV3: {}", message.getComposedMessageId(), error);
        }

        private void notifySuccess(Context context) {
            LOGGER.warn("Removed message without content in MessageV3: {}", message.getComposedMessageId());
            context.incrementRemovedImapUidEntries();
            context.addFixedInconsistency(message.getComposedMessageId().getComposedMessageId());
        }
    }

    private static class OutdatedMessageIdEntry implements Inconsistency {
        private final CassandraMessageMetadata messageFromMessageId;
        private final CassandraMessageMetadata messageFromImapUid;

        private OutdatedMessageIdEntry(CassandraMessageMetadata message, CassandraMessageMetadata messageFromImapUid) {
            this.messageFromMessageId = message;
            this.messageFromImapUid = messageFromImapUid;
        }

        @Override
        public Mono<Task.Result> fix(Context context, CassandraMessageIdToImapUidDAO imapUidDAO, CassandraMessageIdDAO messageIdDAO) {
            ComposedMessageIdWithMetaData id = messageFromImapUid.getComposedMessageId();
            return messageIdDAO.updateMetadata(id.getComposedMessageId(),
                    UpdatedFlags.builder()
                        // The update only writes flag changes: diff against the stale record so that extra flags get removed
                        .oldFlags(messageFromMessageId.getComposedMessageId().getFlags())
                        .newFlags(id.getFlags())
                        .modSeq(id.getModSeq())
                        .messageId(id.getComposedMessageId().getMessageId())
                        .uid(id.getComposedMessageId().getUid())
                        .internalDate(messageFromImapUid.getInternalDate())
                        .build())
                .doOnSuccess(any -> notifySuccess(context))
                .thenReturn(Task.Result.COMPLETED)
                .onErrorResume(error -> {
                    notifyFailure(context);
                    return Mono.just(Task.Result.PARTIAL);
                });
        }

        private void notifyFailure(Context context) {
            context.addErrors(messageFromMessageId.getComposedMessageId().getComposedMessageId());
            LOGGER.error("Failed to fix inconsistency for outdated message in MessageId: {}", messageFromMessageId.getComposedMessageId());
        }

        private void notifySuccess(Context context) {
            LOGGER.info("Inconsistency fixed for outdated message in MessageId: {}", messageFromMessageId.getComposedMessageId());
            context.incrementUpdatedMessageIdEntries();
            context.addFixedInconsistency(messageFromMessageId.getComposedMessageId().getComposedMessageId());
        }
    }

    private static class OrphanMessageIdEntry implements Inconsistency {
        private final CassandraMessageMetadata message;

        private OrphanMessageIdEntry(CassandraMessageMetadata message) {
            this.message = message;
        }

        @Override
        public Mono<Task.Result> fix(Context context, CassandraMessageIdToImapUidDAO imapUidDAO, CassandraMessageIdDAO messageIdDAO) {
            return messageIdDAO.delete((CassandraId) message.getComposedMessageId().getComposedMessageId().getMailboxId(), message.getComposedMessageId().getComposedMessageId().getUid())
                .doOnSuccess(any -> notifySuccess(context))
                .thenReturn(Task.Result.COMPLETED)
                .onErrorResume(error -> {
                    notifyFailure(context);
                    return Mono.just(Task.Result.PARTIAL);
                });
        }

        private void notifyFailure(Context context) {
            context.addErrors(message.getComposedMessageId().getComposedMessageId());
            LOGGER.error("Failed to fix inconsistency for orphan message in MessageId: {}", message.getComposedMessageId());
        }

        private void notifySuccess(Context context) {
            LOGGER.info("Inconsistency fixed for orphan message in MessageId: {}", message.getComposedMessageId());
            context.incrementRemovedMessageIdEntries();
            context.addFixedInconsistency(message.getComposedMessageId().getComposedMessageId());
        }
    }

    public static class RunningOptions {

        public static final boolean DEFAULT_CLEANUP_ENTRIES_WITHOUT_CONTENT = false;
        public static final RunningOptions DEFAULT = new RunningOptions(100);

        private final int messagesPerSecond;
        private final boolean cleanupEntriesWithoutContent;

        public RunningOptions(int messagesPerSecond) {
            this(messagesPerSecond, DEFAULT_CLEANUP_ENTRIES_WITHOUT_CONTENT);
        }

        public RunningOptions(int messagesPerSecond, boolean cleanupEntriesWithoutContent) {
            Preconditions.checkArgument(messagesPerSecond > 0, "'messagesPerSecond' must be strictly positive");

            this.messagesPerSecond = messagesPerSecond;
            this.cleanupEntriesWithoutContent = cleanupEntriesWithoutContent;
        }

        public int getMessagesPerSecond() {
            return this.messagesPerSecond;
        }

        public boolean isCleanupEntriesWithoutContent() {
            return cleanupEntriesWithoutContent;
        }
    }

    public static class Context {
        static class Snapshot {
            public static Builder builder() {
                return new Builder();
            }

            static class Builder {
                private Optional<Long> processedImapUidEntries;
                private Optional<Long> processedMessageIdEntries;
                private Optional<Long> addedMessageIdEntries;
                private Optional<Long> updatedMessageIdEntries;
                private Optional<Long> removedMessageIdEntries;
                private Optional<Long> removedImapUidEntries;
                private ImmutableList.Builder<ComposedMessageId> fixedInconsistencies;
                private ImmutableList.Builder<ComposedMessageId> errors;

                Builder() {
                    processedImapUidEntries = Optional.empty();
                    processedMessageIdEntries = Optional.empty();
                    addedMessageIdEntries = Optional.empty();
                    updatedMessageIdEntries = Optional.empty();
                    removedMessageIdEntries = Optional.empty();
                    removedImapUidEntries = Optional.empty();
                    fixedInconsistencies = ImmutableList.builder();
                    errors = ImmutableList.builder();
                }

                public Builder processedImapUidEntries(long count) {
                    processedImapUidEntries = Optional.of(count);
                    return this;
                }

                public Builder processedMessageIdEntries(long count) {
                    processedMessageIdEntries = Optional.of(count);
                    return this;
                }

                public Builder addedMessageIdEntries(long count) {
                    addedMessageIdEntries = Optional.of(count);
                    return this;
                }

                public Builder updatedMessageIdEntries(long count) {
                    updatedMessageIdEntries = Optional.of(count);
                    return this;
                }

                public Builder removedMessageIdEntries(long count) {
                    removedMessageIdEntries = Optional.of(count);
                    return this;
                }

                public Builder removedImapUidEntries(long count) {
                    removedImapUidEntries = Optional.of(count);
                    return this;
                }

                public Builder addFixedInconsistencies(ComposedMessageId composedMessageId) {
                    fixedInconsistencies.add(composedMessageId);
                    return this;
                }

                public Builder errors(ComposedMessageId composedMessageId) {
                    errors.add(composedMessageId);
                    return this;
                }

                public SolveMessageInconsistenciesService.Context.Snapshot build() {
                    return new SolveMessageInconsistenciesService.Context.Snapshot(
                        processedImapUidEntries.orElse(0L),
                        processedMessageIdEntries.orElse(0L),
                        addedMessageIdEntries.orElse(0L),
                        updatedMessageIdEntries.orElse(0L),
                        removedMessageIdEntries.orElse(0L),
                        removedImapUidEntries.orElse(0L),
                        fixedInconsistencies.build(),
                        errors.build());
                }
            }

            private final long processedImapUidEntries;
            private final long processedMessageIdEntries;
            private final long addedMessageIdEntries;
            private final long updatedMessageIdEntries;
            private final long removedMessageIdEntries;
            private final long removedImapUidEntries;
            private final ImmutableList<ComposedMessageId> fixedInconsistencies;
            private final ImmutableList<ComposedMessageId> errors;

            private Snapshot(long processedImapUidEntries, long processedMessageIdEntries,
                             long addedMessageIdEntries, long updatedMessageIdEntries,
                             long removedMessageIdEntries, long removedImapUidEntries,
                             ImmutableList<ComposedMessageId> fixedInconsistencies,
                             ImmutableList<ComposedMessageId> errors) {
                this.processedImapUidEntries = processedImapUidEntries;
                this.processedMessageIdEntries = processedMessageIdEntries;
                this.addedMessageIdEntries = addedMessageIdEntries;
                this.updatedMessageIdEntries = updatedMessageIdEntries;
                this.removedMessageIdEntries = removedMessageIdEntries;
                this.removedImapUidEntries = removedImapUidEntries;
                this.fixedInconsistencies = fixedInconsistencies;
                this.errors = errors;
            }

            public long getProcessedImapUidEntries() {
                return processedImapUidEntries;
            }

            public long getProcessedMessageIdEntries() {
                return processedMessageIdEntries;
            }

            public long getAddedMessageIdEntries() {
                return addedMessageIdEntries;
            }

            public long getUpdatedMessageIdEntries() {
                return updatedMessageIdEntries;
            }

            public long getRemovedMessageIdEntries() {
                return removedMessageIdEntries;
            }

            public long getRemovedImapUidEntries() {
                return removedImapUidEntries;
            }

            public ImmutableList<ComposedMessageId> getFixedInconsistencies() {
                return fixedInconsistencies;
            }

            public ImmutableList<ComposedMessageId> getErrors() {
                return errors;
            }

            @Override
            public final boolean equals(Object o) {
                if (o instanceof Snapshot) {
                    Snapshot snapshot = (Snapshot) o;

                    return Objects.equals(this.processedImapUidEntries, snapshot.processedImapUidEntries)
                        && Objects.equals(this.processedMessageIdEntries, snapshot.processedMessageIdEntries)
                        && Objects.equals(this.addedMessageIdEntries, snapshot.addedMessageIdEntries)
                        && Objects.equals(this.updatedMessageIdEntries, snapshot.updatedMessageIdEntries)
                        && Objects.equals(this.removedMessageIdEntries, snapshot.removedMessageIdEntries)
                        && Objects.equals(this.removedImapUidEntries, snapshot.removedImapUidEntries)
                        && Objects.equals(this.errors, snapshot.errors)
                        && Objects.equals(this.fixedInconsistencies, snapshot.fixedInconsistencies);
                }
                return false;
            }

            @Override
            public final int hashCode() {
                return Objects.hash(processedImapUidEntries, processedMessageIdEntries, addedMessageIdEntries, updatedMessageIdEntries, removedMessageIdEntries, removedImapUidEntries, fixedInconsistencies, errors);
            }

            @Override
            public String toString() {
                return MoreObjects.toStringHelper(this)
                    .add("processedImapUidEntries", processedImapUidEntries)
                    .add("processedMessageIdEntries", processedMessageIdEntries)
                    .add("addedMessageIdEntries", addedMessageIdEntries)
                    .add("updatedMessageIdEntries", updatedMessageIdEntries)
                    .add("removedMessageIdEntries", removedMessageIdEntries)
                    .add("removedImapUidEntries", removedImapUidEntries)
                    .add("fixedInconsistencies", fixedInconsistencies)
                    .add("errors", errors)
                    .toString();
            }
        }

        private final AtomicLong processedImapUidEntries;
        private final AtomicLong processedMessageIdEntries;
        private final AtomicLong addedMessageIdEntries;
        private final AtomicLong updatedMessageIdEntries;
        private final AtomicLong removedMessageIdEntries;
        private final AtomicLong removedImapUidEntries;
        private final ConcurrentLinkedDeque<ComposedMessageId> fixedInconsistencies;
        private final ConcurrentLinkedDeque<ComposedMessageId> errors;

        Context() {
            this(new AtomicLong(), new AtomicLong(), new AtomicLong(), new AtomicLong(), new AtomicLong(), new AtomicLong(), ImmutableList.of(), ImmutableList.of());
        }

        private Context(AtomicLong processedImapUidEntries, AtomicLong processedMessageIdEntries, AtomicLong addedMessageIdEntries,
                        AtomicLong updatedMessageIdEntries, AtomicLong removedMessageIdEntries, AtomicLong removedImapUidEntries,
                        Collection<ComposedMessageId> fixedInconsistencies, Collection<ComposedMessageId> errors) {
            this.processedImapUidEntries = processedImapUidEntries;
            this.processedMessageIdEntries = processedMessageIdEntries;
            this.addedMessageIdEntries = addedMessageIdEntries;
            this.updatedMessageIdEntries = updatedMessageIdEntries;
            this.removedMessageIdEntries = removedMessageIdEntries;
            this.removedImapUidEntries = removedImapUidEntries;
            this.fixedInconsistencies = new ConcurrentLinkedDeque<>(fixedInconsistencies);
            this.errors = new ConcurrentLinkedDeque<>(errors);
        }

        void incrementProcessedImapUidEntries() {
            processedImapUidEntries.incrementAndGet();
        }

        void incrementMessageIdEntries() {
            processedMessageIdEntries.incrementAndGet();
        }

        void incrementAddedMessageIdEntries() {
            addedMessageIdEntries.incrementAndGet();
        }

        void incrementUpdatedMessageIdEntries() {
            updatedMessageIdEntries.incrementAndGet();
        }

        void incrementRemovedMessageIdEntries() {
            removedMessageIdEntries.incrementAndGet();
        }

        void incrementRemovedImapUidEntries() {
            removedImapUidEntries.incrementAndGet();
        }

        void addFixedInconsistency(ComposedMessageId messageId) {
            fixedInconsistencies.add(messageId);
        }

        void addErrors(ComposedMessageId messageId) {
            errors.add(messageId);
        }

        Snapshot snapshot() {
            return new Snapshot(
                processedImapUidEntries.get(),
                processedMessageIdEntries.get(),
                addedMessageIdEntries.get(),
                updatedMessageIdEntries.get(),
                removedMessageIdEntries.get(),
                removedImapUidEntries.get(),
                ImmutableList.copyOf(fixedInconsistencies),
                ImmutableList.copyOf(errors));
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(SolveMessageInconsistenciesService.class);
    private static final Duration PERIOD = Duration.ofSeconds(1);
    // Entries of messages created recently are never removed: their content could be written but not visible yet
    private static final Duration CLEANUP_GRACE_PERIOD = Duration.ofDays(1);
    private static final long UUID_EPOCH_OFFSET_IN_100NS = 0x01B21DD213814000L;

    private final CassandraMessageIdToImapUidDAO messageIdToImapUidDAO;
    private final CassandraMessageIdDAO messageIdDAO;
    private final CassandraMessageDAOV3 messageDAOV3;
    private final CassandraConfiguration cassandraConfiguration;
    private final Clock clock;

    @Inject
    SolveMessageInconsistenciesService(CassandraMessageIdToImapUidDAO messageIdToImapUidDAO, CassandraMessageIdDAO messageIdDAO,
                                       CassandraMessageDAOV3 messageDAOV3, CassandraConfiguration cassandraConfiguration) {
        this(messageIdToImapUidDAO, messageIdDAO, messageDAOV3, cassandraConfiguration, Clock.systemUTC());
    }

    SolveMessageInconsistenciesService(CassandraMessageIdToImapUidDAO messageIdToImapUidDAO, CassandraMessageIdDAO messageIdDAO,
                                       CassandraMessageDAOV3 messageDAOV3, CassandraConfiguration cassandraConfiguration, Clock clock) {
        this.messageIdToImapUidDAO = messageIdToImapUidDAO;
        this.messageIdDAO = messageIdDAO;
        this.messageDAOV3 = messageDAOV3;
        this.cassandraConfiguration = cassandraConfiguration;
        this.clock = clock;
    }

    private ConsistencyChoice chooseReadConsistency() {
        if (cassandraConfiguration.isMessageWriteStrongConsistency()) {
            return STRONG;
        }
        return WEAK;
    }

    public Mono<Task.Result> fixMessageInconsistencies(Context context, RunningOptions runningOptions) {
        return Flux.concat(
                fixInconsistenciesInMessageId(context, runningOptions),
                fixInconsistenciesInImapUid(context, runningOptions))
            .reduce(Task.Result.COMPLETED, Task::combine);
    }

    private Flux<Task.Result> fixInconsistenciesInImapUid(Context context, RunningOptions runningOptions) {
        return messageIdToImapUidDAO.retrieveAllMessages()
            .transform(ReactorUtils.<CassandraMessageMetadata, Task.Result>throttle()
                .elements(runningOptions.getMessagesPerSecond())
                .per(PERIOD)
                .forOperation(metaData -> detectInconsistencyInImapUid(metaData, runningOptions)
                    .doOnNext(any -> context.incrementProcessedImapUidEntries())
                    .flatMap(inconsistency -> inconsistency.fix(context, messageIdToImapUidDAO, messageIdDAO))));
    }

    private Mono<Inconsistency> detectInconsistencyInImapUid(CassandraMessageMetadata message, RunningOptions runningOptions) {
        return checkContentIfNeeded(message, runningOptions)
            .switchIfEmpty(Mono.defer(() -> compareWithMessageIdRecord(message, runningOptions)))
            .onErrorResume(error -> Mono.just(new FailedToRetrieveRecord(message)));
    }

    // Entries resurrected in both ImapUid and MessageId are consistent with each other: the content is thus checked
    // for every entry when a cleanup is requested.
    private Mono<Inconsistency> checkContentIfNeeded(CassandraMessageMetadata message, RunningOptions runningOptions) {
        if (!runningOptions.isCleanupEntriesWithoutContent()) {
            return Mono.empty();
        }
        CassandraMessageId messageId = (CassandraMessageId) message.getComposedMessageId().getComposedMessageId().getMessageId();
        return hasContent(messageId)
            .filter(hasContent -> !hasContent)
            .map(any -> entryWithoutContent(message, runningOptions));
    }

    private Inconsistency entryWithoutContent(CassandraMessageMetadata message, RunningOptions runningOptions) {
        CassandraMessageId messageId = (CassandraMessageId) message.getComposedMessageId().getComposedMessageId().getMessageId();
        if (runningOptions.isCleanupEntriesWithoutContent() && isOutsideGracePeriod(messageId)) {
            return new RemovableImapUidEntryWithoutContent(message, Mono.defer(() -> hasContent(messageId)));
        }
        return new ImapUidEntryWithoutContent(message);
    }

    private boolean isOutsideGracePeriod(CassandraMessageId messageId) {
        return creationInstant(messageId)
            .map(creation -> creation.plus(CLEANUP_GRACE_PERIOD).isBefore(clock.instant()))
            .orElse(false);
    }

    private static Optional<Instant> creationInstant(CassandraMessageId messageId) {
        UUID uuid = messageId.get();
        if (uuid.version() != 1) {
            return Optional.empty();
        }
        return Optional.of(Instant.ofEpochMilli((uuid.timestamp() - UUID_EPOCH_OFFSET_IN_100NS) / 10_000));
    }

    private Mono<Inconsistency> compareWithMessageIdRecord(CassandraMessageMetadata messageFromImapUid, RunningOptions runningOptions) {
        ComposedMessageId ids = messageFromImapUid.getComposedMessageId().getComposedMessageId();
        CassandraId mailboxId = (CassandraId) ids.getMailboxId();
        MessageUid uid = ids.getUid();
        CassandraMessageId messageId = (CassandraMessageId) ids.getMessageId();

        return messageIdDAO.retrieve(mailboxId, uid)
            .handle(publishIfPresent())
            .flatMap(messageIdRecord -> {
                if (messageIdRecord.equals(messageFromImapUid)) {
                    return Mono.just(NO_INCONSISTENCY);
                }
                return detectOutdatedMessageIdEntry(mailboxId, messageId, messageIdRecord);
            })
            .switchIfEmpty(
                detectOrphanImapUidEntry(mailboxId, messageId, runningOptions));
    }

    private Mono<Inconsistency> detectOutdatedMessageIdEntry(CassandraId mailboxId, CassandraMessageId messageId, CassandraMessageMetadata messageIdRecord) {
        return messageIdToImapUidDAO.retrieve(messageId, Optional.of(mailboxId), chooseReadConsistency())
            .filter(Predicate.not(Predicate.isEqual(messageIdRecord)))
            .<Inconsistency>map(upToDateMessageFromImapUid -> new OutdatedMessageIdEntry(messageIdRecord, upToDateMessageFromImapUid))
            .next()
            .switchIfEmpty(Mono.just(NO_INCONSISTENCY));
    }

    private Mono<Inconsistency> detectOrphanImapUidEntry(CassandraId mailboxId, CassandraMessageId messageId, RunningOptions runningOptions) {
        return messageIdToImapUidDAO.retrieve(messageId, Optional.of(mailboxId), chooseReadConsistency())
            .next()
            .flatMap(orphanEntry -> hasContent(messageId)
                .map(hasContent -> {
                    if (hasContent) {
                        return new OrphanImapUidEntry(orphanEntry);
                    }
                    return entryWithoutContent(orphanEntry, runningOptions);
                }))
            .switchIfEmpty(Mono.just(NO_INCONSISTENCY));
    }

    // Upon optimistic consistency, an empty read is retried with the READ execution profile (QUORUM by default)
    private Mono<Boolean> hasContent(CassandraMessageId messageId) {
        return messageDAOV3.retrieveMessage(messageId, FetchType.METADATA)
            .hasElement();
    }

    private Flux<Task.Result> fixInconsistenciesInMessageId(Context context, RunningOptions runningOptions) {
        return messageIdDAO.retrieveAllMessages()
            .transform(ReactorUtils.<CassandraMessageMetadata, Task.Result>throttle()
                .elements(runningOptions.getMessagesPerSecond())
                .per(PERIOD)
                .forOperation(metadata -> detectInconsistencyInMessageId(metadata)
                    .doOnNext(any -> context.incrementMessageIdEntries())
                    .flatMap(inconsistency -> inconsistency.fix(context, messageIdToImapUidDAO, messageIdDAO))));
    }

    private Mono<Inconsistency> detectInconsistencyInMessageId(CassandraMessageMetadata message) {
        return messageIdToImapUidDAO.retrieve((CassandraMessageId) message.getComposedMessageId().getComposedMessageId().getMessageId(),
                Optional.of((CassandraId) message.getComposedMessageId().getComposedMessageId().getMailboxId()), chooseReadConsistency())
            .map(uidRecord -> NO_INCONSISTENCY)
            .next()
            .switchIfEmpty(detectOrphanMessageIdEntry(message))
            .onErrorResume(error -> Mono.just(new FailedToRetrieveRecord(message)));
    }

    private Mono<Inconsistency> detectOrphanMessageIdEntry(CassandraMessageMetadata message) {
        return messageIdDAO.retrieve((CassandraId) message.getComposedMessageId().getComposedMessageId().getMailboxId(), message.getComposedMessageId().getComposedMessageId().getUid())
            .handle(publishIfPresent())
            .<Inconsistency>map(OrphanMessageIdEntry::new)
            .switchIfEmpty(Mono.just(NO_INCONSISTENCY));
    }
}