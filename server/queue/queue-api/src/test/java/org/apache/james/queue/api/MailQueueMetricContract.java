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

import static org.apache.james.queue.api.MailQueue.DEQUEUED_METRIC_NAME_PREFIX;
import static org.apache.james.queue.api.MailQueue.ENQUEUED_METRIC_NAME_PREFIX;
import static org.apache.james.queue.api.MailQueue.ENQUEUED_TIMER_METRIC_NAME_PREFIX;
import static org.apache.james.queue.api.MailQueue.QUEUE_SIZE_METRIC_NAME_PREFIX;
import static org.apache.james.queue.api.Mails.defaultMail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.stream.IntStream;

import jakarta.mail.MessagingException;

import org.apache.james.metrics.api.Gauge;
import org.apache.mailet.base.test.FakeMail;
import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.github.fge.lambdas.Throwing;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@ExtendWith(MailQueueMetricExtension.class)
public interface MailQueueMetricContract extends MailQueueContract {

    default FakeMail fakeMail(int i) throws MessagingException {
        return defaultMail()
            .name("name" + i)
            .build();
    }

    default void enQueueMail(Integer times) {
        IntStream
            .rangeClosed(1, times)
            .forEach(Throwing.intConsumer(time -> enQueue(fakeMail(time))));
    }

    default void deQueueMail(Integer times) {
        Flux.from(getMailQueue().deQueue())
            .take(times)
            .flatMap(x -> Mono.fromCallable(() -> {
                x.done(MailQueue.MailQueueItem.CompletionStatus.SUCCESS);
                return x;
            }).subscribeOn(SCHEDULER))
            .blockLast();
    }

    @Test
    default void constructorShouldRegisterGetQueueSizeGauge(MailQueueMetricExtension.MailQueueMetricTestSystem testSystem) {
        enQueueMail(3);

        ArgumentCaptor<Gauge<?>> gaugeCaptor = ArgumentCaptor.forClass(Gauge.class);
        verify(testSystem.getSpyGaugeRegistry(), times(1)).register(startsWith(QUEUE_SIZE_METRIC_NAME_PREFIX), gaugeCaptor.capture());
        Mockito.verifyNoMoreInteractions(testSystem.getSpyGaugeRegistry());

        Gauge<?> registeredGauge = gaugeCaptor.getValue();
        assertThat(registeredGauge.get()).isEqualTo(3L);
    }

    @Test
    default void enqueueShouldIncreaseEnQueueMetric(MailQueueMetricExtension.MailQueueMetricTestSystem testSystem) {
        enQueueMail(2);

        Awaitility.await().atMost(Durations.FIVE_SECONDS).untilAsserted(() ->
            assertThat(testSystem.getMetricFactory().countForPrefixName(ENQUEUED_METRIC_NAME_PREFIX))
                .hasSize(1)
                .satisfies(values -> {
                    assertThat(values.values()).hasSize(1);
                    assertThat(values.values()).element(0).isEqualTo(2);
                }));
    }

    @Test
    default void enqueueShouldNotTouchDequeueMetric(MailQueueMetricExtension.MailQueueMetricTestSystem testSystem) throws InterruptedException {
        enQueueMail(2);

        Awaitility.await().atMost(Durations.FIVE_SECONDS).untilAsserted(() ->
            assertThat(testSystem.getMetricFactory().countForPrefixName(ENQUEUED_METRIC_NAME_PREFIX))
                .hasSize(1)
                .satisfies(values -> {
                    assertThat(values.values()).hasSize(1);
                    assertThat(values.values()).element(0).isEqualTo(2);
                }));
        assertThat(testSystem.getMetricFactory().countForPrefixName(DEQUEUED_METRIC_NAME_PREFIX))
            .hasSize(1)
            .satisfies(values -> {
                assertThat(values.values()).hasSize(1);
                assertThat(values.values()).element(0).isEqualTo(0);
            });
    }

    @Test
    default void dequeueShouldIncreaseDequeueMetric(MailQueueMetricExtension.MailQueueMetricTestSystem testSystem) {
        enQueueMail(2);
        deQueueMail(2);

        assertThat(testSystem.getMetricFactory().countForPrefixName(DEQUEUED_METRIC_NAME_PREFIX))
            .hasSize(1)
            .satisfies(values -> {
                assertThat(values.values()).hasSize(1);
                assertThat(values.values()).element(0).isEqualTo(2);
            });
    }

    @Test
    default void dequeueShouldNotTouchEnqueueMetric(MailQueueMetricExtension.MailQueueMetricTestSystem testSystem) {
        enQueueMail(2);
        deQueueMail(2);

        Awaitility.await().atMost(Durations.FIVE_SECONDS).untilAsserted(() ->
            assertThat(testSystem.getMetricFactory().countForPrefixName(DEQUEUED_METRIC_NAME_PREFIX))
                .hasSize(1)
                .satisfies(values -> {
                    assertThat(values.values()).hasSize(1);
                    assertThat(values.values()).element(0).isEqualTo(2);
                }));
        assertThat(testSystem.getMetricFactory().countForPrefixName(ENQUEUED_METRIC_NAME_PREFIX))
            .hasSize(1)
            .satisfies(values -> {
                assertThat(values.values()).hasSize(1);
                assertThat(values.values()).element(0).isEqualTo(2);
            });
    }

    @Test
    default void enqueueShouldPublishEnqueueTimeMetric(MailQueueMetricExtension.MailQueueMetricTestSystem testSystem) {
        enQueueMail(2);

        assertThat(testSystem.getMetricFactory().executionTimesForPrefixName(ENQUEUED_TIMER_METRIC_NAME_PREFIX))
            .satisfies(executionMap -> {
                assertThat(executionMap.keySet()).hasSize(1);
                assertThat(executionMap.values()).hasSize(2);
            });
    }

}
