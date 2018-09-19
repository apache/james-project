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

import java.time.Clock;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import javax.inject.Inject;
import javax.mail.internet.MimeMessage;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.Store;
import org.apache.james.blob.mail.MimeMessagePartsId;
import org.apache.james.metrics.api.GaugeRegistry;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.james.queue.rabbitmq.view.api.DeleteCondition;
import org.apache.james.queue.rabbitmq.view.api.MailQueueView;
import org.apache.mailet.Mail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;

public class RabbitMQMailQueue implements ManageableMailQueue {

    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitMQMailQueue.class);

    static class Factory {
        private final MetricFactory metricFactory;
        private final GaugeRegistry gaugeRegistry;
        private final RabbitClient rabbitClient;
        private final Store<MimeMessage, MimeMessagePartsId> mimeMessageStore;
        private final MailReferenceSerializer mailReferenceSerializer;
        private final Function<MailReferenceDTO, Mail> mailLoader;
        private final MailQueueView mailQueueView;
        private final Clock clock;

        @Inject
        @VisibleForTesting Factory(MetricFactory metricFactory, GaugeRegistry gaugeRegistry,
                                   RabbitClient rabbitClient,
                                   Store<MimeMessage, MimeMessagePartsId> mimeMessageStore,
                                   BlobId.Factory blobIdFactory,
                                   MailQueueView mailQueueView,
                                   Clock clock) {
            this.metricFactory = metricFactory;
            this.gaugeRegistry = gaugeRegistry;
            this.rabbitClient = rabbitClient;
            this.mimeMessageStore = mimeMessageStore;
            this.mailQueueView = mailQueueView;
            this.clock = clock;
            this.mailReferenceSerializer = new MailReferenceSerializer();
            this.mailLoader = Throwing.function(new MailLoader(mimeMessageStore, blobIdFactory)::load).sneakyThrow();
        }

        RabbitMQMailQueue create(MailQueueName mailQueueName) {
            mailQueueView.initialize(mailQueueName);

            return new RabbitMQMailQueue(
                metricFactory,
                mailQueueName,
                gaugeRegistry,
                new Enqueuer(mailQueueName, rabbitClient, mimeMessageStore, mailReferenceSerializer,
                    metricFactory, mailQueueView, clock),
                new Dequeuer(mailQueueName, rabbitClient, mailLoader, mailReferenceSerializer,
                    metricFactory, mailQueueView),
                mailQueueView);
        }
    }

    private final MailQueueName name;
    private final MetricFactory metricFactory;
    private final GaugeRegistry gaugeRegistry;
    private final Enqueuer enqueuer;
    private final Dequeuer dequeuer;
    private final MailQueueView mailQueueView;

    RabbitMQMailQueue(MetricFactory metricFactory, MailQueueName name,
                      GaugeRegistry gaugeRegistry, Enqueuer enqueuer, Dequeuer dequeuer,
                      MailQueueView mailQueueView) {
        this.metricFactory = metricFactory;
        this.name = name;
        this.gaugeRegistry = gaugeRegistry;
        this.enqueuer = enqueuer;
        this.dequeuer = dequeuer;
        this.mailQueueView = mailQueueView;

        this.gaugeRegistry.register(QUEUE_SIZE_METRIC_NAME_PREFIX + name.asString(), this::getSize);
    }

    @Override
    public String getName() {
        return name.asString();
    }

    @Override
    public void enQueue(Mail mail, long delay, TimeUnit unit) throws MailQueueException {
        if (delay > 0) {
            LOGGER.info("Ignored delay upon enqueue of {} : {} {}.", mail.getName(), delay, unit);
        }
        enQueue(mail);
    }

    @Override
    public void enQueue(Mail mail) {
        metricFactory.runPublishingTimerMetric(ENQUEUED_TIMER_METRIC_NAME_PREFIX + name.asString(),
            Throwing.runnable(() -> enqueuer.enQueue(mail)).sneakyThrow());
    }

    @Override
    public MailQueueItem deQueue() {
        return metricFactory.runPublishingTimerMetric(DEQUEUED_TIMER_METRIC_NAME_PREFIX + name.asString(),
            Throwing.supplier(dequeuer::deQueue).sneakyThrow());
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
        return mailQueueView.delete(DeleteCondition.all()).join();
    }

    @Override
    public long remove(Type type, String value) {
        return mailQueueView.delete(DeleteCondition.from(type, value)).join();
    }

    @Override
    public MailQueueIterator browse() {
        return mailQueueView.browse();
    }
}