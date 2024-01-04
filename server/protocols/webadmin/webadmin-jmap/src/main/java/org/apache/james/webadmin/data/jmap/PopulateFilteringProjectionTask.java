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

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.james.core.Username;
import org.apache.james.eventsourcing.EventId;
import org.apache.james.eventsourcing.EventWithState;
import org.apache.james.jmap.api.filtering.Rules;
import org.apache.james.jmap.api.filtering.impl.EventSourcingFilteringManagement;
import org.apache.james.jmap.api.filtering.impl.FilteringAggregate;
import org.apache.james.jmap.api.filtering.impl.FilteringAggregateId;
import org.apache.james.jmap.api.filtering.impl.RuleSetDefined;
import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;
import org.apache.james.user.api.UsersRepository;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import scala.Some;

public class PopulateFilteringProjectionTask implements Task {
    static final TaskType TASK_TYPE = TaskType.of("PopulateFilteringProjectionTask");

    public static class AdditionalInformation implements TaskExecutionDetails.AdditionalInformation {
        private static AdditionalInformation from(AtomicLong processedUserCount, AtomicLong failedUserCount) {
            return new AdditionalInformation(processedUserCount.get(),
                failedUserCount.get(),
                Clock.systemUTC().instant());
        }

        private final long processedUserCount;
        private final long failedUserCount;
        private final Instant timestamp;

        public AdditionalInformation(long processedUserCount, long failedUserCount, Instant timestamp) {
            this.processedUserCount = processedUserCount;
            this.failedUserCount = failedUserCount;
            this.timestamp = timestamp;
        }

        public long getProcessedUserCount() {
            return processedUserCount;
        }

        public long getFailedUserCount() {
            return failedUserCount;
        }

        @Override
        public Instant timestamp() {
            return timestamp;
        }
    }

    public static class PopulateFilteringProjectionTaskDTO implements TaskDTO {
        private final String type;

        public PopulateFilteringProjectionTaskDTO(@JsonProperty("type") String type) {
            this.type = type;
        }

        @Override
        public String getType() {
            return type;
        }
    }

    public static TaskDTOModule<PopulateFilteringProjectionTask, PopulateFilteringProjectionTaskDTO> module(EventSourcingFilteringManagement.NoReadProjection noReadProjection,
                                                                                                            EventSourcingFilteringManagement.ReadProjection readProjection,
                                                                                                            UsersRepository usersRepository) {
        return DTOModule
            .forDomainObject(PopulateFilteringProjectionTask.class)
            .convertToDTO(PopulateFilteringProjectionTaskDTO.class)
            .toDomainObjectConverter(dto -> asTask(noReadProjection, readProjection, usersRepository))
            .toDTOConverter(PopulateFilteringProjectionTask::asDTO)
            .typeName(TASK_TYPE.asString())
            .withFactory(TaskDTOModule::new);
    }

    private static PopulateFilteringProjectionTaskDTO asDTO(PopulateFilteringProjectionTask task, String type) {
        return new PopulateFilteringProjectionTaskDTO(type);
    }

    private static PopulateFilteringProjectionTask asTask(EventSourcingFilteringManagement.NoReadProjection noReadProjection,
                                                          EventSourcingFilteringManagement.ReadProjection readProjection,
                                                          UsersRepository usersRepository) {
        return new PopulateFilteringProjectionTask(noReadProjection, readProjection, usersRepository);
    }

    private final EventSourcingFilteringManagement.NoReadProjection noReadProjection;
    private final EventSourcingFilteringManagement.ReadProjection readProjection;
    private final UsersRepository usersRepository;
    private final AtomicLong processedUserCount = new AtomicLong(0L);
    private final AtomicLong failedUserCount = new AtomicLong(0L);

    public PopulateFilteringProjectionTask(EventSourcingFilteringManagement.NoReadProjection noReadProjection,
                                           EventSourcingFilteringManagement.ReadProjection readProjection,
                                           UsersRepository usersRepository) {
        this.noReadProjection = noReadProjection;
        this.readProjection = readProjection;
        this.usersRepository = usersRepository;
    }

    @Override
    public Result run() {
        return Flux.from(usersRepository.listReactive())
            .concatMap(user -> Mono.from(noReadProjection.listRulesForUser(user))
                .flatMap(rules ->
                    rules.getVersion().asEventId()
                        .flatMap(eventId -> readProjection.subscriber()
                            .map(s -> Mono.from(s.handleReactive(asEvent(user, rules, eventId)))))
                        .orElse(Mono.empty()))
                .thenReturn(Result.COMPLETED)
                .doOnNext(next -> processedUserCount.incrementAndGet())
                .onErrorResume(e -> {
                    LOGGER.error("Failed populating Cassandra filter read projection for {}", user);
                    failedUserCount.incrementAndGet();
                    return Mono.just(Result.PARTIAL);
                }))
            .reduce(Task::combine)
            .switchIfEmpty(Mono.just(Result.COMPLETED))
            .block();
    }

    private EventWithState asEvent(Username user, Rules rules, EventId eventId) {
        return new EventWithState(new RuleSetDefined(new FilteringAggregateId(user), eventId, ImmutableList.copyOf(rules.getRules())),
            Some.apply(new FilteringAggregate.FilterState(ImmutableList.copyOf(rules.getRules()))));
    }

    @Override
    public TaskType type() {
        return TASK_TYPE;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.of(AdditionalInformation.from(processedUserCount, failedUserCount));
    }
}