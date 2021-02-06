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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.queue.api.MailQueueName;
import org.apache.james.transport.mailets.RemoteDelivery;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;

class RemoteDeliveryRunningTest {
    private static final MailQueueName QUEUE_NAME = MailQueueName.of("queueName");

    private RemoteDelivery remoteDelivery;
    private MailQueue mailQueue;
    private CountDownLatch countDownLatch;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        countDownLatch = new CountDownLatch(1);
        MailQueueFactory<MailQueue> mailQueueFactory = mock(MailQueueFactory.class);
        remoteDelivery = new RemoteDelivery(mock(DNSService.class), mock(DomainList.class), mailQueueFactory,
            mock(MetricFactory.class), RemoteDelivery.ThreadState.START_THREADS);

        mailQueue = mock(MailQueue.class);
        when(mailQueueFactory.createQueue(QUEUE_NAME)).thenReturn(mailQueue);
    }

    @Test
    void remoteDeliveryShouldStart() throws Exception {
        when(mailQueue.deQueue()).thenAnswer(invocation -> {
            countDownLatch.countDown();
            Thread.sleep(TimeUnit.SECONDS.toMillis(2));
            return Flux.never();
        });
        remoteDelivery.init(FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.OUTGOING, QUEUE_NAME.asString())
            .setProperty(RemoteDeliveryConfiguration.HELO_NAME, "Hello_name")
            .build());

        countDownLatch.await();
        verify(mailQueue).deQueue();
    }

    @AfterEach
    void tearDown() {
        remoteDelivery.destroy();
    }
}
