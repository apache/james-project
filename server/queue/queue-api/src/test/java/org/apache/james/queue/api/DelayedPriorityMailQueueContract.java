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
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;

public interface DelayedPriorityMailQueueContract extends DelayedMailQueueContract, PriorityMailQueueContract {

    @Override
    MailQueue getMailQueue();

    @Test
    default void delayedHighPriorityMailShouldBeDeQueuedBeforeLowPriorityNonDelayedMailAfterDelayExpiracy() throws Exception {
        getMailQueue().enQueue(defaultMail()
            .name("name1")
            .attribute(MailPrioritySupport.LOW_PRIORITY_ATTRIBUTE)
            .build());

        int delay = 1;
        TimeUnit unit = TimeUnit.SECONDS;
        getMailQueue().enQueue(defaultMail()
            .name("name2")
            .attribute(MailPrioritySupport.HIGH_PRIORITY_ATTRIBUTE)
            .build(),
            delay,
            unit);

        Thread.sleep(unit.toMillis(2 * delay));

        Iterator<MailQueue.MailQueueItem> mailQueueItems = Flux.from(getMailQueue().deQueue()).subscribeOn(SCHEDULER).toIterable().iterator();
        MailQueue.MailQueueItem item1 = mailQueueItems.next();
        item1.done(MailQueue.MailQueueItem.CompletionStatus.SUCCESS);
        MailQueue.MailQueueItem item2 = mailQueueItems.next();
        item2.done(MailQueue.MailQueueItem.CompletionStatus.SUCCESS);

        assertThat(item1.getMail().getName()).isEqualTo("name2");
        assertThat(item2.getMail().getName()).isEqualTo("name1");
    }

    @Test
    default void delayedHighPriorityMailShouldBeDeQueuedAfterNonDelayedMail() throws Exception {
        getMailQueue().enQueue(defaultMail()
            .name("name1")
            .attribute(MailPrioritySupport.LOW_PRIORITY_ATTRIBUTE)
            .build());

        int delay = 1;
        TimeUnit unit = TimeUnit.SECONDS;
        getMailQueue().enQueue(defaultMail()
            .name("name2")
            .attribute(MailPrioritySupport.HIGH_PRIORITY_ATTRIBUTE)
            .build(),
            delay,
            unit);

        Iterator<MailQueue.MailQueueItem> mailQueueItems = Flux.from(getMailQueue().deQueue()).subscribeOn(SCHEDULER).toIterable().iterator();
        MailQueue.MailQueueItem item1 = mailQueueItems.next();
        item1.done(MailQueue.MailQueueItem.CompletionStatus.SUCCESS);
        MailQueue.MailQueueItem item2 = mailQueueItems.next();
        item2.done(MailQueue.MailQueueItem.CompletionStatus.SUCCESS);

        assertThat(item1.getMail().getName()).isEqualTo("name1");
        assertThat(item2.getMail().getName()).isEqualTo("name2");
    }
}
