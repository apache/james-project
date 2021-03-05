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

import static org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

import org.apache.james.core.Username;
import org.apache.james.json.DTOModule;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.fge.lambdas.Throwing;
import com.github.steveash.guavate.Guavate;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class CreateMissingParentsTask implements Task {
    private static final Username USERNAME = Username.of("createMissingParentsTask");
    static final TaskType TASK_TYPE = TaskType.of("CreateMissingParentsTask");

    public static class AdditionalInformation implements TaskExecutionDetails.AdditionalInformation {
        private static AdditionalInformation from(Set<String> created, long totalCreated, Set<String> failures, long totalFailure) {
            return new AdditionalInformation(Clock.systemUTC().instant(), created, totalCreated, failures, totalFailure);
        }

        private final Instant timestamp;
        private final Set<String> created;
        private final long totalCreated;
        private final Set<String> failures;
        private final long totalFailure;

        public AdditionalInformation(Instant timestamp, Set<String> created, long totalCreated, Set<String> failures, long totalFailure) {
            this.created = created;
            this.failures = failures;
            this.totalCreated = totalCreated;
            this.totalFailure = totalFailure;
            this.timestamp = timestamp;
        }

        public Set<String> getCreated() {
            return created;
        }

        public long getTotalCreated() {
            return totalCreated;
        }

        public Set<String> getFailures() {
            return failures;
        }

        public long getTotalFailure() {
            return totalFailure;
        }

        @Override
        public Instant timestamp() {
            return timestamp;
        }
    }

    public static class CreateMissingParentsTaskDTO implements TaskDTO {
        private final String type;

        public CreateMissingParentsTaskDTO(@JsonProperty("type") String type) {
            this.type = type;
        }

        @Override
        public String getType() {
            return type;
        }
    }

    public static TaskDTOModule<CreateMissingParentsTask, CreateMissingParentsTask.CreateMissingParentsTaskDTO> module(MailboxManager mailboxManager) {
        return DTOModule
            .forDomainObject(CreateMissingParentsTask.class)
            .convertToDTO(CreateMissingParentsTask.CreateMissingParentsTaskDTO.class)
            .toDomainObjectConverter(dto -> new CreateMissingParentsTask(mailboxManager))
            .toDTOConverter((task, type) -> new CreateMissingParentsTask.CreateMissingParentsTaskDTO(type))
            .typeName(TASK_TYPE.asString())
            .withFactory(TaskDTOModule::new);
    }

    private final ConcurrentLinkedQueue<MailboxId> created;
    private final AtomicLong totalCreated;
    private final ConcurrentLinkedQueue<String> failures;
    private final AtomicLong totalFailure;
    private final MailboxManager mailboxManager;

    public CreateMissingParentsTask(MailboxManager mailboxManager) {
        this.mailboxManager = mailboxManager;
        this.created = new ConcurrentLinkedQueue<>();
        this.totalCreated = new AtomicLong(0L);
        this.failures = new ConcurrentLinkedQueue<>();
        this.totalFailure = new AtomicLong(0L);
    }

    @Override
    public Result run() {
        MailboxSession session = mailboxManager.createSystemSession(USERNAME);
        try {
            List<MailboxPath> mailboxPaths = mailboxManager.list(session);

            char delimiter = session.getPathDelimiter();
            Set<MailboxPath> parentPaths = mailboxPaths
                .stream()
                .filter(path -> path.hasParent(delimiter))
                .flatMap(path -> path.getParents(delimiter))
                .collect(Guavate.toImmutableSet());

            return Flux.fromIterable(parentPaths)
                .filter(Predicate.not(mailboxPaths::contains))
                .flatMap(this::createMailbox, DEFAULT_CONCURRENCY)
                .reduce(Task::combine)
                .switchIfEmpty(Mono.just(Result.COMPLETED))
                .subscribeOn(Schedulers.elastic())
                .block();
        } catch (MailboxException e) {
            LOGGER.error("Error fetching mailbox paths", e);
            return Result.PARTIAL;
        }
    }

    private Mono<Result> createMailbox(MailboxPath path) {
        return Mono.fromRunnable(() -> {
            MailboxSession ownerSession = mailboxManager.createSystemSession(path.getUser());
            Optional<MailboxId> mailboxId = Throwing.supplier(() -> mailboxManager.createMailbox(path, ownerSession)).sneakyThrow().get();
            recordSuccess(mailboxId);
        })
        .then(Mono.just(Result.COMPLETED))
        .onErrorResume(e -> {
            LOGGER.error("Error creating missing parent mailbox: {}", path.getName(), e);
            recordFailure(path);
            return Mono.just(Result.PARTIAL);
        });
    }

    @Override
    public TaskType type() {
        return TASK_TYPE;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.of(AdditionalInformation.from(
            created.stream().map(MailboxId::serialize).collect(Guavate.toImmutableSet()),
            totalCreated.get(),
            failures.stream().collect(Guavate.toImmutableSet()),
            totalFailure.get()));
    }

    private void recordSuccess(Optional<MailboxId> mailboxId) {
        if (mailboxId.isPresent()) {
            created.add(mailboxId.get());
            totalCreated.incrementAndGet();
        }
    }

    private void recordFailure(MailboxPath mailboxPath) {
        failures.add(mailboxPath.asString());
        totalFailure.incrementAndGet();
    }
}
