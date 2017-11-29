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

package org.apache.james.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.james.modules.server.MemoryMailQueueFactory;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.MailQueue.MailQueueItem;
import org.apache.james.queue.api.MailQueueItemDecoratorFactory;
import org.apache.mailet.Mail;
import org.apache.mailet.base.test.FakeMail;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.base.Throwables;

public class MemoryMailQueueFactoryTest {

    public static final String KEY = "key";
    public static final String BIS = "bis";
    public static final String NAME_1 = "Name1";
    public static final String NAME_2 = "Name2";

    private MemoryMailQueueFactory memoryMailQueueFactory;
    private ExecutorService executorService;

    @Before
    public void setUp() {
        memoryMailQueueFactory = new MemoryMailQueueFactory(mailQueueItem -> new MailQueueItemDecoratorFactory.MailQueueItemDecorator(mailQueueItem) {
            @Override
            public Mail getMail() {
                return mailQueueItem.getMail();
            }

            @Override
            public void done(boolean success) throws MailQueue.MailQueueException {

            }
        });
        executorService = Executors.newFixedThreadPool(2);
    }

    @After
    public void tearDown() {
        executorService.shutdownNow();
    }

    @Test
    public void getQueueShouldNotReturnNull() {
        assertThat(memoryMailQueueFactory.getQueue(KEY)).isNotNull();
    }

    @Test
    public void getQueueShouldReturnTwoTimeTheSameResultWhenUsedWithTheSameKey() {
        assertThat(memoryMailQueueFactory.getQueue(KEY)).isEqualTo(memoryMailQueueFactory.getQueue(KEY));
    }

    @Test
    public void getQueueShouldNotReturnTheSameQueueForTwoDifferentNames() {
        assertThat(memoryMailQueueFactory.getQueue(KEY)).isNotEqualTo(memoryMailQueueFactory.getQueue(BIS));
    }

    @Test
    public void dequeueShouldWork() throws Exception{
        Mail mail = FakeMail.builder()
                .name(NAME_1)
                .build();
        memoryMailQueueFactory.getQueue(KEY).enQueue(mail);
        assertThat(memoryMailQueueFactory.getQueue(KEY).deQueue().getMail().getName()).startsWith(NAME_1);
    }

    @Test
    public void dequeueShouldPreserveState() throws Exception{
        String state = "state";
        Mail mail = FakeMail.builder()
                .name(NAME_1)
                .state(state)
                .build();
        memoryMailQueueFactory.getQueue(KEY).enQueue(mail);

        String newState = memoryMailQueueFactory
            .getQueue(KEY)
            .deQueue()
            .getMail()
            .getState();

        assertThat(newState).isEqualTo(state);
    }

    @Test
    public void dequeueShouldWorkWithMultipleMessages() throws Exception{
        Mail mail1 = FakeMail.builder()
                .name(NAME_1)
                .build();
        Mail mail2 = FakeMail.builder()
                .name(NAME_2)
                .build();
        memoryMailQueueFactory.getQueue(KEY).enQueue(mail1);
        memoryMailQueueFactory.getQueue(KEY).enQueue(mail2);
        assertThat(memoryMailQueueFactory.getQueue(KEY).deQueue().getMail().getName()).startsWith(NAME_2);
        assertThat(memoryMailQueueFactory.getQueue(KEY).deQueue().getMail().getName()).startsWith(NAME_1);
    }

    @Test(timeout = 20000)
    public void deQueueShouldWaitForAMailToBeEnqueued() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Mail mail = FakeMail.builder()
                .name(NAME_1)
                .build();
        executorService.submit(() -> {
            try {
                latch.await();
                memoryMailQueueFactory.getQueue(KEY).enQueue(mail);
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        });
        Future<MailQueueItem> tryDequeue = executorService.submit(() -> memoryMailQueueFactory.getQueue(KEY).deQueue());
        assertThatThrownBy(() -> tryDequeue.get(100, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
        latch.countDown();
        assertThat(tryDequeue.get().getMail().getName()).startsWith(NAME_1);
    }
}
