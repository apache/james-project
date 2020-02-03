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

import java.time.Duration;

import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.queue.api.MailQueueItemDecoratorFactory;
import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.james.queue.rabbitmq.view.api.DeleteCondition;
import org.apache.james.queue.rabbitmq.view.api.MailQueueView;
import org.apache.mailet.Mail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.MoreObjects;

import reactor.core.publisher.Flux;

public class RabbitMQMailQueue implements ManageableMailQueue {

    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitMQMailQueue.class);

    private final MailQueueName name;
    private final MetricFactory metricFactory;
    private final Enqueuer enqueuer;
    private final Dequeuer dequeuer;
    private final MailQueueView mailQueueView;
    private final MailQueueItemDecoratorFactory decoratorFactory;

    RabbitMQMailQueue(MetricFactory metricFactory, MailQueueName name,
                      Enqueuer enqueuer, Dequeuer dequeuer,
                      MailQueueView mailQueueView, MailQueueItemDecoratorFactory decoratorFactory) {
        this.metricFactory = metricFactory;
        this.name = name;
        this.enqueuer = enqueuer;
        this.dequeuer = dequeuer;
        this.mailQueueView = mailQueueView;
        this.decoratorFactory = decoratorFactory;
    }

    @Override
    public String getName() {
        return name.asString();
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
        metricFactory.runPublishingTimerMetric(ENQUEUED_TIMER_METRIC_NAME_PREFIX + name.asString(),
            Throwing.runnable(() -> enqueuer.enQueue(mail)).sneakyThrow());
    }

    @Override
    public Flux<MailQueueItem> deQueue() {
        return dequeuer.deQueue()
            .map(decoratorFactory::decorate);
    }

    @Override
    public long getSize() {
        return mailQueueView.getSize();
    }

    @Override
    public long flush() {
        LOGGER.warn("Delays are not supported by RabbitMQ. Flush is a NOOP.");
        return 0;
    }

    @Override
    public long clear() {
        return mailQueueView.delete(DeleteCondition.all());
    }

    @Override
    public long remove(Type type, String value) {
        return mailQueueView.delete(DeleteCondition.from(type, value));
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
}