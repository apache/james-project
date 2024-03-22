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

import jakarta.jms.ConnectionFactory;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.apache.james.metrics.api.NoopGaugeRegistry;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.queue.activemq.metric.ActiveMQMetricCollector;
import org.apache.james.queue.activemq.metric.ActiveMQMetricCollectorNoop;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.queue.api.MailQueueFactoryContract;
import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.james.queue.api.ManageableMailQueueFactoryContract;
import org.apache.james.queue.api.RawMailQueueItemDecoratorFactory;
import org.apache.james.queue.jms.BrokerExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;

public class ActiveMQMailQueueFactoryTest {

    @Nested
    @ExtendWith(BrokerExtension.class)
    public static class ActiveMQMailQueueFactoryNoBlobsTest implements MailQueueFactoryContract<ManageableMailQueue>, ManageableMailQueueFactoryContract {
        ActiveMQMailQueueFactory mailQueueFactory;

        @BeforeEach
        public void setUp(BrokerService brokerService) {
            ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://localhost?create=false");
            RawMailQueueItemDecoratorFactory mailQueueItemDecoratorFactory = new RawMailQueueItemDecoratorFactory();
            RecordingMetricFactory metricFactory = new RecordingMetricFactory();
            NoopGaugeRegistry gaugeRegistry = new NoopGaugeRegistry();
            ActiveMQMetricCollector metricCollector = new ActiveMQMetricCollectorNoop();
            mailQueueFactory = new ActiveMQMailQueueFactory(connectionFactory, mailQueueItemDecoratorFactory, metricFactory, gaugeRegistry, metricCollector);
            mailQueueFactory.setUseJMX(false);
            mailQueueFactory.setUseBlobMessages(false);
        }

        @AfterEach
        public void tearDown() {
            mailQueueFactory.destroy();
        }

        @Override
        public MailQueueFactory<ManageableMailQueue> getMailQueueFactory() {
            return mailQueueFactory;
        }
    }

    @Nested
    @ExtendWith(BrokerExtension.class)
    public static class ActiveMQMailQueueFactoryBlobsTest implements MailQueueFactoryContract<ManageableMailQueue>, ManageableMailQueueFactoryContract {

        static final String BASE_DIR = "file://target/james-test";

        ActiveMQMailQueueFactory mailQueueFactory;
        ActiveMQMailQueueBlobTest.MyFileSystem fileSystem;

        @BeforeEach
        public void setUp(BrokerService brokerService) {
            fileSystem = new ActiveMQMailQueueBlobTest.MyFileSystem();
            ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://localhost?create=false");


            FileSystemBlobTransferPolicy policy = new FileSystemBlobTransferPolicy();
            policy.setFileSystem(fileSystem);
            policy.setDefaultUploadUrl(BASE_DIR);
            connectionFactory.setBlobTransferPolicy(policy);

            RawMailQueueItemDecoratorFactory mailQueueItemDecoratorFactory = new RawMailQueueItemDecoratorFactory();
            RecordingMetricFactory metricFactory = new RecordingMetricFactory();
            NoopGaugeRegistry gaugeRegistry = new NoopGaugeRegistry();
            ActiveMQMetricCollector metricCollector = new ActiveMQMetricCollectorNoop();
            mailQueueFactory = new ActiveMQMailQueueFactory(connectionFactory, mailQueueItemDecoratorFactory, metricFactory, gaugeRegistry, metricCollector);
            mailQueueFactory.setUseJMX(false);
            mailQueueFactory.setUseBlobMessages(true);
        }

        @AfterEach
        public void tearDown() throws Exception {
            mailQueueFactory.destroy();
            fileSystem.destroy();
        }

        @Override
        public MailQueueFactory<ManageableMailQueue> getMailQueueFactory() {
            return mailQueueFactory;
        }
    }

}