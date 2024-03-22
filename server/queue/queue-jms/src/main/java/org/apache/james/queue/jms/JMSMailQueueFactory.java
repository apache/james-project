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

import jakarta.inject.Inject;
import jakarta.jms.ConnectionFactory;

import org.apache.james.metrics.api.GaugeRegistry;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.queue.api.MailQueueItemDecoratorFactory;
import org.apache.james.queue.api.MailQueueName;
import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.james.queue.library.AbstractMailQueueFactory;

/**
 * {@link MailQueueFactory} implementation which use JMS
 */
public class JMSMailQueueFactory extends AbstractMailQueueFactory<ManageableMailQueue> {

    protected final ConnectionFactory connectionFactory;
    protected final MailQueueItemDecoratorFactory mailQueueItemDecoratorFactory;
    protected final MetricFactory metricFactory;
    protected final GaugeRegistry gaugeRegistry;

    @Inject
    public JMSMailQueueFactory(ConnectionFactory connectionFactory, MailQueueItemDecoratorFactory mailQueueItemDecoratorFactory,
                               MetricFactory metricFactory, GaugeRegistry gaugeRegistry) {
        this.connectionFactory = connectionFactory;
        this.mailQueueItemDecoratorFactory = mailQueueItemDecoratorFactory;
        this.metricFactory = metricFactory;
        this.gaugeRegistry = gaugeRegistry;
    }

    @Override
    protected ManageableMailQueue createCacheableMailQueue(MailQueueName name) {
        return new JMSCacheableMailQueue(connectionFactory, mailQueueItemDecoratorFactory, name, metricFactory, gaugeRegistry);
    }

}
