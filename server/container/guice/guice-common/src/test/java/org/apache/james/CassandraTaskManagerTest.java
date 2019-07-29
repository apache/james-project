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

package org.apache.james;

import org.apache.james.eventsourcing.eventstore.EventStore;
import org.apache.james.eventsourcing.eventstore.cassandra.CassandraEventStoreExtension;
import org.apache.james.eventsourcing.eventstore.cassandra.dto.EventDTOModule;
import org.apache.james.server.task.json.JsonTaskSerializer;
import org.apache.james.server.task.json.dto.TestTaskDTOModules;
import org.apache.james.task.CompletedTask;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskId;
import org.apache.james.task.TaskManager;
import org.apache.james.task.eventsourcing.EventSourcingTaskManager;
import org.apache.james.task.eventsourcing.MemoryRecentTasksProjection;
import org.apache.james.task.eventsourcing.MemoryTaskExecutionDetailsProjection;
import org.apache.james.task.eventsourcing.RecentTasksProjection;
import org.apache.james.task.eventsourcing.TaskExecutionDetailsProjection;
import org.apache.james.task.eventsourcing.cassandra.TasksSerializationModule;

import org.awaitility.Awaitility;
import org.awaitility.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class CassandraTaskManagerTest {
    private static final JsonTaskSerializer TASK_SERIALIZER = new JsonTaskSerializer(TestTaskDTOModules.COMPLETED_TASK_MODULE);

    private static final List<EventDTOModule<?, ?>> MODULES = TasksSerializationModule.MODULES.apply(TASK_SERIALIZER);

    @RegisterExtension
    static CassandraEventStoreExtension eventStoreExtension = new CassandraEventStoreExtension(MODULES.stream().toArray(EventDTOModule[]::new));

    @Test
    void givenOneEventStoreTwoEventTaskManagersShareTheSameEvents(EventStore eventStore) {
        RecentTasksProjection recentTasksProjection = new MemoryRecentTasksProjection();
        TaskExecutionDetailsProjection executionDetailsProjection = new MemoryTaskExecutionDetailsProjection();
        TaskManager taskManager1 = new EventSourcingTaskManager(eventStore, executionDetailsProjection, recentTasksProjection);
        TaskManager taskManager2 = new EventSourcingTaskManager(eventStore, executionDetailsProjection, recentTasksProjection);

        TaskId taskId = taskManager1.submit(new CompletedTask());
        Awaitility.await()
            .atMost(Duration.FIVE_SECONDS)
            .pollInterval(100L, TimeUnit.MILLISECONDS)
            .until(() -> taskManager1.await(taskId).getStatus() == TaskManager.Status.COMPLETED);

        TaskExecutionDetails detailsFromTaskManager1 = taskManager1.getExecutionDetails(taskId);
        TaskExecutionDetails detailsFromTaskManager2 = taskManager2.getExecutionDetails(taskId);
        assertThat(detailsFromTaskManager1).isEqualTo(detailsFromTaskManager2);
    }
}
