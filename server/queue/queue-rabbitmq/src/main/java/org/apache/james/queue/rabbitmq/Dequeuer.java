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

import java.util.function.Consumer;

import org.apache.james.backends.rabbitmq.ReceiverProvider;
import org.apache.james.blob.api.ObjectNotFoundException;
import org.apache.james.metrics.api.Metric;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.queue.rabbitmq.view.api.DeleteCondition;
import org.apache.james.queue.rabbitmq.view.api.MailQueueView;
import org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueBrowser;
import org.apache.james.util.ReactorUtils;
import org.apache.mailet.Mail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.github.fge.lambdas.consumers.ThrowingConsumer;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.AcknowledgableDelivery;
import reactor.rabbitmq.ConsumeOptions;
import reactor.rabbitmq.Receiver;

class Dequeuer {
    private static final Logger LOGGER = LoggerFactory.getLogger(Dequeuer.class);
    private static final boolean REQUEUE = true;

    private static class RabbitMQMailQueueItem implements MailQueue.MailQueueItem {

        private final Consumer<CompletionStatus> ack;
        private final EnqueueId enqueueId;
        private final Mail mail;

        private RabbitMQMailQueueItem(Consumer<CompletionStatus> ack, MailWithEnqueueId mailWithEnqueueId) {
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
        public void done(CompletionStatus success) {
            ack.accept(success);
        }

    }

    private final MailLoader mailLoader;
    private final Metric dequeueMetric;
    private final MailReferenceSerializer mailReferenceSerializer;
    private final MailQueueView<CassandraMailQueueBrowser.CassandraMailQueueItemView> mailQueueView;
    private final MailQueueFactory.PrefetchCount prefetchCount;
    private final ReceiverProvider receiverProvider;
    private final MailQueueName name;

    Dequeuer(MailQueueName name, ReceiverProvider receiverProvider, MailLoader mailLoader,
             MailReferenceSerializer serializer, MetricFactory metricFactory,
             MailQueueView<CassandraMailQueueBrowser.CassandraMailQueueItemView> mailQueueView, MailQueueFactory.PrefetchCount prefetchCount) {
        this.mailLoader = mailLoader;
        this.mailReferenceSerializer = serializer;
        this.mailQueueView = mailQueueView;
        this.dequeueMetric = metricFactory.generate(DEQUEUED_METRIC_NAME_PREFIX + name.asString());
        this.receiverProvider = receiverProvider;
        this.prefetchCount = prefetchCount;
        this.name = name;
    }

    Flux<? extends MailQueue.MailQueueItem> deQueue() {
        return Flux.using(receiverProvider::createReceiver,
                receiver -> receiver.consumeManualAck(this.name.toWorkQueueName().asString(), new ConsumeOptions().qos(this.prefetchCount.asInt())),
                Receiver::close)
            .filter(getResponse -> getResponse.getBody() != null)
            .flatMapSequential(this::loadItem)
            .concatMap(this::filterIfDeleted);
    }

    private Mono<RabbitMQMailQueueItem> filterIfDeleted(RabbitMQMailQueueItem item) {
        return mailQueueView.isPresent(item.getEnqueueId())
            .<RabbitMQMailQueueItem>handle((isPresent, sink) -> {
                if (isPresent) {
                    sink.next(item);
                } else {
                    item.done(MailQueue.MailQueueItem.CompletionStatus.SUCCESS);
                    sink.complete();
                }
            })
            .onErrorResume(e -> Mono.fromRunnable(() -> {
                LOGGER.error("Failure to see if {} was deleted", item.enqueueId.asUUID(), e);
                item.done(MailQueue.MailQueueItem.CompletionStatus.RETRY);
            })
                .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
                .then(Mono.error(e)));
    }

    private Mono<RabbitMQMailQueueItem> loadItem(AcknowledgableDelivery response) {
        return loadMail(response)
            .map(mailWithEnqueueId -> new RabbitMQMailQueueItem(ack(response, mailWithEnqueueId), mailWithEnqueueId))
            .onErrorResume(e -> {
                LOGGER.error("Failed to load email, requeue corresponding message", e);
                response.nack(REQUEUE);
                return Mono.empty();
            });
    }

    private ThrowingConsumer<MailQueue.MailQueueItem.CompletionStatus> ack(AcknowledgableDelivery response, MailWithEnqueueId mailWithEnqueueId) {
        return success -> {
            switch (success) {
                case SUCCESS:
                    dequeueMetric.increment();
                    response.ack();
                    Mono.from(mailQueueView.delete(DeleteCondition.withEnqueueId(mailWithEnqueueId.getEnqueueId(), mailWithEnqueueId.getBlobIds()))).block();
                    break;
                case RETRY:
                    response.nack(REQUEUE);
                    break;
                case REJECT:
                    response.nack(!REQUEUE);
                    break;
            }
        };
    }

    private Mono<MailWithEnqueueId> loadMail(AcknowledgableDelivery delivery) {
        return toMailReference(delivery)
            .flatMap(reference -> mailLoader.load(reference)
                .onErrorResume(ObjectNotFoundException.class, e -> {
                    LOGGER.error("Fail to load mail {} with enqueueId {} as underlying blobs do not exist. Discarding this message to prevent an infinite loop.", reference.getName(), reference.getEnqueueId(), e);
                    delivery.nack(!REQUEUE);
                    return Mono.empty();
                })
                .onErrorResume(e -> {
                    LOGGER.error("Fail to load mail {} with enqueueId {}", reference.getName(), reference.getEnqueueId(), e);
                    delivery.nack(REQUEUE);
                    return Mono.empty();
                }));
    }

    private Mono<MailReferenceDTO> toMailReference(AcknowledgableDelivery delivery) {
        return Mono.fromCallable(delivery::getBody)
            .map(Throwing.function(mailReferenceSerializer::read).sneakyThrow())
            .onErrorResume(e -> {
                LOGGER.error("Fail to deserialize MailReferenceDTO. Discarding this message to prevent an infinite loop.", e);
                delivery.nack(!REQUEUE);
                return Mono.empty();
            });
    }
}
