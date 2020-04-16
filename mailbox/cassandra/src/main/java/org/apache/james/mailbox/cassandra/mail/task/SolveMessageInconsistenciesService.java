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

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

import javax.inject.Inject;

import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdToImapUidDAO;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class SolveMessageInconsistenciesService {

    @FunctionalInterface
    interface Inconsistency {
        Mono<Task.Result> fix(Context context, CassandraMessageIdToImapUidDAO imapUidDAO, CassandraMessageIdDAO messageIdDAO);
    }

    private static Inconsistency NO_INCONSISTENCY = (context, imapUidDAO, messageIdDAO) -> Mono.just(Task.Result.COMPLETED);

    private static class FailedToRetrieveRecord implements Inconsistency {
        private final ComposedMessageIdWithMetaData message;

        private FailedToRetrieveRecord(ComposedMessageIdWithMetaData message) {
            this.message = message;
        }

        @Override
        public Mono<Task.Result> fix(Context context, CassandraMessageIdToImapUidDAO imapUidDAO, CassandraMessageIdDAO messageIdDAO) {
            context.addErrors(message.getComposedMessageId());
            LOGGER.error("Failed to retrieve record: {}", message.getComposedMessageId());
            return Mono.just(Task.Result.PARTIAL);
        }
    }

    private static class OrphanImapUidEntry implements Inconsistency {
        private final ComposedMessageIdWithMetaData message;

        private OrphanImapUidEntry(ComposedMessageIdWithMetaData message) {
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
            context.addErrors(message.getComposedMessageId());
            LOGGER.error("Failed to fix inconsistency for orphan message in ImapUid: {}", message.getComposedMessageId());
        }

        private void notifySuccess(Context context) {
            LOGGER.info("Inconsistency fixed for orphan message in ImapUid: {}", message.getComposedMessageId());
            context.incrementAddedMessageIdEntries();
            context.addFixedInconsistency(message.getComposedMessageId());
        }
    }

    private static class OutdatedMessageIdEntry implements Inconsistency {
        private final ComposedMessageIdWithMetaData messageFromMessageId;
        private final ComposedMessageIdWithMetaData messageFromImapUid;

        private OutdatedMessageIdEntry(ComposedMessageIdWithMetaData message, ComposedMessageIdWithMetaData messageFromImapUid) {
            this.messageFromMessageId = message;
            this.messageFromImapUid = messageFromImapUid;
        }

        @Override
        public Mono<Task.Result> fix(Context context, CassandraMessageIdToImapUidDAO imapUidDAO, CassandraMessageIdDAO messageIdDAO) {
            return messageIdDAO.updateMetadata(messageFromImapUid)
                .doOnSuccess(any -> notifySuccess(context))
                .thenReturn(Task.Result.COMPLETED)
                .onErrorResume(error -> {
                    notifyFailure(context);
                    return Mono.just(Task.Result.PARTIAL);
                });
        }

        private void notifyFailure(Context context) {
            context.addErrors(messageFromMessageId.getComposedMessageId());
            LOGGER.error("Failed to fix inconsistency for outdated message in MessageId: {}", messageFromMessageId.getComposedMessageId());
        }

        private void notifySuccess(Context context) {
            LOGGER.info("Inconsistency fixed for outdated message in MessageId: {}", messageFromMessageId.getComposedMessageId());
            context.incrementUpdatedMessageIdEntries();
            context.addFixedInconsistency(messageFromMessageId.getComposedMessageId());
        }
    }

    private static class OrphanMessageIdEntry implements Inconsistency {
        private final ComposedMessageIdWithMetaData message;

        private OrphanMessageIdEntry(ComposedMessageIdWithMetaData message) {
            this.message = message;
        }

        @Override
        public Mono<Task.Result> fix(Context context, CassandraMessageIdToImapUidDAO imapUidDAO, CassandraMessageIdDAO messageIdDAO) {
            return messageIdDAO.delete((CassandraId) message.getComposedMessageId().getMailboxId(), message.getComposedMessageId().getUid())
                .doOnSuccess(any -> notifySuccess(context))
                .thenReturn(Task.Result.COMPLETED)
                .onErrorResume(error -> {
                    notifyFailure(context);
                    return Mono.just(Task.Result.PARTIAL);
                });
        }

        private void notifyFailure(Context context) {
            context.addErrors(message.getComposedMessageId());
            LOGGER.error("Failed to fix inconsistency for orphan message in MessageId: {}", message.getComposedMessageId());
        }

        private void notifySuccess(Context context) {
            LOGGER.info("Inconsistency fixed for orphan message in MessageId: {}", message.getComposedMessageId());
            context.incrementRemovedMessageIdEntries();
            context.addFixedInconsistency(message.getComposedMessageId());
        }
    }

    static class Context {
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
                private ImmutableList.Builder<ComposedMessageId> fixedInconsistencies;
                private ImmutableList.Builder<ComposedMessageId> errors;

                Builder() {
                    processedImapUidEntries = Optional.empty();
                    processedMessageIdEntries = Optional.empty();
                    addedMessageIdEntries = Optional.empty();
                    updatedMessageIdEntries = Optional.empty();
                    removedMessageIdEntries = Optional.empty();
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
                        fixedInconsistencies.build(),
                        errors.build());
                }
            }

            private final long processedImapUidEntries;
            private final long processedMessageIdEntries;
            private final long addedMessageIdEntries;
            private final long updatedMessageIdEntries;
            private final long removedMessageIdEntries;
            private final ImmutableList<ComposedMessageId> fixedInconsistencies;
            private final ImmutableList<ComposedMessageId> errors;

            private Snapshot(long processedImapUidEntries, long processedMessageIdEntries,
                             long addedMessageIdEntries, long updatedMessageIdEntries,
                             long removedMessageIdEntries,
                             ImmutableList<ComposedMessageId> fixedInconsistencies,
                             ImmutableList<ComposedMessageId> errors) {
                this.processedImapUidEntries = processedImapUidEntries;
                this.processedMessageIdEntries = processedMessageIdEntries;
                this.addedMessageIdEntries = addedMessageIdEntries;
                this.updatedMessageIdEntries = updatedMessageIdEntries;
                this.removedMessageIdEntries = removedMessageIdEntries;
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
                        && Objects.equals(this.errors, snapshot.errors)
                        && Objects.equals(this.fixedInconsistencies, snapshot.fixedInconsistencies);
                }
                return false;
            }

            @Override
            public final int hashCode() {
                return Objects.hash(processedImapUidEntries, processedMessageIdEntries, addedMessageIdEntries, updatedMessageIdEntries, removedMessageIdEntries, fixedInconsistencies, errors);
            }

            @Override
            public String toString() {
                return MoreObjects.toStringHelper(this)
                    .add("processedImapUidEntries", processedImapUidEntries)
                    .add("processedMessageIdEntries", processedMessageIdEntries)
                    .add("addedMessageIdEntries", addedMessageIdEntries)
                    .add("updatedMessageIdEntries", updatedMessageIdEntries)
                    .add("removedMessageIdEntries", removedMessageIdEntries)
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
        private final ConcurrentLinkedDeque<ComposedMessageId> fixedInconsistencies;
        private final ConcurrentLinkedDeque<ComposedMessageId> errors;

        Context() {
            this(new AtomicLong(), new AtomicLong(), new AtomicLong(), new AtomicLong(), new AtomicLong(), ImmutableList.of(), ImmutableList.of());
        }

        private Context(AtomicLong processedImapUidEntries, AtomicLong processedMessageIdEntries, AtomicLong addedMessageIdEntries,
                        AtomicLong updatedMessageIdEntries, AtomicLong removedMessageIdEntries, Collection<ComposedMessageId> fixedInconsistencies,
                        Collection<ComposedMessageId> errors) {
            this.processedImapUidEntries = processedImapUidEntries;
            this.processedMessageIdEntries = processedMessageIdEntries;
            this.addedMessageIdEntries = addedMessageIdEntries;
            this.updatedMessageIdEntries = updatedMessageIdEntries;
            this.removedMessageIdEntries = removedMessageIdEntries;
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
                ImmutableList.copyOf(fixedInconsistencies),
                ImmutableList.copyOf(errors));
        }
    }

    public static final Logger LOGGER = LoggerFactory.getLogger(SolveMessageInconsistenciesService.class);

    private final CassandraMessageIdToImapUidDAO messageIdToImapUidDAO;
    private final CassandraMessageIdDAO messageIdDAO;

    @Inject
    SolveMessageInconsistenciesService(CassandraMessageIdToImapUidDAO messageIdToImapUidDAO, CassandraMessageIdDAO messageIdDAO) {
        this.messageIdToImapUidDAO = messageIdToImapUidDAO;
        this.messageIdDAO = messageIdDAO;
    }

    Mono<Task.Result> fixMessageInconsistencies(Context context) {
        return Flux.concat(
            fixInconsistenciesInMessageId(context),
            fixInconsistenciesInImapUid(context))
            .reduce(Task.Result.COMPLETED, Task::combine);
    }

    private Flux<Task.Result> fixInconsistenciesInImapUid(Context context) {
        return messageIdToImapUidDAO.retrieveAllMessages()
            .doOnNext(any -> context.incrementProcessedImapUidEntries())
            .concatMap(this::detectInconsistencyInImapUid)
            .concatMap(inconsistency -> inconsistency.fix(context, messageIdToImapUidDAO, messageIdDAO));
    }

    private Mono<Inconsistency> detectInconsistencyInImapUid(ComposedMessageIdWithMetaData message) {
        return messageIdToImapUidDAO.retrieve((CassandraMessageId) message.getComposedMessageId().getMessageId(), Optional.of((CassandraId) message.getComposedMessageId().getMailboxId()))
            .next()
            .flatMap(this::compareWithMessageIdRecord)
            .onErrorResume(error -> Mono.just(new FailedToRetrieveRecord(message)));
    }

    private Mono<Inconsistency> compareWithMessageIdRecord(ComposedMessageIdWithMetaData upToDateMessageFromImapUid) {
        return messageIdDAO.retrieve((CassandraId) upToDateMessageFromImapUid.getComposedMessageId().getMailboxId(), upToDateMessageFromImapUid.getComposedMessageId().getUid())
            .flatMap(Mono::justOrEmpty)
            .map(messageIdRecord -> {
                if (messageIdRecord.equals(upToDateMessageFromImapUid)) {
                    return NO_INCONSISTENCY;
                }
                return new OutdatedMessageIdEntry(messageIdRecord, upToDateMessageFromImapUid);
            })
            .switchIfEmpty(Mono.just(new OrphanImapUidEntry(upToDateMessageFromImapUid)));
    }

    private Flux<Task.Result> fixInconsistenciesInMessageId(Context context) {
        return messageIdDAO.retrieveAllMessages()
            .doOnNext(any -> context.incrementMessageIdEntries())
            .concatMap(this::detectInconsistencyInMessageId)
            .concatMap(inconsistency -> inconsistency.fix(context, messageIdToImapUidDAO, messageIdDAO));
    }

    private Mono<Inconsistency> detectInconsistencyInMessageId(ComposedMessageIdWithMetaData message) {
        return messageIdDAO.retrieve((CassandraId) message.getComposedMessageId().getMailboxId(), message.getComposedMessageId().getUid())
            .flatMap(Mono::justOrEmpty)
            .flatMap(upToDateMessage -> messageIdToImapUidDAO.retrieve((CassandraMessageId) message.getComposedMessageId().getMessageId(), Optional.of((CassandraId) message.getComposedMessageId().getMailboxId()))
                .map(uidRecord -> NO_INCONSISTENCY)
                .switchIfEmpty(Mono.just(new OrphanMessageIdEntry(message)))
                .next())
            .onErrorResume(error -> Mono.just(new FailedToRetrieveRecord(message)));
    }
}