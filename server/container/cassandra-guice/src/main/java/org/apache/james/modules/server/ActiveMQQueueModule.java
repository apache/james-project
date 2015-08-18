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

package org.apache.james.modules.server;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import org.apache.james.queue.activemq.ActiveMQMailQueueFactory;
import org.apache.james.queue.api.MailQueueFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.ConnectionFactory;

public class ActiveMQQueueModule extends AbstractModule {

    private static Logger LOGGER = LoggerFactory.getLogger(ActiveMQQueueModule.class);

    @Override
    protected void configure() {

    }

    @Provides
    @Singleton
    ConnectionFactory provideEmbededActiveMQ(EmbeddedActiveMQ embeddedActiveMQ) {
        return embeddedActiveMQ.getConnectionFactory();
    }

    @Provides
    @Singleton
    public MailQueueFactory createActiveMailQueueFactory(ConnectionFactory connectionFactory, ActiveMQMailQueueFactory activeMQMailQueueFactory) {
        activeMQMailQueueFactory.setUseJMX(true);
        activeMQMailQueueFactory.setConnectionFactory(connectionFactory);
        activeMQMailQueueFactory.setLog(LOGGER);
        activeMQMailQueueFactory.init();
        return activeMQMailQueueFactory;
    }

}
