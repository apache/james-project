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

package org.apache.james.queue.api;

import static org.apache.james.queue.api.Mails.defaultMail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Stopwatch;

@ExtendWith(ExecutorExtension.class)
public interface DelayedMailQueueContract {

    MailQueue getMailQueue();

    @Test
    default void enqueueShouldDelayMailsWhenSpecified(ExecutorService executorService) throws Exception {
        getMailQueue().enQueue(defaultMail()
            .build(),
            2L,
            TimeUnit.SECONDS);

        Future<?> future = executorService.submit(Throwing.runnable(() -> getMailQueue().deQueue()));
        assertThatThrownBy(() -> future.get(1, TimeUnit.SECONDS))
            .isInstanceOf(TimeoutException.class);
    }

    @Test
    default void enqueueWithNegativeDelayShouldNotDelayDelivery(ExecutorService executorService) throws Exception {
        getMailQueue().enQueue(defaultMail()
            .build(),
            -30L,
            TimeUnit.SECONDS);

        Future<?> future = executorService.submit(Throwing.runnable(() -> getMailQueue().deQueue()));
        future.get(1, TimeUnit.SECONDS);
    }

    @Test
    default void enqueueWithReasonablyLongDelayShouldDelayMail(ExecutorService executorService) throws Exception {
        getMailQueue().enQueue(defaultMail()
            .build(),
            365*1000,
            TimeUnit.DAYS);

        Future<?> future = executorService.submit(Throwing.runnable(() -> getMailQueue().deQueue()));
        assertThatThrownBy(() -> future.get(1, TimeUnit.SECONDS))
            .isInstanceOf(TimeoutException.class);
    }

    @Test
    default void enqueueWithVeryLongDelayShouldDelayMail(ExecutorService executorService) throws Exception {
        getMailQueue().enQueue(defaultMail()
            .build(),
            Long.MAX_VALUE / (3600 * 24),
            TimeUnit.DAYS);

        Future<?> future = executorService.submit(Throwing.runnable(() -> getMailQueue().deQueue()));
        assertThatThrownBy(() -> future.get(1, TimeUnit.SECONDS))
            .isInstanceOf(TimeoutException.class);
    }

    @Test
    default void delayedMailCanBeRetrievedFromTheQueue() throws Exception {
        getMailQueue().enQueue(defaultMail()
            .name("name1")
            .build(),
            1L,
            TimeUnit.SECONDS);

        MailQueue.MailQueueItem mailQueueItem = getMailQueue().deQueue();
        assertThat(mailQueueItem.getMail().getName()).isEqualTo("name1");
    }

    @Test
    default void delayShouldAtLeastBeTheOneSpecified() throws Exception {
        long delay = 1L;
        TimeUnit unit = TimeUnit.SECONDS;
        Stopwatch started = Stopwatch.createStarted();

        getMailQueue().enQueue(defaultMail()
            .build(),
            delay,
            unit);

        getMailQueue().deQueue();
        assertThat(started.elapsed(TimeUnit.MILLISECONDS))
            .isGreaterThan(unit.toMillis(delay));
    }

}
