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

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;

import org.apache.james.metrics.api.Metric;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.queue.api.MailQueue;
import org.apache.mailet.Mail;

import com.github.fge.lambdas.Throwing;
import com.github.fge.lambdas.consumers.ThrowingConsumer;
import com.nurkiewicz.asyncretry.AsyncRetryExecutor;
import com.rabbitmq.client.GetResponse;

class Dequeuer {
    private static class NoMailYetException extends RuntimeException {
    }

    private static class RabbitMQMailQueueItem implements MailQueue.MailQueueItem {
        private final Consumer<Boolean> ack;
        private final Mail mail;

        private RabbitMQMailQueueItem(Consumer<Boolean> ack, Mail mail) {
            this.ack = ack;
            this.mail = mail;
        }

        @Override
        public Mail getMail() {
            return mail;
        }

        @Override
        public void done(boolean success) throws MailQueue.MailQueueException {
            ack.accept(success);
        }
    }

    private static final int TEN_MS = 10;

    private final MailQueueName name;
    private final RabbitClient rabbitClient;
    private final Function<MailReferenceDTO, Mail> mailLoader;
    private final Metric dequeueMetric;
    private final MailReferenceSerializer mailReferenceSerializer;

    Dequeuer(MailQueueName name, RabbitClient rabbitClient, Function<MailReferenceDTO, Mail> mailLoader, MailReferenceSerializer serializer, MetricFactory metricFactory) {
        this.name = name;
        this.rabbitClient = rabbitClient;
        this.mailLoader = mailLoader;
        this.mailReferenceSerializer = serializer;
        this.dequeueMetric = metricFactory.generate(DEQUEUED_METRIC_NAME_PREFIX + name.asString());
    }

    MailQueue.MailQueueItem deQueue() throws MailQueue.MailQueueException {
        return pollChannel()
            .thenApply(Throwing.function(this::loadItem).sneakyThrow())
            .join();
    }

    private RabbitMQMailQueueItem loadItem(GetResponse response) throws MailQueue.MailQueueException {
        Mail mail = loadMail(response);
        ThrowingConsumer<Boolean> ack = ack(response.getEnvelope().getDeliveryTag(), mail);
        return new RabbitMQMailQueueItem(ack, mail);
    }

    private ThrowingConsumer<Boolean> ack(long deliveryTag, Mail mail) {
        return success -> {
            try {
                if (success) {
                    dequeueMetric.increment();
                    rabbitClient.ack(deliveryTag);
                } else {
                    rabbitClient.nack(deliveryTag);
                }
            } catch (IOException e) {
                throw new MailQueue.MailQueueException("Failed to ACK " + mail.getName() + " with delivery tag " + deliveryTag, e);
            }
        };
    }

    private Mail loadMail(GetResponse response) throws MailQueue.MailQueueException {
        MailReferenceDTO mailDTO = toMailReference(response);
        return mailLoader.apply(mailDTO);
    }

    private MailReferenceDTO toMailReference(GetResponse getResponse) throws MailQueue.MailQueueException {
        try {
            return mailReferenceSerializer.read(getResponse.getBody());
        } catch (IOException e) {
            throw new MailQueue.MailQueueException("Failed to parse DTO", e);
        }
    }

    private CompletableFuture<GetResponse> pollChannel() {
        return new AsyncRetryExecutor(Executors.newSingleThreadScheduledExecutor())
            .withFixedRate()
            .withMinDelay(TEN_MS)
            .retryOn(NoMailYetException.class)
            .getWithRetry(this::singleChannelRead);
    }

    private GetResponse singleChannelRead() throws IOException {
        return rabbitClient.poll(name)
            .filter(getResponse -> getResponse.getBody() != null)
            .orElseThrow(NoMailYetException::new);
    }

}
