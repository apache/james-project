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

import java.time.Duration;

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

import org.apache.james.core.healthcheck.ComponentName;
import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.core.healthcheck.Result;
import org.apache.james.metrics.api.GaugeRegistry;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class ActiveMQHealthCheck implements HealthCheck {
    public static final Logger LOGGER = LoggerFactory.getLogger(ActiveMQHealthCheck.class);
    public static final ComponentName COMPONENT_NAME = new ComponentName("Embedded ActiveMQ");
    private static final Duration RECEIVE_TIMEOUT = Duration.ofSeconds(1);
    private final ConnectionFactory connectionFactory;
    private final ActiveMQBrokerStatistics brokerStatistics;

    @Inject
    public ActiveMQHealthCheck(ConnectionFactory connectionFactory, GaugeRegistry gaugeRegistry) {
        this.connectionFactory = connectionFactory;
        this.brokerStatistics = new ActiveMQBrokerStatistics(gaugeRegistry);
    }

    @Override
    public ComponentName componentName() {
        return COMPONENT_NAME;
    }

    @Override
    public Publisher<Result> check() {
        return Mono.fromCallable(() -> {
            try {
                retrieveAndUpdateBrokerStatistics();
                return Result.healthy(COMPONENT_NAME);
            } catch (Exception e) {
                LOGGER.warn("{} is unhealthy. {}", COMPONENT_NAME.getName(), e.getMessage());
                return Result.unhealthy(COMPONENT_NAME, e.toString(), e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private void retrieveAndUpdateBrokerStatistics() throws JMSException {
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

            Queue testQueue = session.createQueue(ActiveMQBrokerStatistics.STATS_QUEUE_NAME);
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
            brokerStatistics.update((MapMessage)reply);
        } finally {
            if (producer != null) {
                try {
                    producer.close();
                } catch (JMSException e) {
                    LOGGER.warn("error while closing producer", e);
                }
            }

            if (consumer != null) {
                try {
                    consumer.close();
                } catch (JMSException e) {
                    LOGGER.warn("error while closing consumer", e);
                }
            }

            if (replyTo != null) {
                try {
                    // we need to delete the temporary queue to be sure we will
                    // free up memory if thats not done and a pool is used
                    // its possible that we will register a new mbean in jmx for
                    // every TemporaryQueue which will never get unregistered
                    replyTo.delete();
                } catch (JMSException e) {
                    LOGGER.warn("error while deleting temporary queue", e);
                }
            }

            if (session != null) {
                try {
                    session.close();
                } catch (JMSException e) {
                    LOGGER.warn("error while closing session", e);
                }
            }

            if (connection != null) {
                try {
                    connection.close();
                } catch (JMSException e) {
                    LOGGER.warn("error while closing connection", e);
                }
            }
        }
    }
}

