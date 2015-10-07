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
import java.net.MalformedURLException;
import java.util.List;
import java.util.Map;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TemporaryQueue;
import javax.mail.MessagingException;

import org.apache.activemq.ActiveMQSession;
import org.apache.activemq.BlobMessage;
import org.apache.activemq.command.ActiveMQBlobMessage;
import org.apache.activemq.util.JMSExceptionSupport;
import org.apache.james.core.MimeMessageCopyOnWriteProxy;
import org.apache.james.core.MimeMessageInputStream;
import org.apache.james.core.MimeMessageSource;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.jms.JMSMailQueue;
import org.apache.mailet.Mail;
import org.slf4j.Logger;
import org.springframework.jms.connection.SessionProxy;

/**
 * <p>
 * {@link MailQueue} implementation which use an ActiveMQ Queue.
 * <p>
 * </p>
 * This implementation require at ActiveMQ 5.4.0+.
 * <p>
 * </p>
 * When a {@link Mail} attribute is found and is not one of the supported
 * primitives, then the toString() method is called on the attribute value to
 * convert it
 * <p>
 * </p>
 * The implementation use {@link BlobMessage} or {@link ObjectMessage},
 * depending on the constructor which was used
 * <p>
 * </p>
 * See <a
 * href="http://activemq.apache.org/blob-messages.html">http://activemq.apache
 * .org/blob-messages.html</a> for more details
 * <p>
 * </p>
 * Some other supported feature is handling of priorities. See:<br>
 * <a href="http://activemq.apache.org/how-can-i-support-priority-queues.html">
 * http://activemq.apache.org/how-can-i-support-priority-queues.html</a>
 * <p>
 * </p>
 * For this just add a {@link Mail} attribute with name {@link #MAIL_PRIORITY}
 * to it. It should use one of the following value {@link #LOW_PRIORITY},
 * {@link #NORMAL_PRIORITY}, {@link #HIGH_PRIORITY}
 * <p>
 * </p>
 * To have a good throughput you should use a caching connection factory. </p>
 */
public class ActiveMQMailQueue extends JMSMailQueue implements ActiveMQSupport {

    private final boolean useBlob;

    /**
     * Construct a {@link ActiveMQMailQueue} which only use {@link BlobMessage}
     * 
     */
    public ActiveMQMailQueue(final ConnectionFactory connectionFactory, final String queuename, final Logger logger) {
        this(connectionFactory, queuename, true, logger);
    }

    /**
     * Construct a new ActiveMQ based {@link MailQueue}.
     * 
     * @param connectionFactory
     * @param queuename
     * @param useBlob
     * @param logger
     */
    public ActiveMQMailQueue(final ConnectionFactory connectionFactory, final String queuename, boolean useBlob, final Logger logger) {
        super(connectionFactory, queuename, logger);
        this.useBlob = useBlob;
    }

    /**
     * @see
     * org.apache.james.queue.jms.JMSMailQueue#populateMailMimeMessage(javax.jms.Message, org.apache.mailet.Mail)
     */
    protected void populateMailMimeMessage(Message message, Mail mail) throws MessagingException, JMSException {
        if (message instanceof BlobMessage) {
            try {
                BlobMessage blobMessage = (BlobMessage) message;
                try {
                    // store URL and queueName for later usage
                    mail.setAttribute(JAMES_BLOB_URL, blobMessage.getURL());
                    mail.setAttribute(JAMES_QUEUE_NAME, queueName);
                } catch (MalformedURLException e) {
                    // Ignore on error
                    logger.debug("Unable to get url from blobmessage for mail " + mail.getName());
                }
                MimeMessageSource source = new MimeMessageBlobMessageSource(blobMessage);
                mail.setMessage(new MimeMessageCopyOnWriteProxy(source));
            
            } catch (JMSException e) {
                throw new MailQueueException("Unable to populate MimeMessage for mail " + mail.getName(), e);
            }
        } else {
            super.populateMailMimeMessage(message, mail);
        }
    }

    
    /**
     * Produce the mail to the JMS Queue
     */
    protected void produceMail(Session session, Map<String, Object> props, int msgPrio, Mail mail) throws JMSException, MessagingException, IOException {
        MessageProducer producer = null;
        BlobMessage blobMessage = null;
        boolean reuse = false;

        try {

            // check if we should use a blob message here
            if (useBlob) {
                ActiveMQSession amqSession = getAMQSession(session);
                
                /*
                 * Remove this optimization as it could lead to problems when the same blob content
                 * is shared across different messages. 
                 * 
                 * I still think it would be a good idea to somehow do this but at the moment it's just 
                 * safer to disable it.
                 * 
                 * TODO: Re-Enable it again once it works!
                 * 
                 * See JAMES-1240
                if (wrapper instanceof MimeMessageCopyOnWriteProxy) {
                    wrapper = ((MimeMessageCopyOnWriteProxy) mm).getWrappedMessage();
                }

                if (wrapper instanceof MimeMessageWrapper) {
                    URL blobUrl = (URL) mail.getAttribute(JAMES_BLOB_URL);
                    String fromQueue = (String) mail.getAttribute(JAMES_QUEUE_NAME);
                    MimeMessageWrapper mwrapper = (MimeMessageWrapper) wrapper;

                    if (blobUrl != null && fromQueue != null && mwrapper.isModified() == false) {
                        // the message content was not changed so don't need to
                        // upload it again and can just point to the url
                        blobMessage = amqSession.createBlobMessage(blobUrl);
                        reuse = true;
                    }

                }*/
                if (blobMessage == null) {
                    // just use the MimeMessageInputStream which can read every
                    // MimeMessage implementation
                    blobMessage = amqSession.createBlobMessage(new MimeMessageInputStream(mail.getMessage()));
                }
                 
                    
                // store the queue name in the props
                props.put(JAMES_QUEUE_NAME, queueName);

                Queue queue = session.createQueue(queueName);

                producer = session.createProducer(queue);
                for (Map.Entry<String, Object> entry : props.entrySet()) {
                    blobMessage.setObjectProperty(entry.getKey(), entry.getValue());
                }
                producer.send(blobMessage, Message.DEFAULT_DELIVERY_MODE, msgPrio, Message.DEFAULT_TIME_TO_LIVE);
                    
              

            } else {
                super.produceMail(session, props, msgPrio, mail);
            }
        } catch (JMSException e) {
            if (!reuse && blobMessage != null && blobMessage instanceof ActiveMQBlobMessage) {
                ((ActiveMQBlobMessage) blobMessage).deleteFile();
            }
            throw e;
        } finally {

            try {
                if (producer != null)
                    producer.close();
            } catch (JMSException e) {
                // ignore here
            }
        }

    }

    /**
     * Cast the given {@link Session} to an {@link ActiveMQSession}
     * 
     * @param session
     * @return amqSession
     * @throws JMSException
     */
    protected ActiveMQSession getAMQSession(Session session) {
        ActiveMQSession amqSession;

        if (session instanceof SessionProxy) {
            // handle Springs CachingConnectionFactory
            amqSession = (ActiveMQSession) ((SessionProxy) session).getTargetSession();
        } else {
            // just cast as we have no other idea
            amqSession = (ActiveMQSession) session;
        }
        return amqSession;
    }

    @Override
    protected MailQueueItem createMailQueueItem(Connection connection, Session session, MessageConsumer consumer, Message message) throws JMSException, MessagingException {
        Mail mail = createMail(message);
        return new ActiveMQMailQueueItem(mail, connection, session, consumer, message, logger);
    }

    @Override
    public List<Message> removeWithSelector(String selector) throws MailQueueException {
        List<Message> mList = super.removeWithSelector(selector);

        // Handle the blob messages
        for (Message m : mList) {
            if (m instanceof ActiveMQBlobMessage) {
                try {
                    // Should get remove once this issue is closed:
                    // https://issues.apache.org/activemq/browse/AMQ-3018
                    ((ActiveMQBlobMessage) m).deleteFile();
                } catch (Exception e) {
                    logger.error("Unable to delete blob file for message " + m, e);
                }
            }
        }
        return mList;
    }

    @Override
    protected Message copy(Session session, Message m) throws JMSException {
        if (m instanceof ActiveMQBlobMessage) {
            ActiveMQBlobMessage b = (ActiveMQBlobMessage) m;
            ActiveMQBlobMessage copy = (ActiveMQBlobMessage) getAMQSession(session).createBlobMessage(b.getURL());
            try {
                copy.setProperties(b.getProperties());
            } catch (IOException e) {
                throw JMSExceptionSupport.create("Unable to copy message " + m, e);
            }
            return copy;
        } else {
            return super.copy(session, m);
        }
    }

    /**
     * Try to use ActiveMQ StatisticsPlugin to get size and if that fails
     * fallback to {@link JMSMailQueue#getSize()}
     */
    @Override
    public long getSize() throws MailQueueException {

        Connection connection = null;
        Session session = null;
        MessageConsumer consumer = null;
        MessageProducer producer = null;
        TemporaryQueue replyTo = null;
        long size;

        try {
            connection = connectionFactory.createConnection();
            connection.start();

            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            replyTo = session.createTemporaryQueue();
            consumer = session.createConsumer(replyTo);

            Queue myQueue = session.createQueue(queueName);
            producer = session.createProducer(null);

            String queueName = "ActiveMQ.Statistics.Destination." + myQueue.getQueueName();
            Queue query = session.createQueue(queueName);

            Message msg = session.createMessage();
            msg.setJMSReplyTo(replyTo);
            producer.send(query, msg);
            MapMessage reply = (MapMessage) consumer.receive(2000);
            if (reply != null && reply.itemExists("size")) {
                try {
                    size = reply.getLong("size");
                    return size;
                } catch (NumberFormatException e) {
                    // if we hit this we can't calculate the size so just catch
                    // it
                }
            }

        } catch (Exception e) {
            throw new MailQueueException("Unable to remove mails", e);

        } finally {

            if (consumer != null) {

                try {
                    consumer.close();
                } catch (JMSException e1) {
                    e1.printStackTrace();
                    // ignore on rollback
                }
            }

            if (producer != null) {

                try {
                    producer.close();
                } catch (JMSException e1) {
                    // ignore on rollback
                }
            }

            if (replyTo != null) {
                try {

                    // we need to delete the temporary queue to be sure we will
                    // free up memory if thats not done and a pool is used
                    // its possible that we will register a new mbean in jmx for
                    // every TemporaryQueue which will never get unregistered
                    replyTo.delete();
                } catch (JMSException e) {
                }
            }
            try {
                if (session != null)
                    session.close();
            } catch (JMSException e1) {
                // ignore here
            }

            try {
                if (connection != null)
                    connection.close();
            } catch (JMSException e1) {
                // ignore here
            }
        }

        // if we came to this point we should just fallback to super method
        return super.getSize();
    }

}
