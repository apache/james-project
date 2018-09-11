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
import java.io.Serializable;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.Executors;

import javax.mail.MessagingException;
import javax.mail.internet.AddressException;
import javax.mail.internet.MimeMessage;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.Store;
import org.apache.james.blob.mail.MimeMessagePartsId;
import org.apache.james.core.MailAddress;
import org.apache.james.metrics.api.Metric;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.server.core.MailImpl;
import org.apache.james.util.SerializationUtil;
import org.apache.mailet.Mail;
import org.apache.mailet.PerRecipientHeaders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.lambdas.Throwing;
import com.github.steveash.guavate.Guavate;
import com.nurkiewicz.asyncretry.AsyncRetryExecutor;
import com.rabbitmq.client.GetResponse;

class Dequeuer {
    private static class NoMailYetException extends RuntimeException {
    }

    private static class RabbitMQMailQueueItem implements MailQueue.MailQueueItem {
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
        public void done(boolean success) throws MailQueue.MailQueueException {
            try {
                rabbitClient.ack(deliveryTag);
            } catch (IOException e) {
                throw new MailQueue.MailQueueException("Failed to ACK " + mail.getName() + " with delivery tag " + deliveryTag, e);
            }
        }
    }

    private static final int TEN_MS = 10;

    private final MailQueueName name;
    private final RabbitClient rabbitClient;
    private final Store<MimeMessage, MimeMessagePartsId> mimeMessageStore;
    private final BlobId.Factory blobIdFactory;
    private final ObjectMapper objectMapper;
    private final Metric dequeueMetric;

    Dequeuer(MailQueueName name, RabbitClient rabbitClient, Store<MimeMessage, MimeMessagePartsId> mimeMessageStore, BlobId.Factory blobIdFactory, ObjectMapper objectMapper, MetricFactory metricFactory) {
        this.name = name;
        this.rabbitClient = rabbitClient;
        this.mimeMessageStore = mimeMessageStore;
        this.blobIdFactory = blobIdFactory;
        this.objectMapper = objectMapper;
        this.dequeueMetric = metricFactory.generate(DEQUEUED_METRIC_NAME_PREFIX + name.asString());
    }

    MailQueue.MailQueueItem deQueue() throws MailQueue.MailQueueException {
        GetResponse getResponse = pollChannel();
        MailDTO mailDTO = toDTO(getResponse);
        Mail mail = toMail(mailDTO);
        dequeueMetric.increment();
        return new RabbitMQMailQueueItem(rabbitClient, getResponse.getEnvelope().getDeliveryTag(), mail);
    }

    private MailDTO toDTO(GetResponse getResponse) throws MailQueue.MailQueueException {
        try {
            return objectMapper.readValue(getResponse.getBody(), MailDTO.class);
        } catch (IOException e) {
            throw new MailQueue.MailQueueException("Failed to parse DTO", e);
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

    private Mail toMail(MailDTO dto) throws MailQueue.MailQueueException {
        try {
            MimeMessage mimeMessage = mimeMessageStore.read(
                MimeMessagePartsId.builder()
                    .headerBlobId(blobIdFactory.from(dto.getHeaderBlobId()))
                    .bodyBlobId(blobIdFactory.from(dto.getBodyBlobId()))
                    .build())
                .join();

            MailImpl mail = new MailImpl(
                dto.getName(),
                dto.getSender().map(MailAddress::getMailSender).orElse(null),
                dto.getRecipients()
                    .stream()
                    .map(Throwing.<String, MailAddress>function(MailAddress::new).sneakyThrow())
                    .collect(Guavate.toImmutableList()),
                mimeMessage);

            mail.setErrorMessage(dto.getErrorMessage());
            mail.setRemoteAddr(dto.getRemoteAddr());
            mail.setRemoteHost(dto.getRemoteHost());
            mail.setState(dto.getState());
            dto.getLastUpdated()
                .map(Instant::toEpochMilli)
                .map(Date::new)
                .ifPresent(mail::setLastUpdated);

            dto.getAttributes()
                .forEach((name, value) -> mail.setAttribute(name, SerializationUtil.<Serializable>deserialize(value)));

            mail.addAllSpecificHeaderForRecipient(retrievePerRecipientHeaders(dto));

            return mail;
        } catch (AddressException e) {
            throw new MailQueue.MailQueueException("Failed to parse mail address", e);
        } catch (MessagingException e) {
            throw new MailQueue.MailQueueException("Failed to generate mime message", e);
        }
    }

    private PerRecipientHeaders retrievePerRecipientHeaders(MailDTO dto) {
        PerRecipientHeaders perRecipientHeaders = new PerRecipientHeaders();
        dto.getPerRecipientHeaders()
            .entrySet()
            .stream()
            .flatMap(entry -> entry.getValue().toHeaders().stream()
                .map(Throwing.function(header -> Pair.of(new MailAddress(entry.getKey()), header))))
            .forEach(pair -> perRecipientHeaders.addHeaderForRecipient(pair.getValue(), pair.getKey()));
        return perRecipientHeaders;
    }
}
