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

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.rspamd.client.RspamdHttpClient;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.util.ReactorUtils;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;

import reactor.core.publisher.Mono;

public class FeedHamToRspamdTask implements Task {
    public static final TaskType TASK_TYPE = TaskType.of("FeedHamToRspamdTask");

    public static class AdditionalInformation implements TaskExecutionDetails.AdditionalInformation {

        private static AdditionalInformation from(Context context, RunningOptions runningOptions) {
            Context.Snapshot snapshot = context.snapshot();
            return new AdditionalInformation(
                Clock.systemUTC().instant(),
                snapshot.getHamMessageCount(),
                snapshot.getReportedHamMessageCount(),
                snapshot.getErrorCount(),
                runningOptions);
        }

        private final Instant timestamp;
        private final long hamMessageCount;
        private final long reportedHamMessageCount;
        private final long errorCount;
        private final RunningOptions runningOptions;

        public AdditionalInformation(Instant timestamp, long hamMessageCount, long reportedHamMessageCount, long errorCount, RunningOptions runningOptions) {
            this.timestamp = timestamp;
            this.hamMessageCount = hamMessageCount;
            this.reportedHamMessageCount = reportedHamMessageCount;
            this.errorCount = errorCount;
            this.runningOptions = runningOptions;
        }

        public long getHamMessageCount() {
            return hamMessageCount;
        }

        public long getReportedHamMessageCount() {
            return reportedHamMessageCount;
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
                private Optional<Long> hamMessageCount;
                private Optional<Long> reportedHamMessageCount;
                private Optional<Long> errorCount;

                Builder() {
                    hamMessageCount = Optional.empty();
                    reportedHamMessageCount = Optional.empty();
                    errorCount = Optional.empty();
                }

                public Snapshot build() {
                    return new Snapshot(
                        hamMessageCount.orElse(0L),
                        reportedHamMessageCount.orElse(0L),
                        errorCount.orElse(0L));
                }

                public Builder hamMessageCount(long hamMessageCount) {
                    this.hamMessageCount = Optional.of(hamMessageCount);
                    return this;
                }

                public Builder reportedHamMessageCount(long reportedHamMessageCount) {
                    this.reportedHamMessageCount = Optional.of(reportedHamMessageCount);
                    return this;
                }

                public Builder errorCount(long errorCount) {
                    this.errorCount = Optional.of(errorCount);
                    return this;
                }
            }

            private final long hamMessageCount;
            private final long reportedHamMessageCount;
            private final long errorCount;

            public Snapshot(long hamMessageCount, long reportedHamMessageCount, long errorCount) {
                this.hamMessageCount = hamMessageCount;
                this.reportedHamMessageCount = reportedHamMessageCount;
                this.errorCount = errorCount;
            }

            public long getHamMessageCount() {
                return hamMessageCount;
            }

            public long getReportedHamMessageCount() {
                return reportedHamMessageCount;
            }

            public long getErrorCount() {
                return errorCount;
            }

            @Override
            public final boolean equals(Object o) {
                if (o instanceof Snapshot) {
                    Snapshot snapshot = (Snapshot) o;

                    return Objects.equals(this.hamMessageCount, snapshot.hamMessageCount)
                        && Objects.equals(this.reportedHamMessageCount, snapshot.reportedHamMessageCount)
                        && Objects.equals(this.errorCount, snapshot.errorCount);
                }
                return false;
            }

            @Override
            public final int hashCode() {
                return Objects.hash(hamMessageCount, reportedHamMessageCount, errorCount);
            }

            @Override
            public String toString() {
                return MoreObjects.toStringHelper(this)
                    .add("hamMessageCount", hamMessageCount)
                    .add("reportedHamMessageCount", reportedHamMessageCount)
                    .add("errorCount", errorCount)
                    .toString();
            }
        }

        private final AtomicLong hamMessageCount;
        private final AtomicLong reportedHamMessageCount;
        private final AtomicLong errorCount;

        public Context() {
            this.hamMessageCount = new AtomicLong();
            this.reportedHamMessageCount = new AtomicLong();
            this.errorCount = new AtomicLong();
        }

        public void incrementHamMessageCount() {
            hamMessageCount.incrementAndGet();
        }

        public void incrementReportedHamMessageCount(int count) {
            reportedHamMessageCount.addAndGet(count);
        }

        public void incrementErrorCount() {
            errorCount.incrementAndGet();
        }

        public Snapshot snapshot() {
            return Snapshot.builder()
                .hamMessageCount(hamMessageCount.get())
                .reportedHamMessageCount(reportedHamMessageCount.get())
                .errorCount(errorCount.get())
                .build();
        }
    }

    private final GetMailboxMessagesService messagesService;
    private final RspamdHttpClient rspamdHttpClient;
    private final RunningOptions runningOptions;
    private final Context context;
    private final Clock clock;

    public FeedHamToRspamdTask(MailboxManager mailboxManager, UsersRepository usersRepository, MessageIdManager messageIdManager, MailboxSessionMapperFactory mapperFactory,
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
        try {
            return messagesService.getHamMessagesOfAllUser(afterDate, runningOptions.getSamplingProbability(), context)
                .transform(ReactorUtils.<Pair<Username, MessageResult>, Result>throttle()
                    .elements(runningOptions.getMessagesPerSecond())
                    .per(Duration.ofSeconds(1))
                    .forOperation(messageResultAndUser -> Mono.fromSupplier(Throwing.supplier(() ->
                        rspamdHttpClient.reportAsHam(
                            messageResultAndUser.getRight().getFullContent().getInputStream(),
                            RspamdHttpClient.Options.forUser(messageResultAndUser.getLeft()))))
                        .then(Mono.fromCallable(() -> {
                            context.incrementReportedHamMessageCount(1);
                            return Result.COMPLETED;
                        }))
                        .onErrorResume(error -> {
                            LOGGER.error("Error when report ham message to Rspamd", error);
                            context.incrementErrorCount();
                            return Mono.just(Result.PARTIAL);
                        })))
                .reduce(Task::combine)
                .switchIfEmpty(Mono.just(Result.COMPLETED))
                .block();
        } catch (UsersRepositoryException e) {
            LOGGER.error("Error while accessing users from repository", e);
            return Result.PARTIAL;
        }
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
