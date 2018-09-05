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

import java.io.IOException;
import java.util.Optional;

import org.apache.james.queue.api.MailQueue;

import com.google.common.collect.ImmutableMap;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.GetResponse;

class RabbitClient {

    private static final boolean AUTO_ACK = true;
    private static final boolean AUTO_DELETE = true;
    private static final boolean DURABLE = true;
    private static final boolean EXCLUSIVE = true;
    private static final boolean MULTIPLE = true;
    private static final ImmutableMap<String, Object> NO_ARGUMENTS = ImmutableMap.of();
    private static final String ROUTING_KEY = "";

    private final Channel channel;

    RabbitClient(Channel channel) {
        this.channel = channel;
    }

    void attemptQueueCreation(MailQueueName name) {
        try {
            channel.exchangeDeclare(name.toRabbitExchangeName().asString(), "direct", DURABLE);
            channel.queueDeclare(name.toWorkQueueName().asString(), DURABLE, !EXCLUSIVE, !AUTO_DELETE, NO_ARGUMENTS);
            channel.queueBind(name.toWorkQueueName().asString(), name.toRabbitExchangeName().asString(), ROUTING_KEY);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    void publish(MailQueueName name, byte[] message) throws MailQueue.MailQueueException {
        try {
            channel.basicPublish(name.toRabbitExchangeName().asString(), ROUTING_KEY, new AMQP.BasicProperties(), message);
        } catch (IOException e) {
            throw new MailQueue.MailQueueException("Unable to publish to RabbitMQ", e);
        }
    }

    void ack(long deliveryTag) throws IOException {
        channel.basicAck(deliveryTag, !MULTIPLE);
    }

    Optional<GetResponse> poll(MailQueueName name) throws IOException {
        return Optional.ofNullable(channel.basicGet(name.toWorkQueueName().asString(), !AUTO_ACK));
    }
}
