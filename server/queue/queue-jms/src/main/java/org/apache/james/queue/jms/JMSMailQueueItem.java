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

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.Session;

import org.apache.james.queue.api.MailQueue.MailQueueException;
import org.apache.james.queue.api.MailQueue.MailQueueItem;
import org.apache.mailet.Mail;

/**
 * JMS {@link MailQueueItem} implementation
 */
public class JMSMailQueueItem implements MailQueueItem {

    protected final Mail mail;
    protected final Connection connection;
    protected final Session session;
    protected final MessageConsumer consumer;

    public JMSMailQueueItem(Mail mail, Connection connection, Session session, MessageConsumer consumer) {
        this.mail = mail;
        this.connection = connection;
        this.session = session;
        this.consumer = consumer;
    }

    @Override
    public void done(boolean success) throws MailQueueException {
        try {
            if (success) {
                session.commit();
            } else {
                try {
                    session.rollback();
                } catch (JMSException e1) {
                    // ignore on rollback
                }
            }
        } catch (JMSException ex) {
            throw new MailQueueException("Unable to commit dequeue operation for mail " + mail.getName(), ex);
        } finally {
            if (consumer != null) {

                try {
                    consumer.close();
                } catch (JMSException e1) {
                    // ignore on rollback
                }
            }
            try {
                if (session != null)
                    session.close();
            } catch (JMSException e) {
                // ignore here
            }

            try {
                if (connection != null)
                    connection.close();
            } catch (JMSException e) {
                // ignore here
            }
        }
    }

    @Override
    public Mail getMail() {
        return mail;
    }

}
