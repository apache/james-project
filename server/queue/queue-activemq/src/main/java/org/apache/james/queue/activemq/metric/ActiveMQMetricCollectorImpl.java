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

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TemporaryQueue;

import org.apache.james.metrics.api.GaugeRegistry;
import org.apache.james.metrics.api.MetricFactory;
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

    public static final Duration REFRESH_DELAY = Duration.ofSeconds(2);
    public static final Duration REFRESH_INTERVAL = Duration.ofSeconds(5);
    public static final Duration RECEIVE_TIMEOUT = Duration.ofSeconds(1);
    public static final Duration REFRESH_TIMEOUT = RECEIVE_TIMEOUT.multipliedBy(2);

    private final ConnectionFactory connectionFactory;
    private final MetricFactory metricFactory;
    private final GaugeRegistry gaugeRegistry;

    private final Map<String, ActiveMQStatistics> registeredStatistics = new HashMap<>();

    private Disposable disposable;

    @Inject
    public ActiveMQMetricCollectorImpl(ConnectionFactory connectionFactory, MetricFactory metricFactory, GaugeRegistry gaugeRegistry) {
        this.connectionFactory = connectionFactory;
        this.metricFactory = metricFactory;
        this.gaugeRegistry = gaugeRegistry;
    }

    @Override
    public void collectBrokerStatistics() {
        collectStatistics(new ActiveMQBrokerStatistics());
    }

    @Override
    public void collectQueueStatistics(MailQueueName name) {
        collectStatistics(ActiveMQQueueStatistics.from(name.asString()));
    }

    private void collectStatistics(ActiveMQStatistics statistics) {
        if (!registeredStatistics.containsKey(statistics.getName())) {
            LOGGER.info("collecting statistics for {}", statistics.getName());
            registeredStatistics.put(statistics.getName(), statistics);
            statistics.registerMetrics(gaugeRegistry);
        }
    }

    @Override
    public void start() {
        collectBrokerStatistics();

        LOGGER.info("start delay={} interval={}", REFRESH_DELAY, REFRESH_INTERVAL);
        disposable = Flux.interval(REFRESH_DELAY, REFRESH_INTERVAL)
            .flatMap(any -> Flux.fromStream(() -> registeredStatistics.values().stream())
                .flatMap((s) -> {
                    Mono<Void> task = Mono.fromCallable(() -> fetchAndUpdate(s)).timeout(REFRESH_TIMEOUT);
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
    Void fetchAndUpdate(ActiveMQStatistics stats) throws JMSException {
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

            Message reply = consumer.receive(RECEIVE_TIMEOUT.toMillis());
            if (reply == null) {
                throw new JMSException("no message received, timed out after " + RECEIVE_TIMEOUT);
            } else if (!(reply instanceof MapMessage)) {
                throw new JMSException("expected MapMessage but got " + reply.getClass());
            }
            stats.update((MapMessage)reply);
        } finally {
            if (producer != null) {
                producer.close();
            }

            if (consumer != null) {
                consumer.close();
            }

            if (replyTo != null) {
                // we need to delete the temporary queue to be sure we will
                // free up memory if thats not done and a pool is used
                // its possible that we will register a new mbean in jmx for
                // every TemporaryQueue which will never get unregistered
                replyTo.delete();
            }

            if (session != null) {
                session.close();
            }

            if (connection != null) {
                connection.close();
            }
        }
        return null;
    }

}
