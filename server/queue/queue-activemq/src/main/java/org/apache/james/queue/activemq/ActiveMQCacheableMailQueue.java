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
import java.util.Map;

import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;
import jakarta.mail.MessagingException;

import org.apache.james.metrics.api.GaugeRegistry;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.MailQueueItemDecoratorFactory;
import org.apache.james.queue.api.MailQueueName;
import org.apache.james.queue.jms.JMSCacheableMailQueue;
import org.apache.mailet.Mail;

/**
 * <p>
 * {@link MailQueue} implementation backed by Apache ActiveMQ Artemis.
 * </p>
 * <p>
 * Fixes JAMES-4192: Instead of using JMS message selectors for delayed messages
 * (which cause head-of-line blocking and memory deadlocks when paging in Artemis),
 * this queue relies on Artemis native scheduled delivery via {@code _AMQ_SCHED_DELIVERY}
 * and consumes messages without a selector filter.
 * </p>
 */
public class ActiveMQCacheableMailQueue extends JMSCacheableMailQueue {

    /**
     * Artemis scheduled delivery header name.
     * Value is the epoch timestamp in milliseconds when the message should be delivered.
     */
    public static final String AMQ_SCHEDULED_DELIVERY = "_AMQ_SCHED_DELIVERY";

    private static final ThreadLocal<Long> CURRENT_DELIVERY_DELAY = new ThreadLocal<>();

    public ActiveMQCacheableMailQueue(ConnectionFactory connectionFactory,
                                      MailQueueItemDecoratorFactory mailQueueItemDecoratorFactory,
                                      MailQueueName queueName,
                                      MetricFactory metricFactory,
                                      GaugeRegistry gaugeRegistry) {
        super(connectionFactory, mailQueueItemDecoratorFactory, queueName, metricFactory, gaugeRegistry);
    }

    /**
     * Artemis handles scheduled delivery natively via its ScheduledDeliveryHandler.
     * No message selector is needed on consumers, completely eliminating head-of-line blocking
     * and paging deadlocks (JAMES-4192).
     */
    @Override
    protected String getMessageSelector() {
        return null;
    }

    @Override
    public void enQueue(Mail mail, Duration delay) throws MailQueue.MailQueueException {
        long delayMillis = (delay != null && !delay.isNegative()) ? delay.toMillis() : 0L;
        if (delayMillis > 0) {
            CURRENT_DELIVERY_DELAY.set(delayMillis);
        }
        try {
            super.enQueue(mail, delay);
        } finally {
            CURRENT_DELIVERY_DELAY.remove();
        }
    }

    @Override
    protected void produceMail(Map<String, Object> props, int msgPrio, Mail mail) throws JMSException, MessagingException, IOException {
        Long delayMillis = CURRENT_DELIVERY_DELAY.get();
        if (delayMillis != null && delayMillis > 0) {
            long deliverAt = System.currentTimeMillis() + delayMillis;
            props.put(AMQ_SCHEDULED_DELIVERY, deliverAt);
        }
        super.produceMail(props, msgPrio, mail);
    }
}
