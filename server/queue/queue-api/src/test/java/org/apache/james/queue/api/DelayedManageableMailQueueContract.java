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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.mailet.Mail;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ExecutorExtension.class)
public interface DelayedManageableMailQueueContract extends DelayedMailQueueContract, ManageableMailQueueContract {

    ManageableMailQueue getManageableMailQueue();

    @Test
    default void flushShouldRemoveDelays(ExecutorService executorService) throws Exception {
        getManageableMailQueue().enQueue(defaultMail()
            .name("name1")
            .build(),
            30L,
            TimeUnit.SECONDS);

        getManageableMailQueue().flush();

        Future<MailQueue.MailQueueItem> tryDequeue = executorService.submit(() -> getManageableMailQueue().deQueue());
        assertThat(tryDequeue.get(1, TimeUnit.SECONDS).getMail().getName())
            .isEqualTo("name1");
    }

    @Test
    default void flushShouldPreserveBrowseOrder() throws Exception {
        getManageableMailQueue().enQueue(defaultMail()
            .name("name1")
            .build());

        getManageableMailQueue().enQueue(defaultMail()
            .name("name2")
            .build(),
            30L,
            TimeUnit.SECONDS);

        getManageableMailQueue().enQueue(defaultMail()
            .name("name3")
            .build(),
            2L,
            TimeUnit.SECONDS);

        getManageableMailQueue().flush();

        assertThat(getManageableMailQueue().browse())
            .extracting(ManageableMailQueue.MailQueueItemView::getMail)
            .extracting(Mail::getName)
            .containsExactly("name1", "name2", "name3");
    }

}
