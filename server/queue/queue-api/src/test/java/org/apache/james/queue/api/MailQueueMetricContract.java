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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.stream.IntStream;

import javax.mail.MessagingException;

import org.apache.james.metrics.api.Gauge;
import org.apache.mailet.base.test.FakeMail;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.github.fge.lambdas.Throwing;

@ExtendWith(MailQueueMetricExtension.class)
public interface MailQueueMetricContract extends MailQueueContract {

    default FakeMail fakeMail() throws MessagingException {
        return defaultMail()
            .name("name1")
            .build();
    }

    default void enQueueMail(Integer times) throws Exception {
        IntStream
            .rangeClosed(1, times)
            .forEach(Throwing.intConsumer(time -> enQueue(fakeMail())));
    }

    default void deQueueMail(Integer times) throws Exception {
        IntStream
            .rangeClosed(1, times)
            .forEach(Throwing.intConsumer(time -> getMailQueue().deQueue().done(true)));
    }

    @Test
    default void constructorShouldRegisterGetQueueSizeGauge(MailQueueMetricExtension.MailQueueMetricTestSystem testSystem) throws Exception {
        enQueueMail(3);

        ArgumentCaptor<Gauge<?>> gaugeCaptor = ArgumentCaptor.forClass(Gauge.class);
        verify(testSystem.getSpyGaugeRegistry(), times(1)).register(any(), gaugeCaptor.capture());
        Mockito.verifyNoMoreInteractions(testSystem.getSpyGaugeRegistry());

        Gauge<?> registeredGauge = gaugeCaptor.getValue();
        Assertions.assertThat(registeredGauge.get()).isEqualTo(3L);
    }

    @Test
    default void enqueueShouldIncreaseEnQueueMetric(MailQueueMetricExtension.MailQueueMetricTestSystem testSystem) throws Exception {
        enQueueMail(2);

        verify(testSystem.getSpyEnqueuedMailsMetric(), times(2)).increment();
        Mockito.verifyNoMoreInteractions(testSystem.getSpyEnqueuedMailsMetric());
    }

    @Test
    default void enqueueShouldNotTouchDequeueMetric(MailQueueMetricExtension.MailQueueMetricTestSystem testSystem) throws Exception {
        enQueueMail(2);

        verify(testSystem.getSpyEnqueuedMailsMetric(), times(2)).increment();
        Mockito.verifyNoMoreInteractions(testSystem.getSpyDequeuedMailsMetric());
    }

    @Test
    default void dequeueShouldIncreaseDequeueMetric(MailQueueMetricExtension.MailQueueMetricTestSystem testSystem) throws Exception {
        enQueueMail(2);
        deQueueMail(2);

        verify(testSystem.getSpyDequeuedMailsMetric(), times(2)).increment();
        Mockito.verifyNoMoreInteractions(testSystem.getSpyDequeuedMailsMetric());
    }

    @Test
    default void dequeueShouldNotTouchEnqueueMetric(MailQueueMetricExtension.MailQueueMetricTestSystem testSystem) throws Exception {
        enQueueMail(2);
        deQueueMail(2);

        verify(testSystem.getSpyDequeuedMailsMetric(), times(2)).increment();
        Mockito.verifyNoMoreInteractions(testSystem.getSpyDequeuedMailsMetric());

        verify(testSystem.getSpyEnqueuedMailsMetric(), times(2)).increment();
        Mockito.verifyNoMoreInteractions(testSystem.getSpyEnqueuedMailsMetric());
    }

    @Test
    default void enqueueShouldPublishEnqueueTimeMetric(MailQueueMetricExtension.MailQueueMetricTestSystem testSystem) throws Exception {
        enQueueMail(2);

        verify(testSystem.getSpyEnqueuedMailsTimeMetric(), times(2)).stopAndPublish();
    }

    @Test
    default void enqueueShouldNotPublishDequeueTimeMetric(MailQueueMetricExtension.MailQueueMetricTestSystem testSystem) throws Exception {
        enQueueMail(2);

        verify(testSystem.getSpyEnqueuedMailsTimeMetric(), times(2)).stopAndPublish();
        verifyNoMoreInteractions(testSystem.getSpyDequeuedMailsTimeMetric());
    }

    @Test
    default void dequeueShouldPublishDequeueTimeMetric(MailQueueMetricExtension.MailQueueMetricTestSystem testSystem) throws Exception {
        enQueueMail(2);
        deQueueMail(2);

        verify(testSystem.getSpyDequeuedMailsTimeMetric(), times(2)).stopAndPublish();
    }

    @Test
    default void dequeueShouldNotPublishEnqueueTimeMetric(MailQueueMetricExtension.MailQueueMetricTestSystem testSystem) throws Exception {
        enQueueMail(2);
        verify(testSystem.getSpyEnqueuedMailsTimeMetric(), times(2)).stopAndPublish();

        deQueueMail(2);
        verify(testSystem.getSpyDequeuedMailsTimeMetric(), times(2)).stopAndPublish();
        verifyNoMoreInteractions(testSystem.getSpyEnqueuedMailsTimeMetric());
    }

}
