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

package org.apache.james.webadmin.service;

import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.SearchQuery.DateResolution;
import org.apache.james.task.Task;
import org.apache.james.task.Task.Result;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.util.DurationParser;
import org.apache.james.util.ReactorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


public class ExpireMailboxService {

    public static class RunningOptions {
        public static final RunningOptions DEFAULT = new RunningOptions(1, MailboxConstants.INBOX,
            Optional.of(false), Optional.empty(), true, Optional.empty());

        public static RunningOptions fromParams(
                Optional<String> byExpiresHeader, Optional<String> olderThan,
                Optional<String> usersPerSecond, Optional<String> mailbox,
                boolean useSavedDate, Optional<String> user) {
            try {
                if (byExpiresHeader.isPresent() == olderThan.isPresent()) {
                    throw new IllegalArgumentException("Must specify either 'olderThan' or 'byExpiresHeader' parameter");
                }
                return new RunningOptions(
                    usersPerSecond.map(Integer::parseInt).orElse(DEFAULT.getUsersPerSecond()),
                    mailbox.orElse(DEFAULT.getMailbox()), Optional.of(useSavedDate), user,
                    byExpiresHeader.isPresent(), olderThan);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("'usersPerSecond' must be numeric");
            }
        }
        
        private final int usersPerSecond;
        
        private final String mailbox;
        
        private final boolean byExpiresHeader;

        private final Optional<String> olderThan;
        private final Optional<Boolean> useSavedDate;
        private final Optional<String> user;

        @JsonIgnore
        private final Optional<Duration> maxAgeDuration;

        @JsonCreator
        public RunningOptions(@JsonProperty("usersPerSecond") int usersPerSecond,
                              @JsonProperty("mailbox") String mailbox,
                              @JsonProperty("useSavedDate") Optional<Boolean> useSavedDate,
                              @JsonProperty("user") Optional<String> user,
                              @JsonProperty("byExpiresHeader") boolean byExpiresHeader,
                              @JsonProperty("olderThan") Optional<String> olderThan) {
            Preconditions.checkArgument(usersPerSecond > 0, "'usersPerSecond' must be strictly positive");
            this.usersPerSecond = usersPerSecond;
            this.mailbox = mailbox;
            this.byExpiresHeader = byExpiresHeader;
            this.useSavedDate = useSavedDate;
            this.user = user;
            this.olderThan = olderThan;
            this.maxAgeDuration = olderThan.map(v -> DurationParser.parse(olderThan.get(), ChronoUnit.DAYS));
        }

        public int getUsersPerSecond() {
            return usersPerSecond;
        }
        
        public String getMailbox() { 
            return mailbox;
        }

        public boolean getByExpiresHeader() {
            return byExpiresHeader;
        }

        public Optional<String> getOlderThan() {
            return olderThan;
        }

        public Optional<Boolean> getUseSavedDate() {
            return useSavedDate;
        }

        public Optional<String> getUser() {
            return user;
        }
    }

    public static class Context {
        private final AtomicLong inboxesExpired;
        private final AtomicLong inboxesFailed;
        private final AtomicLong inboxesProcessed;
        private final AtomicLong messagesDeleted;

        public Context() {
            this.inboxesExpired = new AtomicLong(0L);
            this.inboxesFailed = new AtomicLong(0L);
            this.inboxesProcessed = new AtomicLong(0L);
            this.messagesDeleted = new AtomicLong(0L);
        }

        public long getInboxesExpired() {
            return inboxesExpired.get();
        }

        public long getInboxesFailed() {
            return inboxesFailed.get();
        }

        public long getInboxesProcessed() {
            return inboxesProcessed.get();
        }

        public long getMessagesDeleted() {
            return messagesDeleted.get();
        }

        public void incrementExpiredCount() {
            inboxesExpired.incrementAndGet();
        }

        public void incrementFailedCount() {
            inboxesFailed.incrementAndGet();
        }

        public void incrementProcessedCount() {
            inboxesProcessed.incrementAndGet();
        }
        
        public void incrementMessagesDeleted(long count) { 
            messagesDeleted.addAndGet(count);
        }
    }

    private interface MessageListingStrategy {
        Mono<List<MessageUid>> listMessages(MessageManager messageManager, RunningOptions runningOptions, MailboxSession session);

        class InternalDateBeforeListingStrategy implements MessageListingStrategy {
            private final Clock clock;

            public InternalDateBeforeListingStrategy(Clock clock) {
                this.clock = clock;
            }

            @Override
            public Mono<List<MessageUid>> listMessages(MessageManager messageManager, RunningOptions runningOptions, MailboxSession session) {
                Preconditions.checkArgument(runningOptions.maxAgeDuration.isPresent());

                Date limit = Date.from(clock.instant().minus(runningOptions.maxAgeDuration.get()));
                SearchQuery.Criterion internalDateBefore = SearchQuery.internalDateBefore(limit, DateResolution.Second);
                try {
                    return Flux.from(messageManager.search(SearchQuery.of(internalDateBefore), session))
                        .collectList();
                } catch (MailboxException e) {
                    return Mono.error(e);
                }
            }
        }

        class SavedDateDateBeforeListingStrategy implements MessageListingStrategy {
            private final Clock clock;

            public SavedDateDateBeforeListingStrategy(Clock clock) {
                this.clock = clock;
            }

            @Override
            public Mono<List<MessageUid>> listMessages(MessageManager messageManager, RunningOptions runningOptions, MailboxSession session) {
                Preconditions.checkArgument(runningOptions.maxAgeDuration.isPresent());
                Preconditions.checkArgument(runningOptions.getUseSavedDate().equals(Optional.of(true)));

                Date limit = Date.from(clock.instant().minus(runningOptions.maxAgeDuration.get()));

                return Flux.from(messageManager.getMessagesReactive(MessageRange.all(), FetchGroup.MINIMAL, session))
                    .filter(message -> message.getSaveDate().map(savedDate -> savedDate.compareTo(limit) < 0).orElse(true))
                    .map(MessageResult::getUid)
                    .collectList();
            }
        }

        class HeaderBasedMessageListingStrategy implements MessageListingStrategy {
            private final Clock clock;

            public HeaderBasedMessageListingStrategy(Clock clock) {
                this.clock = clock;
            }

            @Override
            public Mono<List<MessageUid>> listMessages(MessageManager messageManager, RunningOptions runningOptions, MailboxSession session) {
                SearchQuery.Criterion criterion = SearchQuery.headerDateBefore("Expires", Date.from(clock.instant()), DateResolution.Second);
                try {
                    return Flux.from(messageManager.search(SearchQuery.of(criterion), session))
                        .collectList();
                } catch (MailboxException e) {
                    return Mono.error(e);
                }
            }
        }

        class Factory {
            private final Clock clock;

            public Factory(Clock clock) {
                this.clock = clock;
            }

            MessageListingStrategy choose(RunningOptions runningOptions) {
                if (runningOptions.byExpiresHeader) {
                    return new HeaderBasedMessageListingStrategy(clock);
                }
                Preconditions.checkArgument(runningOptions.maxAgeDuration.isPresent());
                if (runningOptions.getUseSavedDate().equals(Optional.of(true))) {
                    return new SavedDateDateBeforeListingStrategy(clock);
                }
                return new InternalDateBeforeListingStrategy(clock);
            }
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ExpireMailboxService.class);

    private final UsersRepository usersRepository;
    private final MailboxManager mailboxManager;
    private final MessageListingStrategy.Factory factory;

    @Inject
    public ExpireMailboxService(UsersRepository usersRepository, MailboxManager mailboxManager, Clock clock) {
        this.usersRepository = usersRepository;
        this.mailboxManager = mailboxManager;
        this.factory = new MessageListingStrategy.Factory(clock);
    }

    public Mono<Result> expireMailboxes(Context context, RunningOptions runningOptions) {
        return users(runningOptions)
            .transform(ReactorUtils.<Username, Task.Result>throttle()
                .elements(runningOptions.getUsersPerSecond())
                .per(Duration.ofSeconds(1))
                .forOperation(username -> loadMailbox(runningOptions, username)
                    .flatMap(mailboxWithSession -> expireMessages(context, runningOptions, mailboxWithSession))))
            .reduce(Task.Result.COMPLETED, Task::combine)
            .onErrorResume(UsersRepositoryException.class, e -> {
                LOGGER.error("Error while accessing users from repository", e);
                return Mono.just(Task.Result.PARTIAL);
            });
    }

    private Flux<Username> users(RunningOptions runningOptions) {
        return runningOptions.user
            .map(Username::of)
            .map(Flux::just)
            .orElseGet(() -> Flux.from(usersRepository.listReactive()));
    }

    private Mono<Pair<MessageManager, MailboxSession>> loadMailbox(RunningOptions runningOptions, Username username) {
        MailboxSession session = mailboxManager.createSystemSession(username);
        MailboxPath mailboxPath = MailboxPath.forUser(username, runningOptions.getMailbox());

        return Mono.from(mailboxManager.getMailboxReactive(mailboxPath, session))
            // newly created users do not have mailboxes yet, just skip them
            .onErrorResume(MailboxNotFoundException.class, ignore -> Mono.empty())
            .map(messageManager -> Pair.of(messageManager, session));
    }

    private Mono<Result> expireMessages(Context context, RunningOptions runningOptions, Pair<MessageManager, MailboxSession> mailboxWithSession) {
        return factory.choose(runningOptions)
            .listMessages(mailboxWithSession.getKey(), runningOptions, mailboxWithSession.getValue())
            .flatMap(uids -> deleteMessagesReactive(mailboxWithSession.getKey(), mailboxWithSession.getValue(), uids))
            .doOnNext(expired -> recordSuccess(context, expired))
            .then(Mono.just(Result.COMPLETED))
            .onErrorResume(e -> manageFailure(context, mailboxWithSession, e));
    }

    private Mono<Integer> deleteMessagesReactive(MessageManager mgr, MailboxSession session, List<MessageUid> uids) {
        if (uids.isEmpty()) {
            return Mono.just(0);
        } else {
            return Flux.fromIterable(uids)
                .window(128)
                .flatMap(Flux::collectList)
                .concatMap(u -> mgr.deleteReactive(u, session).thenReturn(u.size()))
                .reduce(Integer::sum);
        }
    }

    private Mono<Result> manageFailure(Context context, Pair<MessageManager, MailboxSession> mailboxWithSession, Throwable e) {
        LOGGER.warn("Failed to expire user mailbox {}", mailboxWithSession.getValue().getUser().asString(), e);
        context.incrementFailedCount();
        context.incrementProcessedCount();
        return Mono.just(Result.PARTIAL);
    }

    private void recordSuccess(Context context, Integer expired) {
        if (expired > 0) {
            context.incrementExpiredCount();
            context.incrementMessagesDeleted(expired);
        }
        context.incrementProcessedCount();
    }
}
