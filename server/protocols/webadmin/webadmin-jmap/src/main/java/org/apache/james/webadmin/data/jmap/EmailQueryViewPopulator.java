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

import static jakarta.mail.Flags.Flag.DELETED;

import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

import jakarta.inject.Inject;

import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.jmap.api.projections.EmailQueryView;
import org.apache.james.jmap.api.projections.EmailQueryViewManager;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.task.Task;
import org.apache.james.task.Task.Result;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.util.ReactorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class EmailQueryViewPopulator {
    private static final Logger LOGGER = LoggerFactory.getLogger(EmailQueryViewPopulator.class);
    private static final Duration PERIOD = Duration.ofSeconds(1);
    private static final String POPULATOR_LOCAL_PART = "email-query-view-populator";
    public static final int MAILBOX_CONCURRENCY = 1;

    static class Progress {
        private final AtomicLong processedMailboxCount;
        private final AtomicLong processedMessageCount;
        private final AtomicLong failedMailboxCount;
        private final AtomicLong failedMessageCount;

        Progress() {
            failedMailboxCount = new AtomicLong();
            processedMessageCount = new AtomicLong();
            processedMailboxCount = new AtomicLong();
            failedMessageCount = new AtomicLong();
        }

        private void incrementProcessedMailboxCount() {
            processedMailboxCount.incrementAndGet();
        }

        private void incrementProcessedMessageCount() {
            processedMessageCount.incrementAndGet();
        }

        private void incrementFailedMailboxCount() {
            failedMailboxCount.incrementAndGet();
        }

        private void incrementFailedMessageCount() {
            failedMessageCount.incrementAndGet();
        }

        long getProcessedMailboxCount() {
            return processedMailboxCount.get();
        }

        long getProcessedMessageCount() {
            return processedMessageCount.get();
        }

        long getFailedMailboxCount() {
            return failedMailboxCount.get();
        }

        long getFailedMessageCount() {
            return failedMessageCount.get();
        }
    }

    private record MessageWithOwnerView(EmailQueryView ownerView, MessageResult messageResult) {
    }

    private final MailboxSessionMapperFactory mailboxSessionMapperFactory;
    private final MailboxManager mailboxManager;
    private final EmailQueryViewManager emailQueryViewManager;
    private final UsersRepository usersRepository;
    private final DomainList domainList;

    @Inject
    EmailQueryViewPopulator(MailboxSessionMapperFactory mailboxSessionMapperFactory,
                            MailboxManager mailboxManager,
                            EmailQueryViewManager emailQueryViewManager,
                            UsersRepository usersRepository,
                            DomainList domainList) {
        this.mailboxSessionMapperFactory = mailboxSessionMapperFactory;
        this.mailboxManager = mailboxManager;
        this.emailQueryViewManager = emailQueryViewManager;
        this.usersRepository = usersRepository;
        this.domainList = domainList;
    }

    Mono<Result> populateView(Progress progress, RunningOptions runningOptions) {
        return correctProjection(listAllMessageWithOwnerViews(progress), runningOptions, progress)
            .map(result -> progress.getFailedMailboxCount() > 0 ? Result.PARTIAL : result);
    }

    private Flux<MessageWithOwnerView> listAllMessageWithOwnerViews(Progress progress) {
        return listAllMailboxes()
            .flatMap(mailbox -> listMessageWithOwnerViews(progress, mailbox), MAILBOX_CONCURRENCY)
            .filter(messageWithOwnerView -> !messageWithOwnerView.messageResult().getFlags().contains(DELETED));
    }

    private Flux<Mailbox> listAllMailboxes() {
        if (usersRepository.supportVirtualHosting()) {
            // Row level security scopes the mailbox listing to the domain of the session, hence we list domain by domain
            return Mono.fromCallable(domainList::getDomains)
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapIterable(domains -> domains)
                .concatMap(this::listMailboxes);
        }
        return listMailboxes(Username.of(POPULATOR_LOCAL_PART), mailbox -> true);
    }

    private Flux<Mailbox> listMailboxes(Domain domain) {
        // Without row level security the listing spans all domains: keep only the mailboxes of this domain to process each mailbox once
        return listMailboxes(Username.fromLocalPartWithDomain(POPULATOR_LOCAL_PART, domain),
            mailbox -> mailbox.getUser().getDomainPart().equals(Optional.of(domain)));
    }

    private Flux<Mailbox> listMailboxes(Username populatorUser, Predicate<Mailbox> filter) {
        return Flux.using(() -> mailboxManager.createSystemSession(populatorUser),
            session -> mailboxSessionMapperFactory.getMailboxMapper(session).list()
                .filter(filter),
            mailboxManager::endProcessingRequest);
    }

    private Flux<MessageWithOwnerView> listMessageWithOwnerViews(Progress progress, Mailbox mailbox) {
        MailboxSession ownerSession = mailboxManager.createSystemSession(mailbox.getUser());
        EmailQueryView ownerView = emailQueryViewManager.getEmailQueryView(mailbox.getUser());

        return Mono.from(mailboxManager.getMailboxReactive(mailbox.getMailboxId(), ownerSession))
            .flatMap(messageManager -> ownerView.delete(mailbox.getMailboxId()).thenReturn(messageManager))
            .flatMapMany(messageManager -> listAllMessages(messageManager, ownerSession))
            .map(messageResult -> new MessageWithOwnerView(ownerView, messageResult))
            .doOnComplete(progress::incrementProcessedMailboxCount)
            .onErrorResume(MailboxNotFoundException.class, e -> {
                LOGGER.info("Mailbox {} was deleted before its JMAP emailQuery view could be re-computed", mailbox.getMailboxId());
                return Flux.empty();
            })
            .onErrorResume(MailboxException.class, e -> {
                LOGGER.error("JMAP emailQuery view re-computation aborted for mailbox {} as we failed listing its messages", mailbox.getMailboxId(), e);
                progress.incrementFailedMailboxCount();
                return Flux.empty();
            })
            .doFinally(any -> mailboxManager.endProcessingRequest(ownerSession));
    }

    private Mono<Result> correctProjection(MessageWithOwnerView messageWithOwnerView, Progress progress) {
        MessageResult messageResult = messageWithOwnerView.messageResult();
        return Mono.fromCallable(() -> {
            MailboxId mailboxId = messageResult.getMailboxId();
            MessageId messageId = messageResult.getMessageId();
            ThreadId threadId = messageResult.getThreadId();
            ZonedDateTime receivedAt = ZonedDateTime.ofInstant(messageResult.getInternalDate().toInstant(), ZoneOffset.UTC);
            return new EmailQueryView.Entry(mailboxId, messageId, receivedAt, threadId);
        })
            .flatMap(entry -> messageWithOwnerView.ownerView().save(entry.getMailboxId(), entry.getReceivedAt(), entry.getMessageId(), entry.getThreadId()))
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

    private Mono<Result> correctProjection(Flux<MessageWithOwnerView> entries, RunningOptions runningOptions, Progress progress) {
        return entries.transform(ReactorUtils.<MessageWithOwnerView, Result>throttle()
                .elements(runningOptions.getMessagesPerSecond())
                .per(PERIOD)
                .forOperation(entry -> correctProjection(entry, progress)))
            .reduce(Task::combine)
            .switchIfEmpty(Mono.just(Result.COMPLETED));
    }

    private Flux<MessageResult> listAllMessages(MessageManager messageManager, MailboxSession session) {
        return Flux.from(messageManager.getMessagesReactive(MessageRange.all(), FetchGroup.HEADERS, session));
    }

}
