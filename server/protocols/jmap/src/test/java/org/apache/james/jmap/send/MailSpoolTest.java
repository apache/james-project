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

package org.apache.james.jmap.send;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import org.apache.james.mailbox.model.TestMessageId;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.MailQueue.MailQueueException;
import org.apache.james.queue.api.MailQueue.MailQueueItem;
import org.apache.mailet.Mail;
import org.apache.mailet.base.test.FakeMail;
import org.junit.Before;
import org.junit.Test;

public class MailSpoolTest {

    private MailSpool mailSpool;
    private MailQueue myQueue;

    @Before
    public void setup() {
        myQueue = new MyQueue();

        mailSpool = new MailSpool(name -> myQueue);
    }

    @Test
    public void sendShouldEnQueueTheMail() throws Exception {
        FakeMail mail = FakeMail.defaultFakeMail();

        mailSpool.send(mail, new MailMetadata(TestMessageId.of(1), "user"));

        assertThat(myQueue.deQueue())
            .isNotNull()
            .extracting(MailQueueItem::getMail)
            .containsExactly(mail);
    }

    private static class MyQueue implements MailQueue {

        private ConcurrentLinkedQueue<Mail> queue;

        public MyQueue() {
            queue = new ConcurrentLinkedQueue<Mail>();
        }

        @Override
        public void enQueue(Mail mail) throws MailQueueException {
            queue.add(mail);
        }

        @Override
        public void enQueue(Mail mail, long delay, TimeUnit unit) throws MailQueueException {
        }

        @Override
        public MailQueueItem deQueue() throws MailQueueException {
            return new MyMailQueueItem(queue.poll());
        }
    }

    private static class MyMailQueueItem implements MailQueueItem {

        private final Mail mail;

        public MyMailQueueItem(Mail mail) {
            this.mail = mail;
        }

        @Override
        public Mail getMail() {
            return mail;
        }

        @Override
        public void done(boolean success) throws MailQueueException {
        }
    }
}
