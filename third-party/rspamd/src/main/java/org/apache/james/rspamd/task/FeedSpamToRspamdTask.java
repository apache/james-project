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

package org.apache.james.rspamd.task;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.rspamd.client.RspamdHttpClient;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.util.ReactorUtils;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;

import reactor.core.publisher.Mono;

public class FeedSpamToRspamdTask implements Task {
    public static final String SPAM_MAILBOX_NAME = "Spam";
    public static final TaskType TASK_TYPE = TaskType.of("FeedSpamToRspamdTask");

    public static class AdditionalInformation implements TaskExecutionDetails.AdditionalInformation {

        private static AdditionalInformation from(Context context, RunningOptions runningOptions) {
            Context.Snapshot snapshot = context.snapshot();
            return new AdditionalInformation(
                Clock.systemUTC().instant(),
                snapshot.getSpamMessageCount(),
                snapshot.getReportedSpamMessageCount(),
                snapshot.getErrorCount(),
                runningOptions);
        }

        private final Instant timestamp;
        private final long spamMessageCount;
        private final long reportedSpamMessageCount;
        private final long errorCount;
        private final RunningOptions runningOptions;

        public AdditionalInformation(Instant timestamp, long spamMessageCount, long reportedSpamMessageCount, long errorCount, RunningOptions runningOptions) {
            this.timestamp = timestamp;
            this.spamMessageCount = spamMessageCount;
            this.reportedSpamMessageCount = reportedSpamMessageCount;
            this.errorCount = errorCount;
            this.runningOptions = runningOptions;
        }

        public long getSpamMessageCount() {
            return spamMessageCount;
        }

        public long getReportedSpamMessageCount() {
            return reportedSpamMessageCount;
        }

        public long getErrorCount() {
            return errorCount;
        }

        public RunningOptions getRunningOptions() {
            return runningOptions;
        }

        @Override
        public Instant timestamp() {
            return timestamp;
        }
    }

    public static class Context {

        public static class Snapshot {

            public static Builder builder() {
                return new Builder();
            }

            static class Builder {
                private Optional<Long> spamMessageCount;
                private Optional<Long> reportedSpamMessageCount;
                private Optional<Long> errorCount;

                Builder() {
                    spamMessageCount = Optional.empty();
                    reportedSpamMessageCount = Optional.empty();
                    errorCount = Optional.empty();
                }

                public Snapshot build() {
                    return new Snapshot(
                        spamMessageCount.orElse(0L),
                        reportedSpamMessageCount.orElse(0L),
                        errorCount.orElse(0L));
                }

                public Builder spamMessageCount(long spamMessageCount) {
                    this.spamMessageCount = Optional.of(spamMessageCount);
                    return this;
                }

                public Builder reportedSpamMessageCount(long reportedSpamMessageCount) {
                    this.reportedSpamMessageCount = Optional.of(reportedSpamMessageCount);
                    return this;
                }

                public Builder errorCount(long errorCount) {
                    this.errorCount = Optional.of(errorCount);
                    return this;
                }
            }

            private final long spamMessageCount;
            private final long reportedSpamMessageCount;
            private final long errorCount;

            public Snapshot(long spamMessageCount, long reportedSpamMessageCount, long errorCount) {
                this.spamMessageCount = spamMessageCount;
                this.reportedSpamMessageCount = reportedSpamMessageCount;
                this.errorCount = errorCount;
            }

            public long getSpamMessageCount() {
                return spamMessageCount;
            }

            public long getReportedSpamMessageCount() {
                return reportedSpamMessageCount;
            }

            public long getErrorCount() {
                return errorCount;
            }

            @Override
            public final boolean equals(Object o) {
                if (o instanceof Snapshot) {
                    Snapshot snapshot = (Snapshot) o;

                    return Objects.equals(this.spamMessageCount, snapshot.spamMessageCount)
                        && Objects.equals(this.reportedSpamMessageCount, snapshot.reportedSpamMessageCount)
                        && Objects.equals(this.errorCount, snapshot.errorCount);
                }
                return false;
            }

            @Override
            public final int hashCode() {
                return Objects.hash(spamMessageCount, reportedSpamMessageCount, errorCount);
            }

            @Override
            public String toString() {
                return MoreObjects.toStringHelper(this)
                    .add("spamMessageCount", spamMessageCount)
                    .add("reportedSpamMessageCount", reportedSpamMessageCount)
                    .add("errorCount", errorCount)
                    .toString();
            }
        }

        private final AtomicLong spamMessageCount;
        private final AtomicLong reportedSpamMessageCount;
        private final AtomicLong errorCount;

        public Context() {
            this.spamMessageCount = new AtomicLong();
            this.reportedSpamMessageCount = new AtomicLong();
            this.errorCount = new AtomicLong();
        }

        public void incrementSpamMessageCount() {
            spamMessageCount.incrementAndGet();
        }

        public void incrementReportedSpamMessageCount(int count) {
            reportedSpamMessageCount.addAndGet(count);
        }

        public void incrementErrorCount() {
            errorCount.incrementAndGet();
        }

        public Snapshot snapshot() {
            return Snapshot.builder()
                .spamMessageCount(spamMessageCount.get())
                .reportedSpamMessageCount(reportedSpamMessageCount.get())
                .errorCount(errorCount.get())
                .build();
        }
    }

    private final GetMailboxMessagesService messagesService;
    private final RspamdHttpClient rspamdHttpClient;
    private final RunningOptions runningOptions;
    private final Context context;
    private final Clock clock;

    public FeedSpamToRspamdTask(MailboxManager mailboxManager, UsersRepository usersRepository, MessageIdManager messageIdManager, MailboxSessionMapperFactory mapperFactory,
                                RspamdHttpClient rspamdHttpClient, RunningOptions runningOptions, Clock clock) {
        this.runningOptions = runningOptions;
        this.messagesService = new GetMailboxMessagesService(mailboxManager, usersRepository, mapperFactory, messageIdManager);
        this.rspamdHttpClient = rspamdHttpClient;
        this.context = new Context();
        this.clock = clock;
    }

    @Override
    public Result run() {
        Optional<Date> afterDate = runningOptions.getPeriodInSecond().map(periodInSecond -> Date.from(clock.instant().minusSeconds(periodInSecond)));
        return messagesService.getMailboxMessagesOfAllUser(SPAM_MAILBOX_NAME, afterDate, runningOptions, context)
            .window(runningOptions.getMessagesPerSecond())
            .delaySequence(Duration.ofSeconds(1))
            .flatMap(window -> window.flatMap(messageResult -> rspamdHttpClient.reportAsSpam(Throwing.supplier(() -> messageResult.getFullContent().getInputStream()).get())
                .then(Mono.fromCallable(() -> {
                    context.incrementReportedSpamMessageCount(1);
                    return Result.COMPLETED;
                }))
                .onErrorResume(error -> {
                    LOGGER.error("Error when report spam message to Rspamd", error);
                    context.incrementErrorCount();
                    return Mono.just(Result.PARTIAL);
                }), ReactorUtils.DEFAULT_CONCURRENCY))
            .reduce(Task::combine)
            .switchIfEmpty(Mono.just(Result.COMPLETED))
            .block();
    }

    @Override
    public TaskType type() {
        return TASK_TYPE;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.of(AdditionalInformation.from(context, runningOptions));
    }

    @VisibleForTesting
    public Context.Snapshot snapshot() {
        return context.snapshot();
    }

    public RunningOptions getRunningOptions() {
        return runningOptions;
    }
}
