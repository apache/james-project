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

import java.io.ByteArrayOutputStream;
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
import jakarta.jms.ObjectMessage;
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
 *
 * <p><b>JAMES-4192 fix.</b> Delayed mails are NOT filtered on the main queue with a
 * JMS message selector (which caused head-of-line blocking: Artemis evaluates selectors
 * only against in-memory messages, so once paging kicks in, ready mail sitting in page
 * files behind rejected delayed mail is never delivered). Instead:</p>
 *
 * <ol>
 *   <li>Delayed mails are routed to a dedicated companion queue
 *       {@code <queueName>} + {@value #DELAYED_QUEUE_SUFFIX}. They are stored as ordinary
 *       (non-scheduled) JMS messages, so they remain fully browsable and their body is
 *       available &mdash; unlike Artemis native scheduled messages, which are hidden from
 *       {@code browse()}/{@code getSize()} and truncated by the management API
 *       (ARTEMIS-3141/3128/3175).</li>
 *   <li>A background task periodically moves <em>due</em> messages from the delayed queue
 *       to the main queue. Crucially the transfer consumer uses <b>no selector</b>: it
 *       drains messages and evaluates {@code JAMES_NEXT_DELIVERY <= now} <em>in memory</em>,
 *       re-enqueuing not-yet-due messages. This is paging-safe &mdash; it never asks the
 *       broker to evaluate a selector against paged-out messages, so the JAMES-4192
 *       starvation cannot occur.</li>
 *   <li>The main queue is consumed with <b>no selector</b>
 *       ({@link #getMessageSelector()} returns {@code null}) at wire speed.</li>
 * </ol>
 *
 * <p>{@code getSize()}, {@code browse()}, {@code remove()}, {@code clear()} and
 * {@code flush()} span both queues so the {@code ManageableMailQueue} contract holds for
 * delayed mail.</p>
 */
public class ActiveMQCacheableMailQueue extends JMSCacheableMailQueue {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActiveMQCacheableMailQueue.class);
    private static final String DELAYED_QUEUE_SUFFIX = "-delayed";

    /**
     * Interval between two transfer passes moving due mail from the delayed queue to the
     * main queue. Delayed mail is retry traffic (minutes-to-hours delays), so a coarse
     * interval keeps re-enqueue churn of not-yet-due messages low while adding at most
     * this much latency to a mail becoming due.
     */
    private static final Duration TRANSFER_INTERVAL = Duration.ofMillis(200);

    /**
     * Upper bound on the number of messages consumed from the delayed queue per transfer
     * pass, to cap memory and journal churn when the delayed backlog is large. Remaining
     * messages are handled on subsequent passes.
     */
    private static final int TRANSFER_BATCH_LIMIT = 1000;

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
        this.transferScheduler.scheduleWithFixedDelay(this::transferReadyDelayedMessagesSafe,
            TRANSFER_INTERVAL.toMillis(), TRANSFER_INTERVAL.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * No message selector on the main queue: due-time filtering happens during transfer
     * from the delayed queue, not on the delivery consumer. This is the core of the
     * JAMES-4192 fix (see class javadoc).
     */
    @Override
    protected String getMessageSelector() {
        return null;
    }

    @Override
    public void enQueue(Mail mail, Duration delay) throws MailQueue.MailQueueException {
        if (delay != null && !delay.isNegative() && !delay.isZero()) {
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
            ObjectMessage message = createDelayedMessage(props, mail);

            synchronized (delayedProducer) {
                delayedProducer.send(message, Message.DEFAULT_DELIVERY_MODE, msgPrio, Message.DEFAULT_TIME_TO_LIVE);
            }
            enqueuedMailsMetric.increment();
        } catch (Exception e) {
            throw new MailQueue.MailQueueException("Unable to enqueue delayed mail " + mail, e);
        } finally {
            timeMetric.stopAndPublish();
        }
    }

    private ObjectMessage createDelayedMessage(Map<String, Object> props, Mail mail) throws Exception {
        ObjectMessage message = delayedSession.createObjectMessage();
        for (Map.Entry<String, Object> entry : props.entrySet()) {
            message.setObjectProperty(entry.getKey(), entry.getValue());
        }
        long size = mail.getMessageSize();
        ByteArrayOutputStream out = size > -1 ? new ByteArrayOutputStream((int) size) : new ByteArrayOutputStream();
        mail.getMessage().writeTo(out);
        message.setObject(out.toByteArray());
        return message;
    }

    private void transferReadyDelayedMessagesSafe() {
        try {
            transferReadyDelayedMessages();
        } catch (Exception e) {
            LOGGER.warn("Error transferring delayed messages from {} to {}", delayedQueueName, queueName.asString(), e);
        }
    }

    /**
     * Moves due mail from the delayed queue to the main queue. The consumer intentionally
     * uses <b>no selector</b>: all drained messages are held by the transacted session, the
     * due/not-due decision is made in memory, and not-yet-due messages are re-enqueued.
     * This avoids any broker-side selector evaluation against paged-out messages (JAMES-4192).
     */
    private void transferReadyDelayedMessages() throws JMSException {
        long now = System.currentTimeMillis();
        try (Session transferSession = connection.createSession(true, Session.SESSION_TRANSACTED)) {
            Queue delayedQueue = transferSession.createQueue(delayedQueueName);
            Queue mainQueue = transferSession.createQueue(queueName.asString());
            try (MessageConsumer consumer = transferSession.createConsumer(delayedQueue);
                 MessageProducer mainProducer = transferSession.createProducer(mainQueue);
                 MessageProducer requeueProducer = transferSession.createProducer(delayedQueue)) {

                // Drain up to the batch limit first. Consumed messages are retained by the
                // transacted session until commit, so re-enqueued (not-due) messages cannot
                // be re-read within this same pass.
                List<Message> drained = new ArrayList<>();
                Message message;
                while (drained.size() < TRANSFER_BATCH_LIMIT
                        && (message = consumer.receiveNoWait()) != null) {
                    drained.add(message);
                }

                for (Message m : drained) {
                    Message copy = copy(transferSession, m);
                    if (isDue(m, now)) {
                        mainProducer.send(copy, m.getJMSDeliveryMode(), m.getJMSPriority(), m.getJMSExpiration());
                    } else {
                        requeueProducer.send(copy, m.getJMSDeliveryMode(), m.getJMSPriority(), m.getJMSExpiration());
                    }
                }
                transferSession.commit();
            } catch (Exception e) {
                rollback(transferSession);
                throw e;
            }
        }
    }

    private static boolean isDue(Message message, long now) throws JMSException {
        if (message.propertyExists(FORCE_DELIVERY) && message.getBooleanProperty(FORCE_DELIVERY)) {
            return true;
        }
        return message.getLongProperty(JAMES_NEXT_DELIVERY) <= now;
    }

    @Override
    public long getSize() throws MailQueue.MailQueueException {
        long mainSize = super.getSize();
        // Use a dedicated short-lived session: JMS Session is not thread-safe and
        // delayedSession is reserved for the enqueue path.
        try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
            Queue delayedQueue = session.createQueue(delayedQueueName);
            QueueBrowser browser = session.createBrowser(delayedQueue);
            Enumeration<?> enumeration = browser.getEnumeration();
            return mainSize + Iterators.size(new EnumerationIterator(enumeration));
        } catch (Exception e) {
            LOGGER.error("Unable to get size of delayed queue {}", delayedQueueName, e);
            throw new MailQueue.MailQueueException("Unable to get size of delayed queue " + delayedQueueName, e);
        }
    }

    /**
     * Forces immediate delivery of all delayed mail by draining the delayed queue
     * (selector-less, paging-safe) and re-enqueuing every message on the main queue with
     * {@code FORCE_DELIVERY}, then delegates to the base flush for the main queue.
     */
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
        return flushedDelayed + super.flush();
    }

    @Override
    public long clear() throws MailQueue.MailQueueException {
        return super.clear() + count(removeFromDelayedQueue(null));
    }

    @Override
    public long remove(Type type, String value) throws MailQueue.MailQueueException {
        long mainRemoved = super.remove(type, value);
        String selector = buildSelector(type, value);
        long delayedRemoved = selector != null ? count(removeFromDelayedQueue(selector)) : 0;
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
                    JAMES_MAIL_RECIPIENTS + " LIKE '%" + JAMES_MAIL_SEPARATOR + value + "'");
            default:
                return null;
        }
    }

    private List<Message> removeFromDelayedQueue(String selector) throws MailQueue.MailQueueException {
        List<Message> messages = new ArrayList<>();
        try (Session session = connection.createSession(true, Session.SESSION_TRANSACTED)) {
            Queue delayedQueue = session.createQueue(delayedQueueName);
            try (MessageConsumer consumer = selector != null
                    ? session.createConsumer(delayedQueue, selector)
                    : session.createConsumer(delayedQueue)) {
                boolean first = true;
                Message message = null;
                while (first || message != null) {
                    message = first ? consumer.receive(2000) : consumer.receiveNoWait();
                    first = false;
                    if (message != null) {
                        messages.add(message);
                    }
                }
            }
            session.commit();
            return messages;
        } catch (Exception e) {
            throw new MailQueue.MailQueueException("Unable to remove mails from delayed queue " + delayedQueueName, e);
        }
    }

    @Override
    public MailQueueIterator browse() throws MailQueue.MailQueueException {
        MailQueueIterator mainIterator = super.browse();
        // Dedicated session for the lifetime of this iterator: JMS Session is not
        // thread-safe and delayedSession is reserved for the enqueue path. The session
        // is closed by CombinedMailQueueIterator.close().
        Session browseSession = null;
        QueueBrowser delayedBrowser = null;
        try {
            browseSession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue delayedQueue = browseSession.createQueue(delayedQueueName);
            delayedBrowser = browseSession.createBrowser(delayedQueue);
            @SuppressWarnings("unchecked")
            Enumeration<Message> delayedMessages = delayedBrowser.getEnumeration();
            return new CombinedMailQueueIterator(mainIterator, browseSession, delayedBrowser, delayedMessages);
        } catch (Exception e) {
            if (delayedBrowser != null) {
                try {
                    delayedBrowser.close();
                } catch (JMSException ignored) {
                    // Ignore. See JAMES-2509
                }
            }
            closeSession(browseSession);
            mainIterator.close();
            throw new MailQueue.MailQueueException("Unable to browse queues", e);
        }
    }

    private final class CombinedMailQueueIterator implements MailQueueIterator {
        private final MailQueueIterator mainIterator;
        private final Session browseSession;
        private final QueueBrowser delayedBrowser;
        private final Enumeration<Message> delayedMessages;

        CombinedMailQueueIterator(MailQueueIterator mainIterator, Session browseSession, QueueBrowser delayedBrowser, Enumeration<Message> delayedMessages) {
            this.mainIterator = mainIterator;
            this.browseSession = browseSession;
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
                closeSession(browseSession);
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
