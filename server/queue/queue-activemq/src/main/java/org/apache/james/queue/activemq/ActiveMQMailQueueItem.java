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

package org.apache.james.queue.activemq;

import java.io.IOException;

import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.Session;

import org.apache.activemq.command.ActiveMQBlobMessage;
import org.apache.james.queue.api.MailQueue.MailQueueException;
import org.apache.james.queue.api.MailQueue.MailQueueItem;
import org.apache.james.queue.jms.JMSMailQueueItem;
import org.apache.mailet.Mail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ActiveMQ {@link MailQueueItem} implementation which handles Blob-Messages as
 * well
 */
public class ActiveMQMailQueueItem extends JMSMailQueueItem implements ActiveMQSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger(ActiveMQMailQueueItem.class);

    private final Message message;

    public ActiveMQMailQueueItem(Mail mail, Session session, MessageConsumer consumer, Message message) {
        super(mail,  session, consumer);
        this.message = message;
    }

    @Override
    public void done(CompletionStatus success) throws MailQueueException {
        super.done(success);
        if (success == CompletionStatus.SUCCESS) {
            if (message instanceof ActiveMQBlobMessage && !getMail().getAttribute(JAMES_REUSE_BLOB_URL).isPresent()) {

                // This should get removed once this jira issue was fixed
                // https://issues.apache.org/activemq/browse/AMQ-1529
                try {
                    ((ActiveMQBlobMessage) message).deleteFile();
                } catch (IOException | JMSException e) {
                    LOGGER.warn("Unable to delete blob message file for mail {}", getMail().getName(), e);
                }
            }
            getMail().removeAttribute(JAMES_REUSE_BLOB_URL);
        }

    }

}