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
import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.IntStream;

import org.apache.mailet.Mail;
import org.junit.jupiter.api.Test;

import com.github.fge.lambdas.Throwing;
import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableList;

public interface PriorityMailQueueContract {

    MailQueue getMailQueue();

    @Test
    default void priorityShouldReorderMailsWhenDequeing() throws Exception {
        getMailQueue().enQueue(defaultMail()
            .name("name3")
            .attribute(MailPrioritySupport.MAIL_PRIORITY, 3)
            .build());

        getMailQueue().enQueue(defaultMail()
            .name("name9")
            .attribute(MailPrioritySupport.MAIL_PRIORITY, 9)
            .build());

        getMailQueue().enQueue(defaultMail()
            .name("name1")
            .attribute(MailPrioritySupport.MAIL_PRIORITY, 1)
            .build());

        getMailQueue().enQueue(defaultMail()
            .name("name8")
            .attribute(MailPrioritySupport.MAIL_PRIORITY, 8)
            .build());

        getMailQueue().enQueue(defaultMail()
            .name("name6")
            .attribute(MailPrioritySupport.MAIL_PRIORITY, 6)
            .build());

        getMailQueue().enQueue(defaultMail()
            .name("name0")
            .attribute(MailPrioritySupport.MAIL_PRIORITY, 0)
            .build());

        getMailQueue().enQueue(defaultMail()
            .name("name7")
            .attribute(MailPrioritySupport.MAIL_PRIORITY, 7)
            .build());

        getMailQueue().enQueue(defaultMail()
            .name("name4")
            .attribute(MailPrioritySupport.MAIL_PRIORITY, 4)
            .build());

        getMailQueue().enQueue(defaultMail()
            .name("name2")
            .attribute(MailPrioritySupport.MAIL_PRIORITY, 2)
            .build());

        getMailQueue().enQueue(defaultMail()
            .name("name5")
            .attribute(MailPrioritySupport.MAIL_PRIORITY, 5)
            .build());

        ImmutableList<MailQueue.MailQueueItem> items = IntStream.range(1, 11).boxed()
            .map(Throwing.function(i -> {
                MailQueue.MailQueueItem item = getMailQueue().deQueue();
                item.done(true);
                return item;
            }))
            .collect(Guavate.toImmutableList());

        assertThat(items)
            .extracting(MailQueue.MailQueueItem::getMail)
            .extracting(Mail::getName)
            .containsExactly("name9", "name8", "name7", "name6", "name5", "name4", "name3", "name2", "name1", "name0");
    }

    @Test
    default void negativePriorityShouldDefaultToMinimumPriority() throws Exception {
        getMailQueue().enQueue(defaultMail()
            .name("name0")
            .attribute(MailPrioritySupport.MAIL_PRIORITY, -1)
            .build());
        getMailQueue().enQueue(defaultMail()
            .name("name1")
            .attribute(MailPrioritySupport.MAIL_PRIORITY, 1)
            .build());

        MailQueue.MailQueueItem mailQueueItem1 = getMailQueue().deQueue();
        mailQueueItem1.done(true);
        MailQueue.MailQueueItem mailQueueItem2 = getMailQueue().deQueue();
        mailQueueItem2.done(true);
        assertThat(mailQueueItem1.getMail().getName()).isEqualTo("name1");
        assertThat(mailQueueItem2.getMail().getName()).isEqualTo("name0");
    }

    @Test
    default void tooBigPriorityShouldDefaultToMaximalPriority() throws Exception {
        getMailQueue().enQueue(defaultMail()
            .name("name0")
            .attribute(MailPrioritySupport.MAIL_PRIORITY, 12)
            .build());
        getMailQueue().enQueue(defaultMail()
            .name("name1")
            .attribute(MailPrioritySupport.MAIL_PRIORITY, 8)
            .build());

        MailQueue.MailQueueItem mailQueueItem1 = getMailQueue().deQueue();
        mailQueueItem1.done(true);
        MailQueue.MailQueueItem mailQueueItem2 = getMailQueue().deQueue();
        mailQueueItem2.done(true);
        assertThat(mailQueueItem1.getMail().getName()).isEqualTo("name0");
        assertThat(mailQueueItem2.getMail().getName()).isEqualTo("name1");
    }

    @Test
    default void invalidPriorityShouldDefaultToNormalPriority() throws Exception {
        getMailQueue().enQueue(defaultMail()
            .name("name1")
            .attribute(MailPrioritySupport.MAIL_PRIORITY, "invalid")
            .build());
        getMailQueue().enQueue(defaultMail()
            .name("name2")
            .attribute(MailPrioritySupport.MAIL_PRIORITY, 4)
            .build());
        getMailQueue().enQueue(defaultMail()
            .name("name3")
            .attribute(MailPrioritySupport.MAIL_PRIORITY, 6)
            .build());

        MailQueue.MailQueueItem mailQueueItem1 = getMailQueue().deQueue();
        mailQueueItem1.done(true);
        MailQueue.MailQueueItem mailQueueItem2 = getMailQueue().deQueue();
        mailQueueItem2.done(true);
        MailQueue.MailQueueItem mailQueueItem3 = getMailQueue().deQueue();
        mailQueueItem3.done(true);
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
            .attribute(MailPrioritySupport.MAIL_PRIORITY, 4)
            .build());
        getMailQueue().enQueue(defaultMail()
            .name("name3")
            .attribute(MailPrioritySupport.MAIL_PRIORITY, 6)
            .build());

        MailQueue.MailQueueItem mailQueueItem1 = getMailQueue().deQueue();
        mailQueueItem1.done(true);
        MailQueue.MailQueueItem mailQueueItem2 = getMailQueue().deQueue();
        mailQueueItem2.done(true);
        MailQueue.MailQueueItem mailQueueItem3 = getMailQueue().deQueue();
        mailQueueItem3.done(true);
        assertThat(mailQueueItem1.getMail().getName()).isEqualTo("name3");
        assertThat(mailQueueItem2.getMail().getName()).isEqualTo("name1");
        assertThat(mailQueueItem3.getMail().getName()).isEqualTo("name2");
    }

    @Test
    default void priorityCanBeOmitted() throws Exception {
        getMailQueue().enQueue(defaultMail()
            .name("name1")
            .build());

        MailQueue.MailQueueItem mailQueueItem = getMailQueue().deQueue();
        assertThat(mailQueueItem.getMail().getName()).isEqualTo("name1");
    }
}
