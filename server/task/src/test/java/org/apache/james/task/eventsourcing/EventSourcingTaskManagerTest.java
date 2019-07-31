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

import org.apache.james.eventsourcing.eventstore.EventStore;
import org.apache.james.eventsourcing.eventstore.memory.InMemoryEventStore;
import org.apache.james.task.CountDownLatchExtension;
import org.apache.james.task.MemoryWorkQueue;
import org.apache.james.task.SerialTaskManagerWorker;
import org.apache.james.task.TaskManager;
import org.apache.james.task.TaskManagerContract;
import org.apache.james.task.TaskManagerWorker;
import org.apache.james.task.WorkQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(CountDownLatchExtension.class)
class EventSourcingTaskManagerTest implements TaskManagerContract {

    private EventSourcingTaskManager taskManager;

    @BeforeEach
    void setUp() {
        TaskManagerWorker worker = new SerialTaskManagerWorker();
        WorkQueue workQueue = new MemoryWorkQueue(worker);
        EventStore eventStore = new InMemoryEventStore();
        TaskExecutionDetailsProjection executionDetailsProjection = new MemoryTaskExecutionDetailsProjection();
        taskManager = new EventSourcingTaskManager(worker, workQueue, eventStore, executionDetailsProjection);
    }

    @AfterEach
    void tearDown() {
        taskManager.close();
    }

    @Override
    public TaskManager taskManager() {
        return taskManager;
    }
}