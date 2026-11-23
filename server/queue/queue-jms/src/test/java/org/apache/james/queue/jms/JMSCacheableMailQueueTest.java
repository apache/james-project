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

import static org.apache.james.queue.api.Mails.defaultMailNoRecipient;
import static org.apache.mailet.base.MailAddressFixture.RECIPIENT1;
import static org.apache.mailet.base.MailAddressFixture.RECIPIENT2;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import jakarta.mail.MessagingException;

import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.james.core.MailAddress;
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
import org.apache.mailet.Mail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import reactor.core.publisher.Flux;

@ExtendWith(BrokerExtension.class)
public class JMSCacheableMailQueueTest implements DelayedManageableMailQueueContract, PriorityManageableMailQueueContract, DelayedPriorityMailQueueContract,
    MailQueueMetricContract {

    private JMSCacheableMailQueue mailQueue;
    private ActiveMQConnectionFactory connectionFactory;
    private MailQueueMetricExtension.MailQueueMetricTestSystem metricTestSystem;

    @BeforeEach
    void setUp(EmbeddedActiveMQ broker, MailQueueMetricExtension.MailQueueMetricTestSystem metricTestSystem) {
        // Use InVM transport - broker ID matches BrokerExtension instance counter
        this.metricTestSystem = metricTestSystem;
        connectionFactory = new ActiveMQConnectionFactory("vm://0");
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
    void removeBySenderShouldNotInterpretTheValue() throws Exception {
        MailAddress quotingSender = new MailAddress("x'or'1'='1'or'r'='r@attacker.example");
        enQueue(defaultMailNoRecipient()
            .name("name1")
            .sender(new MailAddress("a@domain.tld"))
            .recipient(RECIPIENT1)
            .build());
        enQueue(defaultMailNoRecipient()
            .name("name2")
            .sender(quotingSender)
            .recipient(RECIPIENT1)
            .build());

        mailQueue.remove(ManageableMailQueue.Type.Sender, quotingSender.asString());

        awaitRemove();

        assertThat(mailQueue.browse())
            .toIterable()
            .extracting(ManageableMailQueue.MailQueueItemView::getMail)
            .extracting(Mail::getName)
            .containsExactly("name1");
    }

    @Test
    void removeByRecipientShouldNotTreatTheValueAsAPattern() throws Exception {
        enQueue(defaultMailNoRecipient()
            .name("name1")
            .recipients(new MailAddress("axb@domain.tld"), new MailAddress("other@domain.tld"))
            .build());

        mailQueue.remove(ManageableMailQueue.Type.Recipient, "a_b@domain.tld");
        mailQueue.remove(ManageableMailQueue.Type.Recipient, "%@domain.tld");

        assertThat(mailQueue.browse())
            .toIterable()
            .extracting(ManageableMailQueue.MailQueueItemView::getMail)
            .extracting(Mail::getName)
            .containsExactly("name1");
    }

    @Test
    void removeByRecipientShouldMatchARecipientHoldingPatternCharacters() throws Exception {
        enQueue(defaultMailNoRecipient()
            .name("name1")
            .recipients(new MailAddress("other@domain.tld"), new MailAddress("a_b%c@domain.tld"), new MailAddress("last@domain.tld"))
            .build());
        enQueue(defaultMailNoRecipient()
            .name("name2")
            .recipient(new MailAddress("other@domain.tld"))
            .build());

        mailQueue.remove(ManageableMailQueue.Type.Recipient, "a_b%c@domain.tld");

        awaitRemove();

        assertThat(mailQueue.browse())
            .toIterable()
            .extracting(ManageableMailQueue.MailQueueItemView::getMail)
            .extracting(Mail::getName)
            .containsExactly("name2");
    }

    @Test
    void recipientHoldingTheSeparatorShouldBeDequeuedAsIs() throws Exception {
        MailAddress recipient = new MailAddress("\"x;a@b.example;y\"@c.example");
        enQueue(defaultMailNoRecipient()
            .name("name1")
            .recipient(recipient)
            .build());

        MailQueue.MailQueueItem item = Flux.from(mailQueue.deQueue()).blockFirst();

        assertThat(item.getMail().getRecipients()).containsExactly(recipient);
    }

    @Test
    void mailsEnqueuedWithoutTheRecipientListShouldStillBeRead() throws Exception {
        JMSCacheableMailQueue formerFormatQueue = new JMSCacheableMailQueue(connectionFactory, new RawMailQueueItemDecoratorFactory(),
            mailQueue.getName(), metricTestSystem.getMetricFactory(), metricTestSystem.getSpyGaugeRegistry()) {
            @Override
            protected Map<String, Object> getJMSProperties(Mail mail, long nextDelivery) throws MessagingException {
                Map<String, Object> properties = new HashMap<>(super.getJMSProperties(mail, nextDelivery));
                properties.remove(JMSSupport.JAMES_MAIL_RECIPIENTS_LIST);
                return properties;
            }
        };
        try {
            formerFormatQueue.enQueue(defaultMailNoRecipient()
                .name("name1")
                .recipients(RECIPIENT1, RECIPIENT2)
                .build());
        } finally {
            formerFormatQueue.dispose();
        }

        MailQueue.MailQueueItem item = Flux.from(mailQueue.deQueue()).blockFirst();

        assertThat(item.getMail().getRecipients()).containsExactly(RECIPIENT1, RECIPIENT2);
    }
}
