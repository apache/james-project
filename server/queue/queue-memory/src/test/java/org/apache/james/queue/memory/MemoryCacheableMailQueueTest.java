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

package org.apache.james.queue.memory;

import static org.apache.james.queue.api.Mails.defaultMail;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;

import org.apache.james.queue.api.DelayedManageableMailQueueContract;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.MailQueueName;
import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.james.queue.api.RawMailQueueItemDecoratorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MemoryCacheableMailQueueTest implements DelayedManageableMailQueueContract {

    private MemoryMailQueueFactory.MemoryCacheableMailQueue mailQueue;

    @BeforeEach
    public void setUp() {
        mailQueue = new MemoryMailQueueFactory.MemoryCacheableMailQueue(MailQueueName.of("test"), new RawMailQueueItemDecoratorFactory(), Clock.systemUTC());
    }

    @AfterEach
    public void tearDown() {
        mailQueue.close();
    }

    @Override
    public MailQueue getMailQueue() {
        return mailQueue;
    }

    @Test
    public void getLastMailShouldReturnNullWhenNoMail() throws Exception {
        assertThat(mailQueue.getLastMail())
            .isNull();
    }

    @Test
    public void getLastMailShouldReturnSingleMail() throws Exception {
        mailQueue.enQueue(defaultMail()
            .name("name")
            .build());

        assertThat(mailQueue.getLastMail().getName())
            .isEqualTo("name");
    }

    @Test
    public void getLastMailShouldReturnLastEnqueuedMail() throws Exception {
        mailQueue.enQueue(defaultMail()
            .name("name1")
            .build());
        mailQueue.enQueue(defaultMail()
            .name("name2")
            .build());

        assertThat(mailQueue.getLastMail().getName())
            .isEqualTo("name2");
    }

    @Test
    public void getLastMailShouldNotAlterMailQueueState() throws Exception {
        mailQueue.enQueue(defaultMail()
            .name("name1")
            .build());
        mailQueue.enQueue(defaultMail()
            .name("name2")
            .build());

        mailQueue.getLastMail();
        assertThat(mailQueue.getLastMail().getName())
            .isEqualTo("name2");
    }

    @Override
    public ManageableMailQueue getManageableMailQueue() {
        return mailQueue;
    }
}
