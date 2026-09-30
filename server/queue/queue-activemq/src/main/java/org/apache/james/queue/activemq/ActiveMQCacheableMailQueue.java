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
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.Queue;
import jakarta.jms.QueueBrowser;
import jakarta.jms.Session;
import jakarta.mail.MessagingException;

import org.apache.commons.collections.iterators.EnumerationIterator;
import org.apache.james.metrics.api.GaugeRegistry;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.api.TimeMetric;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.MailQueueItemDecoratorFactory;
import org.apache.james.queue.api.MailQueueName;
import org.apache.james.queue.jms.JMSCacheableMailQueue;
import org.apache.james.util.concurrent.NamedThreadFactory;
import org.apache.mailet.AttributeUtils;
import org.apache.mailet.Mail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Iterators;

/**
 * <p>
 * {@link MailQueue} implementation backed by Apache ActiveMQ Artemis.
 * </p>
 * <p>
 * Fixes JAMES-4192: Instead of using JMS message selectors on the main queue
 * (which cause head-of-line blocking and memory deadlocks when paging in Artemis),
 * this queue routes delayed messages to a dedicated separate delayed queue ({@code <queueName>-delayed}).
 * Consumers on the main queue consume directly with NO selector at wire speed.
 * A background scheduler periodically transfers ready messages from the delayed queue
 * to the main queue.
 * </p>
 */
public class ActiveMQCacheableMailQueue extends JMSCacheableMailQueue {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActiveMQCacheableMailQueue.class);
    private static final String DELAYED_QUEUE_SUFFIX = "-delayed";

    private final String delayedQueueName;
    private final Session delayedSession;
    private final Queue delayedJmsQueue;
    private final MessageProducer delayedProducer;
    private final ScheduledExecutorService transferScheduler;

    public ActiveMQCacheableMailQueue(ConnectionFactory connectionFactory,
                                      MailQueueItemDecoratorFactory mailQueueItemDecoratorFactory,
                                      MailQueueName queueName,
                                      MetricFactory metricFactory,
                                      GaugeRegistry gaugeRegistry) {
        super(connectionFactory, mailQueueItemDecoratorFactory, queueName, metricFactory, gaugeRegistry);
        this.delayedQueueName = queueName.asString() + DELAYED_QUEUE_SUFFIX;
        try {
            this.delayedSession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            this.delayedJmsQueue = delayedSession.createQueue(delayedQueueName);
            this.delayedProducer = delayedSession.createProducer(delayedJmsQueue);
        } catch (JMSException e) {
            throw new RuntimeException("Unable to initialize delayed queue for " + queueName.asString(), e);
        }

        this.transferScheduler = Executors.newSingleThreadScheduledExecutor(
            NamedThreadFactory.withName("ActiveMQMailQueue-Transfer-" + queueName.asString()));
        this.transferScheduler.scheduleWithFixedDelay(this::transferReadyDelayedMessagesSafe, 50, 50, TimeUnit.MILLISECONDS);
    }

    /**
     * Consumes from the main queue without any message selector, completely eliminating
     * head-of-line blocking and memory paging deadlocks (JAMES-4192).
     */
    @Override
    protected String getMessageSelector() {
        return null;
    }

    @Override
    public void enQueue(Mail mail, Duration delay) throws MailQueue.MailQueueException {
        long delayMillis = (delay != null && !delay.isNegative()) ? delay.toMillis() : 0L;
        if (delayMillis > 0) {
            enQueueDelayed(mail, delay);
        } else {
            super.enQueue(mail, delay);
        }
    }

    private void enQueueDelayed(Mail mail, Duration delay) throws MailQueue.MailQueueException {
        TimeMetric timeMetric = metricFactory.timer(ENQUEUED_TIMER_METRIC_NAME_PREFIX + queueName.asString());
        long nextDeliveryTimestamp = computeNextDeliveryTimestamp(delay);

        try {
            int rawPriority = AttributeUtils.getValueAndCastFromMail(mail, MAIL_PRIORITY, Integer.class)
                .orElse(NORMAL_PRIORITY);
            int msgPrio = Math.max(0, Math.min(9, rawPriority));

            Map<String, Object> props = getJMSProperties(mail, nextDeliveryTimestamp);

            synchronized (delayedProducer) {
                produceMailToQueue(delayedSession, delayedProducer, props, msgPrio, mail);
            }

            enqueuedMailsMetric.increment();
        } catch (Exception e) {
            throw new MailQueue.MailQueueException("Unable to enqueue delayed mail " + mail, e);
        } finally {
            timeMetric.stopAndPublish();
        }
    }

    private void produceMailToQueue(Session targetSession, MessageProducer targetProducer,
                                    Map<String, Object> props, int msgPrio, Mail mail)
            throws JMSException, MessagingException, IOException {
        jakarta.jms.ObjectMessage message = targetSession.createObjectMessage();
        for (Map.Entry<String, Object> entry : props.entrySet()) {
            message.setObjectProperty(entry.getKey(), entry.getValue());
        }

        long size = mail.getMessageSize();
        java.io.ByteArrayOutputStream out = size > -1 ? new java.io.ByteArrayOutputStream((int) size) : new java.io.ByteArrayOutputStream();
        mail.getMessage().writeTo(out);
        message.setObject(out.toByteArray());

        targetProducer.send(message, Message.DEFAULT_DELIVERY_MODE, msgPrio, Message.DEFAULT_TIME_TO_LIVE);
    }

    private void transferReadyDelayedMessagesSafe() {
        try {
            transferReadyDelayedMessages();
        } catch (Exception e) {
            LOGGER.warn("Error transferring delayed messages from {} to {}", delayedQueueName, queueName.asString(), e);
        }
    }

    private void transferReadyDelayedMessages() throws JMSException {
        String selector = JAMES_NEXT_DELIVERY + " <= " + System.currentTimeMillis() + " OR " + FORCE_DELIVERY + " = true";
        try (Session transferSession = connection.createSession(true, Session.SESSION_TRANSACTED)) {
            Queue delayedQueue = transferSession.createQueue(delayedQueueName);
            Queue mainQueue = transferSession.createQueue(queueName.asString());
            try (MessageConsumer consumer = transferSession.createConsumer(delayedQueue, selector);
                 MessageProducer mainProducer = transferSession.createProducer(mainQueue)) {
                Message message;
                while ((message = consumer.receiveNoWait()) != null) {
                    Message copy = copy(transferSession, message);
                    mainProducer.send(copy, message.getJMSDeliveryMode(), message.getJMSPriority(), message.getJMSExpiration());
                }
                transferSession.commit();
            } catch (Exception e) {
                rollback(transferSession);
                throw e;
            }
        }
    }

    @Override
    public long getSize() throws MailQueue.MailQueueException {
        long mainSize = super.getSize();
        long delayedSize = 0;
        try (QueueBrowser browser = delayedSession.createBrowser(delayedJmsQueue)) {
            Enumeration<?> enumeration = browser.getEnumeration();
            delayedSize = Iterators.size(new EnumerationIterator(enumeration));
        } catch (Exception e) {
            LOGGER.error("Unable to get size of delayed queue {}", delayedQueueName, e);
            throw new MailQueue.MailQueueException("Unable to get size of delayed queue " + delayedQueueName, e);
        }
        return mainSize + delayedSize;
    }

    @Override
    public long flush() throws MailQueue.MailQueueException {
        long flushedDelayed = 0;
        try (Session txSession = connection.createSession(true, Session.SESSION_TRANSACTED)) {
            Queue delayedQueue = txSession.createQueue(delayedQueueName);
            Queue mainQueue = txSession.createQueue(queueName.asString());
            try (MessageConsumer consumer = txSession.createConsumer(delayedQueue);
                 MessageProducer mainProducer = txSession.createProducer(mainQueue)) {
                Message message;
                while ((message = consumer.receiveNoWait()) != null) {
                    Message copy = copy(txSession, message);
                    copy.setBooleanProperty(FORCE_DELIVERY, true);
                    mainProducer.send(copy, message.getJMSDeliveryMode(), message.getJMSPriority(), message.getJMSExpiration());
                    flushedDelayed++;
                }
                txSession.commit();
            } catch (Exception e) {
                rollback(txSession);
                throw new MailQueue.MailQueueException("Unable to flush delayed queue " + delayedQueueName, e);
            }
        } catch (JMSException e) {
            throw new MailQueue.MailQueueException("Unable to flush delayed queue " + delayedQueueName, e);
        }

        long flushedMain = super.flush();
        return flushedDelayed + flushedMain;
    }

    @Override
    public long clear() throws MailQueue.MailQueueException {
        long mainCleared = super.clear();
        long delayedCleared = count(removeWithSelectorFromDelayedQueue(null));
        return mainCleared + delayedCleared;
    }

    @Override
    public long remove(Type type, String value) throws MailQueue.MailQueueException {
        long mainRemoved = super.remove(type, value);
        String selector = buildSelector(type, value);
        long delayedRemoved = (selector != null) ? count(removeWithSelectorFromDelayedQueue(selector)) : 0;
        return mainRemoved + delayedRemoved;
    }

    private String buildSelector(Type type, String value) {
        switch (type) {
            case Name:
                return JAMES_MAIL_NAME + " = '" + value + "'";
            case Sender:
                return JAMES_MAIL_SENDER + " = '" + value + "'";
            case Recipient:
                return String.join(" or ",
                    JAMES_MAIL_RECIPIENTS + " = '" + value + "'",
                    JAMES_MAIL_RECIPIENTS + " LIKE '" + value + JAMES_MAIL_SEPARATOR + "%'",
                    JAMES_MAIL_RECIPIENTS + " LIKE '%" + JAMES_MAIL_SEPARATOR + value + JAMES_MAIL_SEPARATOR + "%'",
                    JAMES_MAIL_RECIPIENTS + " LIKE '%" + JAMES_MAIL_SEPARATOR + value + "'"
                );
            default:
                return null;
        }
    }

    private List<Message> removeWithSelectorFromDelayedQueue(String selector) throws MailQueue.MailQueueException {
        boolean first = true;
        List<Message> messages = new ArrayList<>();
        try {
            try (Session txSession = connection.createSession(true, Session.SESSION_TRANSACTED)) {
                Queue dQueue = txSession.createQueue(delayedQueueName);
                try (MessageConsumer consumer = txSession.createConsumer(dQueue, selector)) {
                    Message message = null;
                    while (first || message != null) {
                        if (first) {
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
                txSession.commit();
            }
            return messages;
        } catch (Exception e) {
            throw new MailQueue.MailQueueException("Unable to remove mails from delayed queue", e);
        }
    }

    @Override
    public MailQueueIterator browse() throws MailQueue.MailQueueException {
        MailQueueIterator mainIterator = super.browse();
        QueueBrowser delayedBrowser = null;
        try {
            delayedBrowser = delayedSession.createBrowser(delayedJmsQueue);
            Enumeration<Message> delayedMessages = delayedBrowser.getEnumeration();
            return new CombinedMailQueueIterator(mainIterator, delayedBrowser, delayedMessages);
        } catch (Exception e) {
            if (delayedBrowser != null) {
                try {
                    delayedBrowser.close();
                } catch (JMSException ignored) {
                    // Ignore. See JAMES-2509
                }
            }
            mainIterator.close();
            throw new MailQueue.MailQueueException("Unable to browse queues", e);
        }
    }

    private final class CombinedMailQueueIterator implements MailQueueIterator {
        private final MailQueueIterator mainIterator;
        private final QueueBrowser delayedBrowser;
        private final Enumeration<Message> delayedMessages;

        CombinedMailQueueIterator(MailQueueIterator mainIterator, QueueBrowser delayedBrowser, Enumeration<Message> delayedMessages) {
            this.mainIterator = mainIterator;
            this.delayedBrowser = delayedBrowser;
            this.delayedMessages = delayedMessages;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("Read-only");
        }

        @Override
        public MailQueueItemView next() {
            if (mainIterator.hasNext()) {
                return mainIterator.next();
            }
            while (delayedMessages.hasMoreElements()) {
                try {
                    Message m = delayedMessages.nextElement();
                    return new DefaultMailQueueItemView(createMail(m), nextDeliveryDate(m));
                } catch (MessagingException | JMSException e) {
                    LOGGER.error("Unable to browse delayed queue", e);
                }
            }
            throw new NoSuchElementException();
        }

        @Override
        public boolean hasNext() {
            return mainIterator.hasNext() || delayedMessages.hasMoreElements();
        }

        @Override
        public void close() {
            try {
                mainIterator.close();
            } finally {
                if (delayedBrowser != null) {
                    try {
                        delayedBrowser.close();
                    } catch (JMSException e) {
                        // Ignore. See JAMES-2509
                    }
                }
            }
        }

        private ZonedDateTime nextDeliveryDate(Message m) throws JMSException {
            long nextDeliveryTimestamp = m.getLongProperty(JAMES_NEXT_DELIVERY);
            return Instant.ofEpochMilli(nextDeliveryTimestamp).atZone(ZoneId.systemDefault());
        }
    }

    @Override
    public void dispose() {
        transferScheduler.shutdown();
        try {
            if (!transferScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                transferScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            transferScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        closeProducer(delayedProducer);
        closeSession(delayedSession);
        super.dispose();
    }
}
