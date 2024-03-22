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

package org.apache.james.queue.jms;

import jakarta.jms.ConnectionFactory;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.apache.james.metrics.api.GaugeRegistry;
import org.apache.james.metrics.api.NoopGaugeRegistry;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.queue.api.MailQueueFactoryContract;
import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.james.queue.api.ManageableMailQueueFactoryContract;
import org.apache.james.queue.api.RawMailQueueItemDecoratorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;


@ExtendWith(BrokerExtension.class)
public class JMSCacheableMailQueueFactoryTest implements MailQueueFactoryContract<ManageableMailQueue>, ManageableMailQueueFactoryContract {

    private JMSMailQueueFactory mailQueueFactory;

    @BeforeEach
    void setUp(BrokerService broker) {
        ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://localhost?create=false");
        RawMailQueueItemDecoratorFactory mailQueueItemDecoratorFactory = new RawMailQueueItemDecoratorFactory();
        RecordingMetricFactory metricFactory = new RecordingMetricFactory();
        GaugeRegistry gaugeRegistry = new NoopGaugeRegistry();
        mailQueueFactory = new JMSMailQueueFactory(connectionFactory, mailQueueItemDecoratorFactory, metricFactory, gaugeRegistry);
        mailQueueFactory.setUseJMX(false);
    }

    @AfterEach
    void tearDown() {
        mailQueueFactory.destroy();
    }

    @Override
    public MailQueueFactory<ManageableMailQueue> getMailQueueFactory() {
        return mailQueueFactory;
    }
}