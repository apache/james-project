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
import static org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionManager;
import org.apache.james.backends.cassandra.versions.SchemaVersion;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxPathV3DAO;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.task.Task;
import org.apache.james.task.Task.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class SolveMailboxInconsistenciesService {
    public static final Logger LOGGER = LoggerFactory.getLogger(SolveMailboxInconsistenciesService.class);

    @FunctionalInterface
    public interface Inconsistency {
        static Mono<Inconsistency> detectMailboxDaoInconsistency(Mailbox mailboxEntry, Mono<Mailbox> pathEntry) {
            return pathEntry
                .map(mailboxByPath -> {
                    if (mailboxByPath.getMailboxId().equals(mailboxEntry.getMailboxId())) {
                        return NO_INCONSISTENCY;
                    }
                    // Path entry references another mailbox.
                    return new ConflictingEntryInconsistency(ConflictingEntry.builder()
                        .mailboxDaoEntry(mailboxEntry)
                        .mailboxPathDaoEntry(mailboxByPath));
                })
                .defaultIfEmpty(new OrphanMailboxDAOEntry(mailboxEntry));
        }


        static Mono<Inconsistency> detectMailboxPathDaoInconsistency(Mailbox mailboxByPathEntry, Mono<Mailbox> mailboxEntry) {
            return mailboxEntry
                .map(mailboxById -> {
                    if (mailboxByPathEntry.generateAssociatedPath().equals(mailboxById.generateAssociatedPath())) {
                        return NO_INCONSISTENCY;
                    }
                    // Mailbox references another path
                    return new ConflictingEntryInconsistency(ConflictingEntry.builder()
                        .mailboxDaoEntry(mailboxById)
                        .mailboxPathDaoEntry(mailboxByPathEntry));
                })
                .defaultIfEmpty(new OrphanMailboxPathDAOEntry(mailboxByPathEntry));
        }

        Mono<Result> fix(Context context, CassandraMailboxDAO mailboxDAO, CassandraMailboxPathV3DAO pathV3DAO);
    }

    private static final Inconsistency NO_INCONSISTENCY = (context, mailboxDAO1, pathV2DAO) -> Mono.just(Result.COMPLETED);

    /**
     * The Mailbox is referenced in MailboxDAO but the corresponding
     * reference is missing in MailboxPathDao.
     *
     * In order to solve this inconsistency, we can simply re-reference the mailboxPath.
     */
    private static class OrphanMailboxDAOEntry implements Inconsistency {
        private final Mailbox mailbox;

        private OrphanMailboxDAOEntry(Mailbox mailbox) {
            this.mailbox = mailbox;
        }

        @Override
        public Mono<Result> fix(Context context, CassandraMailboxDAO mailboxDAO, CassandraMailboxPathV3DAO pathV3DAO) {
            return pathV3DAO.save(mailbox)
                .map(success -> {
                    if (success) {
                        notifySuccess(context);
                        return Result.COMPLETED;
                    } else {
                        notifyFailure(context);
                        return Result.PARTIAL;
                    }
                });
        }

        private void notifyFailure(Context context) {
            context.incrementErrors();
            LOGGER.warn("Failed fixing inconsistency for orphan mailbox {} - {}",
                mailbox.getMailboxId().serialize(),
                mailbox.generateAssociatedPath().asString());
        }

        private void notifySuccess(Context context) {
            LOGGER.info("Inconsistency fixed for orphan mailbox {} - {}",
                mailbox.getMailboxId().serialize(),
                mailbox.generateAssociatedPath().asString());
            context.addFixedInconsistency(mailbox.getMailboxId());
        }
    }

    /**
     * The Mailbox is referenced in MailboxPathDao but the corresponding
     * entry is missing in MailboxDao.
     *
     * CassandraIds are guaranteed to be unique, and are immutable once set to a mailbox.
     *
     * This inconsistency arise if mailbox creation fails or upon partial deletes.
     *
     * In both case removing the dandling path registration solves the inconsistency
     *
     * In order to solve this inconsistency, we can simply re-reference the mailboxPath.
     */
    private static class OrphanMailboxPathDAOEntry implements Inconsistency {
        private final Mailbox mailbox;

        private OrphanMailboxPathDAOEntry(Mailbox mailbox) {
            this.mailbox = mailbox;
        }

        @Override
        public Mono<Result> fix(Context context, CassandraMailboxDAO mailboxDAO, CassandraMailboxPathV3DAO pathV3DAO) {
            return pathV3DAO.delete(mailbox.generateAssociatedPath())
                .doOnSuccess(any -> {
                    LOGGER.info("Inconsistency fixed for orphan mailboxPath {} - {}",
                        mailbox.getMailboxId().serialize(),
                        mailbox.generateAssociatedPath().asString());
                    context.addFixedInconsistency(mailbox.getMailboxId());
                })
                .map(any -> Result.COMPLETED)
                .defaultIfEmpty(Result.COMPLETED)
                .onErrorResume(e -> {
                    LOGGER.error("Failed fixing inconsistency for orphan mailboxPath {} - {}",
                        mailbox.getMailboxId().serialize(),
                        mailbox.generateAssociatedPath().asString(),
                        e);
                    context.incrementErrors();
                    return Mono.just(Result.PARTIAL);
                });
        }
    }

    /**
     * The Mailbox is referenced in MailboxDAO but the corresponding
     * reference is pointing to another mailbox in MailboxPathDao.
     *
     * This error can not be recovered as some data-loss might be involved. It is preferable to
     * ask the admin to review then merge the two mailbowes together using {@link MailboxMergingTask}.
     *
     * See https://github.com/apache/james-project/blob/master/src/site/markdown/server/manage-webadmin.md#correcting-ghost-mailbox
     */
    private static class ConflictingEntryInconsistency implements Inconsistency {
        private final ConflictingEntry conflictingEntry;

        private ConflictingEntryInconsistency(ConflictingEntry conflictingEntry) {
            this.conflictingEntry = conflictingEntry;
        }

        @Override
        public Mono<Result> fix(Context context, CassandraMailboxDAO mailboxDAO, CassandraMailboxPathV3DAO pathV3DAO) {
            LOGGER.error("MailboxDAO contains mailbox {} {} which conflict with corresponding registration {} {}. " +
                "We recommend merging these mailboxes together to prevent mail data loss.",
                conflictingEntry.getMailboxDaoEntry().getMailboxId(), conflictingEntry.getMailboxDaoEntry().getMailboxPath(),
                conflictingEntry.getMailboxPathDaoEntry().getMailboxId(), conflictingEntry.getMailboxPathDaoEntry().getMailboxPath());
            context.addConflictingEntries(conflictingEntry);
            return Mono.just(Result.PARTIAL);
        }
    }

    public static class Context {
        static class Builder {
            private Optional<Long> processedMailboxEntries;
            private Optional<Long> processedMailboxPathEntries;
            private ImmutableList.Builder<MailboxId>  fixedInconsistencies;
            private ImmutableList.Builder<ConflictingEntry> conflictingEntries;
            private Optional<Long> errors;

            Builder() {
                processedMailboxPathEntries = Optional.empty();
                fixedInconsistencies = ImmutableList.builder();
                conflictingEntries = ImmutableList.builder();
                errors = Optional.empty();
                processedMailboxEntries = Optional.empty();
            }

            public Builder processedMailboxEntries(long count) {
                processedMailboxEntries = Optional.of(count);
                return this;
            }

            public Builder processedMailboxPathEntries(long count) {
                processedMailboxPathEntries = Optional.of(count);
                return this;
            }

            public Builder addFixedInconsistencies(MailboxId mailboxId) {
                fixedInconsistencies.add(mailboxId);
                return this;
            }

            public Builder addConflictingEntry(ConflictingEntry conflictingEntry) {
                conflictingEntries.add(conflictingEntry);
                return this;
            }

            public Context build() {
                return new Context(
                    processedMailboxEntries.orElse(0L),
                    processedMailboxPathEntries.orElse(0L),
                    fixedInconsistencies.build(),
                    conflictingEntries.build(),
                    errors.orElse(0L));
            }
        }

        public static Builder builder() {
            return new Builder();
        }

        static class Snapshot {
            private final long processedMailboxEntries;
            private final long processedMailboxPathEntries;
            private final ImmutableList<MailboxId> fixedInconsistencies;
            private final ImmutableList<ConflictingEntry> conflictingEntries;
            private final long errors;

            private Snapshot(long processedMailboxEntries, long processedMailboxPathEntries,
                             ImmutableList<MailboxId> fixedInconsistencies,
                             ImmutableList<ConflictingEntry> conflictingEntries, long errors) {
                this.processedMailboxEntries = processedMailboxEntries;
                this.processedMailboxPathEntries = processedMailboxPathEntries;
                this.fixedInconsistencies = fixedInconsistencies;
                this.conflictingEntries = conflictingEntries;
                this.errors = errors;
            }

            long getProcessedMailboxEntries() {
                return processedMailboxEntries;
            }

            long getProcessedMailboxPathEntries() {
                return processedMailboxPathEntries;
            }

            ImmutableList<MailboxId> getFixedInconsistencies() {
                return fixedInconsistencies;
            }

            ImmutableList<ConflictingEntry> getConflictingEntries() {
                return conflictingEntries;
            }

            long getErrors() {
                return errors;
            }

            @Override
            public final boolean equals(Object o) {
                if (o instanceof Snapshot) {
                    Snapshot that = (Snapshot) o;

                    return Objects.equals(this.processedMailboxEntries, that.processedMailboxEntries)
                        && Objects.equals(this.processedMailboxPathEntries, that.processedMailboxPathEntries)
                        && Objects.equals(this.fixedInconsistencies, that.fixedInconsistencies)
                        && Objects.equals(this.errors, that.errors)
                        && Objects.equals(this.conflictingEntries, that.conflictingEntries);
                }
                return false;
            }

            @Override
            public final int hashCode() {
                return Objects.hash(processedMailboxEntries, processedMailboxPathEntries, fixedInconsistencies, conflictingEntries, errors);
            }

            @Override
            public String toString() {
                return MoreObjects.toStringHelper(this)
                    .add("processedMailboxEntries", processedMailboxEntries)
                    .add("processedMailboxPathEntries", processedMailboxPathEntries)
                    .add("fixedInconsistencies", fixedInconsistencies)
                    .add("conflictingEntries", conflictingEntries)
                    .add("errors", errors)
                    .toString();
            }
        }

        private final AtomicLong processedMailboxEntries;
        private final AtomicLong processedMailboxPathEntries;
        private final ConcurrentLinkedDeque<MailboxId> fixedInconsistencies;
        private final ConcurrentLinkedDeque<ConflictingEntry> conflictingEntries;
        private final AtomicLong errors;

        public Context() {
            this(new AtomicLong(), new AtomicLong(), ImmutableList.of(), ImmutableList.of(), new AtomicLong());
        }

        Context(long processedMailboxEntries, long processedMailboxPathEntries, Collection<MailboxId> fixedInconsistencies, Collection<ConflictingEntry> conflictingEntries, long errors) {
            this(new AtomicLong(processedMailboxEntries),
                new AtomicLong(processedMailboxPathEntries),
                fixedInconsistencies,
                conflictingEntries,
                new AtomicLong(errors));
        }

        private Context(AtomicLong processedMailboxEntries, AtomicLong processedMailboxPathEntries, Collection<MailboxId> fixedInconsistencies, Collection<ConflictingEntry> conflictingEntries, AtomicLong errors) {
            this.processedMailboxEntries = processedMailboxEntries;
            this.processedMailboxPathEntries = processedMailboxPathEntries;
            this.fixedInconsistencies = new ConcurrentLinkedDeque<>(fixedInconsistencies);
            this.conflictingEntries = new ConcurrentLinkedDeque<>(conflictingEntries);
            this.errors = errors;
        }

        void incrementProcessedMailboxEntries() {
            processedMailboxEntries.incrementAndGet();
        }

        void incrementProcessedMailboxPathEntries() {
            processedMailboxPathEntries.incrementAndGet();
        }

        void addFixedInconsistency(MailboxId mailboxId) {
            fixedInconsistencies.add(mailboxId);
        }

        void addConflictingEntries(ConflictingEntry conflictingEntry) {
            conflictingEntries.add(conflictingEntry);
        }

        void incrementErrors() {
            errors.incrementAndGet();
        }

        Snapshot snapshot() {
            return new Snapshot(
                processedMailboxEntries.get(),
                processedMailboxPathEntries.get(),
                ImmutableList.copyOf(fixedInconsistencies),
                ImmutableList.copyOf(conflictingEntries),
                errors.get());
        }
    }

    private static final SchemaVersion MAILBOX_PATH_V_3_MIGRATION_PERFORMED_VERSION = new SchemaVersion(8);

    private final CassandraMailboxDAO mailboxDAO;
    private final CassandraMailboxPathV3DAO mailboxPathV3DAO;
    private final CassandraSchemaVersionManager versionManager;

    @Inject
    SolveMailboxInconsistenciesService(CassandraMailboxDAO mailboxDAO, CassandraMailboxPathV3DAO mailboxPathV3DAO,
                                       CassandraSchemaVersionManager versionManager) {
        this.mailboxDAO = mailboxDAO;
        this.mailboxPathV3DAO = mailboxPathV3DAO;
        this.versionManager = versionManager;
    }

    public Mono<Result> fixMailboxInconsistencies(Context context) {
        assertValidVersion();
        return Flux.concat(
                processMailboxDaoInconsistencies(context),
                processMailboxPathDaoInconsistencies(context))
            .reduce(Result.COMPLETED, Task::combine);
    }

    private void assertValidVersion() {
        SchemaVersion version = versionManager.computeVersion().block();

        boolean isVersionValid = version.isAfterOrEquals(MAILBOX_PATH_V_3_MIGRATION_PERFORMED_VERSION);

        Preconditions.checkState(isVersionValid,
            "Schema version %s is required in order to ensure mailboxPathV3DAO to be correctly populated, got %s",
            MAILBOX_PATH_V_3_MIGRATION_PERFORMED_VERSION.getValue(),
            version.getValue());
    }

    private Flux<Result> processMailboxPathDaoInconsistencies(Context context) {
        return mailboxPathV3DAO.listAll()
            .flatMap(this::detectMailboxPathDaoInconsistency, DEFAULT_CONCURRENCY)
            .flatMap(inconsistency -> inconsistency.fix(context, mailboxDAO, mailboxPathV3DAO), DEFAULT_CONCURRENCY)
            .doOnNext(any -> context.incrementProcessedMailboxPathEntries());
    }

    private Flux<Result> processMailboxDaoInconsistencies(Context context) {
        return mailboxDAO.retrieveAllMailboxes()
            .flatMap(this::detectMailboxDaoInconsistency, DEFAULT_CONCURRENCY)
            .flatMap(inconsistency -> inconsistency.fix(context, mailboxDAO, mailboxPathV3DAO), DEFAULT_CONCURRENCY)
            .doOnNext(any -> context.incrementProcessedMailboxEntries());
    }

    private Mono<Inconsistency> detectMailboxDaoInconsistency(Mailbox mailboxEntry) {
        Mono<Mailbox> pathEntry = mailboxPathV3DAO.retrieve(mailboxEntry.generateAssociatedPath(), STRONG);
        return Inconsistency.detectMailboxDaoInconsistency(mailboxEntry, pathEntry);
    }

    private Mono<Inconsistency> detectMailboxPathDaoInconsistency(Mailbox mailboxByPathEntry) {
        CassandraId cassandraId = (CassandraId) mailboxByPathEntry.getMailboxId();

        Mono<Mailbox> mailboxEntry = mailboxDAO.retrieveMailbox(cassandraId);
        return Inconsistency.detectMailboxPathDaoInconsistency(mailboxByPathEntry, mailboxEntry);
    }
}
