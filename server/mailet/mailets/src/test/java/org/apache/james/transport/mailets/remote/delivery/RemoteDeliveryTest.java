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

import static org.apache.mailet.base.MailAddressFixture.JAMES_APACHE_ORG;
import static org.apache.mailet.base.MailAddressFixture.JAMES_APACHE_ORG_DOMAIN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.james.core.MailAddress;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.metrics.api.NoopMetricFactory;
import org.apache.james.queue.api.MailPrioritySupport;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.james.queue.api.RawMailQueueItemDecoratorFactory;
import org.apache.james.queue.memory.MemoryMailQueueFactory;
import org.apache.james.transport.mailets.RemoteDelivery;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.Mail;
import org.apache.mailet.base.MailAddressFixture;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class RemoteDeliveryTest {

    public static class MailProjection {
        public static MailProjection from(Mail mail) {
            return new MailProjection(mail.getName(), mail.getRecipients(), mail.attributesMap());
        }

        public static MailProjection from(ManageableMailQueue.MailQueueItemView item) {
            return from(item.getMail());
        }

        private final String name;
        private final List<MailAddress> recipients;
        private final ImmutableMap<AttributeName, Attribute> attributes;

        public MailProjection(String name, Collection<MailAddress> recipients, Map<AttributeName, Attribute> attributes) {
            this.name = name;
            this.recipients = ImmutableList.copyOf(recipients);
            this.attributes = ImmutableMap.copyOf(attributes);
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof MailProjection) {
                MailProjection mailProjection = (MailProjection) o;

                return Objects.equals(this.name, mailProjection.name)
                    && Objects.equals(this.attributes, mailProjection.attributes)
                    && Objects.equals(this.recipients, mailProjection.recipients);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(name, attributes, recipients);
        }
    }

    public static final String MAIL_NAME = "mail_name";

    private RemoteDelivery remoteDelivery;
    private ManageableMailQueue mailQueue;

    @Before
    public void setUp() throws ConfigurationException {
        MailQueueFactory<ManageableMailQueue> queueFactory = new MemoryMailQueueFactory(new RawMailQueueItemDecoratorFactory());
        mailQueue = queueFactory.createQueue(RemoteDeliveryConfiguration.OUTGOING);
        DNSService dnsService = mock(DNSService.class);
        MemoryDomainList domainList = new MemoryDomainList(dnsService);
        domainList.configure(DomainListConfiguration.builder().defaultDomain(JAMES_APACHE_ORG_DOMAIN));
        remoteDelivery = new RemoteDelivery(dnsService, domainList,
            queueFactory, new NoopMetricFactory(), RemoteDelivery.ThreadState.DO_NOT_START_THREADS);
    }

    @Test
    public void remoteDeliveryShouldAddEmailToSpool() throws Exception {
        remoteDelivery.init(FakeMailetConfig.builder()
            .build());

        Mail mail = FakeMail.builder().name(MAIL_NAME).recipients(MailAddressFixture.ANY_AT_JAMES).build();
        remoteDelivery.service(mail);


        assertThat(mailQueue.browse())
            .extracting(MailProjection::from)
            .containsOnly(MailProjection.from(
                FakeMail.builder()
                    .name(MAIL_NAME + RemoteDelivery.NAME_JUNCTION + JAMES_APACHE_ORG)
                    .recipient(MailAddressFixture.ANY_AT_JAMES)
                    .build()));
    }

    @Test
    public void remoteDeliveryShouldSplitMailsByServerWhenNoGateway() throws Exception {
        remoteDelivery.init(FakeMailetConfig.builder()
            .build());

        Mail mail = FakeMail.builder()
            .name(MAIL_NAME)
            .recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.ANY_AT_JAMES2, MailAddressFixture.OTHER_AT_JAMES)
            .build();
        remoteDelivery.service(mail);


        assertThat(mailQueue.browse())
            .extracting(MailProjection::from)
            .containsOnly(
                MailProjection.from(FakeMail.builder()
                    .name(MAIL_NAME + RemoteDelivery.NAME_JUNCTION + JAMES_APACHE_ORG)
                    .recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES)
                    .build()),
                MailProjection.from(FakeMail.builder()
                    .name(MAIL_NAME + RemoteDelivery.NAME_JUNCTION + MailAddressFixture.JAMES2_APACHE_ORG)
                    .recipients(MailAddressFixture.ANY_AT_JAMES2)
                    .build()));
    }

    @Test
    public void remoteDeliveryShouldNotSplitMailsByServerWhenGateway() throws Exception {
        remoteDelivery.init(FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.GATEWAY, MailAddressFixture.JAMES_LOCAL)
            .build());

        Mail mail = FakeMail.builder()
            .name(MAIL_NAME)
            .recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.ANY_AT_JAMES2, MailAddressFixture.OTHER_AT_JAMES)
            .build();
        remoteDelivery.service(mail);


        assertThat(mailQueue.browse())
            .extracting(MailProjection::from)
            .containsOnly(
                MailProjection.from(FakeMail.builder()
                    .name(MAIL_NAME)
                    .recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.ANY_AT_JAMES2, MailAddressFixture.OTHER_AT_JAMES)
                    .build()));
    }

    @Test
    public void remoteDeliveryShouldGhostMails() throws Exception {
        remoteDelivery.init(FakeMailetConfig.builder()
            .build());

        Mail mail = FakeMail.builder().name(MAIL_NAME).recipients(MailAddressFixture.ANY_AT_JAMES).build();
        remoteDelivery.service(mail);

        assertThat(mail.getState()).isEqualTo(Mail.GHOST);
    }

    @Test
    public void remoteDeliveryShouldAddPriorityIfSpecified() throws Exception {
        remoteDelivery.init(FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.USE_PRIORITY, "true")
            .build());

        Mail mail = FakeMail.builder().name(MAIL_NAME).recipients(MailAddressFixture.ANY_AT_JAMES).build();
        remoteDelivery.service(mail);


        assertThat(mailQueue.browse())
            .extracting(MailProjection::from)
            .containsOnly(MailProjection.from(FakeMail.builder()
                .name(MAIL_NAME + RemoteDelivery.NAME_JUNCTION + MailAddressFixture.JAMES_APACHE_ORG)
                .attribute(MailPrioritySupport.HIGH_PRIORITY_ATTRIBUTE)
                .recipient(MailAddressFixture.ANY_AT_JAMES)
                .build()));
    }

    @Test
    public void remoteDeliveryShouldNotForwardMailsWithNoRecipients() throws Exception {
        remoteDelivery.init(FakeMailetConfig.builder()
            .build());

        Mail mail = FakeMail.builder().name(MAIL_NAME).build();
        remoteDelivery.service(mail);

        assertThat(mailQueue.browse()).isEmpty();
    }

    @Test
    public void remoteDeliveryShouldNotForwardMailsWithNoRecipientsWithGateway() throws Exception {
        remoteDelivery.init(FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.GATEWAY, MailAddressFixture.JAMES_LOCAL)
            .build());

        Mail mail = FakeMail.builder().name(MAIL_NAME).build();
        remoteDelivery.service(mail);

        assertThat(mailQueue.browse()).isEmpty();
    }
}
