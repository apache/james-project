/**
 * *************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 * *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 * *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ***************************************************************/

package org.apache.james.modules;

import org.apache.james.modules.server.HostnameModule;
import org.apache.james.task.TaskManager;
import org.apache.james.task.eventsourcing.EventSourcingTaskManager;
import org.apache.james.task.eventsourcing.TaskExecutionDetailsProjection;
import org.apache.james.task.eventsourcing.TerminationSubscriber;
import org.apache.james.task.eventsourcing.WorkQueueSupplier;
import org.apache.james.task.eventsourcing.cassandra.CassandraTaskExecutionDetailsProjection;
import org.apache.james.task.eventsourcing.distributed.RabbitMQTerminationSubscriber;
import org.apache.james.task.eventsourcing.distributed.RabbitMQWorkQueueSupplier;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;

public class DistributedTaskManagerModule extends AbstractModule {

    @Override
    protected void configure() {
        install(new HostnameModule());
        bind(TaskExecutionDetailsProjection.class).in(Scopes.SINGLETON);
        bind(TaskManager.class).in(Scopes.SINGLETON);
        bind(WorkQueueSupplier.class).in(Scopes.SINGLETON);
        bind(TaskExecutionDetailsProjection.class).to(CassandraTaskExecutionDetailsProjection.class);
        bind(TerminationSubscriber.class).in(Scopes.SINGLETON);
        bind(TerminationSubscriber.class).to(RabbitMQTerminationSubscriber.class);
        bind(TaskManager.class).to(EventSourcingTaskManager.class);
        bind(WorkQueueSupplier.class).to(RabbitMQWorkQueueSupplier.class);
    }
}
