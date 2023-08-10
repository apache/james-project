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

package org.apache.james.transport.mailets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import javax.mail.MessagingException;

import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.queue.api.MailQueueName;
import org.apache.james.queue.api.RawMailQueueItemDecoratorFactory;
import org.apache.james.queue.memory.MemoryMailQueueFactory;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetConfig;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;

class RequeueTest {
    private Mail mailSample;
    private MailQueueFactory<?> mailQueueFactory;
    private MailQueue spoolQueue;

    @BeforeEach
    void beforeEach() throws MessagingException {
        mailSample = FakeMail.builder()
            .name("mail1")
            .sender("sender1@domain.tld")
            .recipients("rcpt1@linagora.com")
            .state("newState")
            .build();

        mailQueueFactory = new MemoryMailQueueFactory(new RawMailQueueItemDecoratorFactory());
        spoolQueue = mailQueueFactory.createQueue(MailQueueFactory.SPOOL);
    }

    @AfterEach
    void afterEach() throws IOException {
        spoolQueue.close();
    }

    private Requeue testee(MailetConfig mailetConfig) throws MessagingException {
        Requeue requeue = new Requeue(mailQueueFactory);
        requeue.init(mailetConfig);
        return requeue;
    }

    @Test
    void mailetShouldRequeueMail() throws MessagingException {
        Requeue mailet = testee(FakeMailetConfig.builder()
            .mailetName("Requeue")
            .build());
        mailet.service(mailSample);

        assertThat(mailSample.getState()).isEqualTo("ghost");

        assertThat(Flux.from(spoolQueue.deQueue())
            .next()
            .block())
            .isNotNull();
    }

    @Test
    void requeueShouldShouldAppliedProcessorWhenConfigureIsProvided() throws MessagingException {
        Requeue mailet = testee(FakeMailetConfig.builder()
            .mailetName("Requeue")
            .setProperty("processor", "processor2")
            .build());

        mailet.service(mailSample);

        MailQueue.MailQueueItem mailQueueItem = Flux.from(spoolQueue.deQueue())
            .next()
            .block();
        assertThat(mailQueueItem).isNotNull();
        assertThat(mailQueueItem.getMail().getState()).isEqualTo("processor2");
    }

    @Test
    void requeueShouldShouldAppliedDefaultProcessorWhenConfigureIsNotProvided() throws MessagingException {
        Requeue mailet = testee(FakeMailetConfig.builder()
            .mailetName("Requeue")
            .build());

        mailet.service(mailSample);

        MailQueue.MailQueueItem mailQueueItem = Flux.from(spoolQueue.deQueue())
            .next()
            .block();
        assertThat(mailQueueItem).isNotNull();
        assertThat(mailQueueItem.getMail().getState()).isEqualTo(Mail.DEFAULT);
    }

    @Test
    void requeueShouldShouldNotConsumeWhenConfigureIsProvided() throws MessagingException {
        Requeue mailet = testee(FakeMailetConfig.builder()
            .mailetName("Requeue")
            .setProperty("consume", "false")
            .build());

        mailet.service(mailSample);

        assertThat(mailSample.getState()).isEqualTo("newState");
    }

    @Test
    void requeueShouldShouldConsumeProcessorByDefault() throws MessagingException {
        Requeue mailet = testee(FakeMailetConfig.builder()
            .mailetName("Requeue")
            .build());

        mailet.service(mailSample);

        assertThat(mailSample.getState()).isEqualTo("ghost");
    }

    @Test
    void requeueShouldShouldAppliedDelayWhenConfigureIsProvided() throws MessagingException {
        Requeue mailet = testee(FakeMailetConfig.builder()
            .mailetName("Requeue")
            .setProperty("delay", "2s")
            .build());

        Instant enqueueTime = Instant.now();
        mailet.service(mailSample);

        AtomicReference<Instant> dequeueTime = new AtomicReference<>();
        MailQueue.MailQueueItem mailQueueItem = Flux.from(spoolQueue.deQueue())
            .next()
            .doOnNext(any -> dequeueTime.set(Instant.now()))
            .block();

        assertThat(mailQueueItem).isNotNull();
        assertThat(Duration.between(enqueueTime, dequeueTime.get()).abs().toSeconds()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void requeueShouldShouldNotAppliedDelayWhenConfigureIsNotProvided() throws MessagingException {
        Requeue mailet = testee(FakeMailetConfig.builder()
            .mailetName("Requeue")
            .build());

        Instant enqueueTime = Instant.now();
        mailet.service(mailSample);

        AtomicReference<Instant> dequeueTime = new AtomicReference<>();
        MailQueue.MailQueueItem mailQueueItem = Flux.from(spoolQueue.deQueue())
            .next()
            .doOnNext(any -> dequeueTime.set(Instant.now()))
            .block();

        assertThat(mailQueueItem).isNotNull();
        assertThat(Duration.between(enqueueTime, dequeueTime.get()).abs().toSeconds()).isCloseTo(0L, offset(1L));
    }

    @Test
    void requeueShouldUseDefaultQueueWhenConfigIsNotProvided() throws MessagingException {
        Requeue mailet = testee(FakeMailetConfig.builder()
            .mailetName("Requeue")
            .build());

        mailet.service(mailSample);

        assertThat(Flux.from(spoolQueue.deQueue())
            .next()
            .block())
            .isNotNull();
    }

    @Test
    void requeueShouldUseQueueWhenConfigIsProvided() throws MessagingException {
        MailQueueName newQueueName = MailQueueName.of("newQueue");
        Requeue mailet = testee(FakeMailetConfig.builder()
            .mailetName("Requeue")
            .setProperty("queue", "newQueue")
            .build());

        mailet.service(mailSample);

        assertThat(Flux.from(mailQueueFactory.createQueue(newQueueName).deQueue())
            .map(MailQueue.MailQueueItem::getMail)
            .blockFirst())
            .isNotNull();
    }

    @Nested
    class Configuration {

        @Test
        void shouldFailWhenBadDelay() {
            Assertions.assertThatThrownBy(() -> testee(FakeMailetConfig.builder()
                    .mailetName("Requeue")
                    .setProperty("delay", "bad")
                    .build()))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldFailWhenNegativeDuration() {
            Assertions.assertThatThrownBy(() -> testee(FakeMailetConfig.builder()
                    .mailetName("Requeue")
                    .setProperty("delay", "-3s")
                    .build()))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void durationWithNoUnitShouldDefaultToSeconds() throws MessagingException {
            Requeue mailet = testee(FakeMailetConfig.builder()
                .mailetName("Requeue")
                .setProperty("delay", "2")
                .build());

            Instant enqueueTime = Instant.now();
            mailet.service(mailSample);

            AtomicReference<Instant> dequeueTime = new AtomicReference<>();

            MailQueue.MailQueueItem mailQueueItem = Flux.from(spoolQueue.deQueue())
                .next()
                .doOnNext(any -> dequeueTime.set(Instant.now()))
                .block();

            assertThat(mailQueueItem).isNotNull();
            assertThat(Duration.between(enqueueTime, dequeueTime.get()).abs().toSeconds()).isGreaterThanOrEqualTo(2);
        }
    }
}
