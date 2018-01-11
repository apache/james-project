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

import javax.jms.ConnectionFactory;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.region.policy.PolicyEntry;
import org.apache.activemq.broker.region.policy.PolicyMap;
import org.apache.james.metrics.api.NoopMetricFactory;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.james.queue.api.ManageableMailQueueContract;
import org.apache.james.queue.api.RawMailQueueItemDecoratorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

public class JMSMailQueueTest implements ManageableMailQueueContract {

    private final static String QUEUE_NAME = "test";

    private JMSMailQueue mailQueue;

    private BrokerService broker;

    @BeforeEach
    public void setUp() throws Exception {
        broker = createBroker();
        broker.start();
        ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://localhost?create=false");
        RawMailQueueItemDecoratorFactory mailQueueItemDecoratorFactory = new RawMailQueueItemDecoratorFactory();
        NoopMetricFactory metricFactory = new NoopMetricFactory();
        mailQueue = new JMSMailQueue(connectionFactory, mailQueueItemDecoratorFactory, QUEUE_NAME, metricFactory);
    }

    private BrokerService createBroker() throws Exception {
        BrokerService aBroker = new BrokerService();
        aBroker.setPersistent(false);
        aBroker.setUseJmx(false);
        aBroker.addConnector("tcp://127.0.0.1:61616");

        // Enable priority support
        PolicyMap pMap = new PolicyMap();
        PolicyEntry entry = new PolicyEntry();
        entry.setPrioritizedMessages(true);
        entry.setQueue(QUEUE_NAME);
        pMap.setPolicyEntries(ImmutableList.of(entry));
        aBroker.setDestinationPolicy(pMap);

        return aBroker;
    }

    @AfterEach
    public void tearDown() throws Exception {
        broker.stop();
    }

    @Override
    public MailQueue getMailQueue() {
        return mailQueue;
    }

    @Test
    @Override
    public ManageableMailQueue getManageableMailQueue() {
        return mailQueue;
    }

    @Override
    @Disabled("JAMES-2295 Disabled as test was dead-locking")
    public void dequeueCanBeChainedBeforeAck() {

    }

    @Test
    @Override
    @Disabled("JAMES-2295 Disabled as test was dead-locking")
    public void dequeueCouldBeInterleavingWithOutOfOrderAck() {

    }

    @Test
    @Override
    @Disabled("JAMES-2301 Per recipients headers are not attached to the message.")
    public void queueShouldPreservePerRecipientHeaders() {

    }

    @Test
    @Override
    @Disabled("JAMES-2296 Not handled by JMS mailqueue. Only single recipient per-recipient removal works")
    public void removeByRecipientShouldRemoveSpecificEmailWhenMultipleRecipients() {

    }
}
