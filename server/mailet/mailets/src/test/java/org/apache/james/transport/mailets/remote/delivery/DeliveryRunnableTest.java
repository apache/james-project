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

import static org.apache.james.transport.mailets.remote.delivery.Bouncer.IS_DELIVERY_PERMANENT_ERROR;
import static org.apache.james.transport.mailets.remote.delivery.DeliveryRunnable.OUTGOING_MAILS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Date;
import java.util.function.Supplier;

import org.apache.james.domainlist.api.DomainList;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.queue.api.MailPrioritySupport;
import org.apache.james.queue.api.MailQueue;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetContext;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DeliveryRunnableTest {

    public static final Date FIXED_DATE = new Date(1159599194961L);
    public static final Supplier<Date> FIXED_DATE_SUPPLIER = () -> FIXED_DATE;

    private DeliveryRunnable testee;
    private RecordingMetricFactory metricFactory;
    private Bouncer bouncer;
    private MailDelivrer mailDelivrer;
    private MailQueue mailQueue;

    @BeforeEach
    void setUp() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.DEBUG, "true")
            .setProperty(RemoteDeliveryConfiguration.USE_PRIORITY, "true")
            .setProperty(RemoteDeliveryConfiguration.DELAY_TIME, "1000,2000,3000,4000,5000")
            .build();

        RemoteDeliveryConfiguration configuration = new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class));
        metricFactory = new RecordingMetricFactory();
        bouncer = mock(Bouncer.class);
        mailDelivrer = mock(MailDelivrer.class);
        mailQueue = mock(MailQueue.class);
        testee = new DeliveryRunnable(mailQueue, configuration, metricFactory, bouncer, mailDelivrer, FIXED_DATE_SUPPLIER, mock(MailetContext.class));
    }

    @Test
    void deliverySuccessShouldIncrementMetric() throws Exception {
        FakeMail fakeMail = FakeMail.defaultFakeMail();
        when(mailDelivrer.deliver(fakeMail)).thenReturn(ExecutionResult.success());

        testee.attemptDelivery(fakeMail);

        assertThat(metricFactory.countFor(OUTGOING_MAILS))
            .isEqualTo(1);
    }

    @Test
    void deliveryPermanentFailureShouldBounceTheMail() throws Exception {
        FakeMail fakeMail = FakeMail.defaultFakeMail();
        Exception exception = new Exception();
        when(mailDelivrer.deliver(fakeMail)).thenReturn(ExecutionResult.permanentFailure(exception));

        testee.attemptDelivery(fakeMail);

        verify(bouncer).bounce(fakeMail, exception);
        verifyNoMoreInteractions(bouncer);
    }

    @Test
    void deliveryPermanentFailureShouldNotIncrementDeliveryMetric() throws Exception {
        FakeMail fakeMail = FakeMail.defaultFakeMail();
        Exception exception = new Exception();
        when(mailDelivrer.deliver(fakeMail)).thenReturn(ExecutionResult.permanentFailure(exception));

        testee.attemptDelivery(fakeMail);

        assertThat(metricFactory.countFor(OUTGOING_MAILS))
            .isEqualTo(0);
    }

    @Test
    void deliveryTemporaryFailureShouldNotIncrementDeliveryMetric() throws Exception {
        FakeMail fakeMail = FakeMail.builder().name("name").state(Mail.DEFAULT).build();
        Exception exception = new Exception();
        when(mailDelivrer.deliver(fakeMail)).thenReturn(ExecutionResult.temporaryFailure(exception));

        testee.attemptDelivery(fakeMail);

        assertThat(metricFactory.countFor(OUTGOING_MAILS))
            .isEqualTo(0);
    }

    @Test
    void deliveryTemporaryFailureShouldFailOnMailsWithoutState() throws Exception {
        FakeMail fakeMail = FakeMail.defaultFakeMail();
        Exception exception = new Exception();
        when(mailDelivrer.deliver(fakeMail)).thenReturn(ExecutionResult.temporaryFailure(exception));

        assertThatThrownBy(() -> testee.attemptDelivery(fakeMail))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void deliveryTemporaryFailureShouldRetryDelivery() throws Exception {
        FakeMail fakeMail = FakeMail.builder().name("name").state(Mail.DEFAULT).build();
        Exception exception = new Exception();
        when(mailDelivrer.deliver(fakeMail)).thenReturn(ExecutionResult.temporaryFailure(exception));

        testee.attemptDelivery(fakeMail);

        verify(mailQueue).enQueue(FakeMail.builder()
                .name("name")
                .attribute(DeliveryRetriesHelper.makeAttribute(1))
                .attribute(MailPrioritySupport.LOW_PRIORITY_ATTRIBUTE)
                .attribute(new Attribute(IS_DELIVERY_PERMANENT_ERROR, AttributeValue.of(false)))
                .state(Mail.ERROR)
                .lastUpdated(FIXED_DATE)
                .build(),
            Duration.ofSeconds(1));
        verifyNoMoreInteractions(mailQueue);
    }

    @Test
    void deliveryTemporaryFailureShouldRetryDeliveryWithRightDelay() throws Exception {
        FakeMail fakeMail = FakeMail.builder()
            .name("name")
            .state(Mail.ERROR)
            .attribute(DeliveryRetriesHelper.makeAttribute(2))
            .build();
        Exception exception = new Exception();
        when(mailDelivrer.deliver(fakeMail)).thenReturn(ExecutionResult.temporaryFailure(exception));

        testee.attemptDelivery(fakeMail);

        verify(mailQueue).enQueue(FakeMail.builder()
                .name("name")
                .attribute(DeliveryRetriesHelper.makeAttribute(3))
                .attribute(MailPrioritySupport.LOW_PRIORITY_ATTRIBUTE)
                .attribute(new Attribute(IS_DELIVERY_PERMANENT_ERROR, AttributeValue.of(false)))
                .state(Mail.ERROR)
                .lastUpdated(FIXED_DATE)
                .build(),
            Duration.ofSeconds(3));
        verifyNoMoreInteractions(mailQueue);
    }

    @Test
    void deliveryTemporaryFailureShouldRetryDeliveryOnMaximumRetryNumber() throws Exception {
        FakeMail fakeMail = FakeMail.builder()
            .name("name")
            .state(Mail.ERROR)
            .attribute(DeliveryRetriesHelper.makeAttribute(4))
            .build();
        Exception exception = new Exception();
        when(mailDelivrer.deliver(fakeMail)).thenReturn(ExecutionResult.temporaryFailure(exception));

        testee.attemptDelivery(fakeMail);

        verify(mailQueue).enQueue(FakeMail.builder()
                .name("name")
                .attribute(DeliveryRetriesHelper.makeAttribute(5))
                .attribute(MailPrioritySupport.LOW_PRIORITY_ATTRIBUTE)
                .attribute(new Attribute(IS_DELIVERY_PERMANENT_ERROR, AttributeValue.of(false)))
                .state(Mail.ERROR)
                .lastUpdated(FIXED_DATE)
                .build(),
            Duration.ofSeconds(5));
        verifyNoMoreInteractions(mailQueue);
    }

    @Test
    void deliveryTemporaryFailureShouldNotRetryDeliveryOverMaximumRetryNumber() throws Exception {
        FakeMail fakeMail = FakeMail.builder()
            .name("name")
            .state(Mail.ERROR)
            .attribute(DeliveryRetriesHelper.makeAttribute(5))
            .build();
        Exception exception = new Exception();
        when(mailDelivrer.deliver(fakeMail)).thenReturn(ExecutionResult.temporaryFailure(exception));

        testee.attemptDelivery(fakeMail);

        verifyNoMoreInteractions(mailQueue);
    }

    @Test
    void deliveryTemporaryFailureShouldBounceWhenRetryExceeded() throws Exception {
        FakeMail fakeMail = FakeMail.builder()
            .name("name")
            .state(Mail.ERROR)
            .attribute(DeliveryRetriesHelper.makeAttribute(5))
            .build();
        Exception exception = new Exception("");
        when(mailDelivrer.deliver(fakeMail)).thenReturn(ExecutionResult.temporaryFailure(exception));

        testee.attemptDelivery(fakeMail);

        verify(bouncer).bounce(eq(fakeMail), any(Exception.class));
        verifyNoMoreInteractions(bouncer);
    }

    @Test
    void deliveryTemporaryFailureShouldResetDeliveryCountOnNonErrorState() throws Exception {
        FakeMail fakeMail = FakeMail.builder()
            .name("name")
            .state(Mail.DEFAULT)
            .attribute(DeliveryRetriesHelper.makeAttribute(5))
            .build();
        Exception exception = new Exception();
        when(mailDelivrer.deliver(fakeMail)).thenReturn(ExecutionResult.temporaryFailure(exception));

        testee.attemptDelivery(fakeMail);

        verify(mailQueue).enQueue(FakeMail.builder()
                .name("name")
                .attribute(DeliveryRetriesHelper.makeAttribute(1))
                .attribute(MailPrioritySupport.LOW_PRIORITY_ATTRIBUTE)
                .attribute(new Attribute(IS_DELIVERY_PERMANENT_ERROR, AttributeValue.of(false)))
                .state(Mail.ERROR)
                .lastUpdated(FIXED_DATE)
                .build(),
            Duration.ofSeconds(1));
        verifyNoMoreInteractions(mailQueue);
    }

    @Test
    void deliveryTemporaryFailureShouldRetryDeliveryWithPreservedPriority() throws Exception {
        FakeMail fakeMail = FakeMail.builder().name("name")
            .attribute(MailPrioritySupport.NORMAL_PRIORITY_ATTRIBUTE)
            .state(Mail.DEFAULT).build();
        Exception exception = new Exception();
        when(mailDelivrer.deliver(fakeMail)).thenReturn(ExecutionResult.temporaryFailure(exception));

        testee.attemptDelivery(fakeMail);

        verify(mailQueue).enQueue(FakeMail.builder()
                .name("name")
                .attribute(DeliveryRetriesHelper.makeAttribute(1))
                .attribute(MailPrioritySupport.LOW_PRIORITY_ATTRIBUTE)
                .attribute(new Attribute(IS_DELIVERY_PERMANENT_ERROR, AttributeValue.of(false)))
                .state(Mail.ERROR)
                .lastUpdated(FIXED_DATE)
                .build(),
            Duration.ofSeconds(1));
        verifyNoMoreInteractions(mailQueue);
    }
}
