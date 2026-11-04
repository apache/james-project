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

import static org.apache.james.backends.rabbitmq.Constants.AUTO_DELETE;
import static org.apache.james.backends.rabbitmq.Constants.DURABLE;
import static org.apache.james.backends.rabbitmq.Constants.EMPTY_ROUTING_KEY;
import static org.apache.james.backends.rabbitmq.Constants.EXCLUSIVE;
import static org.apache.james.backends.rabbitmq.Constants.evaluateAutoDelete;
import static org.apache.james.backends.rabbitmq.Constants.evaluateDurable;
import static org.apache.james.backends.rabbitmq.Constants.evaluateExclusive;
import static org.apache.james.queue.api.MailQueue.QUEUE_SIZE_METRIC_NAME_PREFIX;

import java.time.Clock;
import java.util.Optional;
import java.util.Set;

import jakarta.inject.Inject;
import jakarta.mail.internet.MimeMessage;

import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.backends.rabbitmq.ReceiverProvider;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.Store;
import org.apache.james.blob.mail.MimeMessagePartsId;
import org.apache.james.blob.mail.MimeMessageStore;
import org.apache.james.metrics.api.GaugeRegistry;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.queue.api.MailQueueItemDecoratorFactory;
import org.apache.james.queue.rabbitmq.view.RabbitMQMailQueueConfiguration;
import org.apache.james.queue.rabbitmq.view.api.MailQueueView;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.BindingSpecification;
import reactor.rabbitmq.ExchangeSpecification;
import reactor.rabbitmq.QueueSpecification;
import reactor.rabbitmq.Sender;

public class RabbitMQMailQueueFactory implements MailQueueFactory<RabbitMQMailQueue> {

    @VisibleForTesting static class PrivateFactory {
        private final MetricFactory metricFactory;
        private final GaugeRegistry gaugeRegistry;
        private final ReceiverProvider receiverProvider;
        private final Sender sender;
        private final Store<MimeMessage, MimeMessagePartsId> mimeMessageStore;
        private final MailReferenceSerializer mailReferenceSerializer;
        private final MailLoader mailLoader;
        private final MailQueueView.Factory mailQueueViewFactory;
        private final Clock clock;
        private final MailQueueItemDecoratorFactory decoratorFactory;
        private final RabbitMQMailQueueConfiguration configuration;

        @Inject
        @VisibleForTesting PrivateFactory(MetricFactory metricFactory,
                                          GaugeRegistry gaugeRegistry,
                                          Sender sender, ReceiverProvider receiverProvider, MimeMessageStore.Factory mimeMessageStoreFactory,
                                          BlobId.Factory blobIdFactory,
                                          MailQueueView.Factory mailQueueViewFactory,
                                          Clock clock,
                                          MailQueueItemDecoratorFactory decoratorFactory,
                                          RabbitMQMailQueueConfiguration configuration) {
            this.metricFactory = metricFactory;
            this.gaugeRegistry = gaugeRegistry;
            this.sender = sender;
            this.receiverProvider = receiverProvider;
            this.mimeMessageStore = mimeMessageStoreFactory.mimeMessageStore();
            this.mailQueueViewFactory = mailQueueViewFactory;
            this.clock = clock;
            this.decoratorFactory = decoratorFactory;
            this.mailReferenceSerializer = new MailReferenceSerializer();
            this.mailLoader = new MailLoader(mimeMessageStore, blobIdFactory);
            this.configuration = configuration;
        }

        RabbitMQMailQueue create(MailQueueName mailQueueName, PrefetchCount prefetchCount) {
            MailQueueView mailQueueView = mailQueueViewFactory.create(mailQueueName);
            mailQueueView.initialize(mailQueueName);

            RabbitMQMailQueue rabbitMQMailQueue = new RabbitMQMailQueue(
                metricFactory,
                mailQueueName,
                new Enqueuer(mailQueueName, sender, mimeMessageStore, mailReferenceSerializer,
                    metricFactory, mailQueueView, configuration, clock),
                new Dequeuer(mailQueueName, receiverProvider, mailLoader, mailReferenceSerializer,
                    metricFactory, mailQueueView, prefetchCount),
                mailQueueView,
                decoratorFactory);

            registerGaugeFor(rabbitMQMailQueue);
            return rabbitMQMailQueue;
        }

        private void registerGaugeFor(RabbitMQMailQueue rabbitMQMailQueue) {
            if (configuration.isSizeMetricsEnabled()) {
                this.gaugeRegistry.register(QUEUE_SIZE_METRIC_NAME_PREFIX + rabbitMQMailQueue.getName().asString(), rabbitMQMailQueue::getSize);
            }
        }
    }

    private final RabbitMQMailQueueManagement mqManagementApi;
    private final PrivateFactory privateFactory;
    private final RabbitMQConfiguration configuration;
    private final Sender sender;

    @VisibleForTesting
    @Inject
    RabbitMQMailQueueFactory(Sender sender,
                             RabbitMQMailQueueManagement mqManagementApi,
                             PrivateFactory privateFactory,
                             RabbitMQConfiguration configuration) {
        this.sender = sender;
        this.mqManagementApi = mqManagementApi;
        this.privateFactory = privateFactory;
        this.configuration = configuration;
    }

    @Override
    public Optional<RabbitMQMailQueue> getQueue(org.apache.james.queue.api.MailQueueName name, PrefetchCount count) {
        return getQueueFromRabbitServer(MailQueueName.fromString(name.asString()), count);
    }

    @Override
    public RabbitMQMailQueue createQueue(org.apache.james.queue.api.MailQueueName name, PrefetchCount count) {
        MailQueueName mailQueueName = MailQueueName.fromString(name.asString());
        return getQueueFromRabbitServer(mailQueueName, count)
            .orElseGet(() -> createQueueIntoRabbitServer(mailQueueName, count));
    }

    @Override
    public Set<org.apache.james.queue.api.MailQueueName> listCreatedMailQueues() {
        return mqManagementApi.listCreatedMailQueueNames()
            .map(MailQueueName::asString)
            .map(org.apache.james.queue.api.MailQueueName::of)
            .collect(ImmutableSet.toImmutableSet());
    }

    @Override
    public boolean supportsDelays() {
        return false;
    }

    private RabbitMQMailQueue createQueueIntoRabbitServer(MailQueueName mailQueueName, PrefetchCount prefetchCount) {
        String exchangeName = mailQueueName.toRabbitExchangeName().asString();
        Flux.concat(
            sender.declareExchange(ExchangeSpecification.exchange(exchangeName)
                .durable(true)
                .type("direct")),
            declareDeadLetterExchange(mailQueueName),
            sender.declareQueue(QueueSpecification.queue(mailQueueName.toWorkQueueName().asString())
                .durable(evaluateDurable(DURABLE, configuration.isQuorumQueuesUsed()))
                .exclusive(evaluateExclusive(!EXCLUSIVE, configuration.isQuorumQueuesUsed()))
                .autoDelete(evaluateAutoDelete(!AUTO_DELETE, configuration.isQuorumQueuesUsed()))
                .arguments(configuration.workQueueArgumentsBuilder()
                    .deadLetter(mailQueueName.toDeadLetterExchangeName())
                    .build())),
            declareDeadLetterQueue(mailQueueName),
            sender.bind(BindingSpecification.binding()
                .exchange(mailQueueName.toRabbitExchangeName().asString())
                .queue(mailQueueName.toWorkQueueName().asString())
                .routingKey(EMPTY_ROUTING_KEY)),
            declareDeadLetterBinding(mailQueueName))
            .then()
            .block();
        return privateFactory.create(mailQueueName, prefetchCount);
    }

    private Optional<RabbitMQMailQueue> getQueueFromRabbitServer(MailQueueName name, PrefetchCount prefetchCount) {
        return mqManagementApi.listCreatedMailQueueNames()
            .filter(name::equals)
            .findFirst()
            .map(queueName -> {
                recreateMissingDeadLetterInfrastructure(queueName);
                return privateFactory.create(queueName, prefetchCount);
            });
    }

    /**
     * Verifies the dead letter infrastructure associated with an existing mail queue: dead letter
     * exchange, dead letter queue and the binding between the two. Missing pieces are idempotently
     * re-declared, self-healing infrastructure that was manually deleted, e.g. between two James
     * restarts. Existing pieces are left untouched, even when bearing different arguments. The
     * binding is always re-declared as RabbitMQ collapses equivalent bindings.
     */
    private void recreateMissingDeadLetterInfrastructure(MailQueueName mailQueueName) {
        Flux.concat(
            mqManagementApi.exchangeExists(mailQueueName.toDeadLetterExchangeName())
                ? Mono.empty()
                : declareDeadLetterExchange(mailQueueName),
            mqManagementApi.queueExists(mailQueueName.toDeadLetterQueueName())
                ? Mono.empty()
                : declareDeadLetterQueue(mailQueueName),
            declareDeadLetterBinding(mailQueueName))
            .then()
            .block();
    }

    private Mono<Void> declareDeadLetterExchange(MailQueueName mailQueueName) {
        return sender.declareExchange(ExchangeSpecification.exchange(mailQueueName.toDeadLetterExchangeName())
            .durable(true)
            .type("direct"))
            .then();
    }

    private Mono<Void> declareDeadLetterQueue(MailQueueName mailQueueName) {
        return sender.declareQueue(QueueSpecification.queue(mailQueueName.toDeadLetterQueueName())
            .durable(evaluateDurable(DURABLE, configuration.isQuorumQueuesUsed()))
            .exclusive(evaluateExclusive(!EXCLUSIVE, configuration.isQuorumQueuesUsed()))
            .autoDelete(evaluateAutoDelete(!AUTO_DELETE, configuration.isQuorumQueuesUsed()))
            .arguments(configuration.workQueueArgumentsBuilder()
                .build()))
            .then();
    }

    private Mono<Void> declareDeadLetterBinding(MailQueueName mailQueueName) {
        return sender.bind(BindingSpecification.binding()
            .exchange(mailQueueName.toDeadLetterExchangeName())
            .queue(mailQueueName.toDeadLetterQueueName())
            .routingKey(EMPTY_ROUTING_KEY))
            .then();
    }
}
