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

import java.util.HashMap;
import java.util.Map;

import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;
import jakarta.jms.MapMessage;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.Queue;
import jakarta.jms.Session;
import jakarta.jms.TemporaryQueue;

import org.apache.james.metrics.api.GaugeRegistry;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.queue.activemq.ActiveMQConfiguration;
import org.apache.james.queue.api.MailQueueName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class ActiveMQMetricCollectorImpl implements ActiveMQMetricCollector {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActiveMQMetricCollectorImpl.class);

    private final ActiveMQMetricConfiguration config;
    private final ConnectionFactory connectionFactory;
    private final MetricFactory metricFactory;
    private final GaugeRegistry gaugeRegistry;

    private final Map<String, ActiveMQMetrics> registeredStatistics = new HashMap<>();

    private Disposable disposable;

    @Inject
    public ActiveMQMetricCollectorImpl(ActiveMQConfiguration activeMQConfiguration, ConnectionFactory connectionFactory, MetricFactory metricFactory, GaugeRegistry gaugeRegistry) {
        this.config = activeMQConfiguration.getMetricConfiguration();
        this.connectionFactory = connectionFactory;
        this.metricFactory = metricFactory;
        this.gaugeRegistry = gaugeRegistry;
    }

    @Override
    public void collectBrokerStatistics() {
        collectStatistics(ActiveMQMetrics.forBroker(gaugeRegistry));
    }

    @Override
    public void collectQueueStatistics(MailQueueName name) {
        collectStatistics(ActiveMQMetrics.forQueue(name.asString(), gaugeRegistry));
    }

    private void collectStatistics(ActiveMQMetrics statistics) {
        if (config.isEnabled() && !registeredStatistics.containsKey(statistics.getName())) {
            LOGGER.info("collecting statistics for {}", statistics.getName());
            registeredStatistics.put(statistics.getName(), statistics);
        }
    }

    @Override
    public void start() {
        if (!config.isEnabled()) {
            LOGGER.info("collecting statistics disabled");
            return;
        }

        collectBrokerStatistics();

        LOGGER.info("start delay={} interval={} timeout={} aqmp_timeout={}",
            config.getStartDelay(), config.getInterval(), config.getTimeout(), config.getAqmpTimeout());

        disposable = Flux.interval(config.getStartDelay(), config.getInterval())
            .flatMap(any -> Flux.fromStream(() -> registeredStatistics.values().stream())
                .flatMap((s) -> {
                    Mono<Void> task = Mono.fromCallable(() -> fetchAndUpdate(s)).timeout(config.getTimeout());
                    return metricFactory.decoratePublisherWithTimerMetric(s.getName() + "._time", task);
                })
            )
            .onErrorContinue(this::logError)
            .subscribeOn(Schedulers.newSingle(ActiveMQMetricCollectorImpl.class.getSimpleName()))
            .subscribe();
    }

    @Override
    @PreDestroy
    public void stop() {
        disposable.dispose();
    }

    private Void logError(Throwable error, Object triggeringValue) {
        LOGGER.warn("failed to fetch and update broker statistics", error);
        return null;
    }

    @VisibleForTesting
    Void fetchAndUpdate(ActiveMQMetrics stats) throws JMSException {
        Connection connection = null;
        Session session = null;
        TemporaryQueue replyTo = null;
        MessageConsumer consumer = null;
        MessageProducer producer = null;
        try {
            connection = connectionFactory.createConnection();
            connection.start();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            replyTo = session.createTemporaryQueue();
            consumer = session.createConsumer(replyTo);

            Queue testQueue = session.createQueue(stats.getName());
            producer = session.createProducer(testQueue);
            Message msg = session.createMessage();
            msg.setJMSReplyTo(replyTo);
            producer.send(msg);

            long timeoutMs = config.getAqmpTimeout().toMillis();
            Message reply = consumer.receive(timeoutMs);
            if (reply == null) {
                throw new JMSException("no message received, timed out after " + timeoutMs + " ms");
            } else if (!(reply instanceof MapMessage)) {
                throw new JMSException("expected MapMessage but got " + reply.getClass());
            }
            stats.updateMetrics((MapMessage)reply);
        } finally {
            if (producer != null) {
                try {
                    producer.close();
                } catch (JMSException e) {
                    // ignore
                }
            }

            if (consumer != null) {
                try {
                    consumer.close();
                } catch (JMSException e) {
                    // ignore
                }
            }

            if (replyTo != null) {
                // we need to delete the temporary queue to be sure we will
                // free up memory if thats not done and a pool is used
                // its possible that we will register a new mbean in jmx for
                // every TemporaryQueue which will never get unregistered
                try {
                    replyTo.delete();
                } catch (JMSException e) {
                    // ignore
                }
            }

            if (session != null) {
                try {
                    session.close();
                } catch (JMSException e) {
                    // ignore
                }
            }

            if (connection != null) {
                try {
                    connection.close();
                } catch (JMSException e) {
                    // ignore
                }
            }
        }
        return null;
    }

}
