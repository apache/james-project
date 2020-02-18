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

import static org.apache.james.queue.api.MailQueue.DEQUEUED_METRIC_NAME_PREFIX;

import java.io.Closeable;
import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Function;

import org.apache.james.backends.rabbitmq.ReceiverProvider;
import org.apache.james.metrics.api.Metric;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.queue.rabbitmq.view.api.DeleteCondition;
import org.apache.james.queue.rabbitmq.view.api.MailQueueView;
import org.apache.mailet.Mail;

import com.github.fge.lambdas.consumers.ThrowingConsumer;
import com.rabbitmq.client.Delivery;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.rabbitmq.AcknowledgableDelivery;
import reactor.rabbitmq.ConsumeOptions;
import reactor.rabbitmq.Receiver;

class Dequeuer implements Closeable {
    private static final boolean REQUEUE = true;

    private static class RabbitMQMailQueueItem implements MailQueue.MailQueueItem {

        private final Consumer<Boolean> ack;
        private final EnqueueId enqueueId;
        private final Mail mail;

        private RabbitMQMailQueueItem(Consumer<Boolean> ack, MailWithEnqueueId mailWithEnqueueId) {
            this.ack = ack;
            this.enqueueId = mailWithEnqueueId.getEnqueueId();
            this.mail = mailWithEnqueueId.getMail();
        }

        @Override
        public Mail getMail() {
            return mail;
        }

        public EnqueueId getEnqueueId() {
            return enqueueId;
        }

        @Override
        public void done(boolean success) {
            ack.accept(success);
        }

    }

    private final Function<MailReferenceDTO, MailWithEnqueueId> mailLoader;
    private final Metric dequeueMetric;
    private final MailReferenceSerializer mailReferenceSerializer;
    private final MailQueueView mailQueueView;
    private final Receiver receiver;
    private final Flux<AcknowledgableDelivery> flux;

    Dequeuer(MailQueueName name, ReceiverProvider receiverProvider, Function<MailReferenceDTO, MailWithEnqueueId> mailLoader,
             MailReferenceSerializer serializer, MetricFactory metricFactory,
             MailQueueView mailQueueView, MailQueueFactory.PrefetchCount prefetchCount) {
        this.mailLoader = mailLoader;
        this.mailReferenceSerializer = serializer;
        this.mailQueueView = mailQueueView;
        this.dequeueMetric = metricFactory.generate(DEQUEUED_METRIC_NAME_PREFIX + name.asString());
        this.receiver = receiverProvider.createReceiver();
        this.flux = this.receiver
            .consumeManualAck(name.toWorkQueueName().asString(), new ConsumeOptions().qos(prefetchCount.asInt()))
            .filter(getResponse -> getResponse.getBody() != null);
    }

    @Override
    public void close() {
        receiver.close();
    }

    Flux<? extends MailQueue.MailQueueItem> deQueue() {
        return flux.flatMapSequential(response -> loadItem(response).subscribeOn(Schedulers.elastic()))
            .concatMap(item -> filterIfDeleted(item).subscribeOn(Schedulers.elastic()));
    }

    private Mono<RabbitMQMailQueueItem> filterIfDeleted(RabbitMQMailQueueItem item) {
        return mailQueueView.isPresent(item.getEnqueueId())
            .flatMap(isPresent -> keepWhenPresent(item, isPresent));
    }

    private Mono<? extends RabbitMQMailQueueItem> keepWhenPresent(RabbitMQMailQueueItem item, Boolean isPresent) {
        if (isPresent) {
            return Mono.just(item);
        }
        item.done(true);
        return Mono.empty();
    }

    private Mono<RabbitMQMailQueueItem> loadItem(AcknowledgableDelivery response) {
        try {
            MailWithEnqueueId mailWithEnqueueId = loadMail(response);
            ThrowingConsumer<Boolean> ack = ack(response, mailWithEnqueueId);
            return Mono.just(new RabbitMQMailQueueItem(ack, mailWithEnqueueId));
        } catch (MailQueue.MailQueueException e) {
            return Mono.error(e);
        }
    }

    private ThrowingConsumer<Boolean> ack(AcknowledgableDelivery response, MailWithEnqueueId mailWithEnqueueId) {
        return success -> {
            if (success) {
                dequeueMetric.increment();
                response.ack();
                mailQueueView.delete(DeleteCondition.withEnqueueId(mailWithEnqueueId.getEnqueueId()));
            } else {
                response.nack(REQUEUE);
            }
        };
    }

    private MailWithEnqueueId loadMail(Delivery response) throws MailQueue.MailQueueException {
        MailReferenceDTO mailDTO = toMailReference(response);
        return mailLoader.apply(mailDTO);
    }

    private MailReferenceDTO toMailReference(Delivery getResponse) throws MailQueue.MailQueueException {
        try {
            return mailReferenceSerializer.read(getResponse.getBody());
        } catch (IOException e) {
            throw new MailQueue.MailQueueException("Failed to parse DTO", e);
        }
    }

}
