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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import javax.inject.Inject;
import javax.mail.internet.MimeMessage;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.Store;
import org.apache.james.blob.mail.MimeMessagePartsId;
import org.apache.james.metrics.api.GaugeRegistry;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.queue.api.MailQueueFactory;
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
        private final Function<MailReferenceDTO, Mail> mailLoader;
        private final MailQueueView mailQueueView;
        private final Clock clock;

        @Inject
        @VisibleForTesting PrivateFactory(MetricFactory metricFactory,
                                          GaugeRegistry gaugeRegistry,
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

    private final RabbitClient rabbitClient;
    private final RabbitMQManagementApi mqManagementApi;
    private final PrivateFactory privateFactory;

    // We store created queues to avoid duplicating gauge being registered
    private final ConcurrentHashMap<MailQueueName, RabbitMQMailQueue> instanciatedQueues;

    @VisibleForTesting
    @Inject
    RabbitMQMailQueueFactory(RabbitClient rabbitClient,
                             RabbitMQManagementApi mqManagementApi,
                             PrivateFactory privateFactory) {
        this.rabbitClient = rabbitClient;
        this.mqManagementApi = mqManagementApi;
        this.privateFactory = privateFactory;
        this.instanciatedQueues = new ConcurrentHashMap<>();
    }

    @Override
    public Optional<RabbitMQMailQueue> getQueue(String name) {
        return getQueue(MailQueueName.fromString(name));
    }

    @Override
    public RabbitMQMailQueue createQueue(String name) {
        MailQueueName mailQueueName = MailQueueName.fromString(name);
        return getQueue(mailQueueName)
            .orElseGet(() -> attemptQueueCreation(mailQueueName));
    }

    @Override
    public Set<RabbitMQMailQueue> listCreatedMailQueues() {
        return mqManagementApi.listCreatedMailQueueNames()
            .map(this::getOrElseCreateLocally)
            .collect(ImmutableSet.toImmutableSet());
    }

    private RabbitMQMailQueue attemptQueueCreation(MailQueueName mailQueueName) {
        rabbitClient.attemptQueueCreation(mailQueueName);
        return getOrElseCreateLocally(mailQueueName);
    }

    private Optional<RabbitMQMailQueue> getQueue(MailQueueName name) {
        return mqManagementApi.listCreatedMailQueueNames()
            .filter(name::equals)
            .map(this::getOrElseCreateLocally)
            .findFirst();
    }

    private RabbitMQMailQueue getOrElseCreateLocally(MailQueueName name) {
        return instanciatedQueues.computeIfAbsent(name, privateFactory::create);
    }
}
