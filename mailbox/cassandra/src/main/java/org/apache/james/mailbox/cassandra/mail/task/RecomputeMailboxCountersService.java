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

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.inject.Inject;
import jakarta.mail.Flags;

import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxCounterDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdToImapUidDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageMetadata;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxCounters;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.task.Task;
import org.apache.james.task.Task.Result;
import org.apache.james.util.streams.Limit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class RecomputeMailboxCountersService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RecomputeMailboxCountersService.class);

    private static final int MAILBOX_CONCURRENCY = 2;
    private static final int MESSAGE_CONCURRENCY = 8;

    public static class Options {
        public static Options trustMessageProjection() {
            return of(true);
        }

        public static Options recheckMessageProjection() {
            return of(false);
        }

        public static Options of(boolean value) {
            return new Options(value);
        }

        private final boolean trustMessageProjection;

        private Options(boolean trustMessageProjection) {
            this.trustMessageProjection = trustMessageProjection;
        }

        public boolean isMessageProjectionTrusted() {
            return trustMessageProjection;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof Options) {
                Options options = (Options) o;

                return Objects.equals(this.trustMessageProjection, options.trustMessageProjection);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(trustMessageProjection);
        }
    }

    private static class Counter {
        private final CassandraId mailboxId;
        private final AtomicLong total;
        private final AtomicLong unseen;

        private Counter(CassandraId mailboxId) {
            this.mailboxId = mailboxId;
            unseen = new AtomicLong();
            total = new AtomicLong();
        }

        void process(ComposedMessageIdWithMetaData metadata) {
            total.incrementAndGet();
            if (!metadata.getFlags().contains(Flags.Flag.SEEN)) {
                unseen.incrementAndGet();
            }
        }

        MailboxCounters snapshot() {
            return MailboxCounters.builder()
                .mailboxId(mailboxId)
                .count(total.get())
                .unseen(unseen.get())
                .build();
        }
    }

    public static class Context {
        static class Snapshot {
            private final long processedMailboxCount;
            private final ImmutableList<CassandraId> failedMailboxes;

            private Snapshot(long processedMailboxCount, ImmutableList<CassandraId> failedMailboxes) {
                this.processedMailboxCount = processedMailboxCount;
                this.failedMailboxes = failedMailboxes;
            }

            long getProcessedMailboxCount() {
                return processedMailboxCount;
            }

            ImmutableList<CassandraId> getFailedMailboxes() {
                return failedMailboxes;
            }

            @Override
            public final boolean equals(Object o) {
                if (o instanceof Snapshot) {
                    Snapshot snapshot = (Snapshot) o;

                    return Objects.equals(this.processedMailboxCount, snapshot.processedMailboxCount)
                        && Objects.equals(this.failedMailboxes, snapshot.failedMailboxes);
                }
                return false;
            }

            @Override
            public final int hashCode() {
                return Objects.hash(processedMailboxCount, failedMailboxes);
            }

            @Override
            public String toString() {
                return MoreObjects.toStringHelper(this)
                    .add("processedMailboxCount", processedMailboxCount)
                    .add("failedMailboxes", failedMailboxes)
                    .toString();
            }
        }

        private final AtomicLong processedMailboxCount;
        private final ConcurrentLinkedDeque<CassandraId> failedMailboxes;

        public Context() {
            processedMailboxCount = new AtomicLong();
            failedMailboxes = new ConcurrentLinkedDeque<>();
        }

        void incrementProcessed() {
            processedMailboxCount.incrementAndGet();
        }

        void addToFailedMailboxes(CassandraId cassandraId) {
            failedMailboxes.add(cassandraId);
        }

        Snapshot snapshot() {
            return new Snapshot(processedMailboxCount.get(),
                ImmutableList.copyOf(failedMailboxes));
        }
    }

    private final CassandraMailboxDAO mailboxDAO;
    private final CassandraMessageIdDAO imapUidToMessageIdDAO;
    private final CassandraMessageIdToImapUidDAO messageIdToImapUidDAO;
    private final CassandraMailboxCounterDAO counterDAO;

    @Inject
    RecomputeMailboxCountersService(CassandraMailboxDAO mailboxDAO,
                                    CassandraMessageIdDAO imapUidToMessageIdDAO,
                                    CassandraMessageIdToImapUidDAO messageIdToImapUidDAO,
                                    CassandraMailboxCounterDAO counterDAO) {
        this.mailboxDAO = mailboxDAO;
        this.imapUidToMessageIdDAO = imapUidToMessageIdDAO;
        this.messageIdToImapUidDAO = messageIdToImapUidDAO;
        this.counterDAO = counterDAO;
    }

    Mono<Result> recomputeMailboxCounters(Context context, Options options) {
        return mailboxDAO.retrieveAllMailboxes()
            .flatMap(mailbox -> recomputeMailboxCounter(context, mailbox, options), MAILBOX_CONCURRENCY)
            .reduce(Result.COMPLETED, Task::combine)
            .onErrorResume(e -> {
                LOGGER.error("Error listing mailboxes", e);
                return Mono.just(Result.PARTIAL);
            });
    }

    public Mono<Result> recomputeMailboxCounter(Context context, Mailbox mailbox, Options options) {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();
        Counter counter = new Counter(mailboxId);

        return imapUidToMessageIdDAO.retrieveMessages(mailboxId, MessageRange.all(), Limit.unlimited())
            .map(CassandraMessageMetadata::getComposedMessageId)
            .flatMap(message -> latestMetadata(mailboxId, message, options), MESSAGE_CONCURRENCY)
            .doOnNext(counter::process)
            .then(Mono.defer(() -> counterDAO.resetCounters(counter.snapshot())))
            .then(Mono.just(Result.COMPLETED))
            .doOnNext(any -> {
                LOGGER.info("Counters recomputed for {}", mailboxId.serialize());
                context.incrementProcessed();
            })
            .onErrorResume(e -> {
                context.addToFailedMailboxes(mailboxId);
                LOGGER.error("Error while recomputing counters for {}", mailboxId.serialize(), e);
                return Mono.just(Result.PARTIAL);
            });
    }

    private Flux<ComposedMessageIdWithMetaData> latestMetadata(CassandraId mailboxId,
                                                               ComposedMessageIdWithMetaData message,
                                                               Options options) {
        if (options.isMessageProjectionTrusted()) {
            return Flux.just(message);
        }
        CassandraMessageId messageId = (CassandraMessageId) message.getComposedMessageId().getMessageId();

        return messageIdToImapUidDAO.retrieve(messageId, Optional.of(mailboxId), STRONG)
            .map(CassandraMessageMetadata::getComposedMessageId)
            .doOnNext(trustedMessage -> {
                if (!trustedMessage.equals(message)) {
                    LOGGER.warn("Possible denormalization issue on {}. " +
                            "Mismatch between the two denormalization table. " +
                            "This can also be due to concurrent modifications.",
                        message.getComposedMessageId());
                }
            })
            .switchIfEmpty(Flux.<ComposedMessageIdWithMetaData>empty()
                .doOnComplete(() -> LOGGER.warn("Possible denormalization issue on {}. " +
                        "Source of truth do not contain listed entry." +
                        "This can also be due to concurrent modifications.",
                    message.getComposedMessageId())));
    }
}
