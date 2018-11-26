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

import static org.apache.james.queue.api.MailQueue.ENQUEUED_METRIC_NAME_PREFIX;

import java.time.Clock;
import java.util.concurrent.CompletableFuture;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.james.blob.api.Store;
import org.apache.james.blob.mail.MimeMessagePartsId;
import org.apache.james.metrics.api.Metric;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.rabbitmq.view.api.MailQueueView;
import org.apache.mailet.Mail;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.fge.lambdas.Throwing;

class Enqueuer {
    private final MailQueueName name;
    private final RabbitClient rabbitClient;
    private final Store<MimeMessage, MimeMessagePartsId> mimeMessageStore;
    private final MailReferenceSerializer mailReferenceSerializer;
    private final Metric enqueueMetric;
    private final MailQueueView mailQueueView;
    private final Clock clock;

    Enqueuer(MailQueueName name, RabbitClient rabbitClient, Store<MimeMessage, MimeMessagePartsId> mimeMessageStore,
             MailReferenceSerializer serializer, MetricFactory metricFactory,
             MailQueueView mailQueueView, Clock clock) {
        this.name = name;
        this.rabbitClient = rabbitClient;
        this.mimeMessageStore = mimeMessageStore;
        this.mailReferenceSerializer = serializer;
        this.mailQueueView = mailQueueView;
        this.clock = clock;
        this.enqueueMetric = metricFactory.generate(ENQUEUED_METRIC_NAME_PREFIX + name.asString());
    }

    void enQueue(Mail mail) throws MailQueue.MailQueueException {
        saveMail(mail)
            .thenApply(Throwing.<MimeMessagePartsId, EnqueuedItem>function(partsId -> publishReferenceToRabbit(mail, partsId)).sneakyThrow())
            .thenCompose(mailQueueView::storeMail)
            .thenRun(enqueueMetric::increment)
            .join();
    }

    private CompletableFuture<MimeMessagePartsId> saveMail(Mail mail) throws MailQueue.MailQueueException {
        try {
            return mimeMessageStore.save(mail.getMessage());
        } catch (MessagingException e) {
            throw new MailQueue.MailQueueException("Error while saving blob", e);
        }
    }

    private EnqueuedItem publishReferenceToRabbit(Mail mail, MimeMessagePartsId partsId) throws MailQueue.MailQueueException {
        rabbitClient.publish(name, getMailReferenceBytes(mail, partsId));

        return EnqueuedItem.builder()
            .mailQueueName(name)
            .mail(mail)
            .enqueuedTime(clock.instant())
            .mimeMessagePartsId(partsId)
            .build();
    }

    private byte[] getMailReferenceBytes(Mail mail, MimeMessagePartsId partsId) throws MailQueue.MailQueueException {
        try {
            MailReferenceDTO mailDTO = MailReferenceDTO.fromMail(mail, partsId);
            return mailReferenceSerializer.write(mailDTO);
        } catch (JsonProcessingException e) {
            throw new MailQueue.MailQueueException("Unable to serialize message", e);
        }
    }
}
