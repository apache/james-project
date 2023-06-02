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

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import javax.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
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
        public static final RunningOptions DEFAULT = new RunningOptions(1, MailboxConstants.INBOX, true, Optional.empty());

        public static RunningOptions fromParams(
                Optional<String> byExpiresHeader, Optional<String> olderThan,
                Optional<String> usersPerSecond, Optional<String> mailbox) {
            try {
                if (byExpiresHeader.isPresent() == olderThan.isPresent()) {
                    throw new IllegalArgumentException("Must specify either 'olderThan' or 'byExpiresHeader' parameter");
                }
                return new RunningOptions(
                    usersPerSecond.map(Integer::parseInt).orElse(DEFAULT.getUsersPerSecond()),
                    mailbox.orElse(DEFAULT.getMailbox()), byExpiresHeader.isPresent(), olderThan);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("'usersPerSecond' must be numeric");
            }
        }
        
        private final int usersPerSecond;
        
        private final String mailbox;
        
        private final boolean byExpiresHeader;

        private final Optional<String> olderThan;

        @JsonIgnore
        private final Optional<Duration> maxAgeDuration;

        @JsonCreator
        public RunningOptions(@JsonProperty("usersPerSecond") int usersPerSecond,
                              @JsonProperty("mailbox") String mailbox,
                              @JsonProperty("byExpiresHeader") boolean byExpiresHeader,
                              @JsonProperty("olderThan") Optional<String> olderThan) {
            Preconditions.checkArgument(usersPerSecond > 0, "'usersPerSecond' must be strictly positive");
            this.usersPerSecond = usersPerSecond;
            this.mailbox = mailbox;
            this.byExpiresHeader = byExpiresHeader;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(ExpireMailboxService.class);

    private final UsersRepository usersRepository;
    private final MailboxManager mailboxManager;

    @Inject
    public ExpireMailboxService(UsersRepository usersRepository, MailboxManager mailboxManager) {
        this.usersRepository = usersRepository;
        this.mailboxManager = mailboxManager;
    }

    public Mono<Result> expireMailboxes(Context context, RunningOptions runningOptions, Date now) {
        SearchQuery expiration = SearchQuery.of(
            runningOptions.maxAgeDuration.map(maxAge -> {
                Date limit = Date.from(now.toInstant().minus(maxAge));
                return SearchQuery.internalDateBefore(limit, DateResolution.Second);
            }).orElse(SearchQuery.headerDateBefore("Expires", now, DateResolution.Second)));

        return Flux.from(usersRepository.listReactive())
            .transform(ReactorUtils.<Username, Task.Result>throttle()
                .elements(runningOptions.getUsersPerSecond())
                .per(Duration.ofSeconds(1))
                .forOperation(username ->
                    expireUserMailbox(context, username, runningOptions.getMailbox(), expiration)))
            .reduce(Task.Result.COMPLETED, Task::combine)

            .onErrorResume(UsersRepositoryException.class, e -> {
                LOGGER.error("Error while accessing users from repository", e);
                return Mono.just(Task.Result.PARTIAL);
            });
    }

    private Mono<Result> expireUserMailbox(Context context, Username username, String mailbox, SearchQuery expiration) {
        MailboxSession session = mailboxManager.createSystemSession(username);
        MailboxPath mailboxPath = MailboxPath.forUser(username, mailbox);
        return Mono.from(mailboxManager.getMailboxReactive(mailboxPath, session))
            // newly created users do not have mailboxes yet, just skip them
            .onErrorResume(MailboxNotFoundException.class, ignore -> Mono.empty())
            .flatMap(mgr -> searchMessagesReactive(mgr, session, expiration)
                .flatMap(list -> deleteMessagesReactive(mgr, session, list)))
            .doOnNext(expired -> {
                if (expired > 0) {
                    context.incrementExpiredCount();
                    context.incrementMessagesDeleted(expired);
                }
                context.incrementProcessedCount();
            })
            .then(Mono.just(Task.Result.COMPLETED))
            .onErrorResume(e -> {
                LOGGER.warn("Failed to expire user mailbox {}", username, e);
                context.incrementFailedCount();
                context.incrementProcessedCount();
                return Mono.just(Task.Result.PARTIAL);
            })
            .doFinally(any -> mailboxManager.endProcessingRequest(session));
    }

    private Mono<List<MessageUid>> searchMessagesReactive(MessageManager mgr, MailboxSession session, SearchQuery expiration) {
        try {
            return Flux.from(mgr.search(expiration, session)).collectList();
        } catch (MailboxException e) {
            return Mono.error(e);
        }
    }

    private Mono<Integer> deleteMessagesReactive(MessageManager mgr, MailboxSession session, List<MessageUid> uids) {
        if (uids.isEmpty()) {
            return Mono.just(0);
        } else {
            return Mono.from(mgr.deleteReactive(uids, session))
                .thenReturn(uids.size());
        }
    }
}
