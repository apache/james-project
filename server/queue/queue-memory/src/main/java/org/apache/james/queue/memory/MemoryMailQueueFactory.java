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

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.core.MailAddress;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.queue.api.MailQueueItemDecoratorFactory;
import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.james.server.core.MailImpl;
import org.apache.mailet.Mail;
import org.threeten.extra.Temporals;

import com.github.fge.lambdas.Throwing;
import com.github.steveash.guavate.Guavate;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class MemoryMailQueueFactory implements MailQueueFactory<ManageableMailQueue> {

    private final ConcurrentHashMap<String, MemoryMailQueueFactory.MemoryMailQueue> mailQueues;
    private final MailQueueItemDecoratorFactory mailQueueItemDecoratorFactory;

    @Inject
    public MemoryMailQueueFactory(MailQueueItemDecoratorFactory mailQueueItemDecoratorFactory) {
        this.mailQueues = new ConcurrentHashMap<>();
        this.mailQueueItemDecoratorFactory = mailQueueItemDecoratorFactory;
    }

    @Override
    public Set<ManageableMailQueue> listCreatedMailQueues() {
        return ImmutableSet.copyOf(mailQueues.values());
    }

    @Override
    public Optional<ManageableMailQueue> getQueue(String name) {
        return Optional.ofNullable(mailQueues.get(name));
    }

    @Override
    public MemoryMailQueueFactory.MemoryMailQueue createQueue(String name) {
        MemoryMailQueueFactory.MemoryMailQueue newMailQueue = new MemoryMailQueue(name, mailQueueItemDecoratorFactory);
        return Optional.ofNullable(mailQueues.putIfAbsent(name, newMailQueue))
            .orElse(newMailQueue);
    }

    public static class MemoryMailQueue implements ManageableMailQueue {
        private final DelayQueue<MemoryMailQueueItem> mailItems;
        private final LinkedBlockingDeque<MemoryMailQueueItem> inProcessingMailItems;
        private final String name;
        private final Flux<MailQueueItem> flux;

        public MemoryMailQueue(String name, MailQueueItemDecoratorFactory mailQueueItemDecoratorFactory) {
            this.mailItems = new DelayQueue<>();
            this.inProcessingMailItems = new LinkedBlockingDeque<>();
            this.name = name;
            this.flux = Mono.fromCallable(mailItems::take)
                .repeat()
                .subscribeOn(Schedulers.elastic())
                .flatMap(item ->
                    Mono.fromRunnable(() -> inProcessingMailItems.add(item)).thenReturn(item))
                .map(mailQueueItemDecoratorFactory::decorate);
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public void enQueue(Mail mail, Duration delay) throws MailQueueException {
            ZonedDateTime nextDelivery = calculateNextDelivery(delay);
            try {
                mailItems.put(new MemoryMailQueueItem(cloneMail(mail), this, nextDelivery));
            } catch (MessagingException e) {
                throw new MailQueueException("Error while copying mail " + mail.getName(), e);
            }
        }

        private ZonedDateTime calculateNextDelivery(Duration delay) {
            if (!delay.isNegative()) {
                try {
                    return ZonedDateTime.now().plus(delay);
                } catch (DateTimeException | ArithmeticException e) {
                    return Instant.ofEpochMilli(Long.MAX_VALUE).atZone(ZoneId.of("UTC"));
                }
            }

            return ZonedDateTime.now();
        }

        @Override
        public void enQueue(Mail mail) throws MailQueueException {
            enQueue(mail, 0, TimeUnit.SECONDS);
        }

        private Mail cloneMail(Mail mail) throws MessagingException {
            MailImpl mailImpl = MailImpl.duplicate(mail);
            mailImpl.setName(mail.getName());
            mailImpl.setState(mail.getState());
            mailImpl.addAllSpecificHeaderForRecipient(mail.getPerRecipientSpecificHeaders());
            Optional.ofNullable(mail.getMessage())
                    .ifPresent(Throwing.consumer(message -> mailImpl.setMessage(new MimeMessage(message))));
            return mailImpl;
        }

        @Override
        public Flux<MailQueueItem> deQueue() {
            return flux;
        }

        public Mail getLastMail() throws MailQueueException, InterruptedException {
            MemoryMailQueueItem maybeItem = Iterables.getLast(mailItems, null);
            if (maybeItem == null) {
                return null;
            }
            return maybeItem.getMail();
        }

        @Override
        public long getSize() throws MailQueueException {
            return mailItems.size() + inProcessingMailItems.size();
        }

        @Override
        public long flush() throws MailQueueException {
            int count = 0;
            for (MemoryMailQueueItem item: mailItems) {
                if (mailItems.remove(item)) {
                    enQueue(item.getMail());
                    count += 1;
                }
            }
            return count;
        }

        @Override
        public long clear() throws MailQueueException {
            int size = mailItems.size();
            mailItems.clear();
            return size;
        }

        @Override
        public long remove(Type type, String value) throws MailQueueException {
            ImmutableList<MemoryMailQueueItem> toBeRemoved = mailItems.stream()
                .filter(item -> shouldRemove(item, type, value))
                .collect(Guavate.toImmutableList());
            toBeRemoved.forEach(mailItems::remove);
            return toBeRemoved.size();
        }

        public boolean shouldRemove(MailQueueItem item, Type type, String value) {
            switch (type) {
                case Name:
                    return item.getMail().getName().equals(value);
                case Recipient:
                    return item.getMail().getRecipients().stream()
                        .map(MailAddress::asString)
                        .anyMatch(value::equals);
                case Sender:
                    return item.getMail().getMaybeSender()
                        .asString()
                        .equals(value);
                default:
                    throw new NotImplementedException("Unknown type " + type);
            }
        }

        private void markProcessingAsFinished(MemoryMailQueueItem item) {
            inProcessingMailItems.remove(item);
        }

        @Override
        public MailQueueIterator browse() throws MailQueueException {
            Iterator<MailQueueItemView> underlying = ImmutableList.copyOf(mailItems)
                .stream()
                .map(item -> new MailQueueItemView(item.getMail(), item.delivery))
                .iterator();

            return new MailQueueIterator() {
                @Override
                public void close() {

                }

                @Override
                public boolean hasNext() {
                    return underlying.hasNext();
                }

                @Override
                public MailQueueItemView next() {
                    return underlying.next();
                }
            };
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

    public static class MemoryMailQueueItem implements MailQueue.MailQueueItem, Delayed {
        private final Mail mail;
        private final MemoryMailQueue queue;
        private final ZonedDateTime delivery;

        public MemoryMailQueueItem(Mail mail, MemoryMailQueue queue, ZonedDateTime delivery) {
            this.mail = mail;
            this.queue = queue;
            this.delivery = delivery;
        }

        @Override
        public Mail getMail() {
            return mail;
        }

        @Override
        public void done(boolean success) throws MailQueue.MailQueueException {
            queue.markProcessingAsFinished(this);
            if (!success) {
                queue.enQueue(mail);
            }
        }

        @Override
        public long getDelay(TimeUnit unit) {
            try {
                return ZonedDateTime.now().until(delivery, Temporals.chronoUnit(unit));
            } catch (ArithmeticException e) {
                return Long.MAX_VALUE;
            }
        }

        @Override
        public int compareTo(Delayed o) {
            return Math.toIntExact(getDelay(TimeUnit.MILLISECONDS) - o.getDelay(TimeUnit.MILLISECONDS));
        }
    }
}
