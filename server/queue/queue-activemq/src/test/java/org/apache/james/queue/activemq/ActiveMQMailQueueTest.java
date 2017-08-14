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
package org.apache.james.queue.activemq;

import java.util.Arrays;

import javax.jms.ConnectionFactory;

import org.apache.activemq.broker.BrokerPlugin;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.region.policy.PolicyEntry;
import org.apache.activemq.broker.region.policy.PolicyMap;
import org.apache.activemq.plugin.StatisticsBrokerPlugin;
import org.apache.james.metrics.api.NoopMetricFactory;
import org.apache.james.queue.api.MailQueueItemDecoratorFactory;
import org.apache.james.queue.jms.AbstractJMSMailQueueTest;
import org.apache.james.queue.jms.JMSMailQueue;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ActiveMQMailQueueTest extends AbstractJMSMailQueueTest {

    private static BrokerService brokerService;

    @BeforeClass
    public static void setupBroker() throws Exception {
        brokerService = createBroker();
        brokerService.start();
    }

    @AfterClass
    public static void tearDownBroker() throws Exception {
        if (brokerService != null) {
            brokerService.stop();
        }
    }

    protected static BrokerService createBroker() throws Exception {
        BrokerService broker = new BrokerService();
        broker.setPersistent(false);
        broker.setUseJmx(false);
        broker.addConnector("tcp://127.0.0.1:61616");

        // Enable priority support
        PolicyMap pMap = new PolicyMap();
        PolicyEntry entry = new PolicyEntry();
        entry.setPrioritizedMessages(true);
        entry.setQueue(QUEUE_NAME);
        pMap.setPolicyEntries(Arrays.asList(entry));
        broker.setDestinationPolicy(pMap);
        // Enable statistics
        broker.setPlugins(new BrokerPlugin[]{new StatisticsBrokerPlugin()});
        broker.setEnableStatistics(true);

        return broker;
    }

    @Override
    protected JMSMailQueue createQueue(ConnectionFactory factory, MailQueueItemDecoratorFactory mailQueueItemDecoratorFactory, String queueName) {
        Logger log = LoggerFactory.getLogger(ActiveMQMailQueueTest.class);

        return new ActiveMQMailQueue(factory, mailQueueItemDecoratorFactory, queueName, useBlobMessages(), new NoopMetricFactory());
    }

    protected boolean useBlobMessages() {
        return false;
    }
}
