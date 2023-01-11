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

import static javax.mail.Flags.Flag.DELETED;
import static org.apache.james.mailbox.MailboxManager.MailboxSearchFetchType.Minimal;

import java.io.IOException;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import javax.inject.Inject;

import org.apache.james.jmap.api.projections.EmailQueryView;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.message.DefaultMessageBuilder;
import org.apache.james.mime4j.stream.MimeConfig;
import org.apache.james.task.Task;
import org.apache.james.task.Task.Result;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.util.ReactorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class EmailQueryViewPopulator {
    private static final Logger LOGGER = LoggerFactory.getLogger(EmailQueryViewPopulator.class);
    private static final Duration PERIOD = Duration.ofSeconds(1);
    public static final int USER_CONCURRENCY = 1;
    public static final int MAILBOX_CONCURRENCY = 1;

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
    private final EmailQueryView emailQueryView;

    @Inject
    EmailQueryViewPopulator(UsersRepository usersRepository,
                            MailboxManager mailboxManager,
                            EmailQueryView emailQueryView) {
        this.usersRepository = usersRepository;
        this.mailboxManager = mailboxManager;
        this.emailQueryView = emailQueryView;
    }

    Mono<Result> populateView(Progress progress, RunningOptions runningOptions) {
        return correctProjection(listAllMailboxMessages(progress), runningOptions, progress);
    }

    private Flux<MessageResult> listAllMailboxMessages(Progress progress) {
        return Flux.from(usersRepository.listReactive())
            .map(mailboxManager::createSystemSession)
            .doOnNext(any -> progress.incrementProcessedUserCount())
            .flatMap(session -> listUserMailboxMessages(progress, session), USER_CONCURRENCY)
            .filter(messageResult -> !messageResult.getFlags().contains(DELETED));
    }

    private Flux<MessageResult> listUserMailboxMessages(Progress progress, MailboxSession session) {
        return listUsersMailboxes(session)
            .flatMap(mailboxMetadata -> retrieveMailbox(session, mailboxMetadata), MAILBOX_CONCURRENCY)
            .flatMap(Throwing.function(messageManager -> listAllMessages(messageManager, session)), MAILBOX_CONCURRENCY)
            .onErrorResume(MailboxException.class, e -> {
                LOGGER.error("JMAP emailQuery view re-computation aborted for {} as we failed listing user mailboxes", session.getUser(), e);
                progress.incrementFailedUserCount();
                return Flux.empty();
            });
    }

    private Mono<Result> correctProjection(MessageResult messageResult, Progress progress) {
        return Mono.fromCallable(() -> {
            MailboxId mailboxId = messageResult.getMailboxId();
            MessageId messageId = messageResult.getMessageId();
            ZonedDateTime receivedAt = ZonedDateTime.ofInstant(messageResult.getInternalDate().toInstant(), ZoneOffset.UTC);
            Message mime4JMessage = parseMessage(messageResult);
            Date sentAtDate = Optional.ofNullable(mime4JMessage.getDate()).orElse(messageResult.getInternalDate());
            ZonedDateTime sentAt = ZonedDateTime.ofInstant(sentAtDate.toInstant(), ZoneOffset.UTC);
            mime4JMessage.dispose();

            return new EmailQueryView.Entry(mailboxId, messageId, sentAt, receivedAt);
        })
            .flatMap(entry -> emailQueryView.save(entry.getMailboxId(), entry.getSentAt(), entry.getReceivedAt(), entry.getMessageId()))
            .thenReturn(Result.COMPLETED)
            .doOnSuccess(any -> progress.incrementProcessedMessageCount())
            .onErrorResume(e -> {
                LOGGER.error("JMAP emailQuery view re-computation aborted for {} - {} - {}",
                    messageResult.getMailboxId(),
                    messageResult.getMessageId(),
                    messageResult.getUid(), e);
                progress.incrementFailedMessageCount();
                return Mono.just(Result.PARTIAL);
            });
    }

    private Mono<Result> correctProjection(Flux<MessageResult> entries, RunningOptions runningOptions, Progress progress) {
        return entries.transform(ReactorUtils.<MessageResult, Result>throttle()
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

    private Flux<MessageResult> listAllMessages(MessageManager messageManager, MailboxSession session) {
        return Flux.from(messageManager.getMessagesReactive(MessageRange.all(), FetchGroup.HEADERS, session));
    }

    private Message parseMessage(MessageResult messageResult) throws IOException, MailboxException {
        DefaultMessageBuilder defaultMessageBuilder = new DefaultMessageBuilder();
        defaultMessageBuilder.setMimeEntityConfig(MimeConfig.PERMISSIVE);
        return defaultMessageBuilder.parseMessage(messageResult.getFullContent().getInputStream());
    }
}
