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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;
import java.util.concurrent.TimeUnit;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.QueueBrowser;
import javax.jms.Session;
import javax.mail.MessagingException;
import javax.mail.internet.AddressException;
import javax.mail.internet.MimeMessage;

import org.apache.james.core.MailAddress;
import org.apache.james.lifecycle.api.Disposable;
import org.apache.james.metrics.api.Metric;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.api.TimeMetric;
import org.apache.james.queue.api.MailPrioritySupport;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.MailQueueItemDecoratorFactory;
import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.james.server.core.MailImpl;
import org.apache.james.server.core.MimeMessageCopyOnWriteProxy;
import org.apache.mailet.Mail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Throwables;

/**
 * <p>
 * {@link MailQueue} implementation which use a JMS Queue for the<br>
 * {@link MailQueue}. This implementation should work with every JMS 1.1.0
 * implementation
 * </p>
 * <p>
 * It use {@link ObjectMessage} with a byte array as payload to store the
 * {@link Mail} objects.
 * </p>
 */
public class JMSMailQueue implements ManageableMailQueue, JMSSupport, MailPrioritySupport, Disposable {

    protected static void closeSession(Session session) {
        if (session != null) {
            try {
                session.close();
            } catch (JMSException e) {
                LOGGER.error("Error while closing session", e);
            }
        }
    }

    protected static void closeProducer(MessageProducer producer) {
        if (producer != null) {
            try {
                producer.close();
            } catch (JMSException e) {
                LOGGER.error("Error while closing producer", e);
            }
        }
    }

    protected static void closeConsumer(MessageConsumer consumer) {
        if (consumer != null) {
            try {
                consumer.close();
            } catch (JMSException e) {
                LOGGER.error("Error while closing consumer", e);
            }
        }
    }

    protected static void rollback(Session session) {
        if (session != null) {
            try {
                session.rollback();
            } catch (JMSException e) {
                LOGGER.error("Error while rolling session back", e);
            }
        }
    }

    private static void closeBrowser(QueueBrowser browser) {
        if (browser != null) {
            try {
                browser.close();
            } catch (JMSException e) {
                LOGGER.error("Error while closing browser", e);
            }
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(JMSMailQueue.class);

    protected final String queueName;
    protected final Connection connection;
    protected final MailQueueItemDecoratorFactory mailQueueItemDecoratorFactory;
    protected final Metric enqueuedMailsMetric;
    protected final Metric mailQueueSize;
    protected final MetricFactory metricFactory;
    public static final String FORCE_DELIVERY = "FORCE_DELIVERY";

    public JMSMailQueue(ConnectionFactory connectionFactory, MailQueueItemDecoratorFactory mailQueueItemDecoratorFactory, String queueName, MetricFactory metricFactory) {
        try {
            connection = connectionFactory.createConnection();
            connection.start();
        } catch (JMSException e) {
            throw Throwables.propagate(e);
        }
        this.mailQueueItemDecoratorFactory = mailQueueItemDecoratorFactory;
        this.queueName = queueName;
        this.metricFactory = metricFactory;
        this.enqueuedMailsMetric = metricFactory.generate("enqueuedMail:" + queueName);
        this.mailQueueSize = metricFactory.generate("mailQueueSize:" + queueName);
    }

    @Override
    public String getMailQueueName() {
        return queueName;
    }

    /**
     * <p>
     * Dequeues a mail when it is ready to process. As JMS does not support delay scheduling out-of-the box,
     * we use a messageselector to check if a mail is ready. For this a
     * {@link MessageConsumer#receive(long)} is used with a timeout of 10
     * seconds.
     * </p>
     * <p>
     * Many JMS implementations support better solutions for this, so this
     * should get overridden by these implementations
     * </p>
     */
    @Override
    public MailQueueItem deQueue() throws MailQueueException {
        Session session = null;
        Message message;
        MessageConsumer consumer = null;

        while (true) {
            TimeMetric timeMetric = metricFactory.timer("dequeueTime:" + queueName);
            try {
                session = connection.createSession(true, Session.SESSION_TRANSACTED);
                Queue queue = session.createQueue(queueName);
                consumer = session.createConsumer(queue, getMessageSelector());

                message = consumer.receive(10000);

                if (message != null) {
                    mailQueueSize.decrement();
                    return createMailQueueItem(connection, session, consumer, message);
                } else {
                    session.commit();
                    closeConsumer(consumer);
                    closeSession(session);
                }

            } catch (Exception e) {
                rollback(session);
                closeConsumer(consumer);
                closeSession(session);
                throw new MailQueueException("Unable to dequeue next message", e);
            } finally {
                timeMetric.stopAndPublish();
            }
        }

    }

    @Override
    public void enQueue(Mail mail, long delay, TimeUnit unit) throws MailQueueException {
        TimeMetric timeMetric = metricFactory.timer("enqueueMailTime:" + queueName);
        Session session = null;

        long mydelay = 0;

        if (delay > 0) {
            mydelay = TimeUnit.MILLISECONDS.convert(delay, unit);
        }

        try {

            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            int msgPrio = NORMAL_PRIORITY;
            Object prio = mail.getAttribute(MAIL_PRIORITY);
            if (prio instanceof Integer) {
                msgPrio = (Integer) prio;
            }

            Map<String, Object> props = getJMSProperties(mail, mydelay);

            produceMail(session, props, msgPrio, mail);

            enqueuedMailsMetric.increment();
            mailQueueSize.increment();
        } catch (Exception e) {
            rollback(session);
            throw new MailQueueException("Unable to enqueue mail " + mail, e);
        } finally {
            timeMetric.stopAndPublish();
            closeSession(session);
        }
    }

    @Override
    public void enQueue(Mail mail) throws MailQueueException {
        enQueue(mail, NO_DELAY, TimeUnit.MILLISECONDS);
    }

    /**
     * Produce the mail to the JMS Queue
     */
    protected void produceMail(Session session, Map<String, Object> props, int msgPrio, Mail mail) throws JMSException, MessagingException, IOException {
        MessageProducer producer = null;

        try {
            Queue queue = session.createQueue(queueName);

            producer = session.createProducer(queue);
            ObjectMessage message = session.createObjectMessage();

            for (Map.Entry<String, Object> entry : props.entrySet()) {
                message.setObjectProperty(entry.getKey(), entry.getValue());
            }

            long size = mail.getMessageSize();
            ByteArrayOutputStream out;
            if (size > -1) {
                out = new ByteArrayOutputStream((int) size);
            } else {
                out = new ByteArrayOutputStream();
            }
            mail.getMessage().writeTo(out);

            // store the byte array in a ObjectMessage so we can use a
            // SharedByteArrayInputStream later
            // without the need of copy the day
            message.setObject(out.toByteArray());

            producer.send(message, Message.DEFAULT_DELIVERY_MODE, msgPrio, Message.DEFAULT_TIME_TO_LIVE);

        } finally {
            closeProducer(producer);
        }
    }

    /**
     * Get JMS Message properties with values
     *
     * @param mail
     * @param delayInMillis
     * @throws JMSException
     * @throws MessagingException
     */
    protected Map<String, Object> getJMSProperties(Mail mail, long delayInMillis) throws MessagingException {
        Map<String, Object> props = new HashMap<>();
        long nextDelivery = -1;
        if (delayInMillis > 0) {
            nextDelivery = System.currentTimeMillis() + delayInMillis;

        }
        props.put(JAMES_NEXT_DELIVERY, nextDelivery);
        props.put(JAMES_MAIL_ERROR_MESSAGE, mail.getErrorMessage());
        props.put(JAMES_MAIL_LAST_UPDATED, mail.getLastUpdated().getTime());
        props.put(JAMES_MAIL_MESSAGE_SIZE, mail.getMessageSize());
        props.put(JAMES_MAIL_NAME, mail.getName());

        StringBuilder recipientsBuilder = new StringBuilder();

        Iterator<MailAddress> recipients = mail.getRecipients().iterator();
        while (recipients.hasNext()) {
            String recipient = recipients.next().toString();
            recipientsBuilder.append(recipient.trim());
            if (recipients.hasNext()) {
                recipientsBuilder.append(JAMES_MAIL_SEPARATOR);
            }
        }
        props.put(JAMES_MAIL_RECIPIENTS, recipientsBuilder.toString());
        props.put(JAMES_MAIL_REMOTEADDR, mail.getRemoteAddr());
        props.put(JAMES_MAIL_REMOTEHOST, mail.getRemoteHost());

        String sender;
        MailAddress s = mail.getSender();
        if (s == null) {
            sender = "";
        } else {
            sender = mail.getSender().toString();
        }

        StringBuilder attrsBuilder = new StringBuilder();
        Iterator<String> attrs = mail.getAttributeNames();
        while (attrs.hasNext()) {
            String attrName = attrs.next();
            attrsBuilder.append(attrName);

            Object value = convertAttributeValue(mail.getAttribute(attrName));
            props.put(attrName, value);

            if (attrs.hasNext()) {
                attrsBuilder.append(JAMES_MAIL_SEPARATOR);
            }
        }
        props.put(JAMES_MAIL_ATTRIBUTE_NAMES, attrsBuilder.toString());
        props.put(JAMES_MAIL_SENDER, sender);
        props.put(JAMES_MAIL_STATE, mail.getState());
        return props;
    }

    /**
     * Create the complete Mail from the JMS Message. So the created
     * {@link Mail} is completely populated
     *
     * @param message
     * @return the complete mail
     * @throws MessagingException
     * @throws JMSException
     */
    protected final Mail createMail(Message message) throws MessagingException, JMSException {
        MailImpl mail = new MailImpl();
        populateMail(message, mail);
        populateMailMimeMessage(message, mail);

        return mail;
    }

    /**
     * Populat the given {@link Mail} instance with a {@link MimeMessage}. The
     * {@link MimeMessage} is read from the JMS Message. This implementation use
     * a {@link BytesMessage}
     *
     * @param message
     * @param mail
     * @throws MessagingException
     */
    protected void populateMailMimeMessage(Message message, Mail mail) throws MessagingException, JMSException {
        if (message instanceof ObjectMessage) {
            mail.setMessage(new MimeMessageCopyOnWriteProxy(new MimeMessageObjectMessageSource((ObjectMessage) message)));
        } else {
            throw new MailQueueException("Not supported JMS Message received " + message);
        }

    }

    /**
     * Populate Mail with values from Message. This exclude the
     * {@link MimeMessage}
     *
     * @param message
     * @param mail
     * @throws JMSException
     */
    protected void populateMail(Message message, MailImpl mail) throws JMSException {
        mail.setErrorMessage(message.getStringProperty(JAMES_MAIL_ERROR_MESSAGE));
        mail.setLastUpdated(new Date(message.getLongProperty(JAMES_MAIL_LAST_UPDATED)));
        mail.setName(message.getStringProperty(JAMES_MAIL_NAME));

        List<MailAddress> rcpts = new ArrayList<>();
        String recipients = message.getStringProperty(JAMES_MAIL_RECIPIENTS);
        StringTokenizer recipientTokenizer = new StringTokenizer(recipients, JAMES_MAIL_SEPARATOR);
        while (recipientTokenizer.hasMoreTokens()) {
            String token = recipientTokenizer.nextToken();
            try {
                MailAddress rcpt = new MailAddress(token);
                rcpts.add(rcpt);
            } catch (AddressException e) {
                // Should never happen as long as the user does not modify the
                // the header by himself
                LOGGER.error("Unable to parse the recipient address {} for mail {}, so we ignore it", token, mail.getName(), e);
            }
        }
        mail.setRecipients(rcpts);
        mail.setRemoteAddr(message.getStringProperty(JAMES_MAIL_REMOTEADDR));
        mail.setRemoteHost(message.getStringProperty(JAMES_MAIL_REMOTEHOST));

        String attributeNames = message.getStringProperty(JAMES_MAIL_ATTRIBUTE_NAMES);
        StringTokenizer namesTokenizer = new StringTokenizer(attributeNames, JAMES_MAIL_SEPARATOR);
        while (namesTokenizer.hasMoreTokens()) {
            String name = namesTokenizer.nextToken();

            // Now cast the property back to Serializable and set it as attribute.
            // See JAMES-1241
            Object attrValue = message.getObjectProperty(name);

            // ignore null values. See JAMES-1294
            if (attrValue != null) {
                if (attrValue instanceof Serializable) {
                    mail.setAttribute(name, (Serializable) attrValue);
                } else {
                    LOGGER.error("Not supported mail attribute {} of type {} for mail {}", name, attrValue, mail.getName());
                }
            }
        }

        String sender = message.getStringProperty(JAMES_MAIL_SENDER);
        if (sender == null || sender.trim().length() <= 0) {
            mail.setSender(null);
        } else {
            try {
                mail.setSender(new MailAddress(sender));
            } catch (AddressException e) {
                // Should never happen as long as the user does not modify the
                // the header by himself
                LOGGER.error("Unable to parse the sender address {} for mail {}, so we fallback to a null sender", sender, mail.getName(), e);
                mail.setSender(null);
            }
        }

        mail.setState(message.getStringProperty(JAMES_MAIL_STATE));

    }

    /**
     * Convert the attribute value if necessary.
     *
     * @param value
     * @return convertedValue
     */
    protected Object convertAttributeValue(Object value) {
        if (value == null || value instanceof String || value instanceof Byte || value instanceof Long || value instanceof Double || value instanceof Boolean || value instanceof Integer || value instanceof Short || value instanceof Float) {
            return value;
        }
        return value.toString();
    }

    @Override
    public String toString() {
        return "MailQueue:" + queueName;
    }

    /**
     * Create a {@link org.apache.james.queue.api.MailQueue.MailQueueItem} for the given parameters
     *
     * @param connection
     * @param session
     * @param consumer
     * @param message
     * @return item
     * @throws JMSException
     * @throws MessagingException
     */
    protected MailQueueItem createMailQueueItem(Connection connection, Session session, MessageConsumer consumer, Message message) throws JMSException, MessagingException {
        final Mail mail = createMail(message);
        JMSMailQueueItem jmsMailQueueItem = new JMSMailQueueItem(mail, connection, session, consumer);
        return mailQueueItemDecoratorFactory.decorate(jmsMailQueueItem);
    }

    protected String getMessageSelector() {
        return JAMES_NEXT_DELIVERY + " <= " + System.currentTimeMillis() + " OR " + FORCE_DELIVERY + " = true";
    }

    @SuppressWarnings("unchecked")
    @Override
    public long getSize() throws MailQueueException {
        Session session = null;
        QueueBrowser browser = null;
        int size = 0;
        try {
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue(queueName);

            browser = session.createBrowser(queue);

            Enumeration<Message> messages = browser.getEnumeration();

            while (messages.hasMoreElements()) {
                messages.nextElement();
                size++;
            }
            return size;
        } catch (Exception e) {
            LOGGER.error("Unable to get size of queue {}", queueName, e);
            throw new MailQueueException("Unable to get size of queue " + queueName, e);
        } finally {
            closeBrowser(browser);
            closeSession(session);
        }
    }

    @Override
    public long flush() throws MailQueueException {
        Session session = null;
        Message message = null;
        MessageConsumer consumer = null;
        MessageProducer producer = null;
        boolean first = true;
        long count = 0;
        try {

            session = connection.createSession(true, Session.SESSION_TRANSACTED);
            Queue queue = session.createQueue(queueName);
            consumer = session.createConsumer(queue);
            producer = session.createProducer(queue);

            while (first || message != null) {
                if (first) {
                    // give the consumer 2000 ms to receive messages
                    message = consumer.receive(2000);
                } else {
                    message = consumer.receiveNoWait();
                }
                first = false;

                if (message != null) {
                    Message m = copy(session, message);
                    m.setBooleanProperty(FORCE_DELIVERY, true);
                    producer.send(m, message.getJMSDeliveryMode(), message.getJMSPriority(), message.getJMSExpiration());
                    count++;
                }
            }
            session.commit();
            return count;
        } catch (Exception e) {
            LOGGER.error("Unable to flush mail", e);
            rollback(session);
            throw new MailQueueException("Unable to get size of queue " + queueName, e);
        } finally {
            closeConsumer(consumer);
            closeProducer(producer);
            closeSession(session);
        }
    }

    @Override
    public long clear() throws MailQueueException {
        return count(removeWithSelector(null));
    }

    protected long count(List<Message> msgs) {
        if (msgs == null) {
            return -1;
        } else {
            return msgs.size();
        }
    }

    /**
     * Remove messages with the given selector
     *
     * @param selector
     * @return messages
     */
    public List<Message> removeWithSelector(String selector) throws MailQueueException {
        Session session = null;
        Message message = null;
        MessageConsumer consumer = null;
        boolean first = true;
        List<Message> messages = new ArrayList<>();

        try {
            session = connection.createSession(true, Session.SESSION_TRANSACTED);
            Queue queue = session.createQueue(queueName);
            consumer = session.createConsumer(queue, selector);
            while (first || message != null) {
                if (first) {
                    // give the consumer 2000 ms to receive messages
                    message = consumer.receive(2000);
                } else {
                    message = consumer.receiveNoWait();
                }
                first = false;
                if (message != null) {
                    messages.add(message);
                }
            }
            session.commit();
            return messages;
        } catch (Exception e) {
            rollback(session);
            throw new MailQueueException("Unable to remove mails", e);

        } finally {
            closeConsumer(consumer);
            closeSession(session);
        }
    }

    /**
     * Create a copy of the given {@link Message}. This includes the properties
     * and the payload
     *
     * @param session
     * @param m
     * @return copy
     * @throws JMSException
     */
    @SuppressWarnings("unchecked")
    protected Message copy(Session session, Message m) throws JMSException {
        ObjectMessage message = (ObjectMessage) m;
        ObjectMessage copy = session.createObjectMessage(message.getObject());

        Enumeration<String> properties = message.getPropertyNames();
        while (properties.hasMoreElements()) {
            String name = properties.nextElement();
            copy.setObjectProperty(name, message.getObjectProperty(name));
        }

        return copy;
    }

    @Override
    public long remove(Type type, String value) throws MailQueueException {
        switch (type) {
            case Name:
                return count(removeWithSelector(JAMES_MAIL_NAME + " = '" + value + "'"));
            case Sender:
                return count(removeWithSelector(JAMES_MAIL_SENDER + " = '" + value + "'"));
            case Recipient:
                return count(removeWithSelector(JAMES_MAIL_RECIPIENTS + " = '" + value + "' or " + JAMES_MAIL_RECIPIENTS + " = '%," + value + "' or " + JAMES_MAIL_RECIPIENTS + " = '%," + value + "%'"));
            default:
                break;
        }
        return -1;
    }

    @Override
    @SuppressWarnings("unchecked")
    public MailQueueIterator browse() throws MailQueueException {
        Session session = null;
        QueueBrowser browser = null;
        try {
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue(queueName);

            browser = session.createBrowser(queue);

            final Enumeration<Message> messages = browser.getEnumeration();

            final Session mySession = session;
            final QueueBrowser myBrowser = browser;

            return new MailQueueIterator() {

                @Override
                public void remove() {
                    throw new UnsupportedOperationException("Read-only");
                }

                @Override
                public MailQueueItemView next() {
                    while (hasNext()) {
                        try {
                            Message m = messages.nextElement();
                            return new MailQueueItemView(createMail(m),
                                m.getLongProperty(JAMES_NEXT_DELIVERY));
                        } catch (MessagingException | JMSException e) {
                            LOGGER.error("Unable to browse queue", e);
                        }
                    }

                    throw new NoSuchElementException();
                }

                @Override
                public boolean hasNext() {
                    return messages.hasMoreElements();
                }

                @Override
                public void close() {
                    closeBrowser(myBrowser);
                    closeSession(mySession);
                }
            };

        } catch (Exception e) {

            closeBrowser(browser);
            closeSession(session);

            LOGGER.error("Unable to browse queue {}", queueName, e);
            throw new MailQueueException("Unable to browse queue " + queueName, e);
        }
    }

    @Override
    public void dispose() {
        try {
            connection.close();
        } catch (JMSException e) {
            LOGGER.error("Error while closing session", e);
        }
    }
    
}
