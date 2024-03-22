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

package org.apache.james.webadmin.data.jmap;

import static org.apache.james.mailbox.MailboxManager.MailboxSearchFetchType.Minimal;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.core.Username;
import org.apache.james.jmap.api.projections.MessageFastViewPrecomputedProperties;
import org.apache.james.jmap.api.projections.MessageFastViewProjection;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.task.Task;
import org.apache.james.task.Task.Result;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.util.ReactorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class MessageFastViewProjectionCorrector {
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageFastViewProjectionCorrector.class);
    private static final Duration PERIOD = Duration.ofSeconds(1);
    public static final int USER_CONCURRENCY = 1;
    public static final int MAILBOX_CONCURRENCY = 1;

    private static class ProjectionEntry {
        private final MessageManager messageManager;
        private final MessageUid uid;
        private final MailboxSession session;

        private ProjectionEntry(MessageManager messageManager, MessageUid uid, MailboxSession session) {
            this.messageManager = messageManager;
            this.uid = uid;
            this.session = session;
        }

        private MessageManager getMessageManager() {
            return messageManager;
        }

        private MessageUid getUid() {
            return uid;
        }

        private MailboxSession getSession() {
            return session;
        }
    }

    static class Progress {
        private final AtomicLong processedUserCount;
        private final AtomicLong processedMessageCount;
        private final AtomicLong failedUserCount;
        private final AtomicLong failedMessageCount;

        Progress() {
            failedUserCount = new AtomicLong();
            processedMessageCount = new AtomicLong();
            processedUserCount = new AtomicLong();
            failedMessageCount = new AtomicLong();
        }

        private void incrementProcessedUserCount() {
            processedUserCount.incrementAndGet();
        }

        private void incrementProcessedMessageCount() {
            processedMessageCount.incrementAndGet();
        }

        private void incrementFailedUserCount() {
            failedUserCount.incrementAndGet();
        }

        private void incrementFailedMessageCount() {
            failedMessageCount.incrementAndGet();
        }

        long getProcessedUserCount() {
            return processedUserCount.get();
        }

        long getProcessedMessageCount() {
            return processedMessageCount.get();
        }

        long getFailedUserCount() {
            return failedUserCount.get();
        }

        long getFailedMessageCount() {
            return failedMessageCount.get();
        }
    }

    private final UsersRepository usersRepository;
    private final MailboxManager mailboxManager;
    private final MessageFastViewProjection messageFastViewProjection;
    private final MessageFastViewPrecomputedProperties.Factory projectionItemFactory;

    @Inject
    MessageFastViewProjectionCorrector(UsersRepository usersRepository, MailboxManager mailboxManager,
                                       MessageFastViewProjection messageFastViewProjection,
                                       MessageFastViewPrecomputedProperties.Factory projectionItemFactory) {
        this.usersRepository = usersRepository;
        this.mailboxManager = mailboxManager;
        this.messageFastViewProjection = messageFastViewProjection;
        this.projectionItemFactory = projectionItemFactory;
    }

    Mono<Result> correctAllProjectionItems(Progress progress, RunningOptions runningOptions) {
        return correctProjection(listAllMailboxMessages(progress), runningOptions, progress);
    }

    Mono<Result> correctUsersProjectionItems(Progress progress, Username username, RunningOptions runningOptions) {
        MailboxSession session = mailboxManager.createSystemSession(username);
        return correctProjection(listUserMailboxMessages(progress, session), runningOptions, progress);
    }

    private Flux<ProjectionEntry> listAllMailboxMessages(Progress progress) {
        return Flux.from(usersRepository.listReactive())
            .map(mailboxManager::createSystemSession)
            .doOnNext(any -> progress.incrementProcessedUserCount())
            .flatMap(session -> listUserMailboxMessages(progress, session), USER_CONCURRENCY);
    }

    private Flux<ProjectionEntry> listUserMailboxMessages(Progress progress, MailboxSession session) {
        return listUsersMailboxes(session)
            .flatMap(mailboxMetadata -> retrieveMailbox(session, mailboxMetadata), MAILBOX_CONCURRENCY)
            .flatMap(Throwing.function(messageManager -> listAllMailboxMessages(messageManager, session)
                .map(message -> new ProjectionEntry(messageManager, message.getComposedMessageId().getUid(), session))), MAILBOX_CONCURRENCY)
            .onErrorResume(MailboxException.class, e -> {
                LOGGER.error("JMAP fastview re-computation aborted for {} as we failed listing user mailboxes", session.getUser(), e);
                progress.incrementFailedUserCount();
                return Flux.empty();
            });
    }

    private Mono<Result> correctProjection(ProjectionEntry entry, Progress progress) {
        return retrieveContent(entry.getMessageManager(), entry.getSession(), entry.getUid())
            .map(this::computeProjectionEntry)
            .flatMap(this::storeProjectionEntry)
            .doOnSuccess(any -> progress.incrementProcessedMessageCount())
            .thenReturn(Result.COMPLETED)
            .onErrorResume(e -> {
                LOGGER.error("JMAP fastview re-computation aborted for {} - {} - {}",
                    entry.getSession().getUser(),
                    entry.getMessageManager().getId(),
                    entry.getUid(), e);
                progress.incrementFailedMessageCount();
                return Mono.just(Result.PARTIAL);
            });
    }

    private Mono<Result> correctProjection(Flux<ProjectionEntry> entries, RunningOptions runningOptions, Progress progress) {
        return entries.transform(ReactorUtils.<ProjectionEntry, Task.Result>throttle()
                .elements(runningOptions.getMessagesPerSecond())
                .per(PERIOD)
                .forOperation(entry -> correctProjection(entry, progress)))
            .reduce(Task::combine)
            .switchIfEmpty(Mono.just(Result.COMPLETED));
    }

    private Flux<MailboxMetaData> listUsersMailboxes(MailboxSession session) {
        return mailboxManager.search(MailboxQuery.privateMailboxesBuilder(session).build(), Minimal, session);
    }

    private Mono<MessageManager> retrieveMailbox(MailboxSession session, MailboxMetaData mailboxMetadata) {
        return Mono.from(mailboxManager.getMailboxReactive(mailboxMetadata.getId(), session));
    }

    private Flux<ComposedMessageIdWithMetaData> listAllMailboxMessages(MessageManager messageManager, MailboxSession session) {
        return Flux.from(messageManager.listMessagesMetadata(MessageRange.all(), session));
    }

    private Mono<MessageResult> retrieveContent(MessageManager messageManager, MailboxSession session, MessageUid uid) {
        return Flux.from(messageManager.getMessagesReactive(MessageRange.one(uid), FetchGroup.FULL_CONTENT, session))
            .next();
    }

    private Pair<MessageId, MessageFastViewPrecomputedProperties> computeProjectionEntry(MessageResult messageResult) {
        try {
            return Pair.of(messageResult.getMessageId(), projectionItemFactory.from(messageResult));
        } catch (MailboxException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Mono<Void> storeProjectionEntry(Pair<MessageId, MessageFastViewPrecomputedProperties> pair) {
        return Mono.from(messageFastViewProjection.store(pair.getKey(), pair.getValue()));
    }
}
