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

import static org.apache.james.queue.api.MailQueueContract.SCHEDULER;
import static org.apache.james.queue.api.Mails.defaultMail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.google.common.base.Stopwatch;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface DelayedMailQueueContract {

    MailQueue getMailQueue();

    @Test
    default void enqueueShouldDelayMailsWhenSpecified() throws Exception {
        getMailQueue().enQueue(defaultMail()
            .name("name")
            .build(),
            5L,
            TimeUnit.SECONDS);

        Mono<MailQueue.MailQueueItem> next = Flux.from(getMailQueue().deQueue()).subscribeOn(SCHEDULER).next();
        assertThatThrownBy(() -> next.block(Duration.ofSeconds(1)))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    default void enqueueWithNegativeDelayShouldNotDelayDelivery() throws Exception {
        getMailQueue().enQueue(defaultMail()
            .name("name")
            .build(),
            -30L,
            TimeUnit.SECONDS);

        Mono<MailQueue.MailQueueItem> next = Flux.from(getMailQueue().deQueue()).next();
        assertThatCode(() -> next.block(Duration.ofSeconds(1))).doesNotThrowAnyException();
    }

    @Test
    default void enqueueWithReasonablyLongDelayShouldDelayMail() throws Exception {
        getMailQueue().enQueue(defaultMail()
            .name("name")
            .build(),
            365 * 10,
            TimeUnit.DAYS);

        Mono<MailQueue.MailQueueItem> next = Flux.from(getMailQueue().deQueue()).subscribeOn(SCHEDULER).next();
        assertThatThrownBy(() -> next.block(Duration.ofSeconds(1)))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    default void enqueueWithVeryLongDelayShouldDelayMail() throws Exception {
        getMailQueue().enQueue(defaultMail()
            .name("name")
            .build(),
           ChronoUnit.YEARS.getDuration().multipliedBy(291)
           );

        Mono<MailQueue.MailQueueItem> next = Flux.from(getMailQueue().deQueue()).subscribeOn(SCHEDULER).next();
        assertThatThrownBy(() -> next.block(Duration.ofSeconds(1)))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    default void delayedMailCanBeRetrievedFromTheQueue() throws Exception {
        getMailQueue().enQueue(defaultMail()
            .name("name1")
            .build(),
            1L,
            TimeUnit.SECONDS);

        MailQueue.MailQueueItem mailQueueItem = Flux.from(getMailQueue().deQueue()).blockFirst();
        assertThat(mailQueueItem.getMail().getName()).isEqualTo("name1");
    }

    @Test
    default void delayShouldAtLeastBeTheOneSpecified() throws Exception {
        long delay = 3L;
        TimeUnit unit = TimeUnit.SECONDS;

        Stopwatch started = Stopwatch.createStarted();

        getMailQueue().enQueue(defaultMail()
            .name("name")
            .build(),
            delay,
            unit);

        MailQueue.MailQueueItem mailQueueItem = Flux.from(getMailQueue().deQueue()).blockFirst();
        assertThat(mailQueueItem).isNotNull();
        int epsilon = 1;
        assertThat(started.elapsed())
            .isGreaterThan(Duration.ofSeconds(delay - epsilon));
    }

}
