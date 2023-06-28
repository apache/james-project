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

package org.apache.james.queue.rabbitmq;

import static org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Function;

import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.queue.api.MailQueueItemDecoratorFactory;
import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.james.queue.rabbitmq.view.api.DeleteCondition;
import org.apache.james.queue.rabbitmq.view.api.MailQueueView;
import org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueBrowser;
import org.apache.mailet.Mail;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class RabbitMQMailQueue implements ManageableMailQueue {

    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitMQMailQueue.class);

    private final MailQueueName name;
    private final MetricFactory metricFactory;
    private final Enqueuer enqueuer;
    private final Dequeuer dequeuer;
    private final MailQueueView<CassandraMailQueueBrowser.CassandraMailQueueItemView> mailQueueView;
    private final MailQueueItemDecoratorFactory decoratorFactory;

    RabbitMQMailQueue(MetricFactory metricFactory, MailQueueName name,
                      Enqueuer enqueuer, Dequeuer dequeuer,
                      MailQueueView<CassandraMailQueueBrowser.CassandraMailQueueItemView> mailQueueView, MailQueueItemDecoratorFactory decoratorFactory) {
        this.metricFactory = metricFactory;
        this.name = name;
        this.enqueuer = enqueuer;
        this.dequeuer = dequeuer;
        this.mailQueueView = mailQueueView;
        this.decoratorFactory = decoratorFactory;
    }

    @Override
    public void close() {

    }

    @Override
    public org.apache.james.queue.api.MailQueueName getName() {
        return org.apache.james.queue.api.MailQueueName.of(name.asString());
    }

    @Override
    public void enQueue(Mail mail, Duration delay) {
        if (!delay.isNegative()) {
            LOGGER.info("Ignored delay upon enqueue of {} : {}.", mail.getName(), delay);
        }
        enQueue(mail);
    }

    @Override
    public void enQueue(Mail mail) {
        Mono.from(enqueueReactive(mail)).block();
    }

    @Override
    public Publisher<Void> enqueueReactive(Mail mail) {
        try {
            return metricFactory.decoratePublisherWithTimerMetric(ENQUEUED_TIMER_METRIC_NAME_PREFIX + name.asString(),
                enqueuer.enQueue(mail));
        } catch (MailQueueException e) {
            return Mono.error(e);
        }
    }

    @Override
    public Flux<MailQueueItem> deQueue() {
        return dequeuer.deQueue()
            .map(item -> decoratorFactory.decorate(item, name.toModel()));
    }

    @Override
    public long getSize() {
        return mailQueueView.getSize();
    }

    @Override
    public Publisher<Long> getSizeReactive() {
        return mailQueueView.getSizeReactive();
    }

    @Override
    public long flush() {
        LOGGER.warn("Delays are not supported by RabbitMQ. Flush is a NOOP.");
        return 0;
    }

    @Override
    public long clear() {
        return Mono.from(mailQueueView.delete(DeleteCondition.all())).block();
    }

    @Override
    public long remove(Type type, String value) {
        return Mono.from(mailQueueView.delete(DeleteCondition.from(type, value))).block();
    }

    @Override
    public MailQueueIterator browse() {
        return mailQueueView.browse();
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("name", name)
            .toString();
    }

    public Flux<String> republishNotProcessedMails(Instant olderThan) {
        Function<CassandraMailQueueBrowser.CassandraMailQueueItemView, Mono<String>> requeue = item ->
            enqueuer.reQueue(item)
                .then(Mono.fromRunnable(item::dispose).subscribeOn(Schedulers.boundedElastic()))
                .thenReturn(item.getMail().getName());

        return mailQueueView.browseOlderThanReactive(olderThan)
            .flatMap(requeue, DEFAULT_CONCURRENCY);
    }
}