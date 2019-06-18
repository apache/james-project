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

import static org.apache.james.backend.rabbitmq.Constants.AUTO_DELETE;
import static org.apache.james.backend.rabbitmq.Constants.DURABLE;
import static org.apache.james.backend.rabbitmq.Constants.EMPTY_ROUTING_KEY;
import static org.apache.james.backend.rabbitmq.Constants.EXCLUSIVE;
import static org.apache.james.backend.rabbitmq.Constants.NO_ARGUMENTS;

import java.io.IOException;

import javax.inject.Inject;

import org.apache.james.backend.rabbitmq.RabbitMQChannelPool;
import org.apache.james.queue.api.MailQueue;

import com.rabbitmq.client.AMQP;
import reactor.core.publisher.Flux;
import reactor.rabbitmq.AcknowledgableDelivery;

class RabbitClient {
    private final RabbitMQChannelPool channelPool;

    @Inject
    RabbitClient(RabbitMQChannelPool channelPool) {
        this.channelPool = channelPool;
    }

    void attemptQueueCreation(MailQueueName name) {
        channelPool.execute(channel -> {
            try {
                channel.exchangeDeclare(name.toRabbitExchangeName().asString(), "direct", DURABLE);
                channel.queueDeclare(name.toWorkQueueName().asString(), DURABLE, !EXCLUSIVE, !AUTO_DELETE, NO_ARGUMENTS);
                channel.queueBind(name.toWorkQueueName().asString(), name.toRabbitExchangeName().asString(), EMPTY_ROUTING_KEY);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    void publish(MailQueueName name, byte[] message) throws MailQueue.MailQueueException {
        channelPool.execute(channel -> {
            try {
                channel.basicPublish(name.toRabbitExchangeName().asString(), EMPTY_ROUTING_KEY, new AMQP.BasicProperties(), message);
            } catch (IOException e) {
                throw new MailQueue.MailQueueException("Unable to publish to RabbitMQ", e);
            }
        });
    }

    Flux<AcknowledgableDelivery> receive(MailQueueName name) {
        return channelPool.receive(name.toWorkQueueName().asString());
    }
}
