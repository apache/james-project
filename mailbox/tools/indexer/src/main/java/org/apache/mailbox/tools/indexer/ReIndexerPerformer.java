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

package org.apache.mailbox.tools.indexer;

import static org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY;

import java.time.Duration;

import javax.inject.Inject;
import javax.mail.Flags;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.indexer.ReIndexer.RunningOptions;
import org.apache.james.mailbox.indexer.ReIndexingExecutionFailures;
import org.apache.james.mailbox.indexer.ReIndexingExecutionFailures.ReIndexingFailure;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.search.ListeningMessageSearchIndex;
import org.apache.james.task.Task;
import org.apache.james.task.Task.Result;
import org.apache.james.util.ReactorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

import io.vavr.control.Either;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ReIndexerPerformer {
    public static final int MAILBOX_CONCURRENCY = 1;
    public static final int ONE = 1;
    public static final Duration IDEXING_TIMEOUT = Duration.ofMinutes(1);

    private static class ReIndexingEntry {
        private final Mailbox mailbox;
        private final MailboxSession mailboxSession;
        private final MessageUid uid;

        ReIndexingEntry(Mailbox mailbox, MailboxSession mailboxSession, MessageUid uid) {
            this.mailbox = mailbox;
            this.mailboxSession = mailboxSession;
            this.uid = uid;
        }

        public Mailbox getMailbox() {
            return mailbox;
        }

        public MessageUid getUid() {
            return uid;
        }

        public MailboxSession getMailboxSession() {
            return mailboxSession;
        }
    }

    private interface Failure {
        void recordFailure(ReIndexingContext context);
    }

    private static class MailboxFailure implements Failure {
        private final MailboxId mailboxId;

        private MailboxFailure(MailboxId mailboxId) {
            this.mailboxId = mailboxId;
        }

        public MailboxId getMailboxId() {
            return mailboxId;
        }

        @Override
        public void recordFailure(ReIndexingContext context) {
            context.recordMailboxFailure(mailboxId);
        }
    }

    private static class MessageFailure implements Failure {
        private final MailboxId mailboxId;
        private final MessageUid uid;

        private MessageFailure(MailboxId mailboxId, MessageUid uid) {
            this.mailboxId = mailboxId;
            this.uid = uid;
        }

        public MailboxId getMailboxId() {
            return mailboxId;
        }

        public MessageUid getUid() {
            return uid;
        }

        @Override
        public void recordFailure(ReIndexingContext context) {
            context.recordFailureDetailsForMessage(mailboxId, uid);
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ReIndexerPerformer.class);

    private static final int SINGLE_MESSAGE = 1;
    private static final String RE_INDEXING = "re-indexing";
    private static final Username RE_INDEXER_PERFORMER_USER = Username.of(RE_INDEXING);

    private final MailboxManager mailboxManager;
    private final ListeningMessageSearchIndex messageSearchIndex;
    private final MailboxSessionMapperFactory mailboxSessionMapperFactory;

    @Inject
    public ReIndexerPerformer(MailboxManager mailboxManager,
                              ListeningMessageSearchIndex messageSearchIndex,
                              MailboxSessionMapperFactory mailboxSessionMapperFactory) {
        this.mailboxManager = mailboxManager;
        this.messageSearchIndex = messageSearchIndex;
        this.mailboxSessionMapperFactory = mailboxSessionMapperFactory;
    }

    Mono<Result> reIndexAllMessages(ReIndexingContext reIndexingContext, RunningOptions runningOptions) {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(RE_INDEXER_PERFORMER_USER);
        LOGGER.info("Starting a full reindex");

        Flux<Either<Failure, ReIndexingEntry>> entriesToIndex = mailboxSessionMapperFactory.getMailboxMapper(mailboxSession).list()
            .flatMap(mailbox -> reIndexingEntriesForMailbox(mailbox, mailboxSession, runningOptions), MAILBOX_CONCURRENCY);

        return reIndexMessages(entriesToIndex, runningOptions, reIndexingContext)
            .doFinally(any -> LOGGER.info("Full reindex finished"));
    }

    Mono<Result> reIndexSingleMailbox(MailboxId mailboxId, ReIndexingContext reIndexingContext, RunningOptions runningOptions) {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(RE_INDEXER_PERFORMER_USER);

        Flux<Either<Failure, ReIndexingEntry>> entriesToIndex = mailboxSessionMapperFactory.getMailboxMapper(mailboxSession)
            .findMailboxById(mailboxId)
            .flatMapMany(mailbox -> reIndexingEntriesForMailbox(mailbox, mailboxSession, runningOptions));

        return reIndexMessages(entriesToIndex, runningOptions, reIndexingContext);
    }

    Mono<Result> reIndexUserMailboxes(Username username, ReIndexingContext reIndexingContext, RunningOptions runningOptions) {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(username);
        MailboxMapper mailboxMapper = mailboxSessionMapperFactory.getMailboxMapper(mailboxSession);
        LOGGER.info("Starting a reindex for user {}", username.asString());

        MailboxQuery mailboxQuery = MailboxQuery.privateMailboxesBuilder(mailboxSession).build();

        try {
            Flux<Either<Failure, ReIndexingEntry>> entriesToIndex = mailboxMapper.findMailboxWithPathLike(mailboxQuery.asUserBound())
                .flatMap(mailbox -> reIndexingEntriesForMailbox(mailbox, mailboxSession, runningOptions), MAILBOX_CONCURRENCY);

            return reIndexMessages(entriesToIndex, runningOptions, reIndexingContext)
                .doFinally(any -> LOGGER.info("User {} reindex finished", username.asString()));
        } catch (Exception e) {
            LOGGER.error("Error fetching mailboxes for user: {}", username.asString());
            return Mono.just(Result.PARTIAL);
        }
    }

    Mono<Result> reIndexSingleMessage(MailboxId mailboxId, MessageUid uid, ReIndexingContext reIndexingContext) {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(RE_INDEXER_PERFORMER_USER);

        return mailboxSessionMapperFactory.getMailboxMapper(mailboxSession)
            .findMailboxById(mailboxId)
            .map(mailbox -> new ReIndexingEntry(mailbox, mailboxSession, uid))
            .flatMap(this::fullyReadMessage)
            .flatMap(message -> reIndex(message, mailboxSession))
            .switchIfEmpty(Mono.just(Result.COMPLETED));
    }

    Mono<Result> reIndexMessageId(MessageId messageId) {
        MailboxSession session = mailboxManager.createSystemSession(RE_INDEXER_PERFORMER_USER);

        return mailboxSessionMapperFactory.getMessageIdMapper(session)
            .findReactive(ImmutableList.of(messageId), MessageMapper.FetchType.FULL)
            .flatMap(mailboxMessage -> reIndex(mailboxMessage, session), DEFAULT_CONCURRENCY)
            .reduce(Task::combine)
            .switchIfEmpty(Mono.just(Result.COMPLETED))
            .onErrorResume(e -> {
                LOGGER.warn("Failed to re-index {}", messageId, e);
                return Mono.just(Result.PARTIAL);
            });
    }

    Mono<Result> reIndexErrors(ReIndexingContext reIndexingContext, ReIndexingExecutionFailures previousReIndexingFailures, RunningOptions runningOptions) {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(RE_INDEXER_PERFORMER_USER);
        MailboxMapper mapper = mailboxSessionMapperFactory.getMailboxMapper(mailboxSession);

        Flux<Either<Failure, ReIndexingEntry>> entriesToIndex = Flux.merge(
            Flux.fromIterable(previousReIndexingFailures.messageFailures())
                .flatMap(this::createReindexingEntryFromFailure, ReactorUtils.DEFAULT_CONCURRENCY),
            Flux.fromIterable(previousReIndexingFailures.mailboxFailures())
                .flatMap(mailboxId -> mapper.findMailboxById(mailboxId)
                    .flatMapMany(mailbox -> reIndexingEntriesForMailbox(mailbox, mailboxSession, runningOptions))
                    .onErrorResume(e -> {
                        LOGGER.warn("Failed to re-index {}", mailboxId, e);
                        return Mono.just(Either.left(new MailboxFailure(mailboxId)));
                    }), MAILBOX_CONCURRENCY));

        return reIndexMessages(entriesToIndex, runningOptions, reIndexingContext);
    }

    private Mono<Result> reIndex(MailboxMessage mailboxMessage, MailboxSession session) {
        return mailboxSessionMapperFactory.getMailboxMapper(session)
            .findMailboxById(mailboxMessage.getMailboxId())
            .flatMap(mailbox -> messageSearchIndex.add(session, mailbox, mailboxMessage))
            .thenReturn(Result.COMPLETED)
            .onErrorResume(e -> {
                LOGGER.warn("Failed to re-index {} in {}", mailboxMessage.getUid(), mailboxMessage.getMailboxId(), e);
                return Mono.just(Result.PARTIAL);
            });
    }

    private Mono<MailboxMessage> fullyReadMessage(ReIndexingEntry entry) {
        return mailboxSessionMapperFactory.getMessageMapper(entry.getMailboxSession())
            .findInMailboxReactive(entry.getMailbox(), MessageRange.one(entry.getUid()), MessageMapper.FetchType.FULL, SINGLE_MESSAGE)
            .next();
    }

    private Mono<Either<Failure, ReIndexingEntry>> createReindexingEntryFromFailure(ReIndexingFailure previousFailure) {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(RE_INDEXER_PERFORMER_USER);

        return mailboxSessionMapperFactory.getMailboxMapper(mailboxSession)
            .findMailboxById(previousFailure.getMailboxId())
            .map(mailbox ->  Either.<Failure, ReIndexingEntry>right(new ReIndexingEntry(mailbox, mailboxSession,  previousFailure.getUid())))
            .onErrorResume(e -> {
                LOGGER.warn("ReIndexing failed for {}", previousFailure, e);
                return Mono.just(Either.left(new MessageFailure(previousFailure.getMailboxId(), previousFailure.getUid())));
            });
    }

    private Flux<Either<Failure, ReIndexingEntry>> reIndexingEntriesForMailbox(Mailbox mailbox, MailboxSession mailboxSession, RunningOptions runningOptions) {
        MessageMapper messageMapper = mailboxSessionMapperFactory.getMessageMapper(mailboxSession);

        return updateSearchIndex(mailbox, mailboxSession, runningOptions)
            .thenMany(messageMapper.listAllMessageUids(mailbox))
            .map(uid -> Either.<Failure, ReIndexingEntry>right(new ReIndexingEntry(mailbox, mailboxSession, uid)))
            .onErrorResume(e -> {
                LOGGER.warn("ReIndexing failed for {}", mailbox.generateAssociatedPath(), e);
                return Mono.just(Either.left(new MailboxFailure(mailbox.getMailboxId())));
            });
    }

    private Mono<Void> updateSearchIndex(Mailbox mailbox, MailboxSession mailboxSession, RunningOptions runningOptions) {
        switch (runningOptions.getMode()) {
            case REBUILD_ALL:
                return messageSearchIndex.deleteAll(mailboxSession, mailbox.getMailboxId());
            case REBUILD_ALL_NO_CLEANUP:
            case FIX_OUTDATED:
            default:
                return Mono.empty();
        }
    }

    private Mono<Task.Result> reIndexMessages(Flux<Either<Failure, ReIndexingEntry>> entriesToIndex, RunningOptions runningOptions, ReIndexingContext reIndexingContext) {
        return entriesToIndex.transform(
            ReactorUtils.<Either<Failure, ReIndexingEntry>, Task.Result>throttle()
                .elements(runningOptions.getMessagesPerSecond())
                .per(Duration.ofSeconds(1))
                .forOperation(entry -> reIndex(entry, reIndexingContext, runningOptions)))
            .reduce(Task::combine)
            .switchIfEmpty(Mono.just(Result.COMPLETED));
    }

    private Mono<Task.Result> reIndex(Either<Failure, ReIndexingEntry> failureOrEntry, ReIndexingContext reIndexingContext, RunningOptions runningOptions) {
        return toMono(failureOrEntry.map(entry -> reIndex(entry, runningOptions)))
            .map(this::flatten)
            .map(failureOrTaskResult -> recordIndexingResult(failureOrTaskResult, reIndexingContext));
    }

    private Result recordIndexingResult(Either<Failure, Result> failureOrTaskResult, ReIndexingContext reIndexingContext) {
        return failureOrTaskResult.fold(
            failure -> {
                failure.recordFailure(reIndexingContext);
                return Result.PARTIAL;
            },
            result -> result.onComplete(reIndexingContext::recordSuccess));
    }

    private Mono<Either<Failure, Result>> reIndex(ReIndexingEntry entry, RunningOptions runningOptions) {
        if (runningOptions.getMode() == RunningOptions.Mode.FIX_OUTDATED) {
            return correctIfNeeded(entry)
                .timeout(IDEXING_TIMEOUT);
        }
        return index(entry)
            .timeout(IDEXING_TIMEOUT);
    }

    private Mono<Either<Failure, Result>> index(ReIndexingEntry entry) {
        return fullyReadMessage(entry)
            .flatMap(message -> messageSearchIndex.add(entry.getMailboxSession(), entry.getMailbox(), message))
            .thenReturn(Either.<Failure, Result>right(Result.COMPLETED))
            .onErrorResume(e -> {
                LOGGER.warn("ReIndexing failed for {} {}", entry.getMailbox().generateAssociatedPath(), entry.getUid(), e);
                return Mono.just(Either.left(new MessageFailure(entry.getMailbox().getMailboxId(), entry.getUid())));
            });
    }

    private Mono<Either<Failure, Result>> correctIfNeeded(ReIndexingEntry entry) {
        MessageMapper messageMapper = mailboxSessionMapperFactory.getMessageMapper(entry.getMailboxSession());

        return messageMapper.findInMailboxReactive(entry.getMailbox(), MessageRange.one(entry.getUid()), MessageMapper.FetchType.METADATA, ONE)
            .next()
            .flatMap(message -> isIndexUpToDate(entry.getMailbox(), message)
                .flatMap(upToDate -> {
                    if (upToDate) {
                        return Mono.just(Either.<Failure, Result>right(Result.COMPLETED));
                    }
                    return correct(entry);
                }))
            .onErrorResume(e -> {
                LOGGER.warn("ReIndexing failed for {} {}", entry.getMailbox().generateAssociatedPath(), entry.getUid(), e);
                return Mono.just(Either.left(new MessageFailure(entry.getMailbox().getMailboxId(), entry.getUid())));
            });
    }

    private Mono<Either<Failure, Result>> correct(ReIndexingEntry entry) {
        // Leverage the fact that index does an upsert, removing pre-existing documents
        // We do not need to delete the existing document before indexing it
        return index(entry);
    }

    private Mono<Boolean> isIndexUpToDate(Mailbox mailbox, MailboxMessage message) {
        return messageSearchIndex.retrieveIndexedFlags(mailbox, message.getUid())
            .map(flags -> isIndexUpToDate(message, flags))
            .switchIfEmpty(Mono.just(false));
    }

    private boolean isIndexUpToDate(MailboxMessage message, Flags flags) {
        return message.createFlags().equals(flags);
    }

    private <X, Y> Either<X, Y> flatten(Either<X, Either<X, Y>> nestedEither) {
        return nestedEither.getOrElseGet(Either::left);
    }

    private <X, Y> Mono<Either<X, Y>> toMono(Either<X, Mono<Y>> either) {
        return either.fold(x -> Mono.just(Either.left(x)), yMono -> yMono.map(Either::right));
    }
}