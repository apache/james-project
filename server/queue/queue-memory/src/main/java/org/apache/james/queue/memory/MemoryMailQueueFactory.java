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

import static org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY;

import java.time.Clock;
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
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.core.MailAddress;
import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.queue.api.MailQueueItemDecoratorFactory;
import org.apache.james.queue.api.MailQueueName;
import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.james.server.core.MailImpl;
import org.apache.mailet.Mail;
import org.reactivestreams.Publisher;
import org.threeten.extra.Temporals;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

public class MemoryMailQueueFactory implements MailQueueFactory<MemoryMailQueueFactory.MemoryCacheableMailQueue> {

    private final ConcurrentHashMap<MailQueueName, MemoryCacheableMailQueue> mailQueues;
    private final MailQueueItemDecoratorFactory mailQueueItemDecoratorFactory;
    private final Clock clock;

    @Inject
    public MemoryMailQueueFactory(MailQueueItemDecoratorFactory mailQueueItemDecoratorFactory, Clock clock) {
        this.mailQueues = new ConcurrentHashMap<>();
        this.mailQueueItemDecoratorFactory = mailQueueItemDecoratorFactory;
        this.clock = clock;
    }

    public MemoryMailQueueFactory(MailQueueItemDecoratorFactory mailQueueItemDecoratorFactory) {
        this(mailQueueItemDecoratorFactory, Clock.systemUTC());
    }

    @PreDestroy
    public void clean() {
        mailQueues.clear();
    }

    @Override
    public Set<MailQueueName> listCreatedMailQueues() {
        return mailQueues.values()
            .stream()
            .map(MemoryCacheableMailQueue::getName)
            .collect(ImmutableSet.toImmutableSet());
    }

    @Override
    public Optional<MemoryCacheableMailQueue> getQueue(MailQueueName name, PrefetchCount count) {
        Optional<MemoryCacheableMailQueue> queue = Optional.ofNullable(mailQueues.get(name));
        queue.ifPresent(MemoryCacheableMailQueue::reference);
        return queue;
    }

    @Override
    public MemoryCacheableMailQueue createQueue(MailQueueName name, PrefetchCount prefetchCount) {
        MemoryCacheableMailQueue queue = mailQueues.computeIfAbsent(name, mailQueueName -> new MemoryCacheableMailQueue(mailQueueName, mailQueueItemDecoratorFactory, clock));
        queue.reference();
        return queue;
    }

    public static class MemoryCacheableMailQueue implements ManageableMailQueue {
        private final AtomicInteger references = new AtomicInteger(0);
        private final DelayQueue<MemoryMailQueueItem> mailItems;
        private final LinkedBlockingDeque<MemoryMailQueueItem> inProcessingMailItems;
        private final MailQueueName name;
        private final Flux<MailQueueItem> flux;
        private final Scheduler scheduler;
        private final Clock clock;

        public MemoryCacheableMailQueue(MailQueueName name, MailQueueItemDecoratorFactory mailQueueItemDecoratorFactory, Clock clock) {
            this.clock = clock;
            this.mailItems = new DelayQueue<>();
            this.inProcessingMailItems = new LinkedBlockingDeque<>();
            this.name = name;
            this.scheduler = Schedulers.newSingle("memory-mail-queue");

            this.flux = Mono.<MemoryMailQueueItem>create(sink -> {
                    try {
                        sink.success(mailItems.poll(10, TimeUnit.MILLISECONDS));
                    } catch (InterruptedException e) {
                        sink.success();
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .repeat()
                .subscribeOn(scheduler)
                .flatMap(item ->
                    Mono.fromRunnable(() -> inProcessingMailItems.add(item)).thenReturn(item), DEFAULT_CONCURRENCY)
                .map(item -> mailQueueItemDecoratorFactory.decorate(item, name));
        }

        public void reference() {
            references.incrementAndGet();
        }

        @Override
        public void close() {
            if (references.decrementAndGet() <= 0) {
                this.scheduler.dispose();

                mailItems.forEach(LifecycleUtil::dispose);
                inProcessingMailItems.forEach(LifecycleUtil::dispose);
                mailItems.clear();
                inProcessingMailItems.clear();
            }
        }

        @Override
        public MailQueueName getName() {
            return name;
        }

        @Override
        public void enQueue(Mail mail, Duration delay) throws MailQueueException {
            ZonedDateTime nextDelivery = calculateNextDelivery(delay);
            try {
                mailItems.put(new MemoryMailQueueItem(cloneMail(mail), this, clock, nextDelivery));
            } catch (MessagingException e) {
                throw new MailQueueException("Error while copying mail " + mail.getName(), e);
            }
        }

        @Override
        public Publisher<Void> enqueueReactive(Mail mail) {
            return Mono.fromRunnable(Throwing.runnable(() -> enQueue(mail)).sneakyThrow());
        }

        private ZonedDateTime calculateNextDelivery(Duration delay) {
            if (!delay.isNegative()) {
                try {
                    return ZonedDateTime.now(clock).plus(delay);
                } catch (DateTimeException | ArithmeticException e) {
                    return Instant.ofEpochMilli(Long.MAX_VALUE).atZone(ZoneId.of("UTC"));
                }
            }

            return ZonedDateTime.now(clock);
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

        public Mail getLastMail() {
            MemoryMailQueueItem maybeItem = Iterables.getLast(mailItems, null);
            if (maybeItem == null) {
                return null;
            }
            return maybeItem.getMail();
        }

        @Override
        public long getSize() {
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
        public long clear() {
            int size = mailItems.size();
            mailItems.clear();
            return size;
        }

        @Override
        public long remove(Type type, String value) {
            ImmutableList<MemoryMailQueueItem> toBeRemoved = mailItems.stream()
                .filter(item -> shouldRemove(item, type, value))
                .collect(ImmutableList.toImmutableList());
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
        public MailQueueIterator browse() {
            Iterator<DefaultMailQueueItemView> underlying = ImmutableList.copyOf(mailItems)
                .stream()
                .map(item -> new DefaultMailQueueItemView(item.getMail(), item.delivery))
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

            MemoryCacheableMailQueue that = (MemoryCacheableMailQueue) o;

            return Objects.equal(this.name, that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(name);
        }
    }

    public static class MemoryMailQueueItem implements MailQueue.MailQueueItem, Delayed {
        private final Mail mail;
        private final MemoryCacheableMailQueue queue;
        private final Clock clock;
        private final ZonedDateTime delivery;

        public MemoryMailQueueItem(Mail mail, MemoryCacheableMailQueue queue, Clock clock, ZonedDateTime delivery) {
            this.mail = mail;
            this.queue = queue;
            this.clock = clock;
            this.delivery = delivery;
        }

        @Override
        public Mail getMail() {
            return mail;
        }

        @Override
        public void done(CompletionStatus success) throws MailQueue.MailQueueException {
            queue.markProcessingAsFinished(this);
            if (success == CompletionStatus.RETRY) {
                queue.enQueue(mail);
            }
        }

        @Override
        public long getDelay(TimeUnit unit) {
            try {
                return ZonedDateTime.now(clock).until(delivery, Temporals.chronoUnit(unit));
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
