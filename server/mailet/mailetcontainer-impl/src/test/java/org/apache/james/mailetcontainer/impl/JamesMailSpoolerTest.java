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
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.james.mailrepository.api.MailRepositoryStore;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.metrics.api.NoopGaugeRegistry;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.MailQueueFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

class JamesMailSpoolerTest {
    @BeforeEach
    void setUp() {
        // Subscribe the spool consumers on the calling thread so that the live consumers can be counted synchronously
        Schedulers.setFactory(new Schedulers.Factory() {
            @Override
            public Scheduler newBoundedElastic(int threadCap, int queuedTaskCap, ThreadFactory threadFactory, int ttlSeconds) {
                return Schedulers.immediate();
            }
        });
    }

    @AfterEach
    void tearDown() {
        Schedulers.resetFactory();
    }

    @Test
    void disposeShouldStopEveryConsumerWhenRestartsAreConcurrent() throws Exception {
        AtomicInteger dequeueCalls = new AtomicInteger();
        AtomicInteger liveConsumers = new AtomicInteger();
        CountDownLatch firstRestartCanProceed = new CountDownLatch(1);

        MailQueue queue = mock(MailQueue.class);
        when(queue.deQueue()).thenAnswer(invocation -> {
            if (dequeueCalls.incrementAndGet() == 2) {
                // The first restart is building its consumer
                firstRestartCanProceed.await();
            }
            return Flux.<MailQueue.MailQueueItem>never()
                .doOnSubscribe(any -> liveConsumers.incrementAndGet())
                .doOnCancel(liveConsumers::decrementAndGet);
        });
        MailQueueFactory<MailQueue> queueFactory = mock(MailQueueFactory.class);
        when(queueFactory.createQueue(any(), any())).thenReturn(queue);

        JamesMailSpooler spooler = new JamesMailSpooler(new RecordingMetricFactory(), new NoopGaugeRegistry(),
            mail -> { }, mock(MailRepositoryStore.class), queueFactory);
        spooler.configure(new JamesMailSpooler.Configuration(1, MailRepositoryUrl.from("memory://var/mail/error")));
        spooler.init();

        Thread firstRestart = new Thread(spooler::restart);
        firstRestart.start();
        await().until(() -> dequeueCalls.get() == 2);

        Thread secondRestart = new Thread(spooler::restart);
        secondRestart.start();
        await().until(() -> secondRestart.getState() == Thread.State.BLOCKED
            || secondRestart.getState() == Thread.State.TERMINATED);

        firstRestartCanProceed.countDown();
        firstRestart.join();
        secondRestart.join();

        spooler.dispose();

        assertThat(liveConsumers.get()).isZero();
    }
}
