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
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;
import org.apache.james.user.api.UsersRepository;
import org.reactivestreams.Publisher;

import com.google.common.annotations.VisibleForTesting;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class DeleteUsersDataOfDomainTask implements Task {
    static final TaskType TYPE = TaskType.of("DeleteUsersDataOfDomainTask");
    private static final int LOW_CONCURRENCY = 2;
    private static final int MAX_STORED_FAILED_USERS = 100;

    public static class AdditionalInformation implements TaskExecutionDetails.AdditionalInformation {
        private final Instant timestamp;
        private final Domain domain;
        private final long successfulUsersCount;
        private final long failedUsersCount;
        private final Set<Username> failedUsers;

        public AdditionalInformation(Instant timestamp, Domain domain, long successfulUsersCount, long failedUsersCount, Set<Username> failedUsers) {
            this.timestamp = timestamp;
            this.domain = domain;
            this.successfulUsersCount = successfulUsersCount;
            this.failedUsersCount = failedUsersCount;
            this.failedUsers = failedUsers;
        }

        public Domain getDomain() {
            return domain;
        }

        public long getSuccessfulUsersCount() {
            return successfulUsersCount;
        }

        public long getFailedUsersCount() {
            return failedUsersCount;
        }

        public Set<Username> getFailedUsers() {
            return failedUsers;
        }

        @Override
        public Instant timestamp() {
            return timestamp;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof AdditionalInformation) {
                AdditionalInformation that = (AdditionalInformation) o;

                return Objects.equals(this.successfulUsersCount, that.successfulUsersCount)
                    && Objects.equals(this.failedUsersCount, that.failedUsersCount)
                    && Objects.equals(this.timestamp, that.timestamp)
                    && Objects.equals(this.domain, that.domain);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(timestamp, domain, successfulUsersCount, failedUsersCount);
        }
    }

    static class Context {
        private final AtomicLong successfulUsersCount;
        private final AtomicLong failedUsersCount;
        private final Set<Username> failedUsers;

        public Context() {
            this.successfulUsersCount = new AtomicLong();
            this.failedUsersCount = new AtomicLong();
            this.failedUsers = ConcurrentHashMap.newKeySet();
        }

        private void increaseSuccessfulUsers() {
            successfulUsersCount.incrementAndGet();
        }

        private void increaseFailedUsers() {
            failedUsersCount.incrementAndGet();
        }

        private void addFailedUser(Username username) {
            failedUsers.add(username);
        }

        public long getSuccessfulUsersCount() {
            return successfulUsersCount.get();
        }

        public long getFailedUsersCount() {
            return failedUsersCount.get();
        }

        public Set<Username> getFailedUsers() {
            return failedUsers;
        }
    }

    private final Domain domain;
    private final DeleteUserDataService deleteUserDataService;
    private final UsersRepository usersRepository;
    private final Context context;

    public DeleteUsersDataOfDomainTask(DeleteUserDataService deleteUserDataService, Domain domain, UsersRepository usersRepository) {
        this.deleteUserDataService = deleteUserDataService;
        this.domain = domain;
        this.usersRepository = usersRepository;
        this.context = new Context();
    }

    @Override
    public Result run() {
        return Flux.from(usersRepository.listUsersOfADomainReactive(domain))
            .flatMap(deleteUserData(), LOW_CONCURRENCY)
            .reduce(Task::combine)
            .switchIfEmpty(Mono.just(Result.COMPLETED))
            .block();
    }

    private Function<Username, Publisher<Result>> deleteUserData() {
        return username -> deleteUserDataService.performer().deleteUserData(username)
            .then(Mono.fromCallable(() -> {
                context.increaseSuccessfulUsers();
                return Result.COMPLETED;
            }))
            .onErrorResume(error -> {
                LOGGER.error("Error when deleting data of user {}", username.asString(), error);
                context.increaseFailedUsers();
                if (context.failedUsers.size() < MAX_STORED_FAILED_USERS) {
                    context.addFailedUser(username);
                }
                return Mono.just(Result.PARTIAL);
            });
    }

    @Override
    public TaskType type() {
        return TYPE;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.of(new AdditionalInformation(Clock.systemUTC().instant(), domain, context.getSuccessfulUsersCount(),
            context.getFailedUsersCount(), context.getFailedUsers()));
    }

    public Domain getDomain() {
        return domain;
    }

    @VisibleForTesting
    Context getContext() {
        return context;
    }
}
