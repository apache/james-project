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

package org.apache.james.queue.activemq.metric;

import jakarta.inject.Inject;

import org.apache.james.metrics.api.GaugeRegistry;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.queue.activemq.ActiveMQConfiguration;
import org.apache.james.queue.api.MailQueueName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ActiveMQ Statistics Plugin-based metric collector stub.
 *
 * The original implementation relied on the ActiveMQ Statistics Plugin
 * ("ActiveMQ.Statistics.Destination.*" queues) which is specific to legacy Apache ActiveMQ
 * and is NOT available in Apache ActiveMQ Artemis.
 *
 * This stub implementation delegates to the noop implementation.
 * To collect Artemis-specific metrics, use the Artemis management API or JMX.
 */
public class ActiveMQMetricCollectorImpl implements ActiveMQMetricCollector {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActiveMQMetricCollectorImpl.class);

    @Inject
    public ActiveMQMetricCollectorImpl(ActiveMQConfiguration activeMQConfiguration,
                                       jakarta.jms.ConnectionFactory connectionFactory,
                                       MetricFactory metricFactory,
                                       GaugeRegistry gaugeRegistry) {
        LOGGER.info("ActiveMQMetricCollectorImpl: ActiveMQ Statistics Plugin is not available in Artemis. " +
            "Metric collection via this class is disabled. Use Artemis JMX or management API for broker metrics.");
    }

    @Override
    public void collectBrokerStatistics() {
        // Not supported with Artemis broker
    }

    @Override
    public void collectQueueStatistics(MailQueueName name) {
        // Not supported with Artemis broker
    }

    @Override
    public void start() {
        LOGGER.info("ActiveMQ Statistics Plugin metric collection is disabled (Artemis broker does not support it).");
    }

    @Override
    public void stop() {
    }
}
