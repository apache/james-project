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

package org.apache.james.queue.jms;

import jakarta.jms.JMSException;
import jakarta.jms.MessageConsumer;
import jakarta.jms.Session;

import org.apache.james.queue.api.MailQueue.MailQueueException;
import org.apache.james.queue.api.MailQueue.MailQueueItem;
import org.apache.mailet.Mail;

/**
 * JMS {@link MailQueueItem} implementation
 */
public class JMSMailQueueItem implements MailQueueItem {

    protected final Mail mail;
    protected final Session session;
    protected final MessageConsumer consumer;

    public JMSMailQueueItem(Mail mail, Session session, MessageConsumer consumer) {
        this.mail = mail;
        this.session = session;
        this.consumer = consumer;
    }

    @Override
    public void done(CompletionStatus success) throws MailQueueException {
        try {
            if (success == CompletionStatus.SUCCESS) {
                session.commit();
            } else {
                JMSCacheableMailQueue.rollback(session);
            }
        } catch (JMSException ex) {
            throw new MailQueueException("Unable to commit dequeue operation for mail " + mail.getName(), ex);
        } finally {
            JMSCacheableMailQueue.closeConsumer(consumer);
            JMSCacheableMailQueue.closeSession(session);
        }
    }

    @Override
    public Mail getMail() {
        return mail;
    }

}
