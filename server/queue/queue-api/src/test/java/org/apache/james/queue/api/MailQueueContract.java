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

import static org.apache.james.queue.api.Mails.createMimeMessage;
import static org.apache.james.queue.api.Mails.defaultMail;
import static org.apache.james.util.MimeMessageUtil.asString;
import static org.apache.mailet.base.MailAddressFixture.RECIPIENT1;
import static org.apache.mailet.base.MailAddressFixture.RECIPIENT2;
import static org.apache.mailet.base.MailAddressFixture.SENDER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.Serializable;
import java.time.Duration;
import java.util.Date;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.mail.internet.MimeMessage;

import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.apache.mailet.Attribute;
import org.apache.mailet.Mail;
import org.apache.mailet.PerRecipientHeaders;
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.Test;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public interface MailQueueContract {

    MailQueue getMailQueue();

    default void enQueue(Mail mail) throws MailQueue.MailQueueException {
        getMailQueue().enQueue(mail);
    }

    @Test
    default void queueShouldSupportBigMail() throws Exception {
        String name = "name1";
        // 12 MB of text
        String messageText = Strings.repeat("0123456789\r\n", 1024 * 1024);
        FakeMail mail = defaultMail()
            .name(name)
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .setText(messageText))
            .build();
        enQueue(mail);

        MailQueue.MailQueueItem mailQueueItem = Flux.from(getMailQueue().deQueue()).blockFirst();
        assertThat(mailQueueItem.getMail().getName())
            .isEqualTo(name);
    }

    @Test
    default void queueShouldPreserveMailRecipients() throws Exception {
        enQueue(defaultMail()
            .name("mail")
            .recipients(RECIPIENT1, RECIPIENT2)
            .build());

        MailQueue.MailQueueItem mailQueueItem = Flux.from(getMailQueue().deQueue()).blockFirst();
        assertThat(mailQueueItem.getMail().getRecipients())
            .containsOnly(RECIPIENT1, RECIPIENT2);
    }

    @Test
    default void queueShouldHandleSender() throws Exception {
        enQueue(FakeMail.builder()
            .name("name")
            .mimeMessage(createMimeMessage())
            .recipients(RECIPIENT1, RECIPIENT2)
            .sender(MailAddress.nullSender())
            .lastUpdated(new Date())
            .build());

        MailQueue.MailQueueItem mailQueueItem = Flux.from(getMailQueue().deQueue()).blockFirst();
        assertThat(mailQueueItem.getMail().getMaybeSender())
            .isEqualTo(MaybeSender.nullSender());
    }

    @Test
    default void queueShouldHandleNoSender() throws Exception {
        enQueue(FakeMail.builder()
            .name("name")
            .mimeMessage(createMimeMessage())
            .recipients(RECIPIENT1, RECIPIENT2)
            .lastUpdated(new Date())
            .build());

        MailQueue.MailQueueItem mailQueueItem = Flux.from(getMailQueue().deQueue()).blockFirst();
        assertThat(mailQueueItem.getMail().getMaybeSender())
            .isEqualTo(MaybeSender.nullSender());
    }

    @Test
    default void queueShouldPreserveMailSender() throws Exception {
        enQueue(defaultMail()
            .name("mail")
            .sender(SENDER)
            .build());

        MailQueue.MailQueueItem mailQueueItem = Flux.from(getMailQueue().deQueue()).blockFirst();
        assertThat(mailQueueItem.getMail().getMaybeSender())
            .isEqualTo(MaybeSender.of(SENDER));
    }

    @Test
    default void queueShouldPreserveMimeMessage() throws Exception {
        MimeMessage originalMimeMessage = createMimeMessage();
        enQueue(defaultMail()
            .name("mail")
            .mimeMessage(originalMimeMessage)
            .build());

        MailQueue.MailQueueItem mailQueueItem = Flux.from(getMailQueue().deQueue()).blockFirst();
        assertThat(asString(mailQueueItem.getMail().getMessage()))
            .isEqualTo(asString(originalMimeMessage));
    }

    @Test
    default void queueShouldPreserveMailAttribute() throws Exception {
        Attribute attribute = Attribute.convertToAttribute("any", "value");
        enQueue(defaultMail()
            .name("mail")
            .attribute(attribute)
            .build());

        MailQueue.MailQueueItem mailQueueItem = Flux.from(getMailQueue().deQueue()).blockFirst();
        assertThat(mailQueueItem.getMail().getAttribute(attribute.getName()))
            .contains(attribute);
    }

    @Test
    default void queueShouldPreserveErrorMessage() throws Exception {
        String errorMessage = "ErrorMessage";
        enQueue(defaultMail()
            .name("mail")
            .errorMessage(errorMessage)
            .build());

        MailQueue.MailQueueItem mailQueueItem = Flux.from(getMailQueue().deQueue()).blockFirst();
        assertThat(mailQueueItem.getMail().getErrorMessage())
            .isEqualTo(errorMessage);
    }

    @Test
    default void queueShouldPreserveState() throws Exception {
        String state = "state";
        enQueue(defaultMail()
            .name("mail")
            .state(state)
            .build());

        MailQueue.MailQueueItem mailQueueItem = Flux.from(getMailQueue().deQueue()).blockFirst();
        assertThat(mailQueueItem.getMail().getState())
            .isEqualTo(state);
    }

    @Test
    default void queueShouldPreserveRemoteAddress() throws Exception {
        String remoteAddress = "remote";
        enQueue(defaultMail()
            .name("mail")
            .remoteAddr(remoteAddress)
            .build());

        MailQueue.MailQueueItem mailQueueItem = Flux.from(getMailQueue().deQueue()).blockFirst();
        assertThat(mailQueueItem.getMail().getRemoteAddr())
            .isEqualTo(remoteAddress);
    }

    @Test
    default void queueShouldPreserveRemoteHost() throws Exception {
        String remoteHost = "remote";
        enQueue(defaultMail()
            .name("mail")
            .remoteHost(remoteHost)
            .build());

        MailQueue.MailQueueItem mailQueueItem = Flux.from(getMailQueue().deQueue()).blockFirst();
        assertThat(mailQueueItem.getMail().getRemoteHost())
            .isEqualTo(remoteHost);
    }

    @Test
    default void queueShouldPreserveLastUpdated() throws Exception {
        Date lastUpdated = new Date();
        enQueue(defaultMail()
            .name("mail")
            .lastUpdated(lastUpdated)
            .build());

        MailQueue.MailQueueItem mailQueueItem = Flux.from(getMailQueue().deQueue()).blockFirst();
        assertThat(mailQueueItem.getMail().getLastUpdated())
            .isEqualTo(lastUpdated);
    }

    @Test
    default void queueShouldPreserveName() throws Exception {
        String expectedName = "name";
        enQueue(defaultMail()
            .name(expectedName)
            .build());

        MailQueue.MailQueueItem mailQueueItem = Flux.from(getMailQueue().deQueue()).blockFirst();
        assertThat(mailQueueItem.getMail().getName())
            .isEqualTo(expectedName);
    }

    @Test
    default void queueShouldPreservePerRecipientHeaders() throws Exception {
        PerRecipientHeaders.Header header = PerRecipientHeaders.Header.builder()
            .name("any")
            .value("any")
            .build();
        enQueue(defaultMail()
            .name("mail")
            .addHeaderForRecipient(header, RECIPIENT1)
            .build());

        MailQueue.MailQueueItem mailQueueItem = Flux.from(getMailQueue().deQueue()).blockFirst();
        assertThat(mailQueueItem.getMail().getPerRecipientSpecificHeaders()
            .getHeadersForRecipient(RECIPIENT1))
            .containsOnly(header);
    }

    @Test
    default void queueShouldPreserveNonStringMailAttribute() throws Exception {
        Attribute attribute = Attribute.convertToAttribute("any", new SerializableAttribute("value"));
        enQueue(defaultMail()
                .name("mail")
                .attribute(attribute)
                .build());

        MailQueue.MailQueueItem mailQueueItem = Flux.from(getMailQueue().deQueue()).blockFirst();
        assertThat(mailQueueItem.getMail().getAttribute(attribute.getName()))
            .hasValueSatisfying(item -> {
                assertThat(item)
                        .isEqualTo(attribute);
                assertThat(item.getValue().value())
                        .isInstanceOf(SerializableAttribute.class);
            });
    }

    @Test
    default void dequeueShouldBeFifo() throws Exception {
        String firstExpectedName = "name1";
        enQueue(defaultMail()
            .name(firstExpectedName)
            .build());
        String secondExpectedName = "name2";
        enQueue(defaultMail()
            .name(secondExpectedName)
            .build());

        Iterator<MailQueue.MailQueueItem> items = Flux.from(getMailQueue().deQueue()).subscribeOn(Schedulers.elastic()).toIterable().iterator();
        MailQueue.MailQueueItem mailQueueItem1 = items.next();
        mailQueueItem1.done(true);
        MailQueue.MailQueueItem mailQueueItem2 = items.next();
        mailQueueItem2.done(true);
        assertThat(mailQueueItem1.getMail().getName()).isEqualTo(firstExpectedName);
        assertThat(mailQueueItem2.getMail().getName()).isEqualTo(secondExpectedName);
    }

    @Test
    default void dequeueCanBeChainedBeforeAck() throws Exception {
        enQueue(defaultMail()
            .name("name1")
            .build());
        enQueue(defaultMail()
            .name("name2")
            .build());

        Iterator<MailQueue.MailQueueItem> items = Flux.from(getMailQueue().deQueue())
            .subscribeOn(Schedulers.elastic()).toIterable().iterator();
        MailQueue.MailQueueItem mailQueueItem1 = items.next();
        MailQueue.MailQueueItem mailQueueItem2 = items.next();
        mailQueueItem1.done(true);
        mailQueueItem2.done(true);
        assertThat(mailQueueItem1.getMail().getName()).isEqualTo("name1");
        assertThat(mailQueueItem2.getMail().getName()).isEqualTo("name2");
    }


    @Test
    default void dequeueCouldBeInterleavingWithOutOfOrderAck() throws Exception {
        enQueue(defaultMail()
            .name("name1")
            .build());
        enQueue(defaultMail()
            .name("name2")
            .build());

        Iterator<MailQueue.MailQueueItem> items = Flux.from(getMailQueue().deQueue()).subscribeOn(Schedulers.elastic()).toIterable().iterator();
        MailQueue.MailQueueItem mailQueueItem1 = items.next();
        MailQueue.MailQueueItem mailQueueItem2 = items.next();
        mailQueueItem2.done(true);
        mailQueueItem1.done(true);
        assertThat(mailQueueItem1.getMail().getName()).isEqualTo("name1");
        assertThat(mailQueueItem2.getMail().getName()).isEqualTo("name2");
    }

    @Test
    default void dequeueShouldAllowRetrieveFailItems() throws Exception {
        enQueue(defaultMail()
            .name("name1")
            .build());

        Iterator<MailQueue.MailQueueItem> items = Flux.from(getMailQueue().deQueue()).subscribeOn(Schedulers.elastic()).toIterable().iterator();
        MailQueue.MailQueueItem mailQueueItem1 = items.next();
        mailQueueItem1.done(false);
        MailQueue.MailQueueItem mailQueueItem2 = items.next();
        mailQueueItem2.done(true);
        assertThat(mailQueueItem1.getMail().getName()).isEqualTo("name1");
        assertThat(mailQueueItem2.getMail().getName()).isEqualTo("name1");
    }

    @Test
    default void dequeueShouldNotReturnInProcessingEmails() throws Exception {
        enQueue(defaultMail()
            .name("name")
            .build());

        LinkedBlockingQueue<MailQueue.MailQueueItem> queue = new LinkedBlockingQueue<>(1);
        Flux.from(getMailQueue().deQueue()).subscribeOn(Schedulers.elastic()).subscribe(Throwing.consumer(queue::put));
        queue.take();

        assertThat(queue.poll(2, TimeUnit.SECONDS)).isNull();
    }

    @Test
    default void deQueueShouldBlockWhenNoMail() {
        Mono<MailQueue.MailQueueItem> item = Flux.from(getMailQueue().deQueue()).subscribeOn(Schedulers.elastic()).next();

        assertThatThrownBy(() -> item.block(Duration.ofSeconds(2)))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    default void deQueueShouldWaitForAMailToBeEnqueued() throws Exception {
        MailQueue testee = getMailQueue();

        Mail mail = defaultMail()
            .name("name")
            .build();
        Mono<MailQueue.MailQueueItem> item = Flux.from(testee.deQueue()).next();
        testee.enQueue(mail);

        assertThat(item.block(Duration.ofMinutes(1)).getMail().getName()).isEqualTo("name");
    }

    @Test
    default void concurrentEnqueueDequeueShouldNotFail() throws Exception {
        MailQueue testee = getMailQueue();

        ConcurrentLinkedDeque<Mail> dequeuedMails = new ConcurrentLinkedDeque<>();

        int threadCount = 10;
        int operationCount = 10;
        int totalDequeuedMessages = 50;
        LinkedBlockingQueue<MailQueue.MailQueueItem> itemQueue = new LinkedBlockingQueue<>(1);
        Flux.from(testee
            .deQueue())
            .subscribeOn(Schedulers.elastic())
            .flatMap(e -> {
                try {
                    itemQueue.put(e);
                } catch (InterruptedException ignored) {
                    //ignored
                }
                return Mono.empty();
            }).subscribe();
        ConcurrentTestRunner.builder()
            .operation((threadNumber, step) -> {
                if (step % 2 == 0) {
                    testee.enQueue(defaultMail()
                        .name("name" + threadNumber + "-" + step)
                        .build());
                } else {
                    MailQueue.MailQueueItem mailQueueItem = itemQueue.take();
                    dequeuedMails.add(mailQueueItem.getMail());
                    mailQueueItem.done(true);
                }
            })
            .threadCount(threadCount)
            .operationCount(operationCount)
            .runSuccessfullyWithin(Duration.ofMinutes(5));

        assertThat(
            dequeuedMails.stream()
                .map(Mail::getName)
                .distinct())
            .hasSize(totalDequeuedMessages);
    }

    @Test
    default void concurrentEnqueueDequeueWithAckNackShouldNotFail() throws Exception {
        MailQueue testee = getMailQueue();

        ConcurrentLinkedDeque<Mail> dequeuedMails = new ConcurrentLinkedDeque<>();

        int threadCount = 10;
        int operationCount = 15;
        int totalDequeuedMessages = 50;
        LinkedBlockingDeque<MailQueue.MailQueueItem> deque = new LinkedBlockingDeque<>();
        Flux.from(testee.deQueue()).subscribeOn(Schedulers.elastic()).doOnNext(deque::addFirst).subscribe();
        ConcurrentTestRunner.builder()
            .operation((threadNumber, step) -> {
                if (step % 3 == 0) {
                    testee.enQueue(defaultMail()
                        .name("name" + threadNumber + "-" + step)
                        .build());
                }
                if (step % 3 == 1) {
                    MailQueue.MailQueueItem mailQueueItem = deque.takeLast();
                    mailQueueItem.done(false);
                }
                if (step % 3 == 2) {
                    MailQueue.MailQueueItem mailQueueItem = deque.takeLast();
                    dequeuedMails.add(mailQueueItem.getMail());
                    mailQueueItem.done(true);
                }
            })
            .threadCount(threadCount)
            .operationCount(operationCount)
            .runSuccessfullyWithin(Duration.ofMinutes(1));

        assertThat(
            dequeuedMails.stream()
                .map(Mail::getName)
                .distinct())
            .hasSize(totalDequeuedMessages);
    }

    class SerializableAttribute implements Serializable {
        private final String value;

        SerializableAttribute(String value) {
            this.value = value;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof SerializableAttribute) {
                SerializableAttribute that = (SerializableAttribute) o;

                return Objects.equals(this.value, that.value);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(value);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("value", value)
                    .toString();
        }
    }
}
