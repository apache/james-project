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

import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.Session;

import org.apache.james.queue.api.MailQueue.MailQueueException;
import org.apache.james.queue.api.MailQueue.MailQueueItem;
import org.apache.james.queue.jms.JMSMailQueueItem;
import org.apache.mailet.Mail;

/**
 * Artemis-backed {@link MailQueueItem} implementation.
 * Replaces the legacy ActiveMQ BlobMessage-aware item with a standard JMS implementation.
 */
public class ActiveMQMailQueueItem extends JMSMailQueueItem implements ActiveMQSupport {

    public ActiveMQMailQueueItem(Mail mail, Session session, MessageConsumer consumer, Message message) {
        super(mail, session, consumer);
    }

    @Override
    public void done(CompletionStatus success) throws MailQueueException {
        super.done(success);
    }
}