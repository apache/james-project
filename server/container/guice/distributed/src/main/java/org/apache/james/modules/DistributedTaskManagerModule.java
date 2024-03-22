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

package org.apache.james.modules;

import static org.apache.james.modules.queue.rabbitmq.RabbitMQModule.RABBITMQ_CONFIGURATION_NAME;

import java.io.FileNotFoundException;

import jakarta.inject.Singleton;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.rabbitmq.SimpleConnectionPool;
import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.modules.server.HostnameModule;
import org.apache.james.modules.server.TaskSerializationModule;
import org.apache.james.task.TaskManager;
import org.apache.james.task.eventsourcing.EventSourcingTaskManager;
import org.apache.james.task.eventsourcing.TaskExecutionDetailsProjection;
import org.apache.james.task.eventsourcing.TerminationSubscriber;
import org.apache.james.task.eventsourcing.WorkQueueSupplier;
import org.apache.james.task.eventsourcing.cassandra.CassandraTaskExecutionDetailsProjection;
import org.apache.james.task.eventsourcing.cassandra.CassandraTaskExecutionDetailsProjectionModule;
import org.apache.james.task.eventsourcing.distributed.CancelRequestQueueName;
import org.apache.james.task.eventsourcing.distributed.DistributedTaskManagerHealthCheck;
import org.apache.james.task.eventsourcing.distributed.RabbitMQTerminationSubscriber;
import org.apache.james.task.eventsourcing.distributed.RabbitMQWorkQueue;
import org.apache.james.task.eventsourcing.distributed.RabbitMQWorkQueueConfiguration;
import org.apache.james.task.eventsourcing.distributed.RabbitMQWorkQueueConfiguration$;
import org.apache.james.task.eventsourcing.distributed.RabbitMQWorkQueueReconnectionHandler;
import org.apache.james.task.eventsourcing.distributed.RabbitMQWorkQueueSupplier;
import org.apache.james.task.eventsourcing.distributed.TerminationQueueName;
import org.apache.james.task.eventsourcing.distributed.TerminationReconnectionHandler;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;
import org.apache.james.utils.PropertiesProvider;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;

public class DistributedTaskManagerModule extends AbstractModule {

    @Override
    protected void configure() {
        install(new HostnameModule());
        install(new TaskSerializationModule());

        bind(CassandraTaskExecutionDetailsProjection.class).in(Scopes.SINGLETON);
        bind(EventSourcingTaskManager.class).in(Scopes.SINGLETON);
        bind(RabbitMQWorkQueueSupplier.class).in(Scopes.SINGLETON);
        bind(RabbitMQTerminationSubscriber.class).in(Scopes.SINGLETON);
        bind(TaskExecutionDetailsProjection.class).to(CassandraTaskExecutionDetailsProjection.class);
        bind(TerminationSubscriber.class).to(RabbitMQTerminationSubscriber.class);
        bind(TaskManager.class).to(EventSourcingTaskManager.class);
        bind(WorkQueueSupplier.class).to(RabbitMQWorkQueueSupplier.class);
        bind(CancelRequestQueueName.class).toInstance(CancelRequestQueueName.generate());
        bind(TerminationQueueName.class).toInstance(TerminationQueueName.generate());

        Multibinder<CassandraModule> cassandraDataDefinitions = Multibinder.newSetBinder(binder(), CassandraModule.class);
        cassandraDataDefinitions.addBinding().toInstance(CassandraTaskExecutionDetailsProjectionModule.MODULE());

        Multibinder<SimpleConnectionPool.ReconnectionHandler> reconnectionHandlerMultibinder = Multibinder.newSetBinder(binder(), SimpleConnectionPool.ReconnectionHandler.class);
        reconnectionHandlerMultibinder.addBinding().to(RabbitMQWorkQueueReconnectionHandler.class);
        reconnectionHandlerMultibinder.addBinding().to(TerminationReconnectionHandler.class);

        Multibinder.newSetBinder(binder(), HealthCheck.class)
            .addBinding()
            .to(DistributedTaskManagerHealthCheck.class);
    }

    @Provides
    @Singleton
    private RabbitMQWorkQueueConfiguration getWorkQueueConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException {
        try {
            Configuration configuration = propertiesProvider.getConfiguration(RABBITMQ_CONFIGURATION_NAME);
            return RabbitMQWorkQueueConfiguration$.MODULE$.from(configuration);
        } catch (FileNotFoundException e) {
            return RabbitMQWorkQueueConfiguration$.MODULE$.enabled();
        }
    }

    @ProvidesIntoSet
    InitializationOperation terminationSubscriber(RabbitMQTerminationSubscriber instance) {
        return InitilizationOperationBuilder
            .forClass(RabbitMQTerminationSubscriber.class)
            .init(instance::start);
    }

    @ProvidesIntoSet
    InitializationOperation workQueue(EventSourcingTaskManager instance) {
        return InitilizationOperationBuilder
            .forClass(RabbitMQWorkQueue.class)
            .init(instance::start);
    }

}
