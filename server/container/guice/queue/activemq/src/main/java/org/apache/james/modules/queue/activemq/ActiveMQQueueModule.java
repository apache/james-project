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

package org.apache.james.modules.queue.activemq;

import java.io.FileNotFoundException;

import jakarta.jms.ConnectionFactory;

import org.apache.activemq.store.PersistenceAdapter;
import org.apache.activemq.store.kahadb.KahaDBPersistenceAdapter;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.queue.activemq.ActiveMQConfiguration;
import org.apache.james.queue.activemq.ActiveMQHealthCheck;
import org.apache.james.queue.activemq.ActiveMQMailQueueFactory;
import org.apache.james.queue.activemq.EmbeddedActiveMQ;
import org.apache.james.queue.activemq.metric.ActiveMQMetricCollector;
import org.apache.james.queue.activemq.metric.ActiveMQMetricCollectorImpl;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;
import org.apache.james.utils.PropertiesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;

public class ActiveMQQueueModule extends AbstractModule {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActiveMQQueueModule.class);
    private static final String FILENAME = "activemq";

    @Override
    protected void configure() {
        bind(PersistenceAdapter.class).to(KahaDBPersistenceAdapter.class);
        bind(KahaDBPersistenceAdapter.class).in(Scopes.SINGLETON);
        bind(EmbeddedActiveMQ.class).in(Scopes.SINGLETON);
        bind(ActiveMQMailQueueFactory.class).in(Scopes.SINGLETON);
        bind(ActiveMQMetricCollector.class).to(ActiveMQMetricCollectorImpl.class);
        bind(ActiveMQMetricCollectorImpl.class).in(Scopes.SINGLETON);

        Multibinder.newSetBinder(binder(), HealthCheck.class).addBinding().to(ActiveMQHealthCheck.class);
    }
    
    @Provides
    @Singleton
    ConnectionFactory provideEmbededActiveMQ(EmbeddedActiveMQ embeddedActiveMQ) {
        return embeddedActiveMQ.getConnectionFactory();
    }

    @Provides
    @Singleton
    public MailQueueFactory<? extends ManageableMailQueue> createActiveMQManageableMailQueueFactory(ActiveMQMailQueueFactory activeMQMailQueueFactory) {
        activeMQMailQueueFactory.setUseJMX(true);
        activeMQMailQueueFactory.init();
        return activeMQMailQueueFactory;
    }

    @Provides
    @Singleton
    public MailQueueFactory<?> provideActiveMQMailQueueFactory(MailQueueFactory<? extends ManageableMailQueue> mailQueueFactory) {
        return mailQueueFactory;
    }

    @Provides
    @Singleton
    public MailQueueFactory<? extends MailQueue> provideMailQueueFactoryGenerics(ActiveMQMailQueueFactory queueFactory) {
        return queueFactory;
    }

    @Singleton
    @Provides
    ActiveMQConfiguration activeMQConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException {
        try {
            Configuration configuration = propertiesProvider.getConfigurations(FILENAME);
            return ActiveMQConfiguration.from(configuration);
        } catch (FileNotFoundException e) {
            LOGGER.warn("Could not find {} configuration file, using default configuration", FILENAME);
            return ActiveMQConfiguration.getDefault();
        }
    }

    @ProvidesIntoSet
    InitializationOperation configureMetricCollector(ActiveMQMetricCollector metricCollector) {
        return InitilizationOperationBuilder
            .forClass(ActiveMQMetricCollector.class)
            .init(metricCollector::start);
    }
}
