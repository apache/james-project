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

package org.apache.james.mailbox.quota.task;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

import javax.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaComponent;
import org.apache.james.jmap.api.upload.CurrentUploadUsageRecomputator;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.model.QuotaOperation;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.CurrentQuotaManager;
import org.apache.james.mailbox.quota.UserQuotaRootResolver;
import org.apache.james.mailbox.store.quota.CurrentQuotaCalculator;
import org.apache.james.task.Task;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.util.ReactorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class RecomputeCurrentQuotasService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RecomputeCurrentQuotasService.class);

    public static class RunningOptions {
        public static RunningOptions of(int usersPerSecond, Optional<QuotaComponent> quotaComponent) {
            return new RunningOptions(usersPerSecond, quotaComponent);
        }

        public static RunningOptions withUsersPerSecond(int usersPerSecond) {
            return new RunningOptions(usersPerSecond, Optional.empty());
        }

        public static final int DEFAULT_USERS_PER_SECOND = 1;
        public static final RunningOptions DEFAULT = of(DEFAULT_USERS_PER_SECOND, Optional.empty());

        private final int usersPerSecond;
        private final Optional<QuotaComponent> quotaComponent;

        private RunningOptions(int usersPerSecond, Optional<QuotaComponent> quotaComponent) {
            Preconditions.checkArgument(usersPerSecond > 0, "'usersPerSecond' needs to be strictly positive");

            this.usersPerSecond = usersPerSecond;
            this.quotaComponent = quotaComponent;
        }

        public int getUsersPerSecond() {
            return usersPerSecond;
        }

        public Optional<QuotaComponent> getQuotaComponent() {
            return quotaComponent;
        }
    }

    public static class Context {
        static class Snapshot {
            private final long processedQuotaRootCount;
            private final ImmutableList<QuotaRoot> failedQuotaRoots;

            private Snapshot(long processedQuotaRootCount, ImmutableList<QuotaRoot> failedQuotaRoots) {
                this.processedQuotaRootCount = processedQuotaRootCount;
                this.failedQuotaRoots = failedQuotaRoots;
            }

            long getProcessedQuotaRootCount() {
                return processedQuotaRootCount;
            }

            ImmutableList<QuotaRoot> getFailedQuotaRoots() {
                return failedQuotaRoots;
            }

            @Override
            public final boolean equals(Object o) {
                if (o instanceof Snapshot) {
                    Snapshot that = (Snapshot) o;

                    return Objects.equals(this.processedQuotaRootCount, that.processedQuotaRootCount)
                        && Objects.equals(this.failedQuotaRoots, that.failedQuotaRoots);
                }
                return false;
            }

            @Override
            public final int hashCode() {
                return Objects.hash(processedQuotaRootCount, failedQuotaRoots);
            }

            @Override
            public String toString() {
                return MoreObjects.toStringHelper(this)
                    .add("processedQuotaRootCount", processedQuotaRootCount)
                    .add("failedQuotaRoots", failedQuotaRoots)
                    .toString();
            }
        }

        private final AtomicLong processedQuotaRootCount;
        private final ConcurrentLinkedDeque<QuotaRoot> failedQuotaRoots;

        public Context() {
            this.processedQuotaRootCount = new AtomicLong();
            this.failedQuotaRoots = new ConcurrentLinkedDeque<>();
        }

        public Context(long processedQuotaRootCount, Collection<QuotaRoot> failedQuotaRoots) {
            this.processedQuotaRootCount = new AtomicLong(processedQuotaRootCount);
            this.failedQuotaRoots = new ConcurrentLinkedDeque<>(failedQuotaRoots);
        }

        void incrementProcessed() {
            processedQuotaRootCount.incrementAndGet();
        }

        void addToFailedMailboxes(QuotaRoot quotaRoot) {
            failedQuotaRoots.add(quotaRoot);
        }

        public Snapshot snapshot() {
            return new Snapshot(processedQuotaRootCount.get(),
                ImmutableList.copyOf(failedQuotaRoots));
        }
    }

    private final UsersRepository usersRepository;
    private final CurrentQuotaManager storeCurrentQuotaManager;
    private final CurrentQuotaCalculator currentQuotaCalculator;
    private final UserQuotaRootResolver userQuotaRootResolver;
    private final SessionProvider sessionProvider;
    private final MailboxManager mailboxManager;
    private final CurrentUploadUsageRecomputator currentUploadUsageRecomputator;

    @Inject
    public RecomputeCurrentQuotasService(UsersRepository usersRepository,
                                         CurrentQuotaManager storeCurrentQuotaManager,
                                         CurrentQuotaCalculator currentQuotaCalculator,
                                         UserQuotaRootResolver userQuotaRootResolver,
                                         SessionProvider sessionProvider,
                                         MailboxManager mailboxManager,
                                         CurrentUploadUsageRecomputator currentUploadUsageRecomputator) {
        this.usersRepository = usersRepository;
        this.storeCurrentQuotaManager = storeCurrentQuotaManager;
        this.currentQuotaCalculator = currentQuotaCalculator;
        this.userQuotaRootResolver = userQuotaRootResolver;
        this.sessionProvider = sessionProvider;
        this.mailboxManager = mailboxManager;
        this.currentUploadUsageRecomputator = currentUploadUsageRecomputator;
    }

    public Mono<Task.Result> recomputeCurrentQuotas(Context context, RunningOptions runningOptions) {
        return Flux.from(usersRepository.listReactive())
            .transform(ReactorUtils.<Username, Task.Result>throttle()
                .elements(runningOptions.getUsersPerSecond())
                .per(Duration.ofSeconds(1))
                .forOperation(username -> recomputeQuotasOfUser(runningOptions.quotaComponent, context, username)))
            .reduce(Task.Result.COMPLETED, Task::combine)
            .onErrorResume(UsersRepositoryException.class, e -> {
                LOGGER.error("Error while accessing users from repository", e);
                return Mono.just(Task.Result.PARTIAL);
            });
    }

    private Mono<Task.Result> recomputeQuotasOfUser(Optional<QuotaComponent> quotaComponentOptional, Context context, Username username) {
        Mono<Task.Result> mono1 = recomputeUserCurrentQuotas(context, username);
        Mono<Task.Result> mono2 = recomputeCurrentUploadUsage(username);
        if (quotaComponentOptional.isEmpty()) {
            return Mono.zip(mono1, mono2).map(objects -> Task.combine(objects.getT1(), objects.getT2()));
        } else if (QuotaComponent.MAILBOX.equals(quotaComponentOptional.get())) {
            return mono1;
        } else if (QuotaComponent.JMAP_UPLOADS.equals(quotaComponentOptional.get())) {
            return mono2;
        } else {
            throw new RuntimeException(String.format("Could not a matching quota component for '%s'", quotaComponentOptional.get().getValue()));
        }
    }

    private Mono<Task.Result> recomputeUserCurrentQuotas(Context context, Username username) {
        MailboxSession session = sessionProvider.createSystemSession(username);
        QuotaRoot quotaRoot = userQuotaRootResolver.forUser(username);

        return currentQuotaCalculator.recalculateCurrentQuotas(quotaRoot, session)
            .map(recalculatedQuotas -> QuotaOperation.from(quotaRoot, recalculatedQuotas))
            .flatMap(quotaOperation -> Mono.from(storeCurrentQuotaManager.setCurrentQuotas(quotaOperation)))
            .then(Mono.just(Task.Result.COMPLETED))
            .doOnNext(any -> {
                LOGGER.info("Current quotas recomputed for {}", quotaRoot);
                context.incrementProcessed();
            })
            .onErrorResume(e -> {
                LOGGER.error("Error while recomputing current quotas for {}", quotaRoot, e);
                context.addToFailedMailboxes(quotaRoot);
                return Mono.just(Task.Result.PARTIAL);
            })
            .doFinally(any -> mailboxManager.endProcessingRequest(session));
    }

    private Mono<Task.Result> recomputeCurrentUploadUsage(Username username) {
        return currentUploadUsageRecomputator.recomputeCurrentUploadUsage(username)
            .then(Mono.just(Task.Result.COMPLETED))
            .onErrorResume(e -> {
                LOGGER.error("Error while recomputing current upload usage quota for {}", username, e);
                return Mono.just(Task.Result.PARTIAL);
            });
    }
}
