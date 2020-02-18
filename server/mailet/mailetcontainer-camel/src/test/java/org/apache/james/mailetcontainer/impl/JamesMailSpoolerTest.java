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

package org.apache.james.mailetcontainer.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Duration.ONE_HUNDRED_MILLISECONDS;
import static org.awaitility.Duration.TEN_SECONDS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Consumer;

import javax.mail.MessagingException;

import org.apache.commons.configuration2.plist.PropertyListConfiguration;
import org.apache.james.mailetcontainer.api.MailProcessor;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.api.TimeMetric;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.mailet.Mail;
import org.apache.mailet.base.test.FakeMail;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.UnicastProcessor;

class JamesMailSpoolerTest {
    private static final ConditionFactory CALMLY_AWAIT = Awaitility
            .with().pollInterval(ONE_HUNDRED_MILLISECONDS)
            .and().pollDelay(ONE_HUNDRED_MILLISECONDS)
            .await()
            .atMost(TEN_SECONDS);

    @Test
    void thrownExceptionShouldAckTheItem() throws MessagingException {
        MetricFactory metricFactory = mock(MetricFactory.class);
        when(metricFactory.timer(JamesMailSpooler.SPOOL_PROCESSING)).thenAnswer(ignored -> mock(TimeMetric.class));
        MailQueueFactory<?> queueFactory = mock(MailQueueFactory.class);
        MailProcessor mailProcessor = mock(MailProcessor.class);
        JamesMailSpooler spooler = new JamesMailSpooler(metricFactory, mailProcessor, queueFactory);

        UnicastProcessor<MockedMailQueueItem> workQueue = UnicastProcessor.create();
        MockedMailQueueItem item = new MockedMailQueueItem();
        item.addCallback(isDone -> {
            if (!isDone) {
                workQueue.onNext(item);
            }
        });
        MailQueue queue = mock(MailQueue.class);
        workQueue.onNext(item);
        when(queue.deQueue()).thenAnswer(any -> workQueue.limitRate(1).filter(MockedMailQueueItem::isNotDone));
        when(queueFactory.createQueue(eq(MailQueueFactory.SPOOL), any())).thenAnswer(any -> queue);

        doThrow(new RuntimeException("Arbitrary failure"))
            .doNothing()
            .when(mailProcessor).service(any());

        PropertyListConfiguration configuration = new PropertyListConfiguration();
        configuration.addProperty("threads", 2);
        spooler.configure(configuration);
        spooler.init();

        CALMLY_AWAIT.until(() -> item.getDones().size() == 2);

        assertThat(item.getDones()).containsExactly(false, true);
    }

    @Test
    void threadSuicideShouldAckTheItem() throws MessagingException {
        MetricFactory metricFactory = mock(MetricFactory.class);
        when(metricFactory.timer(JamesMailSpooler.SPOOL_PROCESSING)).thenAnswer(ignored -> mock(TimeMetric.class));
        MailQueueFactory<?> queueFactory = mock(MailQueueFactory.class);
        MailProcessor mailProcessor = mock(MailProcessor.class);
        JamesMailSpooler spooler = new JamesMailSpooler(metricFactory, mailProcessor, queueFactory);

        UnicastProcessor<MockedMailQueueItem> workQueue = UnicastProcessor.create();
        MockedMailQueueItem item = new MockedMailQueueItem();
        item.addCallback(isDone -> {
            if (!isDone) {
                workQueue.onNext(item);
            }
        });
        MailQueue queue = mock(MailQueue.class);
        workQueue.onNext(item);
        when(queue.deQueue()).thenAnswer(any -> workQueue.limitRate(1).filter(MockedMailQueueItem::isNotDone));
        when(queueFactory.createQueue(eq(MailQueueFactory.SPOOL), any())).thenAnswer(any -> queue);

        doAnswer(ignored -> {
            Thread.currentThread().interrupt();
            return null;
        }).doNothing().when(mailProcessor).service(any());

        PropertyListConfiguration configuration = new PropertyListConfiguration();
        configuration.addProperty("threads", 2);
        spooler.configure(configuration);
        spooler.init();

        CALMLY_AWAIT.until(() -> item.getDones().size() == 2);

        assertThat(item.getDones()).containsExactly(false, true);
    }

    private class MockedMailQueueItem implements MailQueue.MailQueueItem {
        private final Collection<Boolean> dones;
        private Consumer<Boolean> doneCallback;

        private MockedMailQueueItem() {
            dones = new ArrayList<>();
        }

        @Override
        public Mail getMail() {
            try {
                return FakeMail.defaultFakeMail();
            } catch (MessagingException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void done(boolean success) throws MailQueue.MailQueueException {
            dones.add(success);
            doneCallback.accept(success);
        }

        public Collection<Boolean> getDones() {
            return dones;
        }

        public boolean isNotDone() {
            return !dones.contains(true);
        }

        public void addCallback(Consumer<Boolean> callback) {
            doneCallback = callback;
        }
    }
}