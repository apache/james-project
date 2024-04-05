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

package org.apache.james.jmap.draft.send;

import java.io.IOException;

import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;

import org.apache.james.jmap.send.MailMetadata;
import org.apache.james.lifecycle.api.Disposable;
import org.apache.james.lifecycle.api.Startable;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

import reactor.core.publisher.Mono;

public class MailSpool implements Startable, Disposable {

    private static final Logger LOGGER = LoggerFactory.getLogger(MailSpool.class);

    private final MailQueueFactory<?> queueFactory;
    private MailQueue queue;

    @Inject
    @VisibleForTesting MailSpool(MailQueueFactory<?> queueFactory) {
        this.queueFactory = queueFactory;
    }

    public void start() {
        queue = queueFactory.createQueue(MailQueueFactory.SPOOL);
    }

    @PreDestroy
    public void dispose() {
        try {
            queue.close();
        } catch (IOException e) {
            LOGGER.debug("error closing queue", e);
        }
    }

    public Mono<Void> send(Mail mail, MailMetadata metadata) {
        mail.setAttribute(new Attribute(MailMetadata.MAIL_METADATA_MESSAGE_ID_ATTRIBUTE, AttributeValue.of(metadata.getMessageId().serialize())));
        mail.setAttribute(new Attribute(MailMetadata.MAIL_METADATA_USERNAME_ATTRIBUTE, AttributeValue.of(metadata.getUsername())));
        return Mono.from(queue.enqueueReactive(mail));
    }
}
