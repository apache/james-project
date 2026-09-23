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

import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.james.metrics.api.GaugeRegistry;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.queue.api.DelayedManageableMailQueueContract;
import org.apache.james.queue.api.DelayedPriorityMailQueueContract;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.MailQueueMetricContract;
import org.apache.james.queue.api.MailQueueMetricExtension;
import org.apache.james.queue.api.MailQueueName;
import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.james.queue.api.PriorityManageableMailQueueContract;
import org.apache.james.queue.api.RawMailQueueItemDecoratorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.google.common.annotations.VisibleForTesting;

@ExtendWith(BrokerExtension.class)
public class JMSCacheableMailQueueTest implements DelayedManageableMailQueueContract, PriorityManageableMailQueueContract, DelayedPriorityMailQueueContract,
    MailQueueMetricContract {

    @VisibleForTesting
    static final int BUFFER_SIZE = 10;
    private static final int DELAYED_MESSAGES_COUNT = 15;

    private JMSCacheableMailQueue mailQueue;

    @BeforeEach
    void setUp(EmbeddedActiveMQ broker, MailQueueMetricExtension.MailQueueMetricTestSystem metricTestSystem) {
        // Use InVM transport - broker ID matches BrokerExtension instance counter
        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://0");
        connectionFactory.setConsumerWindowSize(0);
        RawMailQueueItemDecoratorFactory mailQueueItemDecoratorFactory = new RawMailQueueItemDecoratorFactory();
        MetricFactory metricFactory = metricTestSystem.getMetricFactory();
        GaugeRegistry gaugeRegistry = metricTestSystem.getSpyGaugeRegistry();
        MailQueueName queueName = BrokerExtension.generateRandomQueueName();
        mailQueue = new JMSCacheableMailQueue(connectionFactory, mailQueueItemDecoratorFactory, queueName, metricFactory, gaugeRegistry);
    }

    @AfterEach
    void tearDown() {
        mailQueue.dispose();
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
    @Disabled("JAMES-2308 Flushing JMS mail queue randomly re-order them" +
        "Random test failing around 1% of the time")
    public void flushShouldPreserveBrowseOrder() {

    }

    @Test
    @Override
    @Disabled("JAMES-2312 JMS clear mailqueue can ommit some messages" +
        "Random test failing around 1% of the time")
    public void clearShouldRemoveAllElements() {

    }

    @Test
    @Override
    @Disabled("JAMES-2544 Mixing concurrent operations might lead to a missing file and errors upon dequeue")
    public void concurrentEnqueueDequeueShouldNotFail() {

    }

    @Test
    @Override
    @Disabled("JAMES-2544 Mixing concurrent operations might lead to a missing file and errors upon dequeue")
    public void concurrentEnqueueDequeueWithAckNackShouldNotFail() {

    }

    @Test
    @Override
    @Disabled("JAMES-2794 This test never finishes")
    public void enQueueShouldAcceptMailWithDuplicatedNames() {

    }

    @Test
    @Override
    @Disabled("JAMES-3687 Delayed deletes are buggy")
    public void delayedEmailsShouldBeDeleted() {

    }

    @Test
    @Override
    @Disabled("JAMES-3687 Delayed deletes are buggy")
    public void delayedEmailsShouldBeDeletedWhenMixedWithOtherEmails() {

    }

    @Test
    void delayedMessagesDoNotBlockReadyDelivery_JAMES4192() throws Exception {
        // Enqueue 15 delayed messages (exceeding testing buffer size of 10) to verify out-of-band scheduled delivery (JAMES-4192)
        // In Artemis, delayed messages are managed out-of-band by ScheduledDeliveryHandler and do not
        // occupy active queue page buffers, preventing Head-of-Line blocking of ready messages.
        for (int i = 0; i < DELAYED_MESSAGES_COUNT; i++) {
            mailQueue.enQueue(org.apache.james.queue.api.Mails.defaultMail().name("delayed-" + i).build(), 1, java.util.concurrent.TimeUnit.HOURS);
        }

        // Enqueue one ready message
        mailQueue.enQueue(org.apache.james.queue.api.Mails.defaultMail().name("ready-message").build());

        // Ready message must be dequeued immediately without waiting or head-of-line blocking
        reactor.core.publisher.Mono<MailQueue.MailQueueItem> dequeueMono =
            reactor.core.publisher.Flux.from(mailQueue.deQueue()).next();

        MailQueue.MailQueueItem dequeued = dequeueMono.block(java.time.Duration.ofSeconds(10));
        org.assertj.core.api.Assertions.assertThat(dequeued).isNotNull();
        org.assertj.core.api.Assertions.assertThat(dequeued.getMail().getName()).isEqualTo("ready-message");
        dequeued.done(MailQueue.MailQueueItem.CompletionStatus.SUCCESS);
    }
}
