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

import static org.apache.james.mailbox.store.mail.AbstractMessageMapper.UNLIMITED;

import java.time.Duration;

import javax.inject.Inject;

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

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ReIndexerPerformer {
    private static class ReIndexingEntry {
        private final Mailbox mailbox;
        private final MailboxSession mailboxSession;
        private final MailboxMessage message;

        ReIndexingEntry(Mailbox mailbox, MailboxSession mailboxSession, MailboxMessage message) {
            this.mailbox = mailbox;
            this.mailboxSession = mailboxSession;
            this.message = message;
        }

        public Mailbox getMailbox() {
            return mailbox;
        }

        public MailboxMessage getMessage() {
            return message;
        }

        public MailboxSession getMailboxSession() {
            return mailboxSession;
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

    Mono<Result> reIndexAllMessages(ReprocessingContext reprocessingContext, RunningOptions runningOptions) {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(RE_INDEXER_PERFORMER_USER);
        LOGGER.info("Starting a full reindex");

        Flux<ReIndexingEntry> entriesToIndex = mailboxSessionMapperFactory.getMailboxMapper(mailboxSession).list()
            .flatMap(mailbox -> reIndexingEntriesForMailbox(mailbox, mailboxSession));

        return reIndexMessages(entriesToIndex, runningOptions, reprocessingContext)
            .doFinally(any -> LOGGER.info("Full reindex finished"));
    }

    Mono<Result> reIndexSingleMailbox(MailboxId mailboxId, ReprocessingContext reprocessingContext, RunningOptions runningOptions) {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(RE_INDEXER_PERFORMER_USER);

        Flux<ReIndexingEntry> entriesToIndex = mailboxSessionMapperFactory.getMailboxMapper(mailboxSession)
            .findMailboxByIdReactive(mailboxId)
            .flatMapMany(mailbox -> reIndexingEntriesForMailbox(mailbox, mailboxSession));

        return reIndexMessages(entriesToIndex, runningOptions, reprocessingContext);
    }

    Mono<Result> reIndexUserMailboxes(Username username, ReprocessingContext reprocessingContext, RunningOptions runningOptions) {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(username);
        MailboxMapper mailboxMapper = mailboxSessionMapperFactory.getMailboxMapper(mailboxSession);
        LOGGER.info("Starting a reindex for user {}", username.asString());

        MailboxQuery mailboxQuery = MailboxQuery.privateMailboxesBuilder(mailboxSession).build();

        try {
            Flux<ReIndexingEntry> entriesToIndex = mailboxMapper.findMailboxWithPathLike(mailboxQuery.asUserBound())
                .flatMap(mailbox -> reIndexingEntriesForMailbox(mailbox, mailboxSession));

            return reIndexMessages(entriesToIndex, runningOptions, reprocessingContext)
                .doFinally(any -> LOGGER.info("User {} reindex finished", username.asString()));
        } catch (Exception e) {
            LOGGER.error("Error fetching mailboxes for user: {}", username.asString());
            return Mono.just(Result.PARTIAL);
        }
    }

    Mono<Result> reIndexSingleMessage(MailboxId mailboxId, MessageUid uid, ReprocessingContext reprocessingContext) {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(RE_INDEXER_PERFORMER_USER);

        return mailboxSessionMapperFactory.getMailboxMapper(mailboxSession)
            .findMailboxByIdReactive(mailboxId)
            .flatMap(mailbox -> fullyReadMessage(mailboxSession, mailbox, uid)
                .flatMap(message -> reIndexMessage(mailboxSession, mailbox, reprocessingContext, message)))
            .switchIfEmpty(Mono.just(Result.COMPLETED));
    }

    Mono<Result> reIndexMessageId(MessageId messageId) {
        MailboxSession session = mailboxManager.createSystemSession(RE_INDEXER_PERFORMER_USER);

        return mailboxSessionMapperFactory.getMessageIdMapper(session)
            .findReactive(ImmutableList.of(messageId), MessageMapper.FetchType.Full)
            .flatMap(mailboxMessage -> reIndex(mailboxMessage, session))
            .reduce(Task::combine)
            .switchIfEmpty(Mono.just(Result.COMPLETED))
            .onErrorResume(e -> {
                LOGGER.warn("Failed to re-index {}", messageId, e);
                return Mono.just(Result.PARTIAL);
            });
    }

    Mono<Result> reIndexErrors(ReprocessingContext reprocessingContext, ReIndexingExecutionFailures previousReIndexingFailures, RunningOptions runningOptions) {
        return reIndexMessages(
            Flux.fromIterable(previousReIndexingFailures.failures())
                .flatMap(this::createReindexingEntryFromFailure),
            runningOptions,
            reprocessingContext);
    }

    private Mono<Result> reIndex(MailboxMessage mailboxMessage, MailboxSession session) {
        return mailboxSessionMapperFactory.getMailboxMapper(session)
            .findMailboxByIdReactive(mailboxMessage.getMailboxId())
            .flatMap(mailbox -> messageSearchIndex.add(session, mailbox, mailboxMessage))
            .thenReturn(Result.COMPLETED)
            .onErrorResume(e -> {
                LOGGER.warn("Failed to re-index {} in {}", mailboxMessage.getUid(), mailboxMessage.getMailboxId(), e);
                return Mono.just(Result.PARTIAL);
            });
    }

    private Mono<MailboxMessage> fullyReadMessage(MailboxSession mailboxSession, Mailbox mailbox, MessageUid mUid) {
        return mailboxSessionMapperFactory.getMessageMapper(mailboxSession)
            .findInMailboxReactive(mailbox, MessageRange.one(mUid), MessageMapper.FetchType.Full, SINGLE_MESSAGE)
            .next();
    }

    private Mono<ReIndexingEntry> createReindexingEntryFromFailure(ReIndexingFailure failure) {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(RE_INDEXER_PERFORMER_USER);

        return mailboxSessionMapperFactory.getMailboxMapper(mailboxSession)
            .findMailboxByIdReactive(failure.getMailboxId())
            .flatMap(mailbox -> fullyReadMessage(mailboxSession, mailbox, failure.getUid())
                .map(message -> new ReIndexingEntry(mailbox, mailboxSession, message)));
    }

    private Flux<ReIndexingEntry> reIndexingEntriesForMailbox(Mailbox mailbox, MailboxSession mailboxSession) {
        MessageMapper messageMapper = mailboxSessionMapperFactory.getMessageMapper(mailboxSession);

        return messageSearchIndex.deleteAll(mailboxSession, mailbox.getMailboxId())
            .thenMany(messageMapper.listAllMessageUids(mailbox))
            .flatMap(uid -> messageMapper.findInMailboxReactive(mailbox, MessageRange.one(uid), MessageMapper.FetchType.Full, UNLIMITED))
            .map(message -> new ReIndexingEntry(mailbox, mailboxSession, message));
    }

    private Mono<Task.Result> reIndexMessages(Flux<ReIndexingEntry> entriesToIndex, RunningOptions runningOptions, ReprocessingContext reprocessingContext) {
        return ReactorUtils.Throttler.<ReIndexingEntry, Task.Result>forOperation(entry -> reIndexMessage(entry.getMailboxSession(), entry.getMailbox(), reprocessingContext, entry.getMessage()))
            .window(runningOptions.getMessagesPerSecond(), Duration.ofSeconds(1))
            .throttle(entriesToIndex)
            .reduce(Task::combine)
            .switchIfEmpty(Mono.just(Result.COMPLETED));
    }

    private Mono<Task.Result> reIndexMessage(MailboxSession mailboxSession, Mailbox mailbox, ReprocessingContext reprocessingContext, MailboxMessage message) {
        return Mono.fromCallable(() -> messageSearchIndex.add(mailboxSession, mailbox, message))
            .doOnNext(any -> reprocessingContext.recordSuccess())
            .thenReturn(Result.COMPLETED)
            .onErrorResume(e -> {
                LOGGER.warn("ReIndexing failed for {} {}", mailbox.generateAssociatedPath(), message.getUid(), e);
                reprocessingContext.recordFailureDetailsForMessage(mailbox.getMailboxId(), message.getUid());
                return Mono.just(Result.PARTIAL);
            });
    }
}