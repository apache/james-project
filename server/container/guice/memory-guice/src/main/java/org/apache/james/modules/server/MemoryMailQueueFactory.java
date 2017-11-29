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

package org.apache.james.modules.server;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.queue.api.MailQueueItemDecoratorFactory;
import org.apache.james.server.core.MailImpl;
import org.apache.mailet.Mail;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Objects;
import com.google.inject.Inject;

public class MemoryMailQueueFactory implements MailQueueFactory {

    private final ConcurrentHashMap<String, MailQueue> mailQueues;
    private final MailQueueItemDecoratorFactory mailQueueItemDecoratorFactory;

    @Inject
    public MemoryMailQueueFactory(MailQueueItemDecoratorFactory mailQueueItemDecoratorFactory) {
        this.mailQueues = new ConcurrentHashMap<>();
        this.mailQueueItemDecoratorFactory = mailQueueItemDecoratorFactory;
    }

    @Override
    public MailQueue getQueue(String name) {
        return Optional.ofNullable(mailQueues.get(name))
            .orElseGet(() -> tryInsertNewMailQueue(name));
    }

    private MailQueue tryInsertNewMailQueue(String name) {
        MailQueue newMailQueue = new MemoryMailQueue(name, mailQueueItemDecoratorFactory);
        return Optional.ofNullable(mailQueues.putIfAbsent(name, newMailQueue))
            .orElse(newMailQueue);
    }

    public static class MemoryMailQueue implements MailQueue {

        private final LinkedBlockingDeque<MemoryMailQueueItem> mailItems;
        private final MailQueueItemDecoratorFactory mailQueueItemDecoratorFactory;
        private final String name;

        public MemoryMailQueue(String name,MailQueueItemDecoratorFactory mailQueueItemDecoratorFactory) {
            this.mailItems = new LinkedBlockingDeque<>();
            this.name = name;
            this.mailQueueItemDecoratorFactory = mailQueueItemDecoratorFactory;
        }

        @Override
        public void enQueue(Mail mail, long delay, TimeUnit unit) throws MailQueueException {
            enQueue(mail);
        }

        @Override
        public void enQueue(Mail mail) throws MailQueueException {
            try {
                mailItems.addFirst(new MemoryMailQueueItem(cloneMail(mail)));
            } catch (MessagingException e) {
                throw new MailQueueException("Error while copying mail " + mail.getName(), e);
            }
        }

        private Mail cloneMail(Mail mail) throws MessagingException {
            MailImpl mailImpl = MailImpl.duplicate(mail);
            mailImpl.setState(mail.getState());
            Optional.ofNullable(mail.getMessage())
                    .ifPresent(Throwing.consumer(message -> mailImpl.setMessage(new MimeMessage(message))));
            return mailImpl;
        }

        @Override
        public MailQueueItem deQueue() throws MailQueueException, InterruptedException {
            while (true) {
                MemoryMailQueueItem item = mailItems.take();
                return mailQueueItemDecoratorFactory.decorate(item);
            }
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            MemoryMailQueue that = (MemoryMailQueue) o;

            return Objects.equal(this.name, that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(name);
        }
    }

    public static class MemoryMailQueueItem implements MailQueue.MailQueueItem {

        private final Mail mail;

        public MemoryMailQueueItem(Mail mail) {
            this.mail = mail;
        }

        @Override
        public Mail getMail() {
            return mail;
        }

        @Override
        public void done(boolean success) throws MailQueue.MailQueueException {

        }
    }
}
