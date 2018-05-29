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
import java.nio.charset.StandardCharsets;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

public class InMemoryConsumer extends DefaultConsumer {

    @FunctionalInterface
    interface Operation {
        void perform();
    }

    private final ConcurrentLinkedQueue<Integer> messages;
    private final Operation operation;

    public InMemoryConsumer(Channel channel) {
        this(channel, () -> { });
    }

    public InMemoryConsumer(Channel channel, Operation operation) {
        super(channel);
        this.operation = operation;
        this.messages = new ConcurrentLinkedQueue<>();
    }

    @Override
    public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
        operation.perform();
        Integer payload = Integer.valueOf(new String(body, StandardCharsets.UTF_8));
        messages.add(payload);
    }

    public Queue<Integer> getConsumedMessages() {
        return messages;
    }
}
