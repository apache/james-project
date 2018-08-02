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

import org.apache.mailet.Mail;
import org.apache.mailet.base.MailAddressFixture;
import org.junit.jupiter.api.Test;

import com.google.common.collect.Iterators;

public interface ManageableMailQueueContract extends MailQueueContract {

    ManageableMailQueue getManageableMailQueue();

    @Test
    default void getSizeShouldReturnZeroWhenNoMessage() throws Exception {
        long size = getManageableMailQueue().getSize();

        assertThat(size).isEqualTo(0L);
    }

    @Test
    default void getSizeShouldReturnMessageCount() throws Exception {
        getManageableMailQueue().enQueue(defaultMail().build());

        long size = getManageableMailQueue().getSize();

        assertThat(size).isEqualTo(1L);
    }

    @Test
    default void getSizeShouldReturnMessageCountWhenSeveralMails() throws Exception {
        getManageableMailQueue().enQueue(defaultMail().build());
        getManageableMailQueue().enQueue(defaultMail().build());

        long size = getManageableMailQueue().getSize();

        assertThat(size).isEqualTo(2L);
    }

    @Test
    default void dequeueShouldDecreaseQueueSize() throws Exception {
        getManageableMailQueue().enQueue(defaultMail().build());

        getManageableMailQueue().deQueue().done(true);

        long size = getManageableMailQueue().getSize();

        assertThat(size).isEqualTo(0L);
    }

    @Test
    default void noAckShouldNotDecreaseSize() throws Exception {
        getManageableMailQueue().enQueue(defaultMail().build());

        getManageableMailQueue().deQueue().done(false);

        long size = getManageableMailQueue().getSize();

        assertThat(size).isEqualTo(1L);
    }

    @Test
    default void processedMailsShouldNotDecreaseSize() throws Exception {
        getManageableMailQueue().enQueue(defaultMail().build());

        getManageableMailQueue().deQueue();

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
        getManageableMailQueue().enQueue(defaultMail()
            .name("name")
            .build());

        ManageableMailQueue.MailQueueIterator items = getManageableMailQueue().browse();

        assertThat(items).extracting(ManageableMailQueue.MailQueueItemView::getMail)
            .extracting(Mail::getName)
            .containsExactly("name");
    }

    @Test
    default void browseShouldReturnElementsInOrder() throws Exception {
        getManageableMailQueue().enQueue(defaultMail()
            .name("name1")
            .build());
        getManageableMailQueue().enQueue(defaultMail()
            .name("name2")
            .build());
        getManageableMailQueue().enQueue(defaultMail()
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
        getManageableMailQueue().enQueue(defaultMail()
            .name("name1")
            .build());
        getManageableMailQueue().enQueue(defaultMail()
            .name("name2")
            .build());
        getManageableMailQueue().enQueue(defaultMail()
            .name("name3")
            .build());

        ManageableMailQueue.MailQueueIterator items = getManageableMailQueue().browse();

        assertThatCode(() -> getManageableMailQueue().deQueue()).doesNotThrowAnyException();

    }

    @Test
    default void browseShouldNotFailWhenConcurrentDequeue() throws Exception {
        getManageableMailQueue().enQueue(defaultMail()
            .name("name1")
            .build());
        getManageableMailQueue().enQueue(defaultMail()
            .name("name2")
            .build());
        getManageableMailQueue().enQueue(defaultMail()
            .name("name3")
            .build());

        ManageableMailQueue.MailQueueIterator items = getManageableMailQueue().browse();

        getManageableMailQueue().deQueue();

        assertThatCode(() ->  Iterators.consumingIterator(items)).doesNotThrowAnyException();
    }

    @Test
    default void dequeueShouldNotFailWhenBrowsingIterating() throws Exception {
        getManageableMailQueue().enQueue(defaultMail()
            .name("name1")
            .build());
        getManageableMailQueue().enQueue(defaultMail()
            .name("name2")
            .build());
        getManageableMailQueue().enQueue(defaultMail()
            .name("name3")
            .build());

        ManageableMailQueue.MailQueueIterator items = getManageableMailQueue().browse();
        ManageableMailQueue.MailQueueItemView firstItem = items.next();

        assertThatCode(() -> getManageableMailQueue().deQueue()).doesNotThrowAnyException();

    }

    @Test
    default void browseShouldNotFailWhenConcurrentDequeueWhenIterating() throws Exception {
        getManageableMailQueue().enQueue(defaultMail()
            .name("name1")
            .build());
        getManageableMailQueue().enQueue(defaultMail()
            .name("name2")
            .build());
        getManageableMailQueue().enQueue(defaultMail()
            .name("name3")
            .build());

        ManageableMailQueue.MailQueueIterator items = getManageableMailQueue().browse();
        ManageableMailQueue.MailQueueItemView firstItem = items.next();

        getManageableMailQueue().deQueue();

        assertThatCode(() ->  Iterators.consumingIterator(items)).doesNotThrowAnyException();
    }

    @Test
    default void enqueueShouldNotFailWhenBrowsing() throws Exception {
        getManageableMailQueue().enQueue(defaultMail()
            .name("name1")
            .build());
        getManageableMailQueue().enQueue(defaultMail()
            .name("name2")
            .build());
        getManageableMailQueue().enQueue(defaultMail()
            .name("name3")
            .build());

        ManageableMailQueue.MailQueueIterator items = getManageableMailQueue().browse();

        assertThatCode(() -> getManageableMailQueue().enQueue(defaultMail()
            .name("name4")
            .build())).doesNotThrowAnyException();

    }

    @Test
    default void browseShouldNotFailWhenConcurrentEnqueue() throws Exception {
        getManageableMailQueue().enQueue(defaultMail()
            .name("name1")
            .build());
        getManageableMailQueue().enQueue(defaultMail()
            .name("name2")
            .build());
        getManageableMailQueue().enQueue(defaultMail()
            .name("name3")
            .build());

        ManageableMailQueue.MailQueueIterator items = getManageableMailQueue().browse();

        getManageableMailQueue().enQueue(defaultMail()
            .name("name4")
            .build());

        assertThatCode(() ->  Iterators.consumingIterator(items)).doesNotThrowAnyException();
    }

    @Test
    default void enqueueShouldNotFailWhenBrowsingIterating() throws Exception {
        getManageableMailQueue().enQueue(defaultMail()
            .name("name1")
            .build());
        getManageableMailQueue().enQueue(defaultMail()
            .name("name2")
            .build());
        getManageableMailQueue().enQueue(defaultMail()
            .name("name3")
            .build());

        ManageableMailQueue.MailQueueIterator items = getManageableMailQueue().browse();
        ManageableMailQueue.MailQueueItemView firstItem = items.next();

        assertThatCode(() ->
            getManageableMailQueue().enQueue(defaultMail()
                .name("name4")
                .build()))
            .doesNotThrowAnyException();

    }

    @Test
    default void browseShouldNotFailWhenConcurrentEnqueueWhenIterating() throws Exception {
        getManageableMailQueue().enQueue(defaultMail()
            .name("name1")
            .build());
        getManageableMailQueue().enQueue(defaultMail()
            .name("name2")
            .build());
        getManageableMailQueue().enQueue(defaultMail()
            .name("name3")
            .build());

        ManageableMailQueue.MailQueueIterator items = getManageableMailQueue().browse();
        ManageableMailQueue.MailQueueItemView firstItem = items.next();

        getManageableMailQueue().enQueue(defaultMail()
            .name("name2")
            .build());

        assertThatCode(() ->  Iterators.consumingIterator(items)).doesNotThrowAnyException();
    }

    @Test
    default void clearShouldNotFailWhenBrowsingIterating() throws Exception {
        getManageableMailQueue().enQueue(defaultMail()
            .name("name1")
            .build());
        getManageableMailQueue().enQueue(defaultMail()
            .name("name2")
            .build());
        getManageableMailQueue().enQueue(defaultMail()
            .name("name3")
            .build());

        ManageableMailQueue.MailQueueIterator items = getManageableMailQueue().browse();
        ManageableMailQueue.MailQueueItemView firstItem = items.next();

        assertThatCode(() -> getManageableMailQueue().clear())
            .doesNotThrowAnyException();

    }

    @Test
    default void browseShouldNotFailWhenConcurrentClearWhenIterating() throws Exception {
        getManageableMailQueue().enQueue(defaultMail()
            .name("name1")
            .build());
        getManageableMailQueue().enQueue(defaultMail()
            .name("name2")
            .build());
        getManageableMailQueue().enQueue(defaultMail()
            .name("name3")
            .build());

        ManageableMailQueue.MailQueueIterator items = getManageableMailQueue().browse();
        ManageableMailQueue.MailQueueItemView firstItem = items.next();

        getManageableMailQueue().clear();

        assertThatCode(() ->  Iterators.consumingIterator(items)).doesNotThrowAnyException();
    }

    @Test
    default void flushShouldNotFailWhenBrowsingIterating() throws Exception {
        getManageableMailQueue().enQueue(defaultMail()
            .name("name1")
            .build());
        getManageableMailQueue().enQueue(defaultMail()
            .name("name2")
            .build());
        getManageableMailQueue().enQueue(defaultMail()
            .name("name3")
            .build());

        ManageableMailQueue.MailQueueIterator items = getManageableMailQueue().browse();
        ManageableMailQueue.MailQueueItemView firstItem = items.next();

        assertThatCode(() -> getManageableMailQueue().flush())
            .doesNotThrowAnyException();

    }

    @Test
    default void browseShouldNotFailWhenConcurrentFlushWhenIterating() throws Exception {
        getManageableMailQueue().enQueue(defaultMail()
            .name("name1")
            .build());
        getManageableMailQueue().enQueue(defaultMail()
            .name("name2")
            .build());
        getManageableMailQueue().enQueue(defaultMail()
            .name("name3")
            .build());

        ManageableMailQueue.MailQueueIterator items = getManageableMailQueue().browse();
        ManageableMailQueue.MailQueueItemView firstItem = items.next();

        getManageableMailQueue().flush();

        assertThatCode(() ->  Iterators.consumingIterator(items)).doesNotThrowAnyException();
    }

    @Test
    default void removeShouldNotFailWhenBrowsingIterating() throws Exception {
        getManageableMailQueue().enQueue(defaultMail()
            .name("name1")
            .build());
        getManageableMailQueue().enQueue(defaultMail()
            .name("name2")
            .build());
        getManageableMailQueue().enQueue(defaultMail()
            .name("name3")
            .build());

        ManageableMailQueue.MailQueueIterator items = getManageableMailQueue().browse();
        ManageableMailQueue.MailQueueItemView firstItem = items.next();

        assertThatCode(() -> getManageableMailQueue().flush())
            .doesNotThrowAnyException();

    }

    @Test
    default void browseShouldNotFailWhenConcurrentRemoveWhenIterating() throws Exception {
        getManageableMailQueue().enQueue(defaultMail()
            .name("name1")
            .build());
        getManageableMailQueue().enQueue(defaultMail()
            .name("name2")
            .build());
        getManageableMailQueue().enQueue(defaultMail()
            .name("name3")
            .build());

        ManageableMailQueue.MailQueueIterator items = getManageableMailQueue().browse();
        ManageableMailQueue.MailQueueItemView firstItem = items.next();

        getManageableMailQueue().remove(ManageableMailQueue.Type.Name, "name2");

        assertThatCode(() ->  Iterators.consumingIterator(items)).doesNotThrowAnyException();
    }

    @Test
    default void browsingShouldNotAffectDequeue() throws Exception {
        getManageableMailQueue().enQueue(defaultMail()
            .name("name1")
            .build());
        getManageableMailQueue().enQueue(defaultMail()
            .name("name2")
            .build());
        getManageableMailQueue().enQueue(defaultMail()
            .name("name3")
            .build());

        ManageableMailQueue.MailQueueIterator items = getManageableMailQueue().browse();
        items.next();

        MailQueue.MailQueueItem mailQueueItem = getManageableMailQueue().deQueue();

        assertThat(mailQueueItem.getMail().getName()).isEqualTo("name1");
    }

    @Test
    default void removeByNameShouldRemoveSpecificEmail() throws Exception {
        getManageableMailQueue().enQueue(defaultMail()
            .name("name1")
            .build());
        getManageableMailQueue().enQueue(defaultMail()
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
        getManageableMailQueue().enQueue(defaultMail()
            .sender(OTHER_AT_LOCAL)
            .name("name1")
            .build());
        getManageableMailQueue().enQueue(defaultMail()
            .sender(SENDER)
            .name("name2")
            .build());

        getManageableMailQueue().remove(ManageableMailQueue.Type.Sender, OTHER_AT_LOCAL.asString());

        assertThat(getManageableMailQueue().browse())
            .extracting(ManageableMailQueue.MailQueueItemView::getMail)
            .extracting(Mail::getName)
            .containsExactly("name2");
    }

    @Test
    default void removeByRecipientShouldRemoveSpecificEmail() throws Exception {
        getManageableMailQueue().enQueue(defaultMailNoRecipient()
            .name("name1")
            .recipient(RECIPIENT1)
            .build());
        getManageableMailQueue().enQueue(defaultMailNoRecipient()
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
        getManageableMailQueue().enQueue(defaultMailNoRecipient()
            .name("name1")
            .recipients(RECIPIENT1, RECIPIENT2)
            .build());
        getManageableMailQueue().enQueue(defaultMailNoRecipient()
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
        getManageableMailQueue().enQueue(defaultMail()
            .name("name1")
            .build());
        getManageableMailQueue().enQueue(defaultMail()
            .name("name2")
            .build());

        getManageableMailQueue().clear();

        assertThat(getManageableMailQueue().browse()).isEmpty();
    }

}
