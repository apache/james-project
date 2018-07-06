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
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.mail.internet.MimeMessage;

import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.junit.ExecutorExtension;
import org.apache.mailet.Mail;
import org.apache.mailet.PerRecipientHeaders;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;

@ExtendWith(ExecutorExtension.class)
public interface MailQueueContract {

    MailQueue getMailQueue();

    @Test
    default void queueShouldSupportBigMail() throws Exception {
        String name = "name1";
        // 12 MB of text
        String messageText = Strings.repeat("0123456789\r\n", 1024 * 1024);
        getMailQueue().enQueue(defaultMail()
            .name(name)
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .setText(messageText))
            .build());

        MailQueue.MailQueueItem mailQueueItem = getMailQueue().deQueue();
        assertThat(mailQueueItem.getMail().getName())
            .isEqualTo(name);
    }

    @Test
    default void queueShouldPreserveMailRecipients() throws Exception {
        getMailQueue().enQueue(defaultMail()
            .recipients(RECIPIENT1, RECIPIENT2)
            .build());

        MailQueue.MailQueueItem mailQueueItem = getMailQueue().deQueue();
        assertThat(mailQueueItem.getMail().getRecipients())
            .containsOnly(RECIPIENT1, RECIPIENT2);
    }

    @Test
    default void queueShouldPreserveMailSender() throws Exception {
        getMailQueue().enQueue(defaultMail()
            .sender(SENDER)
            .build());

        MailQueue.MailQueueItem mailQueueItem = getMailQueue().deQueue();
        assertThat(mailQueueItem.getMail().getSender())
            .isEqualTo(SENDER);
    }

    @Test
    default void queueShouldPreserveMimeMessage() throws Exception {
        MimeMessage originalMimeMessage = createMimeMessage();
        getMailQueue().enQueue(defaultMail()
            .mimeMessage(originalMimeMessage)
            .build());

        MailQueue.MailQueueItem mailQueueItem = getMailQueue().deQueue();
        assertThat(asString(mailQueueItem.getMail().getMessage()))
            .isEqualTo(asString(originalMimeMessage));
    }

    @Test
    default void queueShouldPreserveMailAttribute() throws Exception {
        String attributeName = "any";
        String attributeValue = "value";
        getMailQueue().enQueue(defaultMail()
            .attribute(attributeName, attributeValue)
            .build());

        MailQueue.MailQueueItem mailQueueItem = getMailQueue().deQueue();
        assertThat(mailQueueItem.getMail().getAttribute(attributeName))
            .isEqualTo(attributeValue);
    }

    @Test
    default void queueShouldPreserveErrorMessage() throws Exception {
        String errorMessage = "ErrorMessage";
        getMailQueue().enQueue(defaultMail()
            .errorMessage(errorMessage)
            .build());

        MailQueue.MailQueueItem mailQueueItem = getMailQueue().deQueue();
        assertThat(mailQueueItem.getMail().getErrorMessage())
            .isEqualTo(errorMessage);
    }

    @Test
    default void queueShouldPreserveState() throws Exception {
        String state = "state";
        getMailQueue().enQueue(defaultMail()
            .state(state)
            .build());

        MailQueue.MailQueueItem mailQueueItem = getMailQueue().deQueue();
        assertThat(mailQueueItem.getMail().getState())
            .isEqualTo(state);
    }

    @Test
    default void queueShouldPreserveRemoteAddress() throws Exception {
        String remoteAddress = "remote";
        getMailQueue().enQueue(defaultMail()
            .remoteAddr(remoteAddress)
            .build());

        MailQueue.MailQueueItem mailQueueItem = getMailQueue().deQueue();
        assertThat(mailQueueItem.getMail().getRemoteAddr())
            .isEqualTo(remoteAddress);
    }

    @Test
    default void queueShouldPreserveRemoteHost() throws Exception {
        String remoteHost = "remote";
        getMailQueue().enQueue(defaultMail()
            .remoteHost(remoteHost)
            .build());

        MailQueue.MailQueueItem mailQueueItem = getMailQueue().deQueue();
        assertThat(mailQueueItem.getMail().getRemoteHost())
            .isEqualTo(remoteHost);
    }

    @Test
    default void queueShouldPreserveLastUpdated() throws Exception {
        Date lastUpdated = new Date();
        getMailQueue().enQueue(defaultMail()
            .lastUpdated(lastUpdated)
            .build());

        MailQueue.MailQueueItem mailQueueItem = getMailQueue().deQueue();
        assertThat(mailQueueItem.getMail().getLastUpdated())
            .isEqualTo(lastUpdated);
    }

    @Test
    default void queueShouldPreserveName() throws Exception {
        String expectedName = "name";
        getMailQueue().enQueue(defaultMail()
            .name(expectedName)
            .build());

        MailQueue.MailQueueItem mailQueueItem = getMailQueue().deQueue();
        assertThat(mailQueueItem.getMail().getName())
            .isEqualTo(expectedName);
    }

    @Test
    default void queueShouldPreservePerRecipientHeaders() throws Exception {
        PerRecipientHeaders.Header header = PerRecipientHeaders.Header.builder()
            .name("any")
            .value("any")
            .build();
        getMailQueue().enQueue(defaultMail()
            .addHeaderForRecipient(header, RECIPIENT1)
            .build());

        MailQueue.MailQueueItem mailQueueItem = getMailQueue().deQueue();
        assertThat(mailQueueItem.getMail().getPerRecipientSpecificHeaders()
            .getHeadersForRecipient(RECIPIENT1))
            .containsOnly(header);
    }

    @Test
    default void queueShouldPreserveNonStringMailAttribute() throws Exception {
        String attributeName = "any";
        SerializableAttribute attributeValue = new SerializableAttribute("value");
        getMailQueue().enQueue(defaultMail()
                .attribute(attributeName, attributeValue)
                .build());

        MailQueue.MailQueueItem mailQueueItem = getMailQueue().deQueue();
        assertThat(mailQueueItem.getMail().getAttribute(attributeName))
                .isInstanceOf(SerializableAttribute.class)
                .isEqualTo(attributeValue);
    }

    @Test
    default void dequeueShouldBeFifo() throws Exception {
        String firstExpectedName = "name1";
        getMailQueue().enQueue(defaultMail()
            .name(firstExpectedName)
            .build());
        String secondExpectedName = "name2";
        getMailQueue().enQueue(defaultMail()
            .name(secondExpectedName)
            .build());

        MailQueue.MailQueueItem mailQueueItem1 = getMailQueue().deQueue();
        mailQueueItem1.done(true);
        MailQueue.MailQueueItem mailQueueItem2 = getMailQueue().deQueue();
        mailQueueItem2.done(true);
        assertThat(mailQueueItem1.getMail().getName()).isEqualTo(firstExpectedName);
        assertThat(mailQueueItem2.getMail().getName()).isEqualTo(secondExpectedName);
    }

    @Test
    default void dequeueCanBeChainedBeforeAck() throws Exception {
        getMailQueue().enQueue(defaultMail()
            .name("name1")
            .build());
        getMailQueue().enQueue(defaultMail()
            .name("name2")
            .build());

        MailQueue.MailQueueItem mailQueueItem1 = getMailQueue().deQueue();
        MailQueue.MailQueueItem mailQueueItem2 = getMailQueue().deQueue();
        mailQueueItem1.done(true);
        mailQueueItem2.done(true);
        assertThat(mailQueueItem1.getMail().getName()).isEqualTo("name1");
        assertThat(mailQueueItem2.getMail().getName()).isEqualTo("name2");
    }


    @Test
    default void dequeueCouldBeInterleavingWithOutOfOrderAck() throws Exception {
        getMailQueue().enQueue(defaultMail()
            .name("name1")
            .build());
        getMailQueue().enQueue(defaultMail()
            .name("name2")
            .build());

        MailQueue.MailQueueItem mailQueueItem1 = getMailQueue().deQueue();
        MailQueue.MailQueueItem mailQueueItem2 = getMailQueue().deQueue();
        mailQueueItem2.done(true);
        mailQueueItem1.done(true);
        assertThat(mailQueueItem1.getMail().getName()).isEqualTo("name1");
        assertThat(mailQueueItem2.getMail().getName()).isEqualTo("name2");
    }

    @Test
    default void dequeueShouldAllowRetrieveFailItems() throws Exception {
        getMailQueue().enQueue(defaultMail()
            .name("name1")
            .build());
        getMailQueue().enQueue(defaultMail()
            .name("name1")
            .build());

        MailQueue.MailQueueItem mailQueueItem1 = getMailQueue().deQueue();
        mailQueueItem1.done(false);
        MailQueue.MailQueueItem mailQueueItem2 = getMailQueue().deQueue();
        mailQueueItem2.done(true);
        assertThat(mailQueueItem1.getMail().getName()).isEqualTo("name1");
        assertThat(mailQueueItem2.getMail().getName()).isEqualTo("name1");
    }

    @Test
    default void dequeueShouldNotReturnInProcessingEmails(ExecutorService executorService) throws Exception {
        getMailQueue().enQueue(defaultMail()
            .name("name")
            .build());

        getMailQueue().deQueue();

        Future<?> future = executorService.submit(Throwing.runnable(() -> getMailQueue().deQueue()));
        assertThatThrownBy(() -> future.get(2, TimeUnit.SECONDS))
            .isInstanceOf(TimeoutException.class);
    }

    @Test
    default void deQueueShouldBlockWhenNoMail(ExecutorService executorService) throws Exception {
        Future<?> future = executorService.submit(Throwing.runnable(() -> getMailQueue().deQueue()));

        assertThatThrownBy(() -> future.get(2, TimeUnit.SECONDS))
            .isInstanceOf(TimeoutException.class);
    }

    @Test
    default void deQueueShouldWaitForAMailToBeEnqueued(ExecutorService executorService) throws Exception {
        Mail mail = defaultMail()
            .name("name")
            .build();
        Future<MailQueue.MailQueueItem> tryDequeue = executorService.submit(() -> getMailQueue().deQueue());
        getMailQueue().enQueue(mail);

        assertThat(tryDequeue.get().getMail().getName()).isEqualTo("name");
    }

    class SerializableAttribute implements Serializable {
        private final String value;

        public SerializableAttribute(String value) {
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
