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

package org.apache.james.transport.mailets.remoteDelivery;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.apache.james.domainlist.api.DomainList;
import org.apache.james.metrics.api.Metric;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.api.NoopMetricFactory;
import org.apache.james.queue.api.MailQueue;
import org.apache.mailet.Mail;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Supplier;

public class DeliveryRunnableTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeliveryRunnableTest.class);
    public static final Date FIXED_DATE = new Date(1159599194961L);
    public static final Supplier<Date> FIXED_DATE_SUPPLIER = () -> FIXED_DATE;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private DeliveryRunnable testee;
    private Metric outgoingMailsMetric;
    private Bouncer bouncer;
    private MailDelivrer mailDelivrer;
    private MailQueue mailQueue;

    @Before
    public void setUp() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.DELIVERY_THREADS, "1")
            .setProperty(RemoteDeliveryConfiguration.DEBUG, "true")
            .setProperty(RemoteDeliveryConfiguration.DELAY_TIME, "1000,2000,3000,4000,5000")
            .build();

        RemoteDeliveryConfiguration configuration = new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class));
        outgoingMailsMetric = mock(Metric.class);
        MetricFactory mockMetricFactory = mock(MetricFactory.class);
        when(mockMetricFactory.generate(anyString())).thenReturn(outgoingMailsMetric);
        when(mockMetricFactory.timer(anyString())).thenReturn(new NoopMetricFactory.NoopTimeMetric());
        bouncer = mock(Bouncer.class);
        mailDelivrer = mock(MailDelivrer.class);
        mailQueue = mock(MailQueue.class);
        testee = new DeliveryRunnable(mailQueue, configuration, mockMetricFactory, bouncer, mailDelivrer, DeliveryRunnable.DEFAULT_NOT_STARTED, FIXED_DATE_SUPPLIER);
    }

    @Test
    public void deliverySuccessShouldIncrementMetric() throws Exception {
        FakeMail fakeMail = FakeMail.defaultFakeMail();
        when(mailDelivrer.deliver(fakeMail)).thenReturn(ExecutionResult.success());

        testee.attemptDelivery(fakeMail);

        verify(outgoingMailsMetric).increment();
        verifyNoMoreInteractions(outgoingMailsMetric);
    }

    @Test
    public void deliveryPermanentFailureShouldBounceTheMail() throws Exception {
        FakeMail fakeMail = FakeMail.defaultFakeMail();
        Exception exception = new Exception();
        when(mailDelivrer.deliver(fakeMail)).thenReturn(ExecutionResult.permanentFailure(exception));

        testee.attemptDelivery(fakeMail);

        verify(bouncer).bounce(fakeMail, exception);
        verifyNoMoreInteractions(bouncer);
    }

    @Test
    public void deliveryPermanentFailureShouldNotIncrementDeliveryMetric() throws Exception {
        FakeMail fakeMail = FakeMail.defaultFakeMail();
        Exception exception = new Exception();
        when(mailDelivrer.deliver(fakeMail)).thenReturn(ExecutionResult.permanentFailure(exception));

        testee.attemptDelivery(fakeMail);

        verifyNoMoreInteractions(outgoingMailsMetric);
    }

    @Test
    public void deliveryTemporaryFailureShouldNotIncrementDeliveryMetric() throws Exception {
        FakeMail fakeMail = FakeMail.builder().state(Mail.DEFAULT).build();
        Exception exception = new Exception();
        when(mailDelivrer.deliver(fakeMail)).thenReturn(ExecutionResult.temporaryFailure(exception));

        testee.attemptDelivery(fakeMail);

        verifyNoMoreInteractions(outgoingMailsMetric);
    }

    @Test
    public void deliveryTemporaryFailureShouldFailOnMailsWithoutState() throws Exception {
        FakeMail fakeMail = FakeMail.defaultFakeMail();
        Exception exception = new Exception();
        when(mailDelivrer.deliver(fakeMail)).thenReturn(ExecutionResult.temporaryFailure(exception));

        expectedException.expect(NullPointerException.class);

        testee.attemptDelivery(fakeMail);
    }

    @Test
    public void deliveryTemporaryFailureShouldRetryDelivery() throws Exception {
        FakeMail fakeMail = FakeMail.builder().state(Mail.DEFAULT).build();
        Exception exception = new Exception();
        when(mailDelivrer.deliver(fakeMail)).thenReturn(ExecutionResult.temporaryFailure(exception));

        testee.attemptDelivery(fakeMail);

        verify(mailQueue).enQueue(FakeMail.builder()
                .attribute(DeliveryRetriesHelper.DELIVERY_RETRY_COUNT, 1)
                .state(Mail.ERROR)
                .lastUpdated(FIXED_DATE)
                .build(),
            1000,
            TimeUnit.MILLISECONDS);
        verifyNoMoreInteractions(mailQueue);
    }

    @Test
    public void deliveryTemporaryFailureShouldRetryDeliveryWithRightDelay() throws Exception {
        FakeMail fakeMail = FakeMail.builder()
            .state(Mail.ERROR)
            .attribute(DeliveryRetriesHelper.DELIVERY_RETRY_COUNT, 2)
            .build();
        Exception exception = new Exception();
        when(mailDelivrer.deliver(fakeMail)).thenReturn(ExecutionResult.temporaryFailure(exception));

        testee.attemptDelivery(fakeMail);

        verify(mailQueue).enQueue(FakeMail.builder()
                .attribute(DeliveryRetriesHelper.DELIVERY_RETRY_COUNT, 3)
                .state(Mail.ERROR)
                .lastUpdated(FIXED_DATE)
                .build(),
            3000,
            TimeUnit.MILLISECONDS);
        verifyNoMoreInteractions(mailQueue);
    }

    @Test
    public void deliveryTemporaryFailureShouldRetryDeliveryOnMaximumRetryNumber() throws Exception {
        FakeMail fakeMail = FakeMail.builder()
            .state(Mail.ERROR)
            .attribute(DeliveryRetriesHelper.DELIVERY_RETRY_COUNT, 4)
            .build();
        Exception exception = new Exception();
        when(mailDelivrer.deliver(fakeMail)).thenReturn(ExecutionResult.temporaryFailure(exception));

        testee.attemptDelivery(fakeMail);

        verify(mailQueue).enQueue(FakeMail.builder()
                .attribute(DeliveryRetriesHelper.DELIVERY_RETRY_COUNT, 5)
                .state(Mail.ERROR)
                .lastUpdated(FIXED_DATE)
                .build(),
            5000,
            TimeUnit.MILLISECONDS);
        verifyNoMoreInteractions(mailQueue);
    }

    @Test
    public void deliveryTemporaryFailureShouldNotRetryDeliveryOverMaximumRetryNumber() throws Exception {
        FakeMail fakeMail = FakeMail.builder()
            .state(Mail.ERROR)
            .attribute(DeliveryRetriesHelper.DELIVERY_RETRY_COUNT, 5)
            .build();
        Exception exception = new Exception();
        when(mailDelivrer.deliver(fakeMail)).thenReturn(ExecutionResult.temporaryFailure(exception));

        testee.attemptDelivery(fakeMail);

        verifyNoMoreInteractions(mailQueue);
    }

    @Test
    public void deliveryTemporaryFailureShouldBounceWhenRetryExceeded() throws Exception {
        FakeMail fakeMail = FakeMail.builder()
            .state(Mail.ERROR)
            .attribute(DeliveryRetriesHelper.DELIVERY_RETRY_COUNT, 5)
            .build();
        Exception exception = new Exception("");
        when(mailDelivrer.deliver(fakeMail)).thenReturn(ExecutionResult.temporaryFailure(exception));

        testee.attemptDelivery(fakeMail);

        verify(bouncer).bounce(eq(fakeMail), any(Exception.class));
        verifyNoMoreInteractions(bouncer);
    }

    @Test
    public void deliveryTemporaryFailureShouldResetDeliveryCountOnNonErrorState() throws Exception {
        FakeMail fakeMail = FakeMail.builder()
            .state(Mail.DEFAULT)
            .attribute(DeliveryRetriesHelper.DELIVERY_RETRY_COUNT, 5)
            .build();
        Exception exception = new Exception();
        when(mailDelivrer.deliver(fakeMail)).thenReturn(ExecutionResult.temporaryFailure(exception));

        testee.attemptDelivery(fakeMail);

        verify(mailQueue).enQueue(FakeMail.builder()
                .attribute(DeliveryRetriesHelper.DELIVERY_RETRY_COUNT, 1)
                .state(Mail.ERROR)
                .lastUpdated(FIXED_DATE)
                .build(),
            1000,
            TimeUnit.MILLISECONDS);
        verifyNoMoreInteractions(mailQueue);
    }
}
