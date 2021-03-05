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
import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.json.DTOModule;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CreateMissingParentsTask implements Task {
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
            .toDomainObjectConverter(dto -> new CreateMissingParentsTask(mailboxManager, Username.of(dto.username)))
            .toDTOConverter((task, type) -> new CreateMissingParentsTask.CreateMissingParentsTaskDTO(type, task.username.asString()))
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
        MailboxSession session = mailboxManager.createSystemSession(username);
        try {
            mailboxManager.list(session);
        } catch (MailboxException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public TaskType type() {
        return TASK_TYPE;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.of(AdditionalInformation.from(
            created.stream().map(MailboxId::serialize).collect(ImmutableSet.toImmutableSet()),
            totalCreated.get(),
            failures.stream().collect(ImmutableSet.toImmutableSet()),
            totalFailure.get()));
    }
}
