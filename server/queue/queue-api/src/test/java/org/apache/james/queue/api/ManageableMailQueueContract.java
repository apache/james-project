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
import static org.apache.james.queue.api.Mails.defaultMailNoRecipient;
import static org.apache.mailet.base.MailAddressFixture.OTHER_AT_LOCAL;
import static org.apache.mailet.base.MailAddressFixture.RECIPIENT1;
import static org.apache.mailet.base.MailAddressFixture.RECIPIENT2;
import static org.apache.mailet.base.MailAddressFixture.RECIPIENT3;
import static org.apache.mailet.base.MailAddressFixture.SENDER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.time.Duration;

import javax.mail.internet.MimeMessage;

import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.mailet.Attribute;
import org.apache.mailet.Mail;
import org.apache.mailet.base.MailAddressFixture;
import org.junit.jupiter.api.Test;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;

import reactor.core.publisher.Flux;

public interface ManageableMailQueueContract extends MailQueueContract {

    ManageableMailQueue getManageableMailQueue();

    @Test
    default void getSizeShouldReturnZeroWhenNoMessage() throws Exception {
        long size = getManageableMailQueue().getSize();

        assertThat(size).isEqualTo(0L);
    }

    @Test
    default void getSizeShouldReturnMessageCount() throws Exception {
        enQueue(defaultMail().name("name").build());

        long size = getManageableMailQueue().getSize();

        assertThat(size).isEqualTo(1L);
    }

    @Test
    default void getSizeShouldReturnMessageCountWhenSeveralMails() throws Exception {
        enQueue(defaultMail().name("1").build());
        enQueue(defaultMail().name("2").build());

        long size = getManageableMailQueue().getSize();

        assertThat(size).isEqualTo(2L);
    }

    @Test
    default void dequeueShouldDecreaseQueueSize() throws Exception {
        enQueue(defaultMail().name("name").build());

        Flux.from(getManageableMailQueue().deQueue())
            .doOnNext(Throwing.consumer(item -> item.done(true)))
            .blockFirst();

        long size = getManageableMailQueue().getSize();

        assertThat(size).isEqualTo(0L);
    }

    @Test
    default void noAckShouldNotDecreaseSize() throws Exception {
        enQueue(defaultMail().name("name").build());

        Flux.from(getManageableMailQueue().deQueue())
            .doOnNext(Throwing.consumer(item -> item.done(false)))
            .blockFirst();

        long size = getManageableMailQueue().getSize();

        assertThat(size).isEqualTo(1L);
    }

    @Test
    default void processedMailsShouldNotDecreaseSize() throws Exception {
        enQueue(defaultMail().name("name").build());

        Flux.from(getManageableMailQueue().deQueue());

        long size = getManageableMailQueue().getSize();

        assertThat(size).isEqualTo(1L);
    }

    @Test
    default void browseShouldReturnEmptyByDefault() throws Exception {
        ManageableMailQueue.MailQueueIterator items = getManageableMailQueue().browse();

        assertThat(items).isEmpty();
    }

    @Test
    default void browseShouldReturnSingleElement() throws Exception {
        enQueue(defaultMail()
            .name("name")
            .build());

        ManageableMailQueue.MailQueueIterator items = getManageableMailQueue().browse();

        assertThat(items).extracting(ManageableMailQueue.MailQueueItemView::getMail)
            .extracting(Mail::getName)
            .containsExactly("name");
    }

    @Test
    default void browseShouldReturnElementsInOrder() throws Exception {
        enQueue(defaultMail()
            .name("name1")
            .build());
        enQueue(defaultMail()
            .name("name2")
            .build());
        enQueue(defaultMail()
            .name("name3")
            .build());

        ManageableMailQueue.MailQueueIterator items = getManageableMailQueue().browse();

        assertThat(items)
            .extracting(ManageableMailQueue.MailQueueItemView::getMail)
            .extracting(Mail::getName)
            .containsExactly("name1", "name2", "name3");
    }

    @Test
    default void dequeueShouldNotFailWhenBrowsing() throws Exception {
        enQueue(defaultMail()
            .name("name1")
            .build());
        enQueue(defaultMail()
            .name("name2")
            .build());
        enQueue(defaultMail()
            .name("name3")
            .build());

        getManageableMailQueue().browse();

        assertThatCode(() -> Flux.from(getManageableMailQueue().deQueue())).doesNotThrowAnyException();

    }

    @Test
    default void browseShouldNotFailWhenConcurrentDequeue() throws Exception {
        enQueue(defaultMail()
            .name("name1")
            .build());
        enQueue(defaultMail()
            .name("name2")
            .build());
        enQueue(defaultMail()
            .name("name3")
            .build());

        ManageableMailQueue.MailQueueIterator items = getManageableMailQueue().browse();

        Flux.from(getManageableMailQueue().deQueue());

        assertThatCode(() ->  Iterators.consumingIterator(items)).doesNotThrowAnyException();
    }

    @Test
    default void dequeueShouldNotFailWhenBrowsingIterating() throws Exception {
        enQueue(defaultMail()
            .name("name1")
            .build());
        enQueue(defaultMail()
            .name("name2")
            .build());
        enQueue(defaultMail()
            .name("name3")
            .build());

        ManageableMailQueue.MailQueueIterator items = getManageableMailQueue().browse();
        items.next();

        assertThatCode(() -> Flux.from(getManageableMailQueue().deQueue())).doesNotThrowAnyException();

    }

    @Test
    default void dequeueShouldReturnDecoratedMailItem() throws Exception {
        enQueue(defaultMail()
            .name("name1")
            .build());

        assertThat(Flux.from(getManageableMailQueue().deQueue()).blockFirst(Duration.ofMinutes(1)))
            .isInstanceOf(MailQueueItemDecoratorFactory.MailQueueItemDecorator.class);
    }

    @Test
    default void browseShouldNotFailWhenConcurrentDequeueWhenIterating() throws Exception {
        enQueue(defaultMail()
            .name("name1")
            .build());
        enQueue(defaultMail()
            .name("name2")
            .build());
        enQueue(defaultMail()
            .name("name3")
            .build());

        ManageableMailQueue.MailQueueIterator items = getManageableMailQueue().browse();
        items.next();

        Flux.from(getManageableMailQueue().deQueue());

        assertThatCode(() ->  Iterators.consumingIterator(items)).doesNotThrowAnyException();
    }

    @Test
    default void enqueueShouldNotFailWhenBrowsing() throws Exception {
        enQueue(defaultMail()
            .name("name1")
            .build());
        enQueue(defaultMail()
            .name("name2")
            .build());
        enQueue(defaultMail()
            .name("name3")
            .build());

        getManageableMailQueue().browse();

        assertThatCode(() -> enQueue(defaultMail()
            .name("name4")
            .build())).doesNotThrowAnyException();

    }

    @Test
    default void browseShouldNotFailWhenConcurrentEnqueue() throws Exception {
        enQueue(defaultMail()
            .name("name1")
            .build());
        enQueue(defaultMail()
            .name("name2")
            .build());
        enQueue(defaultMail()
            .name("name3")
            .build());

        ManageableMailQueue.MailQueueIterator items = getManageableMailQueue().browse();

        enQueue(defaultMail()
            .name("name4")
            .build());

        assertThatCode(() ->  Iterators.consumingIterator(items)).doesNotThrowAnyException();
    }

    @Test
    default void enqueueShouldNotFailWhenBrowsingIterating() throws Exception {
        enQueue(defaultMail()
            .name("name1")
            .build());
        enQueue(defaultMail()
            .name("name2")
            .build());
        enQueue(defaultMail()
            .name("name3")
            .build());

        ManageableMailQueue.MailQueueIterator items = getManageableMailQueue().browse();
        items.next();

        assertThatCode(() ->
            enQueue(defaultMail()
                .name("name4")
                .build()))
            .doesNotThrowAnyException();

    }

    @Test
    default void browseShouldNotFailWhenConcurrentEnqueueWhenIterating() throws Exception {
        enQueue(defaultMail()
            .name("name1")
            .build());
        enQueue(defaultMail()
            .name("name2")
            .build());
        enQueue(defaultMail()
            .name("name3")
            .build());

        ManageableMailQueue.MailQueueIterator items = getManageableMailQueue().browse();
        items.next();

        enQueue(defaultMail()
            .name("name2")
            .build());

        assertThatCode(() ->  Iterators.consumingIterator(items)).doesNotThrowAnyException();
    }

    @Test
    default void clearShouldNotFailWhenBrowsingIterating() throws Exception {
        enQueue(defaultMail()
            .name("name1")
            .build());
        enQueue(defaultMail()
            .name("name2")
            .build());
        enQueue(defaultMail()
            .name("name3")
            .build());

        ManageableMailQueue.MailQueueIterator items = getManageableMailQueue().browse();
        items.next();

        assertThatCode(() -> getManageableMailQueue().clear())
            .doesNotThrowAnyException();

    }

    @Test
    default void browseShouldNotFailWhenConcurrentClearWhenIterating() throws Exception {
        enQueue(defaultMail()
            .name("name1")
            .build());
        enQueue(defaultMail()
            .name("name2")
            .build());
        enQueue(defaultMail()
            .name("name3")
            .build());

        ManageableMailQueue.MailQueueIterator items = getManageableMailQueue().browse();
        items.next();

        getManageableMailQueue().clear();

        assertThatCode(() ->  Iterators.consumingIterator(items)).doesNotThrowAnyException();
    }

    @Test
    default void flushShouldNotFailWhenBrowsingIterating() throws Exception {
        enQueue(defaultMail()
            .name("name1")
            .build());
        enQueue(defaultMail()
            .name("name2")
            .build());
        enQueue(defaultMail()
            .name("name3")
            .build());

        ManageableMailQueue.MailQueueIterator items = getManageableMailQueue().browse();
        items.next();

        assertThatCode(() -> getManageableMailQueue().flush())
            .doesNotThrowAnyException();

    }

    @Test
    default void browseShouldNotFailWhenConcurrentFlushWhenIterating() throws Exception {
        enQueue(defaultMail()
            .name("name1")
            .build());
        enQueue(defaultMail()
            .name("name2")
            .build());
        enQueue(defaultMail()
            .name("name3")
            .build());

        ManageableMailQueue.MailQueueIterator items = getManageableMailQueue().browse();
        items.next();

        getManageableMailQueue().flush();

        assertThatCode(() ->  Iterators.consumingIterator(items)).doesNotThrowAnyException();
    }

    @Test
    default void removeShouldNotFailWhenBrowsingIterating() throws Exception {
        enQueue(defaultMail()
            .name("name1")
            .build());
        enQueue(defaultMail()
            .name("name2")
            .build());
        enQueue(defaultMail()
            .name("name3")
            .build());

        ManageableMailQueue.MailQueueIterator items = getManageableMailQueue().browse();
        items.next();

        assertThatCode(() -> getManageableMailQueue().flush())
            .doesNotThrowAnyException();

    }

    @Test
    default void browseShouldNotFailWhenConcurrentRemoveWhenIterating() throws Exception {
        enQueue(defaultMail()
            .name("name1")
            .build());
        enQueue(defaultMail()
            .name("name2")
            .build());
        enQueue(defaultMail()
            .name("name3")
            .build());

        ManageableMailQueue.MailQueueIterator items = getManageableMailQueue().browse();
        items.next();

        getManageableMailQueue().remove(ManageableMailQueue.Type.Name, "name2");

        assertThatCode(() ->  Iterators.consumingIterator(items)).doesNotThrowAnyException();
    }

    @Test
    default void browseShouldReturnMailsWithMimeMessage() throws Exception {
        ManageableMailQueue mailQueue = getManageableMailQueue();
        mailQueue.enQueue(defaultMail()
            .name("mail with blob")
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .setSubject("mail subject")
                .setText("mail body")
                .build())
            .build());

        MimeMessage mimeMessage = mailQueue.browse().next().getMail().getMessage();
        String subject = mimeMessage.getSubject();
        Object content = mimeMessage.getContent();

        assertSoftly(softly ->  {
            softly.assertThat(subject).isEqualTo("mail subject");
            softly.assertThat(content).isEqualTo("mail body");
        });
    }

    @Test
    default void browseShouldReturnMailsWithAttributes() throws Exception {
        Attribute attribute1 = Attribute.convertToAttribute("Attribute Name 1", "Attribute Value 1");
        Attribute attribute2 = Attribute.convertToAttribute("Attribute Name 2", "Attribute Value 2");

        ManageableMailQueue mailQueue = getManageableMailQueue();
        mailQueue.enQueue(defaultMail()
            .name("mail with blob")
            .attributes(ImmutableList.of(attribute1, attribute2))
            .build());

        Mail mail = mailQueue.browse().next().getMail();
        assertSoftly(softly -> {
            softly.assertThat(mail.getAttribute(attribute1.getName()))
                .contains(attribute1);
            softly.assertThat(mail.getAttribute(attribute2.getName()))
                .contains(attribute2);
        });
    }

    @Test
    default void browsingShouldNotAffectDequeue() throws Exception {
        enQueue(defaultMail()
            .name("name1")
            .build());
        enQueue(defaultMail()
            .name("name2")
            .build());
        enQueue(defaultMail()
            .name("name3")
            .build());

        ManageableMailQueue.MailQueueIterator items = getManageableMailQueue().browse();
        items.next();

        MailQueue.MailQueueItem mailQueueItem = Flux.from(getManageableMailQueue().deQueue()).blockFirst();

        assertThat(mailQueueItem.getMail().getName()).isEqualTo("name1");
    }

    @Test
    default void removeByNameShouldRemoveSpecificEmail() throws Exception {
        enQueue(defaultMail()
            .name("name1")
            .build());
        enQueue(defaultMail()
            .name("name2")
            .build());

        getManageableMailQueue().remove(ManageableMailQueue.Type.Name, "name2");

        assertThat(getManageableMailQueue().browse())
            .extracting(ManageableMailQueue.MailQueueItemView::getMail)
            .extracting(Mail::getName)
            .containsExactly("name1");
    }

    @Test
    default void removeBySenderShouldRemoveSpecificEmail() throws Exception {
        enQueue(defaultMail()
            .name("name1")
            .sender(OTHER_AT_LOCAL)
            .build());
        enQueue(defaultMail()
            .name("name2")
            .sender(SENDER)
            .build());

        getManageableMailQueue().remove(ManageableMailQueue.Type.Sender, OTHER_AT_LOCAL.asString());

        assertThat(getManageableMailQueue().browse())
            .extracting(ManageableMailQueue.MailQueueItemView::getMail)
            .extracting(Mail::getName)
            .containsExactly("name2");
    }

    @Test
    default void removeByRecipientShouldRemoveSpecificEmail() throws Exception {
        enQueue(defaultMailNoRecipient()
            .name("name1")
            .recipient(RECIPIENT1)
            .build());
        enQueue(defaultMailNoRecipient()
            .name("name2")
            .recipient(RECIPIENT2)
            .build());

        getManageableMailQueue().remove(ManageableMailQueue.Type.Recipient, RECIPIENT2.asString());

        assertThat(getManageableMailQueue().browse())
            .extracting(ManageableMailQueue.MailQueueItemView::getMail)
            .extracting(Mail::getName)
            .containsExactly("name1");
    }

    @Test
    default void removeByRecipientShouldRemoveSpecificEmailWhenMultipleRecipients() throws Exception {
        enQueue(defaultMailNoRecipient()
            .name("name1")
            .recipients(RECIPIENT1, RECIPIENT2)
            .build());
        enQueue(defaultMailNoRecipient()
            .name("name2")
            .recipients(RECIPIENT1, RECIPIENT3)
            .build());

        getManageableMailQueue().remove(ManageableMailQueue.Type.Recipient, RECIPIENT2.asString());

        assertThat(getManageableMailQueue().browse())
            .extracting(ManageableMailQueue.MailQueueItemView::getMail)
            .extracting(Mail::getName)
            .containsExactly("name2");
    }

    @Test
    default void removeByNameShouldNotFailWhenQueueIsEmpty() throws Exception {
        getManageableMailQueue().remove(ManageableMailQueue.Type.Name, "NAME2");
    }

    @Test
    default void removeBySenderShouldNotFailWhenQueueIsEmpty() throws Exception {
        getManageableMailQueue().remove(ManageableMailQueue.Type.Sender, MailAddressFixture.OTHER_AT_LOCAL.asString());
    }

    @Test
    default void removeByRecipientShouldNotFailWhenQueueIsEmpty() throws Exception {
        getManageableMailQueue().remove(ManageableMailQueue.Type.Recipient, MailAddressFixture.OTHER_AT_LOCAL.asString());
    }

    @Test
    default void clearShouldNotFailWhenQueueIsEmpty() throws Exception {
        getManageableMailQueue().clear();
    }

    @Test
    default void flushShouldNotFailWhenQueueIsEmpty() throws Exception {
        getManageableMailQueue().flush();
    }

    @Test
    default void clearShouldRemoveAllElements() throws Exception {
        enQueue(defaultMail()
            .name("name1")
            .build());
        enQueue(defaultMail()
            .name("name2")
            .build());

        getManageableMailQueue().clear();

        assertThat(getManageableMailQueue().browse()).isEmpty();
    }

    @Test
    default void deletedElementsShouldNotBeDequeued() throws Exception {
        enQueue(defaultMail()
            .name("name1")
            .build());
        enQueue(defaultMail()
            .name("name2")
            .build());

        getManageableMailQueue().remove(ManageableMailQueue.Type.Name, "name1");

        assertThat(Flux.from(getManageableMailQueue().deQueue()).blockFirst().getMail().getName())
            .isEqualTo("name2");
    }

}
