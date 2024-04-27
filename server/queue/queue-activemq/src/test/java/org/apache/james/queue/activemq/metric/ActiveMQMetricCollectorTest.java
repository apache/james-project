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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.jms.JMSException;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.ActiveMQPrefetchPolicy;
import org.apache.activemq.broker.BrokerService;
import org.apache.james.metrics.api.Gauge;
import org.apache.james.metrics.api.GaugeRegistry;
import org.apache.james.metrics.api.NoopGaugeRegistry;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.queue.activemq.ActiveMQConfiguration;
import org.apache.james.queue.api.MailQueueName;
import org.apache.james.queue.jms.BrokerExtension;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(BrokerExtension.class)
@Tag(BrokerExtension.STATISTICS)
class ActiveMQMetricCollectorTest {

    private static ActiveMQConnectionFactory connectionFactory;
    private static final ActiveMQConfiguration EMPTY_CONFIGURATION = ActiveMQConfiguration.getDefault();

    @BeforeAll
    static void setup(BrokerService broker) {
        connectionFactory = new ActiveMQConnectionFactory("vm://localhost?create=false");
        ActiveMQPrefetchPolicy prefetchPolicy = new ActiveMQPrefetchPolicy();
        prefetchPolicy.setQueuePrefetch(0);
        connectionFactory.setPrefetchPolicy(prefetchPolicy);
    }

    @Test
    void shouldFailToFetchAndUpdateStatisticsForUnknownQueue() {
        SimpleGaugeRegistry gaugeRegistry = new SimpleGaugeRegistry();
        ActiveMQMetricCollectorImpl testee = new ActiveMQMetricCollectorImpl(EMPTY_CONFIGURATION, connectionFactory, new RecordingMetricFactory(), gaugeRegistry);
        ActiveMQMetrics queueStatistics = ActiveMQMetrics.forQueue("UNKNOWN", gaugeRegistry);

        assertThatThrownBy(() -> testee.fetchAndUpdate(queueStatistics))
            .isInstanceOf(JMSException.class);

        assertThat(gaugeRegistry.getGauge("ActiveMQ.Statistics.Destination.UNKNOWN")).isNull();
    }

    @Test
    void shouldFetchAndUpdateBrokerStatistics() throws Exception {
        SimpleGaugeRegistry gaugeRegistry = new SimpleGaugeRegistry();
        ActiveMQMetricCollectorImpl testee = new ActiveMQMetricCollectorImpl(EMPTY_CONFIGURATION, connectionFactory, new RecordingMetricFactory(), gaugeRegistry);
        ActiveMQMetrics brokerStatistics = ActiveMQMetrics.forBroker(gaugeRegistry);

        long notBefore = System.currentTimeMillis();
        testee.fetchAndUpdate(brokerStatistics);
        Number n = gaugeRegistry.getGauge("ActiveMQ.Statistics.Broker.lastUpdate");
        assertThat(n).isInstanceOf(Long.class);
        assertThat((Long) n).isGreaterThanOrEqualTo(notBefore);
    }

    @Test
    void shouldFetchAndUpdateBrokerStatisticsInGaugeRegistry() throws Exception {
        SimpleGaugeRegistry gaugeRegistry = new SimpleGaugeRegistry();
        ActiveMQMetricCollectorImpl testee = new ActiveMQMetricCollectorImpl(EMPTY_CONFIGURATION, connectionFactory, new RecordingMetricFactory(), gaugeRegistry);
        ActiveMQMetrics brokerStatistics = ActiveMQMetrics.forBroker(gaugeRegistry);

        testee.fetchAndUpdate(brokerStatistics);

        Number n = gaugeRegistry.getGauge("ActiveMQ.Statistics.Broker.storeLimit");
        assertThat(n).isInstanceOf(Long.class);
        assertThat((Long) n).isGreaterThan(0);
    }

    @Test
    void hasExecutionTimeMetrics() {
        RecordingMetricFactory metricFactory = new RecordingMetricFactory();
        NoopGaugeRegistry gaugeRegistry = new NoopGaugeRegistry();
        ActiveMQMetricCollector testee = new ActiveMQMetricCollectorImpl(EMPTY_CONFIGURATION, connectionFactory, metricFactory, gaugeRegistry);
        testee.start();
        testee.collectBrokerStatistics();
        testee.collectQueueStatistics(MailQueueName.of("UNKNOWN"));

        Duration startDelay = EMPTY_CONFIGURATION.getMetricConfiguration().getStartDelay();
        Integer executionTimeCount = Flux.interval(startDelay, Duration.ofSeconds(1))
            .take(3,true)
            .flatMap(n -> Mono.fromCallable(() -> metricFactory.executionTimesForPrefixName("ActiveMQ.").size()))
            .blockLast();
        assertThat(executionTimeCount).isNotNull().isNotZero();

        testee.stop();
    }

    private class SimpleGaugeRegistry implements GaugeRegistry {
        private final Map<String, Gauge<?>> gauges = new ConcurrentHashMap<>();

        @Override
        public <T> GaugeRegistry register(String name, Gauge<T> gauge) {
            gauges.put(name, gauge);
            return this;
        }

        @Override
        public <T> SettableGauge<T> settableGauge(String name) {
            return t -> gauges.put(name, () -> t);
        }

        public Number getGauge(String name) {
            Gauge<?> g = gauges.get(name);
            if (g == null) {
                return null;
            }
            return (Number) g.get();
        }
    }
}

