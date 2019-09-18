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

package org.apache.james.jmap.send;

import javax.inject.Inject;

import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.MailQueue.MailQueueException;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;

import com.google.common.annotations.VisibleForTesting;

public class MailSpool {

    private final MailQueue queue;

    @Inject
    @VisibleForTesting MailSpool(MailQueueFactory<?> queueFactory) {
        queue = queueFactory.createQueue(MailQueueFactory.SPOOL);
    }

    public void send(Mail mail, MailMetadata metadata) throws MailQueueException {
        mail.setAttribute(new Attribute(MailMetadata.MAIL_METADATA_MESSAGE_ID_ATTRIBUTE, AttributeValue.of(metadata.getMessageId().serialize())));
        mail.setAttribute(new Attribute(MailMetadata.MAIL_METADATA_USERNAME_ATTRIBUTE, AttributeValue.of(metadata.getUsername())));
        queue.enQueue(mail);
    }
}
