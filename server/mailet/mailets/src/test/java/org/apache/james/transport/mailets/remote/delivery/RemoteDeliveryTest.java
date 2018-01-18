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

package org.apache.james.transport.mailets.remote.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.mail.MessagingException;

import org.apache.commons.lang.NotImplementedException;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.queue.api.MailPrioritySupport;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.transport.mailets.RemoteDelivery;
import org.apache.mailet.Mail;
import org.apache.mailet.base.MailAddressFixture;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.Before;
import org.junit.Test;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class RemoteDeliveryTest {

    public static final String MAIL_NAME = "mail_name";

    private static class FakeMailQueue implements MailQueue {
        private final List<Mail> enqueuedMail;
        private final String name;

        private FakeMailQueue(String name) {
            this.name = name;
            this.enqueuedMail = Lists.newArrayList();
        }

        @Override
        public String getMailQueueName() {
            return name;
        }

        @Override
        public void enQueue(Mail mail, long delay, TimeUnit unit) throws MailQueueException {
            enQueue(mail);
        }

        @Override
        public void enQueue(Mail mail) throws MailQueueException {
            try {
                enqueuedMail.add(FakeMail.fromMail(mail));
            } catch (MessagingException e) {
                throw Throwables.propagate(e);
            }
        }

        @Override
        public MailQueueItem deQueue() throws MailQueueException, InterruptedException {
            throw new NotImplementedException();
        }

        public List<Mail> getEnqueuedMail() {
            return ImmutableList.copyOf(enqueuedMail);
        }
    }

    private RemoteDelivery remoteDelivery;
    private FakeMailQueue mailQueue;

    @Before
    public void setUp() {
        MailQueueFactory queueFactory = mock(MailQueueFactory.class);
        mailQueue = new FakeMailQueue("any");
        when(queueFactory.getQueue(RemoteDeliveryConfiguration.OUTGOING)).thenReturn(mailQueue);
        remoteDelivery = new RemoteDelivery(mock(DNSService.class), mock(DomainList.class), queueFactory, mock(MetricFactory.class), RemoteDelivery.ThreadState.DO_NOT_START_THREADS);
    }

    @Test
    public void remoteDeliveryShouldAddEmailToSpool() throws Exception {
        remoteDelivery.init(FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.DELIVERY_THREADS, "1")
            .build());

        Mail mail = FakeMail.builder().name(MAIL_NAME).recipients(MailAddressFixture.ANY_AT_JAMES).build();
        remoteDelivery.service(mail);

        assertThat(mailQueue.getEnqueuedMail()).containsOnly(FakeMail.builder()
            .name(MAIL_NAME + RemoteDelivery.NAME_JUNCTION + MailAddressFixture.JAMES_APACHE_ORG)
            .recipient(MailAddressFixture.ANY_AT_JAMES)
            .build());
    }

    @Test
    public void remoteDeliveryShouldSplitMailsByServerWhenNoGateway() throws Exception {
        remoteDelivery.init(FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.DELIVERY_THREADS, "1")
            .build());

        Mail mail = FakeMail.builder()
            .name(MAIL_NAME)
            .recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.ANY_AT_JAMES2, MailAddressFixture.OTHER_AT_JAMES)
            .build();
        remoteDelivery.service(mail);

        assertThat(mailQueue.getEnqueuedMail()).containsOnly(
            FakeMail.builder()
                .name(MAIL_NAME + RemoteDelivery.NAME_JUNCTION + MailAddressFixture.JAMES_APACHE_ORG)
                .recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES)
                .build(),
            FakeMail.builder()
                .name(MAIL_NAME + RemoteDelivery.NAME_JUNCTION + MailAddressFixture.JAMES2_APACHE_ORG)
                .recipients(MailAddressFixture.ANY_AT_JAMES2)
                .build());
    }

    @Test
    public void remoteDeliveryShouldNotSplitMailsByServerWhenGateway() throws Exception {
        remoteDelivery.init(FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.DELIVERY_THREADS, "1")
            .setProperty(RemoteDeliveryConfiguration.GATEWAY, MailAddressFixture.JAMES_LOCAL)
            .build());

        Mail mail = FakeMail.builder()
            .name(MAIL_NAME)
            .recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.ANY_AT_JAMES2, MailAddressFixture.OTHER_AT_JAMES)
            .build();
        remoteDelivery.service(mail);

        assertThat(mailQueue.getEnqueuedMail()).containsOnly(
            FakeMail.builder()
                .name(MAIL_NAME)
                .recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.ANY_AT_JAMES2, MailAddressFixture.OTHER_AT_JAMES)
                .build());
    }

    @Test
    public void remoteDeliveryShouldGhostMails() throws Exception {
        remoteDelivery.init(FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.DELIVERY_THREADS, "1")
            .build());

        Mail mail = FakeMail.builder().name(MAIL_NAME).recipients(MailAddressFixture.ANY_AT_JAMES).build();
        remoteDelivery.service(mail);

        assertThat(mail.getState()).isEqualTo(Mail.GHOST);
    }

    @Test
    public void remoteDeliveryShouldAddPriorityIfSpecified() throws Exception {
        remoteDelivery.init(FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.DELIVERY_THREADS, "1")
            .setProperty(RemoteDeliveryConfiguration.USE_PRIORITY, "true")
            .build());

        Mail mail = FakeMail.builder().name(MAIL_NAME).recipients(MailAddressFixture.ANY_AT_JAMES).build();
        remoteDelivery.service(mail);

        assertThat(mailQueue.getEnqueuedMail()).containsOnly(FakeMail.builder()
            .name(MAIL_NAME + RemoteDelivery.NAME_JUNCTION + MailAddressFixture.JAMES_APACHE_ORG)
            .attribute(MailPrioritySupport.MAIL_PRIORITY, MailPrioritySupport.HIGH_PRIORITY)
            .recipient(MailAddressFixture.ANY_AT_JAMES)
            .build());
    }

    @Test
    public void remoteDeliveryShouldNotForwardMailsWithNoRecipients() throws Exception {
        remoteDelivery.init(FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.DELIVERY_THREADS, "1")
            .build());

        Mail mail = FakeMail.builder().name(MAIL_NAME).build();
        remoteDelivery.service(mail);

        assertThat(mailQueue.getEnqueuedMail()).isEmpty();
    }

    @Test
    public void remoteDeliveryShouldNotForwardMailsWithNoRecipientsWithGateway() throws Exception {
        remoteDelivery.init(FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.DELIVERY_THREADS, "1")
            .setProperty(RemoteDeliveryConfiguration.GATEWAY, MailAddressFixture.JAMES_LOCAL)
            .build());

        Mail mail = FakeMail.builder().name(MAIL_NAME).build();
        remoteDelivery.service(mail);

        assertThat(mailQueue.getEnqueuedMail()).isEmpty();
    }
}
