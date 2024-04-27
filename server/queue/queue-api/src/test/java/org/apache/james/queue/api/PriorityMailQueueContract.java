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

import static org.apache.james.queue.api.MailQueueContract.SCHEDULER;
import static org.apache.james.queue.api.Mails.defaultMail;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Iterator;

import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PriorityMailQueueContract {

    MailQueue getMailQueue();

    @Test
    default void priorityShouldReorderMailsWhenDequeing() throws Exception {
        getMailQueue().enQueue(defaultMail()
            .name("name3")
            .attribute(mailPriority(3))
            .build());

        getMailQueue().enQueue(defaultMail()
            .name("name9")
            .attribute(mailPriority(9))
            .build());

        getMailQueue().enQueue(defaultMail()
            .name("name1")
            .attribute(mailPriority(1))
            .build());

        getMailQueue().enQueue(defaultMail()
            .name("name8")
            .attribute(mailPriority(8))
            .build());

        getMailQueue().enQueue(defaultMail()
            .name("name6")
            .attribute(mailPriority(6))
            .build());

        getMailQueue().enQueue(defaultMail()
            .name("name0")
            .attribute(mailPriority(0))
            .build());

        getMailQueue().enQueue(defaultMail()
            .name("name7")
            .attribute(mailPriority(7))
            .build());

        getMailQueue().enQueue(defaultMail()
            .name("name4")
            .attribute(mailPriority(4))
            .build());

        getMailQueue().enQueue(defaultMail()
            .name("name2")
            .attribute(mailPriority(2))
            .build());

        getMailQueue().enQueue(defaultMail()
            .name("name5")
            .attribute(mailPriority(5))
            .build());

        Iterable<MailQueue.MailQueueItem> items = Flux.from(getMailQueue().deQueue()).take(10)
            .flatMap(item -> {
                try {
                    item.done(MailQueue.MailQueueItem.CompletionStatus.SUCCESS);
                    return Mono.just(item);
                } catch (MailQueue.MailQueueException e) {
                    return Mono.error(e);
                }
            })
            .toIterable();

        assertThat(items)
            .extracting(MailQueue.MailQueueItem::getMail)
            .extracting(Mail::getName)
            .containsExactly("name9", "name8", "name7", "name6", "name5", "name4", "name3", "name2", "name1", "name0");
    }

    @Test
    default void negativePriorityShouldDefaultToMinimumPriority() throws Exception {
        getMailQueue().enQueue(defaultMail()
            .name("name0")
            .attribute(mailPriority(-1))
            .build());
        getMailQueue().enQueue(defaultMail()
            .name("name1")
            .attribute(mailPriority(1))
            .build());

        Iterator<MailQueue.MailQueueItem> mailQueueItems = Flux.from(getMailQueue().deQueue()).subscribeOn(SCHEDULER).toIterable().iterator();
        MailQueue.MailQueueItem mailQueueItem1 = mailQueueItems.next();
        mailQueueItem1.done(MailQueue.MailQueueItem.CompletionStatus.SUCCESS);
        MailQueue.MailQueueItem mailQueueItem2 = mailQueueItems.next();
        mailQueueItem2.done(MailQueue.MailQueueItem.CompletionStatus.SUCCESS);
        assertThat(mailQueueItem1.getMail().getName()).isEqualTo("name1");
        assertThat(mailQueueItem2.getMail().getName()).isEqualTo("name0");
    }

    @Test
    default void tooBigPriorityShouldDefaultToMaximalPriority() throws Exception {
        getMailQueue().enQueue(defaultMail()
            .name("name0")
            .attribute(mailPriority(12))
            .build());
        getMailQueue().enQueue(defaultMail()
            .name("name1")
            .attribute(mailPriority(8))
            .build());

        Iterator<MailQueue.MailQueueItem> mailQueueItems = Flux.from(getMailQueue().deQueue()).subscribeOn(SCHEDULER).toIterable().iterator();
        MailQueue.MailQueueItem mailQueueItem1 = mailQueueItems.next();
        mailQueueItem1.done(MailQueue.MailQueueItem.CompletionStatus.SUCCESS);
        MailQueue.MailQueueItem mailQueueItem2 = mailQueueItems.next();
        mailQueueItem2.done(MailQueue.MailQueueItem.CompletionStatus.SUCCESS);
        assertThat(mailQueueItem1.getMail().getName()).isEqualTo("name0");
        assertThat(mailQueueItem2.getMail().getName()).isEqualTo("name1");
    }

    @Test
    default void invalidPriorityShouldDefaultToNormalPriority() throws Exception {
        getMailQueue().enQueue(defaultMail()
            .name("name1")
            .attribute(mailPriority("invalid"))
            .build());
        getMailQueue().enQueue(defaultMail()
            .name("name2")
            .attribute(mailPriority(4))
            .build());
        getMailQueue().enQueue(defaultMail()
            .name("name3")
            .attribute(mailPriority(6))
            .build());

        Iterator<MailQueue.MailQueueItem> mailQueueItems = Flux.from(getMailQueue().deQueue()).subscribeOn(SCHEDULER).toIterable().iterator();
        MailQueue.MailQueueItem mailQueueItem1 = mailQueueItems.next();
        mailQueueItem1.done(MailQueue.MailQueueItem.CompletionStatus.SUCCESS);
        MailQueue.MailQueueItem mailQueueItem2 = mailQueueItems.next();
        mailQueueItem2.done(MailQueue.MailQueueItem.CompletionStatus.SUCCESS);
        MailQueue.MailQueueItem mailQueueItem3 = mailQueueItems.next();
        mailQueueItem3.done(MailQueue.MailQueueItem.CompletionStatus.SUCCESS);
        assertThat(mailQueueItem1.getMail().getName()).isEqualTo("name3");
        assertThat(mailQueueItem2.getMail().getName()).isEqualTo("name1");
        assertThat(mailQueueItem3.getMail().getName()).isEqualTo("name2");
    }

    @Test
    default void defaultPriorityShouldDefaultToNormalPriority() throws Exception {
        getMailQueue().enQueue(defaultMail()
            .name("name1")
            .build());
        getMailQueue().enQueue(defaultMail()
            .name("name2")
            .attribute(mailPriority(4))
            .build());
        getMailQueue().enQueue(defaultMail()
            .name("name3")
            .attribute(mailPriority(6))
            .build());

        Iterator<MailQueue.MailQueueItem> mailQueueItems = Flux.from(getMailQueue().deQueue()).subscribeOn(SCHEDULER).toIterable().iterator();
        MailQueue.MailQueueItem mailQueueItem1 = mailQueueItems.next();
        mailQueueItem1.done(MailQueue.MailQueueItem.CompletionStatus.SUCCESS);
        MailQueue.MailQueueItem mailQueueItem2 = mailQueueItems.next();
        mailQueueItem2.done(MailQueue.MailQueueItem.CompletionStatus.SUCCESS);
        MailQueue.MailQueueItem mailQueueItem3 = mailQueueItems.next();
        mailQueueItem3.done(MailQueue.MailQueueItem.CompletionStatus.SUCCESS);
        assertThat(mailQueueItem1.getMail().getName()).isEqualTo("name3");
        assertThat(mailQueueItem2.getMail().getName()).isEqualTo("name1");
        assertThat(mailQueueItem3.getMail().getName()).isEqualTo("name2");
    }

    @Test
    default void priorityCanBeOmitted() throws Exception {
        getMailQueue().enQueue(defaultMail()
            .name("name1")
            .build());

        MailQueue.MailQueueItem mailQueueItem = Flux.from(getMailQueue().deQueue()).blockFirst();
        assertThat(mailQueueItem.getMail().getName()).isEqualTo("name1");
    }

    default Attribute mailPriority(Object priority) {
        return new Attribute(MailPrioritySupport.MAIL_PRIORITY, AttributeValue.ofAny(priority));
    }
}
