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
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.MimeMessage;

import org.apache.james.core.MailAddress;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.mailet.Attribute;
import org.apache.mailet.Mail;
import org.apache.mailet.base.MailAddressFixture;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ManageableMailQueueContract extends MailQueueContract {

    default void awaitRemove() {

    }

    ManageableMailQueue getManageableMailQueue();

    @Test
    default void getSizeShouldReturnZeroWhenNoMessage() {
        Awaitility.await().untilAsserted(() -> assertThat(getManageableMailQueue().getSize()).isEqualTo(0L));
    }

    @Test
    default void getSizeShouldReturnMessageCount() throws Exception {
        enQueue(defaultMail().name("name").build());

        Awaitility.await().untilAsserted(() -> assertThat(getManageableMailQueue().getSize()).isEqualTo(1L));
    }

    @Test
    default void getSizeShouldReturnMessageCountWhenSeveralMails() throws Exception {
        enQueue(defaultMail().name("1").build());
        enQueue(defaultMail().name("2").build());

        Awaitility.await().untilAsserted(() -> assertThat(getManageableMailQueue().getSize()).isEqualTo(2L));
    }

    @Timeout(30)
    @Test
    default void dequeueShouldDecreaseQueueSize() throws Exception {
        enQueue(defaultMail().name("name").build());

        Flux.from(getManageableMailQueue().deQueue())
            .concatMap(item -> Mono.fromRunnable(Throwing.runnable(() -> item.done(MailQueue.MailQueueItem.CompletionStatus.SUCCESS))).subscribeOn(SCHEDULER))
            .subscribeOn(SCHEDULER)
            .subscribe();

        Awaitility.await().untilAsserted(() -> assertThat(getManageableMailQueue().getSize()).isEqualTo(0L));
    }

    @Test
    default void noAckShouldNotDecreaseSize() throws Exception {
        enQueue(defaultMail().name("name").build());

        Flux.from(getManageableMailQueue().deQueue())
            .doOnNext(Throwing.consumer(item -> item.done(MailQueue.MailQueueItem.CompletionStatus.RETRY)))
            .blockFirst();

        Awaitility.await().untilAsserted(() -> assertThat(getManageableMailQueue().getSize()).isEqualTo(1L));
    }

    @Test
    default void processedMailsShouldNotDecreaseSize() throws Exception {
        enQueue(defaultMail().name("name").build());

        Flux.from(getManageableMailQueue().deQueue());

        assertThat(getManageableMailQueue().getSize()).isEqualTo(1L);
    }

    @Test
    default void browseShouldReturnEmptyByDefault() throws Exception {
        ManageableMailQueue.MailQueueIterator items = getManageableMailQueue().browse();

        assertThat(items)
            .toIterable()
            .isEmpty();
    }

    @Test
    default void browseShouldReturnSingleElement() throws Exception {
        enQueue(defaultMail()
            .name("name")
            .build());

        ManageableMailQueue.MailQueueIterator items = getManageableMailQueue().browse();

        assertThat(items)
            .toIterable()
            .extracting(ManageableMailQueue.MailQueueItemView::getMail)
            .extracting(Mail::getName)
            .containsExactly("name");
    }

    @Timeout(30)
    @Test
    default void browseShouldReturnEmptyWhenSingleDequeueMessage() throws Exception {
        var mail = defaultMail()
                .name("name")
                .build();
        enQueue(mail);

        Flux.from(getManageableMailQueue().deQueue())
            .flatMap(item -> Mono.fromRunnable(Throwing.runnable(() -> item.done(MailQueue.MailQueueItem.CompletionStatus.SUCCESS)))
                .subscribeOn(SCHEDULER)
                .thenReturn(item))
                .blockFirst();

        ManageableMailQueue.MailQueueIterator items = getManageableMailQueue().browse();

        assertThat(items.hasNext()).isFalse();
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
            .toIterable()
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

        awaitRemove();

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

        awaitRemove();

        assertThat(getManageableMailQueue().browse())
            .toIterable()
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

        awaitRemove();

        assertThat(getManageableMailQueue().browse())
            .toIterable()
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

        awaitRemove();

        assertThat(getManageableMailQueue().browse())
            .toIterable()
            .extracting(ManageableMailQueue.MailQueueItemView::getMail)
            .extracting(Mail::getName)
            .containsExactly("name1");
    }

    static Stream<Arguments> removeByRecipientShouldRemoveSpecificEmailWhenMultipleRecipients() throws AddressException {
        return Stream.of(
            Arguments.of(List.of(RECIPIENT1, RECIPIENT2), RECIPIENT2),
            Arguments.of(List.of(RECIPIENT1, RECIPIENT2), RECIPIENT1),
            Arguments.of(List.of(RECIPIENT1, RECIPIENT2, RECIPIENT3), RECIPIENT2),
            Arguments.of(List.of(RECIPIENT1, new MailAddress(RECIPIENT1.asString() + ".local"), RECIPIENT2), RECIPIENT1),
            Arguments.of(List.of(RECIPIENT1, RECIPIENT2, new MailAddress(RECIPIENT1.asString() + ".local")), RECIPIENT1),
            Arguments.of(List.of(new MailAddress(RECIPIENT1.asString() + ".local"), RECIPIENT1, RECIPIENT2), RECIPIENT1),
            Arguments.of(List.of(new MailAddress(RECIPIENT1.asString() + ".local"), RECIPIENT2, RECIPIENT1), RECIPIENT1)
        );
    }

    @ParameterizedTest
    @MethodSource
    default void removeByRecipientShouldRemoveSpecificEmailWhenMultipleRecipients(List<MailAddress> recipients, MailAddress toRemove) throws Exception {
        enQueue(defaultMailNoRecipient()
            .name("name1")
            .recipients(recipients)
            .build());
        enQueue(defaultMailNoRecipient()
            .name("name2")
            .recipients(recipients.stream().filter(recipient -> !recipient.equals(toRemove)).collect(Collectors.toList()))
            .build());

        getManageableMailQueue().remove(ManageableMailQueue.Type.Recipient, toRemove.asString());

        awaitRemove();

        assertThat(getManageableMailQueue().browse())
            .toIterable()
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

        assertThat(getManageableMailQueue().browse())
            .toIterable()
            .isEmpty();
    }

    @Test
    default void deletedElementsShouldNotBeDequeued() throws Exception {
        enQueue(defaultMail()
            .name("name1")
            .build());
        enQueue(defaultMail()
            .name("name2")
            .build());

        Awaitility.await().untilAsserted(() -> assertThat(getManageableMailQueue().getSize()).isEqualTo(2L));

        getManageableMailQueue().remove(ManageableMailQueue.Type.Name, "name1");

        awaitRemove();

        assertThat(Flux.from(getManageableMailQueue().deQueue()).blockFirst().getMail().getName())
            .isEqualTo("name2");
    }

    @Test
    default void removeShouldNotDeleteFutureEmails() throws MessagingException {
        enQueue(defaultMail()
            .name("name1")
            .build());

        Awaitility.await().untilAsserted(() -> assertThat(getManageableMailQueue().getSize()).isEqualTo(1L));

        getManageableMailQueue().remove(ManageableMailQueue.Type.Recipient, MailAddressFixture.RECIPIENT1.asString());

        awaitRemove();

        enQueue(defaultMail()
            .name("name2")
            .build());

        assertThat(Flux.from(getManageableMailQueue().deQueue()).blockFirst().getMail().getName())
            .isEqualTo("name2");
    }

    @Test
    default void removeShouldNotDeleteFutureEmailsFromBrowse() throws MessagingException {
        enQueue(defaultMail()
            .name("name1")
            .build());

        Awaitility.await().untilAsserted(() -> assertThat(getManageableMailQueue().getSize()).isEqualTo(1L));

        getManageableMailQueue().remove(ManageableMailQueue.Type.Recipient, MailAddressFixture.RECIPIENT1.asString());

        awaitRemove();

        enQueue(defaultMail()
            .name("name2")
            .build());

        assertThat(getManageableMailQueue().browse()).toIterable()
            .extracting(mail -> mail.getMail().getName())
            .containsExactly("name2");
    }

}
