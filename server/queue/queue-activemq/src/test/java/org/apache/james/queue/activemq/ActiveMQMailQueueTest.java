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
import org.apache.james.queue.jms.BrokerExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(BrokerExtension.class)
@Tag(BrokerExtension.STATISTICS)
public class ActiveMQMailQueueTest implements DelayedManageableMailQueueContract, DelayedPriorityMailQueueContract, PriorityManageableMailQueueContract,
    MailQueueMetricContract {

    ActiveMQCacheableMailQueue mailQueue;

    @BeforeEach
    public void setUp(EmbeddedActiveMQ broker, MailQueueMetricExtension.MailQueueMetricTestSystem metricTestSystem) {
        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://0");
        connectionFactory.setConsumerWindowSize(0);
        RawMailQueueItemDecoratorFactory mailQueueItemDecoratorFactory = new RawMailQueueItemDecoratorFactory();
        MetricFactory metricFactory = metricTestSystem.getMetricFactory();
        GaugeRegistry gaugeRegistry = metricTestSystem.getSpyGaugeRegistry();
        MailQueueName queueName = BrokerExtension.generateRandomQueueName();
        mailQueue = new ActiveMQCacheableMailQueue(connectionFactory, mailQueueItemDecoratorFactory, queueName, metricFactory, gaugeRegistry);
    }

    @AfterEach
    public void tearDown() {
        mailQueue.dispose();
    }

    @Override
    public MailQueue getMailQueue() {
        return mailQueue;
    }

    @Override
    public ManageableMailQueue getManageableMailQueue() {
        return mailQueue;
    }

    @Test
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
    @Disabled("JAMES-2308 Flushing JMS mail queue randomly re-order them" +
        "Random test failing around 1% of the time")
    public void flushShouldPreserveBrowseOrder() {

    }

    @Test
    @Override
    @Disabled("JAMES-2309 Long overflow in JMS delays")
    public void enqueueWithVeryLongDelayShouldDelayMail() {

    }

    @Test
    @Override
    @Disabled("JAMES-2312 JMS clear mailqueue can ommit some messages" +
        "Random test failing around 1% of the time")
    public void clearShouldRemoveAllElements() {

    }

    @Test
    @Override
    @Disabled("JAMES-2794 This test never finishes")
    public void enQueueShouldAcceptMailWithDuplicatedNames() {

    }

    @Test
    @Override
    @Disabled("JAMES-2544 Mixing concurrent ack/nack might lead to a deadlock")
    public void concurrentEnqueueDequeueWithAckNackShouldNotFail() {

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
    void delayedMessagesShouldNotBlockReadyMessagesHolBlocking() throws Exception {
        // Enqueue a burst of delayed messages
        for (int i = 0; i < 50; i++) {
            mailQueue.enQueue(org.apache.james.queue.api.Mails.defaultMail()
                .name("delayed-" + i)
                .build(),
                1,
                java.util.concurrent.TimeUnit.HOURS);
        }

        // Enqueue an immediate (ready) message behind the delayed ones
        mailQueue.enQueue(org.apache.james.queue.api.Mails.defaultMail()
            .name("ready-message")
            .build());

        // Dequeue should instantly receive the ready message without head-of-line blocking
        reactor.core.publisher.Mono<org.apache.james.queue.api.MailQueue.MailQueueItem> itemMono =
            reactor.core.publisher.Flux.from(mailQueue.deQueue()).next();
        org.apache.james.queue.api.MailQueue.MailQueueItem dequeuedItem =
            itemMono.block(java.time.Duration.ofSeconds(5));

        org.assertj.core.api.Assertions.assertThat(dequeuedItem).isNotNull();
        org.assertj.core.api.Assertions.assertThat(dequeuedItem.getMail().getName()).isEqualTo("ready-message");
    }

    @Test
    void readyMessagesShouldBeConsumedAtWireSpeedEvenWhenDelayedMessagesExist() throws Exception {
        // Enqueue delayed messages
        for (int i = 0; i < 20; i++) {
            mailQueue.enQueue(org.apache.james.queue.api.Mails.defaultMail()
                .name("delayed-" + i)
                .build(),
                30,
                java.util.concurrent.TimeUnit.MINUTES);
        }

        // Enqueue multiple ready messages
        for (int i = 0; i < 10; i++) {
            mailQueue.enQueue(org.apache.james.queue.api.Mails.defaultMail()
                .name("ready-" + i)
                .build());
        }

        java.util.List<String> dequeuedNames = reactor.core.publisher.Flux.from(mailQueue.deQueue())
            .take(10)
            .map(item -> item.getMail().getName())
            .collectList()
            .block(java.time.Duration.ofSeconds(10));

        org.assertj.core.api.Assertions.assertThat(dequeuedNames)
            .containsExactly("ready-0", "ready-1", "ready-2", "ready-3", "ready-4",
                             "ready-5", "ready-6", "ready-7", "ready-8", "ready-9");
    }
}
