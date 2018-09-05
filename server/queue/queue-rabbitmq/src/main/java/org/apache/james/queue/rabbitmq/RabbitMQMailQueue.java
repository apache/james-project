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
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.james.core.MailAddress;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.server.core.MailImpl;
import org.apache.mailet.Mail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.fge.lambdas.Throwing;
import com.github.steveash.guavate.Guavate;
import com.nurkiewicz.asyncretry.AsyncRetryExecutor;
import com.rabbitmq.client.GetResponse;

public class RabbitMQMailQueue implements MailQueue {
    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitMQMailQueue.class);

    private static class NoMailYetException extends RuntimeException {
    }

    private static class RabbitMQMailQueueItem implements MailQueueItem {
        private final RabbitClient rabbitClient;
        private final long deliveryTag;
        private final Mail mail;

        private RabbitMQMailQueueItem(RabbitClient rabbitClient, long deliveryTag, Mail mail) {
            this.rabbitClient = rabbitClient;
            this.deliveryTag = deliveryTag;
            this.mail = mail;
        }

        @Override
        public Mail getMail() {
            return mail;
        }

        @Override
        public void done(boolean success) throws MailQueueException {
            try {
                rabbitClient.ack(deliveryTag);
            } catch (IOException e) {
                throw new MailQueueException("Failed to ACK " + mail.getName() + " with delivery tag " + deliveryTag, e);
            }
        }
    }

    private static final int TEN_MS = 10;

    private final MailQueueName name;
    private final RabbitClient rabbitClient;
    private final ObjectMapper objectMapper;

    RabbitMQMailQueue(MailQueueName name, RabbitClient rabbitClient) {
        this.name = name;
        this.rabbitClient = rabbitClient;
        this.objectMapper = new ObjectMapper()
            .registerModule(new Jdk8Module())
            .registerModule(new JavaTimeModule())
            .registerModule(new GuavaModule());
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
    public void enQueue(Mail mail) throws MailQueueException {
        MailDTO mailDTO = MailDTO.fromMail(mail);
        byte[] message = getMessageBytes(mailDTO);
        rabbitClient.publish(name, message);
    }

    private byte[] getMessageBytes(MailDTO mailDTO) throws MailQueueException {
        try {
            return objectMapper.writeValueAsBytes(mailDTO);
        } catch (JsonProcessingException e) {
            throw new MailQueueException("Unable to serialize message", e);
        }
    }


    @Override
    public MailQueueItem deQueue() throws MailQueueException {
        GetResponse getResponse = pollChannel();
        MailDTO mailDTO = toDTO(getResponse);
        Mail mail = toMail(mailDTO);
        return new RabbitMQMailQueueItem(rabbitClient, getResponse.getEnvelope().getDeliveryTag(), mail);
    }

    private MailDTO toDTO(GetResponse getResponse) throws MailQueueException {
        try {
            return objectMapper.readValue(getResponse.getBody(), MailDTO.class);
        } catch (IOException e) {
            throw new MailQueueException("Failed to parse DTO", e);
        }
    }

    private GetResponse pollChannel() {
        return new AsyncRetryExecutor(Executors.newSingleThreadScheduledExecutor())
            .withFixedRate()
            .withMinDelay(TEN_MS)
            .retryOn(NoMailYetException.class)
            .getWithRetry(this::singleChannelRead)
            .join();
    }

    private GetResponse singleChannelRead() throws IOException {
        return rabbitClient.poll(name)
            .filter(getResponse -> getResponse.getBody() != null)
            .orElseThrow(NoMailYetException::new);
    }

    private Mail toMail(MailDTO dto) {
        return new MailImpl(
            dto.getName(),
            MailAddress.getMailSender(dto.getSender()),
            dto.getRecipients()
                .stream()
                .map(Throwing.<String, MailAddress>function(MailAddress::new).sneakyThrow())
                .collect(Guavate.toImmutableList()));
    }
}