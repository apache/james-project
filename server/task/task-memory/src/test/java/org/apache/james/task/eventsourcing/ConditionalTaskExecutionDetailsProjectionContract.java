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

package org.apache.james.task.eventsourcing;

import static org.apache.james.task.TaskExecutionDetailsFixture.TASK_EXECUTION_DETAILS;
import static org.apache.james.task.TaskExecutionDetailsFixture.TASK_EXECUTION_DETAILS_UPDATED;
import static org.apache.james.task.TaskExecutionDetailsFixture.TASK_ID;
import static org.apache.james.task.eventsourcing.LoadHookedTaskExecutionDetailsProjection.HOSTNAME;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.eventsourcing.EventId;
import org.apache.james.task.MemoryReferenceTask;
import org.apache.james.task.Task;
import org.apache.james.task.TaskId;
import org.apache.james.task.TaskManager;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;
import scala.compat.java8.OptionConverters;

/**
 * For the projections able to save details only if the stored status is the expected one, which closes the race
 * between two status transitions.
 */
public interface ConditionalTaskExecutionDetailsProjectionContract extends TaskExecutionDetailsProjectionContract {

    @Test
    default void conditionalUpdateShouldSaveWhenTheStatusIsTheExpectedOne() {
        TaskExecutionDetailsProjection testee = testee();
        testee.update(TASK_EXECUTION_DETAILS());

        Boolean saved = Mono.from(testee.updateReactive(TASK_EXECUTION_DETAILS_UPDATED(), TaskManager.Status.COMPLETED)).block();

        assertThat(saved).isTrue();
        assertThat(OptionConverters.toJava(testee.load(TASK_ID())).get().getStatus())
            .isEqualTo(TaskManager.Status.FAILED);
    }

    @Test
    default void conditionalUpdateShouldNotSaveWhenTheStatusChanged() {
        TaskExecutionDetailsProjection testee = testee();
        testee.update(TASK_EXECUTION_DETAILS());

        Boolean saved = Mono.from(testee.updateReactive(TASK_EXECUTION_DETAILS_UPDATED(), TaskManager.Status.IN_PROGRESS)).block();

        assertThat(saved).isFalse();
        assertThat(OptionConverters.toJava(testee.load(TASK_ID())).get().getStatus())
            .isEqualTo(TaskManager.Status.COMPLETED);
    }

    @Test
    default void aCancelRequestLoadedBeforeTheCompletionShouldNotRevertTheCompletedStatus() {
        TaskId taskId = TaskId.generateTaskId();
        TaskAggregateId aggregateId = new TaskAggregateId(taskId);
        LoadHookedTaskExecutionDetailsProjection projection = new LoadHookedTaskExecutionDetailsProjection(testee());
        projection.handle(new Created(aggregateId, EventId.fromSerialized(0), new MemoryReferenceTask(() -> Task.Result.COMPLETED), HOSTNAME));
        projection.handle(new Started(aggregateId, EventId.fromSerialized(1), HOSTNAME));

        projection.handleAfterNextLoad(new Completed(aggregateId, EventId.fromSerialized(3), Task.Result.COMPLETED, scala.Option.empty()));
        projection.handle(new CancelRequested(aggregateId, EventId.fromSerialized(2), HOSTNAME));

        assertThat(OptionConverters.toJava(projection.load(taskId)).get().getStatus())
            .isEqualTo(TaskManager.Status.COMPLETED);
    }
}
