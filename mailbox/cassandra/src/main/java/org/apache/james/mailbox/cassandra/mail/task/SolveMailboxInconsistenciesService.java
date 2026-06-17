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

import jakarta.inject.Inject;

import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionManager;
import org.apache.james.backends.cassandra.versions.SchemaVersion;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxPathV3DAO;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
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
            // Read-repair entry point: never auto-merges (no merging runner available, and read paths
            // must not trigger destructive ghost merges).
            return detectMailboxDaoInconsistency(mailboxEntry, pathEntry, null, false);
        }

        static Mono<Inconsistency> detectMailboxDaoInconsistency(Mailbox mailboxEntry, Mono<Mailbox> pathEntry,
                                                                 MailboxMergingTaskRunner mergingRunner, boolean autoMerge) {
            return pathEntry
                .map(mailboxByPath -> {
                    if (mailboxByPath.getMailboxId().equals(mailboxEntry.getMailboxId())) {
                        return NO_INCONSISTENCY;
                    }
                    // Path entry references another mailbox.
                    return new ConflictingEntryInconsistency(mailboxEntry, mailboxByPath, mergingRunner, autoMerge);
                })
                .defaultIfEmpty(new OrphanMailboxDAOEntry(mailboxEntry));
        }


        static Mono<Inconsistency> detectMailboxPathDaoInconsistency(Mailbox mailboxByPathEntry, Mono<Mailbox> mailboxEntry) {
            // Read-repair entry point: never auto-merges (this site only ever yields same-id conflicts anyway).
            return detectMailboxPathDaoInconsistency(mailboxByPathEntry, mailboxEntry, null, false);
        }

        static Mono<Inconsistency> detectMailboxPathDaoInconsistency(Mailbox mailboxByPathEntry, Mono<Mailbox> mailboxEntry,
                                                                     MailboxMergingTaskRunner mergingRunner, boolean autoMerge) {
            return mailboxEntry
                .map(mailboxById -> {
                    if (mailboxByPathEntry.generateAssociatedPath().equals(mailboxById.generateAssociatedPath())) {
                        return NO_INCONSISTENCY;
                    }
                    // Mailbox references another path
                    return new ConflictingEntryInconsistency(mailboxById, mailboxByPathEntry, mergingRunner, autoMerge);
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
        private final Mailbox mailboxDaoEntry;
        private final Mailbox mailboxPathEntry;
        private final MailboxMergingTaskRunner mergingRunner;
        private final boolean autoMerge;

        private ConflictingEntryInconsistency(Mailbox mailboxDaoEntry, Mailbox mailboxPathEntry,
                                              MailboxMergingTaskRunner mergingRunner, boolean autoMerge) {
            this.mailboxDaoEntry = mailboxDaoEntry;
            this.mailboxPathEntry = mailboxPathEntry;
            this.mergingRunner = mergingRunner;
            this.autoMerge = autoMerge;
        }

        @Override
        public Mono<Result> fix(Context context, CassandraMailboxDAO mailboxDAO, CassandraMailboxPathV3DAO pathV3DAO) {
            if (mailboxDaoEntry.getMailboxId().equals(mailboxPathEntry.getMailboxId())) {
                // Same mailbox referenced by two different paths. This happens when a rename (or any
                // partial path update) failed to drop the old path reference. As both registrations
                // point to the same mailbox id, the messages (keyed by id) are safe regardless of the
                // path we keep: we keep the most recently written path reference, realign the
                // projection on it and drop the stale one.
                return fixSameMailboxConflict(context, mailboxDAO, pathV3DAO);
            }

            if (autoMerge) {
                return autoMergeConflict(context, mailboxDAO, pathV3DAO);
            }
            return reportConflict(context);
        }

        // Two *different* mailboxes resolve to the same path (the historical "ghost mailbox"): the
        // path table registers the winner, while the loser is a projection entry squatting that path.
        // The path table being the source of truth, the registered mailbox wins and the squatting
        // projection is merged into it (its messages and rights are moved over, then its projection
        // row is dropped) using the same machinery as MailboxMergingTask.
        //
        // We only merge once re-reading (STRONG) confirms the clean-ghost picture: the winner still
        // owns the path, the loser still resolves to it, and -- crucially -- the path table vouches
        // for the loser *nowhere*. If the loser owns another path, it is a genuine mailbox with its
        // own (same-id) conflict to resolve first, so we report rather than destroy it.
        private Mono<Result> autoMergeConflict(Context context, CassandraMailboxDAO mailboxDAO, CassandraMailboxPathV3DAO pathV3DAO) {
            CassandraId winnerId = (CassandraId) mailboxPathEntry.getMailboxId();
            CassandraId loserId = (CassandraId) mailboxDaoEntry.getMailboxId();
            MailboxPath conflictingPath = mailboxPathEntry.generateAssociatedPath();

            Mono<Boolean> pathStillOwnedByWinner = pathV3DAO.retrieve(conflictingPath, STRONG)
                .map(entry -> entry.getMailboxId().equals(winnerId))
                .defaultIfEmpty(false);
            Mono<Boolean> loserStillResolvesToPath = mailboxDAO.retrieveMailbox(loserId)
                .map(projection -> projection.generateAssociatedPath().equals(conflictingPath))
                .defaultIfEmpty(false);
            Mono<Boolean> loserIsUnregistered = pathV3DAO.listUserMailboxes(mailboxDaoEntry.getNamespace(), mailboxDaoEntry.getUser(), STRONG)
                .filter(entry -> entry.getMailboxId().equals(loserId))
                .hasElements()
                .map(referenced -> !referenced);

            return Mono.zip(pathStillOwnedByWinner, loserStillResolvesToPath, loserIsUnregistered)
                .flatMap(state -> {
                    boolean cleanGhost = state.getT1() && state.getT2() && state.getT3();
                    if (!cleanGhost) {
                        // State no longer matches the clean-ghost picture (already reconciled, or the
                        // loser owns another path): do not merge.
                        if (state.getT1() && state.getT2()) {
                            // Still conflicting but the loser is registered elsewhere: leave it to an admin.
                            return reportConflict(context);
                        }
                        return Mono.just(Result.COMPLETED);
                    }
                    return mergingRunner.runReactive(loserId, winnerId, new MailboxMergingTask.Context(0))
                        .doOnNext(result -> {
                            LOGGER.info("Auto-merged ghost mailbox {} into {} at path {}",
                                loserId.serialize(), winnerId.serialize(), conflictingPath.asString());
                            context.addFixedInconsistency(winnerId);
                        });
                });
        }

        // Auto-resolution is restricted to the case where BOTH the conflicting path entry and the
        // projection's path are registered to the same mailbox id: only then are they two genuine
        // aliases of a single mailbox, hence safe to deduplicate without data loss. If the
        // projection's path is unregistered or held by another mailbox (e.g. a reference loop), we
        // fall back to the conservative reporting so an admin can merge.
        //
        // As fixes are applied sequentially, we re-read the current state first: a previous fix may
        // already have reconciled this mailbox, in which case there is nothing left to do.
        private Mono<Result> fixSameMailboxConflict(Context context, CassandraMailboxDAO mailboxDAO, CassandraMailboxPathV3DAO pathV3DAO) {
            CassandraId mailboxId = (CassandraId) mailboxPathEntry.getMailboxId();
            MailboxPath conflictingPath = mailboxPathEntry.generateAssociatedPath();

            return pathV3DAO.retrieve(conflictingPath, STRONG)
                .filter(stillRegistered -> stillRegistered.getMailboxId().equals(mailboxId))
                .flatMap(conflictingEntry -> mailboxDAO.retrieveMailbox(mailboxId)
                    .flatMap(currentProjection -> resolveSameMailboxConflict(context, mailboxDAO, pathV3DAO, currentProjection, conflictingEntry)))
                // The conflicting path entry is gone (already reconciled): nothing left to do.
                .switchIfEmpty(Mono.just(Result.COMPLETED));
        }

        private Mono<Result> resolveSameMailboxConflict(Context context, CassandraMailboxDAO mailboxDAO, CassandraMailboxPathV3DAO pathV3DAO,
                                                        Mailbox currentProjection, Mailbox conflictingEntry) {
            MailboxPath projectionPath = currentProjection.generateAssociatedPath();
            MailboxPath conflictingPath = conflictingEntry.generateAssociatedPath();
            if (projectionPath.equals(conflictingPath)) {
                // The projection now matches this path: no longer inconsistent.
                return Mono.just(Result.COMPLETED);
            }
            return pathV3DAO.retrieve(projectionPath, STRONG)
                .filter(projectionRegistration -> projectionRegistration.getMailboxId().equals(currentProjection.getMailboxId()))
                .flatMap(projectionRegistration -> Mono.zip(
                        pathV3DAO.writeTime(conflictingPath),
                        pathV3DAO.writeTime(projectionPath))
                    .flatMap(writeTimes -> {
                        boolean projectionWins = writeTimes.getT2() >= writeTimes.getT1();
                        Mailbox winner = projectionWins ? currentProjection : conflictingEntry;
                        Mailbox loser = projectionWins ? conflictingEntry : currentProjection;
                        return mailboxDAO.save(winner)
                            .then(pathV3DAO.delete(loser.generateAssociatedPath()))
                            .then(Mono.fromRunnable(() -> {
                                LOGGER.info("Inconsistency fixed for mailbox {}: kept path {}, dropped stale path {}",
                                    winner.getMailboxId().serialize(),
                                    winner.generateAssociatedPath().asString(),
                                    loser.generateAssociatedPath().asString());
                                context.addFixedInconsistency(winner.getMailboxId());
                            }))
                            .thenReturn(Result.COMPLETED);
                    }))
                .switchIfEmpty(Mono.defer(() -> reportConflict(context)));
        }

        private Mono<Result> reportConflict(Context context) {
            LOGGER.error("MailboxDAO contains mailbox {} {} which conflict with corresponding registration {} {}. " +
                "We recommend merging these mailboxes together to prevent mail data loss.",
                mailboxDaoEntry.getMailboxId(), mailboxDaoEntry.generateAssociatedPath(),
                mailboxPathEntry.getMailboxId(), mailboxPathEntry.generateAssociatedPath());
            context.addConflictingEntries(ConflictingEntry.builder()
                .mailboxDaoEntry(mailboxDaoEntry)
                .mailboxPathDaoEntry(mailboxPathEntry));
            return Mono.just(Result.PARTIAL);
        }
    }

    public static class RunningOptions {
        public static final int DEFAULT_MAX_ITERATIONS = 1;
        public static final boolean DEFAULT_AUTO_MERGE = false;
        public static final RunningOptions DEFAULT = new RunningOptions(DEFAULT_MAX_ITERATIONS, DEFAULT_AUTO_MERGE);

        private final int maxIterations;
        private final boolean autoMerge;

        public RunningOptions(int maxIterations, boolean autoMerge) {
            Preconditions.checkArgument(maxIterations >= 1, "'maxIterations' must be strictly positive");
            this.maxIterations = maxIterations;
            this.autoMerge = autoMerge;
        }

        public int getMaxIterations() {
            return maxIterations;
        }

        public boolean isAutoMerge() {
            return autoMerge;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof RunningOptions) {
                RunningOptions that = (RunningOptions) o;
                return this.maxIterations == that.maxIterations
                    && this.autoMerge == that.autoMerge;
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(maxIterations, autoMerge);
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

            public Builder errors(long count) {
                errors = Optional.of(count);
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
    private final MailboxMergingTaskRunner mergingRunner;

    @Inject
    SolveMailboxInconsistenciesService(CassandraMailboxDAO mailboxDAO, CassandraMailboxPathV3DAO mailboxPathV3DAO,
                                       CassandraSchemaVersionManager versionManager, MailboxMergingTaskRunner mergingRunner) {
        this.mailboxDAO = mailboxDAO;
        this.mailboxPathV3DAO = mailboxPathV3DAO;
        this.versionManager = versionManager;
        this.mergingRunner = mergingRunner;
    }

    public Mono<Result> fixMailboxInconsistencies(Context context) {
        return fixMailboxInconsistencies(context, RunningOptions.DEFAULT);
    }

    public Mono<Result> fixMailboxInconsistencies(Context context, RunningOptions runningOptions) {
        assertValidVersion();
        return fixUntilStable(context, runningOptions.getMaxIterations(), runningOptions.isAutoMerge());
    }

    // Reconciliation is run to a fixpoint: fixing an inconsistency in one pass (dropping a stale
    // path, merging a ghost mailbox...) can surface a new inconsistency that only a subsequent pass
    // detects. We re-run as long as a pass keeps applying fixes, bounded by maxIterations to guard
    // against oscillation. As fixes only ever grow the fixedInconsistencies list, a pass that adds
    // none means we reached a stable state.
    private Mono<Result> fixUntilStable(Context context, int remainingIterations, boolean autoMerge) {
        int fixedBefore = context.snapshot().getFixedInconsistencies().size();
        return runOnePass(context, autoMerge)
            .flatMap(result -> {
                boolean appliedFixes = context.snapshot().getFixedInconsistencies().size() > fixedBefore;
                if (appliedFixes && remainingIterations > 1) {
                    return fixUntilStable(context, remainingIterations - 1, autoMerge)
                        .map(nextResult -> Task.combine(result, nextResult));
                }
                return Mono.just(result);
            });
    }

    private Mono<Result> runOnePass(Context context, boolean autoMerge) {
        return Flux.concat(
                processMailboxDaoInconsistencies(context, autoMerge),
                processMailboxPathDaoInconsistencies(context, autoMerge))
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

    private Flux<Result> processMailboxPathDaoInconsistencies(Context context, boolean autoMerge) {
        return mailboxPathV3DAO.listAll()
            .flatMap(entry -> detectMailboxPathDaoInconsistency(entry, autoMerge), DEFAULT_CONCURRENCY)
            .doOnNext(any -> context.incrementProcessedMailboxPathEntries())
            // Detect every inconsistency first, then fix them one at a time. Resolving a same-mailbox
            // conflict may realign the projection, so a fully materialized detection set fixed
            // sequentially prevents a fix from racing with the detection or resolution of a sibling
            // path entry. Consistent entries are filtered out to keep the materialized set small;
            // each fix re-confirms the inconsistency against the current state before acting.
            .filter(inconsistency -> inconsistency != NO_INCONSISTENCY)
            .collectList()
            .flatMapMany(inconsistencies -> Flux.fromIterable(inconsistencies)
                .concatMap(inconsistency -> inconsistency.fix(context, mailboxDAO, mailboxPathV3DAO)));
    }

    private Flux<Result> processMailboxDaoInconsistencies(Context context, boolean autoMerge) {
        return mailboxDAO.retrieveAllMailboxes()
            .flatMap(entry -> detectMailboxDaoInconsistency(entry, autoMerge), DEFAULT_CONCURRENCY)
            .doOnNext(any -> context.incrementProcessedMailboxEntries())
            // Detect every inconsistency first, then fix them one at a time. Auto-merging a ghost
            // mailbox mutates message and projection state, so a fully materialized detection set
            // fixed sequentially prevents a fix from racing with the detection of a sibling entry.
            .filter(inconsistency -> inconsistency != NO_INCONSISTENCY)
            .collectList()
            .flatMapMany(inconsistencies -> Flux.fromIterable(inconsistencies)
                .concatMap(inconsistency -> inconsistency.fix(context, mailboxDAO, mailboxPathV3DAO)));
    }

    private Mono<Inconsistency> detectMailboxDaoInconsistency(Mailbox mailboxEntry, boolean autoMerge) {
        Mono<Mailbox> pathEntry = mailboxPathV3DAO.retrieve(mailboxEntry.generateAssociatedPath(), STRONG);
        return Inconsistency.detectMailboxDaoInconsistency(mailboxEntry, pathEntry, mergingRunner, autoMerge);
    }

    private Mono<Inconsistency> detectMailboxPathDaoInconsistency(Mailbox mailboxByPathEntry, boolean autoMerge) {
        CassandraId cassandraId = (CassandraId) mailboxByPathEntry.getMailboxId();

        Mono<Mailbox> mailboxEntry = mailboxDAO.retrieveMailbox(cassandraId);
        return Inconsistency.detectMailboxPathDaoInconsistency(mailboxByPathEntry, mailboxEntry, mergingRunner, autoMerge);
    }
}
