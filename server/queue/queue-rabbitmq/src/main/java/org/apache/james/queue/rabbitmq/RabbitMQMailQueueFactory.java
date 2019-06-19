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

import static org.apache.james.queue.api.MailQueue.QUEUE_SIZE_METRIC_NAME_PREFIX;

import java.time.Clock;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import javax.inject.Inject;
import javax.mail.internet.MimeMessage;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.Store;
import org.apache.james.blob.mail.MimeMessagePartsId;
import org.apache.james.blob.mail.MimeMessageStore;
import org.apache.james.metrics.api.GaugeRegistry;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.queue.api.MailQueueItemDecoratorFactory;
import org.apache.james.queue.rabbitmq.view.api.MailQueueView;
import org.apache.mailet.Mail;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;

public class RabbitMQMailQueueFactory implements MailQueueFactory<RabbitMQMailQueue> {

    @VisibleForTesting static class PrivateFactory {
        private final MetricFactory metricFactory;
        private final GaugeRegistry gaugeRegistry;
        private final RabbitClient rabbitClient;
        private final Store<MimeMessage, MimeMessagePartsId> mimeMessageStore;
        private final MailReferenceSerializer mailReferenceSerializer;
        private final Function<MailReferenceDTO, Pair<EnQueueId, Mail>> mailLoader;
        private final MailQueueView.Factory mailQueueViewFactory;
        private final Clock clock;
        private final MailQueueItemDecoratorFactory decoratorFactory;

        @Inject
        @VisibleForTesting PrivateFactory(MetricFactory metricFactory,
                                          GaugeRegistry gaugeRegistry,
                                          RabbitClient rabbitClient,
                                          MimeMessageStore.Factory mimeMessageStoreFactory,
                                          BlobId.Factory blobIdFactory,
                                          MailQueueView.Factory mailQueueViewFactory,
                                          Clock clock,
                                          MailQueueItemDecoratorFactory decoratorFactory) {
            this.metricFactory = metricFactory;
            this.gaugeRegistry = gaugeRegistry;
            this.rabbitClient = rabbitClient;
            this.mimeMessageStore = mimeMessageStoreFactory.mimeMessageStore();
            this.mailQueueViewFactory = mailQueueViewFactory;
            this.clock = clock;
            this.decoratorFactory = decoratorFactory;
            this.mailReferenceSerializer = new MailReferenceSerializer();
            this.mailLoader = Throwing.function(new MailLoader(mimeMessageStore, blobIdFactory)::load).sneakyThrow();
        }

        RabbitMQMailQueue create(MailQueueName mailQueueName) {
            MailQueueView mailQueueView = mailQueueViewFactory.create(mailQueueName);
            mailQueueView.initialize(mailQueueName);

            RabbitMQMailQueue rabbitMQMailQueue = new RabbitMQMailQueue(
                metricFactory,
                mailQueueName,
                new Enqueuer(mailQueueName, rabbitClient, mimeMessageStore, mailReferenceSerializer,
                    metricFactory, mailQueueView, clock),
                new Dequeuer(mailQueueName, rabbitClient, mailLoader, mailReferenceSerializer,
                    metricFactory, mailQueueView),
                mailQueueView,
                decoratorFactory);

            registerGaugeFor(rabbitMQMailQueue);
            return rabbitMQMailQueue;
        }

        private void registerGaugeFor(RabbitMQMailQueue rabbitMQMailQueue) {
            this.gaugeRegistry.register(QUEUE_SIZE_METRIC_NAME_PREFIX + rabbitMQMailQueue.getName(), rabbitMQMailQueue::getSize);
        }
    }

    /**
     * RabbitMQMailQueue should have a single instance in a given JVM for a given MailQueueName.
     * This class helps at keeping track of previously instanciated MailQueues.
     */
    private class RabbitMQMailQueueObjectPool {

        private final ConcurrentHashMap<MailQueueName, RabbitMQMailQueue> instanciatedQueues;

        RabbitMQMailQueueObjectPool() {
            this.instanciatedQueues = new ConcurrentHashMap<>();
        }

        RabbitMQMailQueue retrieveInstanceFor(MailQueueName name) {
            return instanciatedQueues.computeIfAbsent(name, privateFactory::create);
        }
    }

    private final RabbitClient rabbitClient;
    private final RabbitMQMailQueueManagement mqManagementApi;
    private final PrivateFactory privateFactory;
    private final RabbitMQMailQueueObjectPool mailQueueObjectPool;

    @VisibleForTesting
    @Inject
    RabbitMQMailQueueFactory(RabbitClient rabbitClient,
                             RabbitMQMailQueueManagement mqManagementApi,
                             PrivateFactory privateFactory) {
        this.rabbitClient = rabbitClient;
        this.mqManagementApi = mqManagementApi;
        this.privateFactory = privateFactory;
        this.mailQueueObjectPool = new RabbitMQMailQueueObjectPool();
    }

    @Override
    public Optional<RabbitMQMailQueue> getQueue(String name) {
        return getQueueFromRabbitServer(MailQueueName.fromString(name));
    }

    @Override
    public RabbitMQMailQueue createQueue(String name) {
        MailQueueName mailQueueName = MailQueueName.fromString(name);
        return getQueueFromRabbitServer(mailQueueName)
            .orElseGet(() -> createQueueIntoRabbitServer(mailQueueName));
    }

    @Override
    public Set<RabbitMQMailQueue> listCreatedMailQueues() {
        return mqManagementApi.listCreatedMailQueueNames()
            .map(mailQueueObjectPool::retrieveInstanceFor)
            .collect(ImmutableSet.toImmutableSet());
    }

    private RabbitMQMailQueue createQueueIntoRabbitServer(MailQueueName mailQueueName) {
        rabbitClient.attemptQueueCreation(mailQueueName);
        return mailQueueObjectPool.retrieveInstanceFor(mailQueueName);
    }

    private Optional<RabbitMQMailQueue> getQueueFromRabbitServer(MailQueueName name) {
        return mqManagementApi.listCreatedMailQueueNames()
            .filter(name::equals)
            .map(mailQueueObjectPool::retrieveInstanceFor)
            .findFirst();
    }

}
