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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.List;
import java.util.Map;

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
import javax.mail.internet.MimeMessage;

import org.apache.activemq.ActiveMQSession;
import org.apache.activemq.BlobMessage;
import org.apache.activemq.command.ActiveMQBlobMessage;
import org.apache.activemq.util.JMSExceptionSupport;
import org.apache.james.metrics.api.GaugeRegistry;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.MailQueueItemDecoratorFactory;
import org.apache.james.queue.api.MailQueueName;
import org.apache.james.queue.jms.JMSCacheableMailQueue;
import org.apache.james.server.core.MailImpl;
import org.apache.james.server.core.MimeMessageInputStream;
import org.apache.james.server.core.MimeMessageSource;
import org.apache.james.server.core.MimeMessageWrapper;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Mono;

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
public class ActiveMQCacheableMailQueue extends JMSCacheableMailQueue implements ActiveMQSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger(ActiveMQCacheableMailQueue.class);

    private final boolean useBlob;

    /**
     * Construct a {@link ActiveMQCacheableMailQueue} which only use {@link BlobMessage}
     * 
     */
    public ActiveMQCacheableMailQueue(ConnectionFactory connectionFactory, MailQueueItemDecoratorFactory mailQueueItemDecoratorFactory, MailQueueName queuename, MetricFactory metricFactory,
                                      GaugeRegistry gaugeRegistry) {
        this(connectionFactory, mailQueueItemDecoratorFactory, queuename, true, metricFactory, gaugeRegistry);
    }

    /**
     * Construct a new ActiveMQ based {@link MailQueue}.
     * 
     * @param connectionFactory
     * @param queuename
     * @param useBlob
     */
    public ActiveMQCacheableMailQueue(ConnectionFactory connectionFactory, MailQueueItemDecoratorFactory mailQueueItemDecoratorFactory, MailQueueName queuename, boolean useBlob, MetricFactory metricFactory,
                                      GaugeRegistry gaugeRegistry) {
        super(connectionFactory, mailQueueItemDecoratorFactory, queuename, metricFactory, gaugeRegistry);
        this.useBlob = useBlob;
    }

    @Override
    protected MailImpl.Builder populateMail(Message message) throws JMSException {
        MailImpl.Builder builder = super.populateMail(message);
        if (message instanceof BlobMessage) {
            BlobMessage blobMessage = (BlobMessage) message;
            try {
                // store URL and queueName for later usage
                builder.addAttribute(new Attribute(JAMES_BLOB_URL, AttributeValue.of(blobMessage.getURL())));
                builder.addAttribute(new Attribute(JAMES_QUEUE_NAME, AttributeValue.of(queueName.asString())));
            } catch (MalformedURLException e) {
                // Ignore on error
                LOGGER.debug("Unable to get url from blobmessage for mail");
            }
        }
        return builder;
    }

    @Override
    protected MimeMessage mimeMessage(Message message) throws MessagingException, JMSException {
        if (message instanceof BlobMessage) {
            try {
                BlobMessage blobMessage = (BlobMessage) message;
                MimeMessageSource source = new MimeMessageBlobMessageSource(blobMessage);
                return new MimeMessageWrapper(source);
            
            } catch (JMSException e) {
                throw new MailQueueException("Unable to populate MimeMessage for mail", e);
            }
        } else {
            return super.mimeMessage(message);
        }
    }

    
    /**
     * Produce the mail to the JMS Queue
     */
    @Override
    protected void produceMail(Map<String, Object> props, int msgPrio, Mail mail) throws JMSException, MessagingException, IOException {
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

                for (Map.Entry<String, Object> entry : props.entrySet()) {
                    blobMessage.setObjectProperty(entry.getKey(), entry.getValue());
                }
                producer.send(blobMessage, Message.DEFAULT_DELIVERY_MODE, msgPrio, Message.DEFAULT_TIME_TO_LIVE);

            } else {
                super.produceMail(props, msgPrio, mail);
            }
        } catch (JMSException e) {
            if (!reuse && blobMessage instanceof ActiveMQBlobMessage) {
                ((ActiveMQBlobMessage) blobMessage).deleteFile();
            }
            throw e;
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
        return (ActiveMQSession) session;
    }

    @Override
    protected Mono<MailQueueItem> createMailQueueItem(Session session, MessageConsumer consumer, Message message) throws JMSException, MessagingException {
        try {
            Mail mail = createMail(message);
            ActiveMQMailQueueItem activeMQMailQueueItem = new ActiveMQMailQueueItem(mail, session, consumer, message);
            return Mono.just(mailQueueItemDecoratorFactory.decorate(activeMQMailQueueItem, queueName));
        } catch (MessagingException e) {
            if (e.getCause() instanceof FileNotFoundException) {
                LOGGER.warn("Blob message cannot be found, discarding email", e);
                try {
                    session.commit();
                } catch (JMSException ex) {
                    throw new MailQueueException("Unable to commit dequeue operation for mail", ex);
                } finally {
                    JMSCacheableMailQueue.closeConsumer(consumer);
                    JMSCacheableMailQueue.closeSession(session);
                }
                return Mono.empty();
            }
            return Mono.error(e);
        }
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
                    LOGGER.error("Unable to delete blob file for message {}", m, e);
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
     * fallback to {@link JMSCacheableMailQueue#getSize()}
     */
    @Override
    public long getSize() throws MailQueueException {
        MessageConsumer consumer = null;
        MessageProducer producer = null;
        TemporaryQueue replyTo = null;

        try {
            replyTo = session.createTemporaryQueue();
            consumer = session.createConsumer(replyTo);

            Queue myQueue = session.createQueue(queueName.asString());
            producer = session.createProducer(null);

            String queueName = "ActiveMQ.Statistics.Destination." + myQueue.getQueueName();
            Queue query = session.createQueue(queueName);

            Message msg = session.createMessage();
            msg.setJMSReplyTo(replyTo);
            producer.send(query, msg);
            MapMessage reply = (MapMessage) consumer.receive(2000);
            if (reply != null && reply.itemExists("size")) {
                try {
                    return reply.getLong("size");
                } catch (NumberFormatException e) {
                    return super.getSize();
                }
            }
            return super.getSize();
        } catch (Exception e) {
            throw new MailQueueException("Unable to remove mails", e);

        } finally {
            closeConsumer(consumer);
            closeProducer(producer);
            if (replyTo != null) {
                try {

                    // we need to delete the temporary queue to be sure we will
                    // free up memory if thats not done and a pool is used
                    // its possible that we will register a new mbean in jmx for
                    // every TemporaryQueue which will never get unregistered
                    replyTo.delete();
                } catch (JMSException e) {
                    LOGGER.error("Error while deleting temporary queue", e);
                }
            }
        }
    }

}
