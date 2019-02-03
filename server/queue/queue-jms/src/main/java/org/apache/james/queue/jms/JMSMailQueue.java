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
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
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

import org.apache.commons.collections.iterators.EnumerationIterator;
import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.lifecycle.api.Disposable;
import org.apache.james.metrics.api.Gauge;
import org.apache.james.metrics.api.GaugeRegistry;
import org.apache.james.metrics.api.Metric;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.api.TimeMetric;
import org.apache.james.queue.api.MailPrioritySupport;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.MailQueueItemDecoratorFactory;
import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.james.server.core.MailImpl;
import org.apache.james.server.core.MimeMessageCopyOnWriteProxy;
import org.apache.james.util.SerializationUtil;
import org.apache.mailet.Mail;
import org.apache.mailet.PerRecipientHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.threeten.extra.Temporals;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterators;

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
                // Ignore. See JAMES-2509
            }
        }
    }

    protected static void closeProducer(MessageProducer producer) {
        if (producer != null) {
            try {
                producer.close();
            } catch (JMSException e) {
                // Ignore. See JAMES-2509
            }
        }
    }

    protected static void closeConsumer(MessageConsumer consumer) {
        if (consumer != null) {
            try {
                consumer.close();
            } catch (JMSException e) {
                // Ignore. See JAMES-2509
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
                // Ignore. See JAMES-2509
            }
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(JMSMailQueue.class);

    public static final String FORCE_DELIVERY = "FORCE_DELIVERY";

    protected final String queueName;
    protected final Connection connection;
    protected final MailQueueItemDecoratorFactory mailQueueItemDecoratorFactory;
    protected final Metric enqueuedMailsMetric;
    protected final Metric dequeuedMailsMetric;
    protected final MetricFactory metricFactory;
    protected final GaugeRegistry gaugeRegistry;

    protected final Session session;
    protected final Queue queue;
    protected final MessageProducer producer;

    private final Joiner joiner;
    private final Splitter splitter;

    public JMSMailQueue(ConnectionFactory connectionFactory, MailQueueItemDecoratorFactory mailQueueItemDecoratorFactory,
                        String queueName, MetricFactory metricFactory,
                        GaugeRegistry gaugeRegistry) {
        try {
            connection = connectionFactory.createConnection();
            connection.start();
        } catch (JMSException e) {
            throw new RuntimeException(e);
        }
        this.mailQueueItemDecoratorFactory = mailQueueItemDecoratorFactory;
        this.queueName = queueName;
        this.metricFactory = metricFactory;
        this.enqueuedMailsMetric = metricFactory.generate(ENQUEUED_METRIC_NAME_PREFIX + queueName);
        this.dequeuedMailsMetric = metricFactory.generate(DEQUEUED_METRIC_NAME_PREFIX + queueName);

        this.gaugeRegistry = gaugeRegistry;
        this.gaugeRegistry.register(QUEUE_SIZE_METRIC_NAME_PREFIX + queueName, queueSizeGauge());

        this.joiner = Joiner.on(JAMES_MAIL_SEPARATOR).skipNulls();
        this.splitter = Splitter.on(JAMES_MAIL_SEPARATOR)
                .omitEmptyStrings() // ignore null values. See JAMES-1294
                .trimResults();
        try {
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            queue = session.createQueue(queueName);
            producer = session.createProducer(queue);
        } catch (JMSException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getName() {
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
        MessageConsumer consumer = null;

        while (true) {
            TimeMetric timeMetric = metricFactory.timer(DEQUEUED_TIMER_METRIC_NAME_PREFIX + queueName);
            try {
                session = connection.createSession(true, Session.SESSION_TRANSACTED);
                Queue queue = session.createQueue(queueName);
                consumer = session.createConsumer(queue, getMessageSelector());

                Message message = consumer.receive(10000);

                if (message != null) {
                    dequeuedMailsMetric.increment();
                    return createMailQueueItem(session, consumer, message);
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
        TimeMetric timeMetric = metricFactory.timer(ENQUEUED_TIMER_METRIC_NAME_PREFIX + queueName);

        long nextDeliveryTimestamp = computeNextDeliveryTimestamp(delay, unit);

        try {

            int msgPrio = NORMAL_PRIORITY;
            Object prio = mail.getAttribute(MAIL_PRIORITY);
            if (prio instanceof Integer) {
                msgPrio = (Integer) prio;
            }

            Map<String, Object> props = getJMSProperties(mail, nextDeliveryTimestamp);
            produceMail(props, msgPrio, mail);

            enqueuedMailsMetric.increment();
        } catch (Exception e) {
            throw new MailQueueException("Unable to enqueue mail " + mail, e);
        } finally {
            timeMetric.stopAndPublish();
        }
    }

    public long computeNextDeliveryTimestamp(long delay, TimeUnit unit) {
        if (delay > 0) {
            try {
                return ZonedDateTime.now()
                    .plus(delay, Temporals.chronoUnit(unit))
                    .toInstant()
                    .toEpochMilli();
            } catch (ArithmeticException e) {
                LOGGER.warn("The {} was caused by conversation {}({}) followed by addition to current timestamp. Falling back to Long.MAX_VALUE.",
                        e.getMessage(), delay, unit.name());

                return Long.MAX_VALUE;
            }
        }
        return NO_DELAY;
    }

    @Override
    public void enQueue(Mail mail) throws MailQueueException {
        enQueue(mail, NO_DELAY, TimeUnit.MILLISECONDS);
    }

    /**
     * Produce the mail to the JMS Queue
     */
    protected void produceMail(Map<String, Object> props, int msgPrio, Mail mail) throws JMSException, MessagingException, IOException {
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
    }

    protected Map<String, Object> getJMSProperties(Mail mail, long nextDelivery) throws MessagingException {
        Map<String, Object> props = new HashMap<>();
        props.put(JAMES_NEXT_DELIVERY, nextDelivery);
        props.put(JAMES_MAIL_ERROR_MESSAGE, mail.getErrorMessage());
        props.put(JAMES_MAIL_LAST_UPDATED, mail.getLastUpdated().getTime());
        props.put(JAMES_MAIL_MESSAGE_SIZE, mail.getMessageSize());
        props.put(JAMES_MAIL_NAME, mail.getName());

        // won't serialize the empty headers so it is mandatory
        // to handle nulls when reconstructing mail from message
        if (!mail.getPerRecipientSpecificHeaders().getHeadersByRecipient().isEmpty()) {
            props.put(JAMES_MAIL_PER_RECIPIENT_HEADERS, SerializationUtil.serialize(mail.getPerRecipientSpecificHeaders()));
        }

        String recipientsAsString = joiner.join(mail.getRecipients());

        props.put(JAMES_MAIL_RECIPIENTS, recipientsAsString);
        props.put(JAMES_MAIL_REMOTEADDR, mail.getRemoteAddr());
        props.put(JAMES_MAIL_REMOTEHOST, mail.getRemoteHost());

        String sender = mail.getMaybeSender().asString("");

        org.apache.james.util.streams.Iterators.toStream(mail.getAttributeNames())
                .forEach(attrName -> props.put(attrName, SerializationUtil.serialize(mail.getAttribute(attrName))));

        props.put(JAMES_MAIL_ATTRIBUTE_NAMES, joiner.join(mail.getAttributeNames()));
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

        Optional.ofNullable(SerializationUtil.<PerRecipientHeaders>deserialize(message.getStringProperty(JAMES_MAIL_PER_RECIPIENT_HEADERS)))
                .ifPresent(mail::addAllSpecificHeaderForRecipient);

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

        splitter.split(attributeNames)
                .forEach(name -> setMailAttribute(message, mail, name));

        MaybeSender.getMailSender(message.getStringProperty(JAMES_MAIL_SENDER))
            .asOptional().ifPresent(mail::setSender);
        mail.setState(message.getStringProperty(JAMES_MAIL_STATE));
    }

    /**
     * Retrieves the attribute by {@code name} form {@code message} and tries to add it on {@code mail}.
     *
     * @param message The attribute source.
     * @param mail    The mail on which attribute should be set.
     * @param name    The attribute name.
     */
    private void setMailAttribute(Message message, Mail mail, String name) {
        // Now cast the property back to Serializable and set it as attribute.
        // See JAMES-1241
        Object attrValue = Throwing.function(message::getObjectProperty).apply(name);

        if (attrValue instanceof String) {
            mail.setAttribute(name, SerializationUtil.deserialize((String) attrValue));
        } else {
            LOGGER.error("Not supported mail attribute {} of type {} for mail {}", name, attrValue, mail.getName());
        }
    }

    private Gauge<Long> queueSizeGauge() {
        return () -> Throwing.supplier(this::getSize).get();
    }

    @Override
    public String toString() {
        return "MailQueue:" + queueName;
    }

    /**
     * Create a {@link org.apache.james.queue.api.MailQueue.MailQueueItem} for the given parameters
     *
     * @param session
     * @param consumer
     * @param message
     * @return item
     * @throws JMSException
     * @throws MessagingException
     */
    protected MailQueueItem createMailQueueItem(Session session, MessageConsumer consumer, Message message) throws JMSException, MessagingException {
        Mail mail = createMail(message);
        JMSMailQueueItem jmsMailQueueItem = new JMSMailQueueItem(mail, session, consumer);
        return mailQueueItemDecoratorFactory.decorate(jmsMailQueueItem);
    }

    protected String getMessageSelector() {
        return JAMES_NEXT_DELIVERY + " <= " + System.currentTimeMillis() + " OR " + FORCE_DELIVERY + " = true";
    }

    @Override
    public long getSize() throws MailQueueException {
        try (QueueBrowser browser = session.createBrowser(queue)) {
            Enumeration<?> enumeration = browser.getEnumeration();
            return Iterators.size(new EnumerationIterator(enumeration));
        } catch (Exception e) {
            LOGGER.error("Unable to get size of queue {}", queueName, e);
            throw new MailQueueException("Unable to get size of queue " + queueName, e);
        }
    }

    @Override
    public long flush() throws MailQueueException {
        boolean first = true;
        long count = 0;
        try (Session session = connection.createSession(true, Session.SESSION_TRANSACTED)) {
            Queue queue = session.createQueue(queueName);
            try (MessageConsumer consumer = session.createConsumer(queue)) {
                try (MessageProducer producer = session.createProducer(queue)) {

                    Message message = null;
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
                }
            }
        } catch (Exception e) {
            LOGGER.error("Unable to flush mail", e);
            throw new MailQueueException("Unable to get size of queue " + queueName, e);
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
        boolean first = true;
        List<Message> messages = new ArrayList<>();

        try {
            try (Session session = connection.createSession(true, Session.SESSION_TRANSACTED)) {
                Queue queue = session.createQueue(queueName);
                try (MessageConsumer consumer = session.createConsumer(queue, selector)) {
                    Message message = null;
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
                }
                session.commit();
            }
            return messages;
        } catch (Exception e) {
            throw new MailQueueException("Unable to remove mails", e);
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
                return count(removeWithSelector(JAMES_MAIL_RECIPIENTS + " = '" + value + "' or " + JAMES_MAIL_RECIPIENTS
                        + " LIKE '%" + JAMES_MAIL_SEPARATOR + value + "' or " + JAMES_MAIL_RECIPIENTS + " LIKE '%"
                        + JAMES_MAIL_SEPARATOR + value + "%'"));
            default:
                break;
        }
        return -1;
    }

    @Override
    @SuppressWarnings("unchecked")
    public MailQueueIterator browse() throws MailQueueException {
        QueueBrowser browser = null;
        try {
            browser = session.createBrowser(queue);

            Enumeration<Message> messages = browser.getEnumeration();
            QueueBrowser myBrowser = browser;

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
                            return new MailQueueItemView(createMail(m), nextDeliveryDate(m));
                        } catch (MessagingException | JMSException e) {
                            LOGGER.error("Unable to browse queue", e);
                        }
                    }

                    throw new NoSuchElementException();
                }

                private ZonedDateTime nextDeliveryDate(Message m) throws JMSException {
                    long nextDeliveryTimestamp = m.getLongProperty(JAMES_NEXT_DELIVERY);
                    return Instant.ofEpochMilli(nextDeliveryTimestamp).atZone(ZoneId.systemDefault());
                }

                @Override
                public boolean hasNext() {
                    return messages.hasMoreElements();
                }

                @Override
                public void close() {
                    closeBrowser(myBrowser);
                }
            };

        } catch (Exception e) {
            closeBrowser(browser);

            LOGGER.error("Unable to browse queue {}", queueName, e);
            throw new MailQueueException("Unable to browse queue " + queueName, e);
        }
    }

    @Override
    public void dispose() {
        try {
            closeProducer(producer);
            closeSession(session);
            connection.close();
        } catch (JMSException e) {
            LOGGER.error("Error while closing session", e);
        }
    }
    
}
