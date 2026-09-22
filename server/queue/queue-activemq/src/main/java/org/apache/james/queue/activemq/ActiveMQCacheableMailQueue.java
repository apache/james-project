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

import jakarta.jms.ConnectionFactory;

import org.apache.james.metrics.api.GaugeRegistry;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.MailQueueItemDecoratorFactory;
import org.apache.james.queue.api.MailQueueName;
import org.apache.james.queue.jms.JMSCacheableMailQueue;

/**
 * <p>
 * {@link MailQueue} implementation backed by Apache ActiveMQ Artemis (Jakarta JMS).
 * <p>
 * </p>
 * This implementation replaces the legacy ActiveMQ BlobMessage-based queue with
 * a standard JMS ObjectMessage approach compatible with Artemis.
 * <p>
 * </p>
 * When a {@link org.apache.mailet.Mail} attribute is found and is not one of the supported
 * primitives, then the toString() method is called on the attribute value to
 * convert it
 * <p>
 * </p>
 * For priority support add a {@link org.apache.mailet.Mail} attribute with name
 * {@link #MAIL_PRIORITY} to it. It should use one of the following value
 * {@link #LOW_PRIORITY}, {@link #NORMAL_PRIORITY}, {@link #HIGH_PRIORITY}
 * <p>
 * </p>
 * To have a good throughput you should use a caching connection factory. </p>
 */
public class ActiveMQCacheableMailQueue extends JMSCacheableMailQueue {

    /**
     * Construct a new Artemis-backed {@link MailQueue}.
     *
     * @param connectionFactory        the JMS connection factory
     * @param mailQueueItemDecoratorFactory the item decorator factory
     * @param queuename                the queue name
     * @param metricFactory            the metric factory
     * @param gaugeRegistry            the gauge registry
     */
    public ActiveMQCacheableMailQueue(ConnectionFactory connectionFactory, MailQueueItemDecoratorFactory mailQueueItemDecoratorFactory,
                                      MailQueueName queuename, MetricFactory metricFactory, GaugeRegistry gaugeRegistry) {
        super(connectionFactory, mailQueueItemDecoratorFactory, queuename, metricFactory, gaugeRegistry);
    }

    /**
     * Constructor kept for backward compatibility with callers that pass useBlob flag.
     * The useBlob flag is ignored as Artemis does not support ActiveMQ-specific BlobMessages.
     *
     * @param connectionFactory        the JMS connection factory
     * @param mailQueueItemDecoratorFactory the item decorator factory
     * @param queuename                the queue name
     * @param useBlob                  ignored (Artemis uses standard JMS ObjectMessage)
     * @param metricFactory            the metric factory
     * @param gaugeRegistry            the gauge registry
     */
    public ActiveMQCacheableMailQueue(ConnectionFactory connectionFactory, MailQueueItemDecoratorFactory mailQueueItemDecoratorFactory,
                                      MailQueueName queuename, boolean useBlob, MetricFactory metricFactory, GaugeRegistry gaugeRegistry) {
        super(connectionFactory, mailQueueItemDecoratorFactory, queuename, metricFactory, gaugeRegistry);
    }
}
