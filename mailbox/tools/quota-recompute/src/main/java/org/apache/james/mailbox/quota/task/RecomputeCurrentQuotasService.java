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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaComponent;
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
        public static RunningOptions of(int usersPerSecond, List<QuotaComponent> quotaComponent) {
            return new RunningOptions(usersPerSecond, quotaComponent);
        }

        public static RunningOptions withUsersPerSecond(int usersPerSecond) {
            return new RunningOptions(usersPerSecond, ImmutableList.of());
        }

        public static final int DEFAULT_USERS_PER_SECOND = 1;
        public static final RunningOptions DEFAULT = of(DEFAULT_USERS_PER_SECOND, ImmutableList.of());

        private final int usersPerSecond;
        private final List<QuotaComponent> quotaComponents;

        private RunningOptions(int usersPerSecond, List<QuotaComponent> quotaComponent) {
            Preconditions.checkArgument(usersPerSecond > 0, "'usersPerSecond' needs to be strictly positive");

            this.usersPerSecond = usersPerSecond;
            this.quotaComponents = quotaComponent;
        }

        public int getUsersPerSecond() {
            return usersPerSecond;
        }

        public List<QuotaComponent> getQuotaComponents() {
            return quotaComponents;
        }
    }

    public static class Context {
        static class Snapshot {
            private final List<RecomputeSingleQuotaComponentResult> recomputeSingleQuotaComponentResults;

            private Snapshot(List<RecomputeSingleQuotaComponentResult> results) {
                this.recomputeSingleQuotaComponentResults = results;
            }

            public List<RecomputeSingleQuotaComponentResult> getResults() {
                return recomputeSingleQuotaComponentResults;
            }

            @Override
            public final boolean equals(Object o) {
                if (o instanceof Snapshot) {
                    Snapshot that = (Snapshot) o;

                    return Objects.equals(this.recomputeSingleQuotaComponentResults, that.recomputeSingleQuotaComponentResults);
                }
                return false;
            }

            @Override
            public final int hashCode() {
                return Objects.hash(recomputeSingleQuotaComponentResults);
            }

            @Override
            public String toString() {
                return MoreObjects.toStringHelper(this)
                    .add("results", recomputeSingleQuotaComponentResults)
                    .toString();
            }
        }

        public static class Statistic {
            private final AtomicLong processedIdentifierCount;
            private final ConcurrentLinkedDeque<String> failedIdentifiers;

            public Statistic(AtomicLong processedIdentifierCount, ConcurrentLinkedDeque<String> failedIdentifiers) {
                this.processedIdentifierCount = processedIdentifierCount;
                this.failedIdentifiers = failedIdentifiers;
            }

            public Statistic(long processedQuotaRootCount, Collection<String> failedQuotaRoots) {
                this.processedIdentifierCount = new AtomicLong(processedQuotaRootCount);
                this.failedIdentifiers = new ConcurrentLinkedDeque<>(failedQuotaRoots);
            }

            public void incrementProcessed() {
                processedIdentifierCount.incrementAndGet();
            }

            public void addToFailedIdentifiers(String identifier) {
                failedIdentifiers.add(identifier);
            }
        }

        private Map<QuotaComponent, Statistic> mapQuotaComponentToStatistic;

        public Context() {
            this.mapQuotaComponentToStatistic = new ConcurrentHashMap<>();
        }

        public Context(Map<QuotaComponent, Statistic> mapQuotaComponentToStatistic) {
            this.mapQuotaComponentToStatistic = new ConcurrentHashMap<>(mapQuotaComponentToStatistic);
        }

        public Statistic getStatistic(QuotaComponent quotaComponent) {
            return mapQuotaComponentToStatistic.computeIfAbsent(quotaComponent, key -> new Statistic(new AtomicLong(), new ConcurrentLinkedDeque<>()));
        }

        public Snapshot snapshot() {
            return new Snapshot(mapQuotaComponentToStatistic.entrySet().stream()
                .map(quotaComponentStatisticEntry -> new RecomputeSingleQuotaComponentResult(quotaComponentStatisticEntry.getKey().getValue(),
                    quotaComponentStatisticEntry.getValue().processedIdentifierCount.get(),
                    ImmutableList.copyOf(quotaComponentStatisticEntry.getValue().failedIdentifiers)))
                .collect(Collectors.toUnmodifiableList()));
        }
    }

    private final UsersRepository usersRepository;
    private final Map<QuotaComponent, RecomputeSingleComponentCurrentQuotasService> recomputeSingleComponentCurrentQuotasServiceMap;

    @Inject
    public RecomputeCurrentQuotasService(UsersRepository usersRepository,
                                         Set<RecomputeSingleComponentCurrentQuotasService> recomputeSingleComponentCurrentQuotasServices) {
        this.usersRepository = usersRepository;
        this.recomputeSingleComponentCurrentQuotasServiceMap = recomputeSingleComponentCurrentQuotasServices.stream()
            .collect(Collectors.toUnmodifiableMap(recomputeSingleComponentCurrentQuotasService -> recomputeSingleComponentCurrentQuotasService.getQuotaComponent(),
                recomputeSingleComponentCurrentQuotasService -> recomputeSingleComponentCurrentQuotasService));
    }

    public Mono<Task.Result> recomputeCurrentQuotas(Context context, RunningOptions runningOptions) {
        return Flux.from(usersRepository.listReactive())
            .transform(ReactorUtils.<Username, Task.Result>throttle()
                .elements(runningOptions.getUsersPerSecond())
                .per(Duration.ofSeconds(1))
                .forOperation(username -> recomputeQuotasOfUser(runningOptions.getQuotaComponents(), context, username)))
            .reduce(Task.Result.COMPLETED, Task::combine)
            .onErrorResume(UsersRepositoryException.class, e -> {
                LOGGER.error("Error while accessing users from repository", e);
                return Mono.just(Task.Result.PARTIAL);
            });
    }

    private Mono<Task.Result> recomputeQuotasOfUser(List<QuotaComponent> quotaComponents, Context context, Username username) {
        if (quotaComponents.isEmpty()) {
            return Flux.merge(recomputeSingleComponentCurrentQuotasServiceMap.values().stream()
                    .map(recomputeSingleComponentCurrentQuotasService -> recomputeCurrentQuotas(recomputeSingleComponentCurrentQuotasService, context, username))
                    .collect(Collectors.toUnmodifiableList()))
                .reduce(Task.Result.COMPLETED, Task::combine);
        } else {
            return Flux.fromIterable(quotaComponents)
                .flatMap(quotaComponent -> Optional.ofNullable(recomputeSingleComponentCurrentQuotasServiceMap.get(quotaComponent))
                    .map(recomputeSingleComponentCurrentQuotasService -> recomputeCurrentQuotas(recomputeSingleComponentCurrentQuotasService, context, username))
                    .orElse(Mono.just(Task.Result.PARTIAL)))
                .reduce(Task.Result.COMPLETED, Task::combine);
        }
    }

    public Mono<Task.Result> recomputeCurrentQuotas(RecomputeSingleComponentCurrentQuotasService recomputeSingleComponentCurrentQuotasService, Context context, Username username) {
        return recomputeSingleComponentCurrentQuotasService.recomputeCurrentQuotas(username)
            .then(Mono.just(Task.Result.COMPLETED))
            .doOnNext(any -> {
                LOGGER.info("jmap current upload usage quota recomputed for {}", username);
                context.getStatistic(recomputeSingleComponentCurrentQuotasService.getQuotaComponent()).incrementProcessed();
            })
            .onErrorResume(e -> {
                LOGGER.error("Error while recomputing jmap current upload usage quota for {}", username, e);
                context.getStatistic(recomputeSingleComponentCurrentQuotasService.getQuotaComponent()).addToFailedIdentifiers(username.asString());
                return Mono.just(Task.Result.PARTIAL);
            });
    }
}
