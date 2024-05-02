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
import static org.apache.mailet.base.MailAddressFixture.RECIPIENT1;
import static org.apache.mailet.base.MailAddressFixture.RECIPIENT2;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.james.junit.ExecutorExtension;
import org.apache.mailet.Mail;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import reactor.core.publisher.Flux;

@ExtendWith(ExecutorExtension.class)
public interface DelayedManageableMailQueueContract extends DelayedMailQueueContract, ManageableMailQueueContract {

    @Override
    ManageableMailQueue getManageableMailQueue();

    default void enQueue(Mail mail, long delay, TimeUnit unit) throws MailQueue.MailQueueException {
        getManageableMailQueue().enQueue(mail, delay, unit);
    }


    @Test
    default void delayedMessagesShouldBeBrowesable() throws Exception {
        enQueue(defaultMail()
                .name("name1")
                .build(),
            30L,
            TimeUnit.SECONDS);

        Awaitility.await().untilAsserted(() -> assertThat(getManageableMailQueue().getSize()).isEqualTo(1L));

        assertThat(getManageableMailQueue().browse()).toIterable()
            .extracting(mail -> mail.getMail().getName())
            .containsExactly("name1");
    }

    @Test
    default void delayedMessagesShouldBeCleared() throws Exception {
        enQueue(defaultMail()
                .name("name1")
                .build(),
            30L,
            TimeUnit.SECONDS);

        Awaitility.await().untilAsserted(() -> assertThat(getManageableMailQueue().getSize()).isEqualTo(1L));

        getManageableMailQueue().clear();

        assertThat(getManageableMailQueue().getSize()).isEqualTo(0L);
    }

    @Test
    default void delayedEmailsShouldBeDeleted() throws Exception {
        enQueue(defaultMail()
                .name("abc")
                .build(),
            5L,
            TimeUnit.SECONDS);
        // The queue being FIFO a second email can serve as a wait condition
        enQueue(defaultMail()
                .name("def")
                .build(),
            5L,
            TimeUnit.SECONDS);

        getManageableMailQueue().remove(ManageableMailQueue.Type.Name, "abc");

        ArrayList<String> names = new ArrayList<>();
        Flux.from(getManageableMailQueue().deQueue())
            .subscribeOn(SCHEDULER)
            .subscribe(item -> names.add(item.getMail().getName()));

       Awaitility.await()
           .untilAsserted(() -> assertThat(names).contains("def"));
        assertThat(names).containsExactly("def");
    }

    @Test
    default void deletedDelayedMessagesShouldNotBeBrowseable() throws Exception {
        enQueue(defaultMail()
                .name("name1")
                .build(),
                30L,
                TimeUnit.SECONDS);
        enQueue(defaultMailNoRecipient()
                .name("name2")
                .recipient(RECIPIENT1)
                .build());
        enQueue(defaultMailNoRecipient()
                .name("name3")
                .recipient(RECIPIENT2)
                .build());

        getManageableMailQueue().remove(ManageableMailQueue.Type.Recipient, RECIPIENT2.asString());

        awaitRemove();

        assertThat(getManageableMailQueue().browse()).toIterable()
                .extracting(mail -> mail.getMail().getName())
                .containsExactly("name2");
    }

    @Test
    default void delayedEmailsShouldBeDeletedWhenMixedWithOtherEmails() throws Exception {
        enQueue(defaultMail()
                .name("abc")
                .build(),
            5L,
            TimeUnit.SECONDS);

        getManageableMailQueue().remove(ManageableMailQueue.Type.Name, "abc");

        // The newer email
        enQueue(defaultMail()
                .name("def")
                .build());
        // The queue being FIFO a third email can serve as a wait condition
        enQueue(defaultMail()
                .name("ghi")
                .build(),
            5L,
            TimeUnit.SECONDS);

        ArrayList<String> names = new ArrayList<>();
        Flux.from(getManageableMailQueue().deQueue())
            .subscribeOn(SCHEDULER)
            .subscribe(item -> names.add(item.getMail().getName()));

       Awaitility.await()
           .untilAsserted(() -> assertThat(names).contains("ghi"));
        assertThat(names).containsExactly("def", "ghi");
    }

    @Test
    default void flushShouldRemoveDelaysWhenImmediateMessageFirst() throws Exception {
        enQueue(defaultMail()
                .name("name1")
                .build());
        enQueue(defaultMail()
                .name("name2")
                .build(),
            30L,
            TimeUnit.HOURS);

        Awaitility.await().untilAsserted(() -> assertThat(getManageableMailQueue().getSize()).isEqualTo(2L));

        getManageableMailQueue().flush();

        List<MailQueue.MailQueueItem> items = Flux.from(getManageableMailQueue().deQueue()).bufferTimeout(5, Duration.ofSeconds(5)).blockFirst();
        assertThat(items.stream().map(x -> x.getMail().getName()))
            .containsExactly("name1", "name2");
    }

    @Test
    default void flushShouldRemoveDelays() throws Exception {
        enQueue(defaultMail()
            .name("name1")
            .build(),
            30L,
            TimeUnit.HOURS);

        Awaitility.await().untilAsserted(() -> assertThat(getManageableMailQueue().getSize()).isEqualTo(1L));

        getManageableMailQueue().flush();

        assertThat(Flux.from(getManageableMailQueue().deQueue()).blockFirst(Duration.ofSeconds(5)).getMail().getName())
            .isEqualTo("name1");
    }

    @Test
    default void flushShouldPreserveBrowseOrder() throws Exception {
        enQueue(defaultMail()
            .name("name1")
            .build());

        enQueue(defaultMail()
            .name("name2")
            .build(),
            30L,
            TimeUnit.SECONDS);

        enQueue(defaultMail()
            .name("name3")
            .build(),
            2L,
            TimeUnit.SECONDS);

        getManageableMailQueue().flush();

        assertThat(getManageableMailQueue().browse())
            .toIterable()
            .extracting(ManageableMailQueue.MailQueueItemView::getMail)
            .extracting(Mail::getName)
            .containsExactly("name1", "name2", "name3");
    }

}
