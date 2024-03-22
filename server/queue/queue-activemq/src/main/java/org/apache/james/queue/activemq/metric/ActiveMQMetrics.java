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

import jakarta.jms.JMSException;
import jakarta.jms.MapMessage;

import org.apache.james.metrics.api.GaugeRegistry;
import org.apache.james.metrics.api.GaugeRegistry.SettableGauge;

public class ActiveMQMetrics {
    private static final String TEMP_PERCENT_USAGE = "tempPercentUsage";
    private static final String STORE_PERCENT_USAGE = "storePercentUsage";
    private static final String MEMORY_PERCENT_USAGE = "memoryPercentUsage";

    private static final String MEMORY_LIMIT = "memoryLimit";
    private static final String STORE_LIMIT = "storeLimit";
    private static final String TEMP_LIMIT = "tempLimit";

    private static final String MEMORY_USAGE = "memoryUsage";
    private static final String STORE_USAGE = "storeUsage";
    private static final String TEMP_USAGE = "tempUsage";

    private static final String SIZE = "size";
    private static final String ENQUEUE_COUNT = "enqueueCount";
    private static final String DEQUEUE_COUNT = "dequeueCount";
    private static final String INFLIGHT_COUNT = "inflightCount";
    private static final String PRODUCER_COUNT = "producerCount";
    private static final String CONSUMER_COUNT = "consumerCount";
    private static final String EXPIRED_COUNT = "expiredCount";
    private static final String DISPATCH_COUNT = "dispatchCount";
    private static final String MESSAGES_CACHED = "messagesCached";

    private static final String MIN_ENQUEUE_TIME = "minEnqueueTime";
    private static final String MAX_ENQUEUE_TIME = "maxEnqueueTime";
    private static final String AVERAGE_ENQUEUE_TIME = "averageEnqueueTime";

    private static final String LAST_UPDATE = "lastUpdate";

    private final String statsQueueName;

    private final GaugeRegistry gaugeRegistry;

    private final Map<String, SettableGauge<Integer>> registeredGaugesOfInteger = new HashMap<>();
    private final Map<String, SettableGauge<Double>> registeredGaugesOfDouble = new HashMap<>();
    private final Map<String, SettableGauge<Long>> registeredGaugesOfLong = new HashMap<>();

    public static ActiveMQMetrics forQueue(String queueName, GaugeRegistry gaugeRegistry) {
        return new ActiveMQMetrics("ActiveMQ.Statistics.Destination." + queueName, gaugeRegistry);
    }

    public static ActiveMQMetrics forBroker(GaugeRegistry gaugeRegistry) {
        return new ActiveMQMetrics("ActiveMQ.Statistics.Broker", gaugeRegistry);
    }

    private ActiveMQMetrics(String statsQueueName, GaugeRegistry gaugeRegistry) {
        this.statsQueueName = statsQueueName;
        this.gaugeRegistry = gaugeRegistry;
    }

    public String getName() {
        return statsQueueName;
    }

    public void updateMetrics(MapMessage msg) throws JMSException {

        if (msg.itemExists(MEMORY_PERCENT_USAGE)) {
            setGaugeAndRegisterIfAbsent(MEMORY_PERCENT_USAGE, msg.getInt(MEMORY_PERCENT_USAGE));
        }
        if (msg.itemExists(STORE_PERCENT_USAGE)) {
            setGaugeAndRegisterIfAbsent(TEMP_PERCENT_USAGE, msg.getInt(STORE_PERCENT_USAGE));
        }
        if (msg.itemExists(TEMP_PERCENT_USAGE)) {
            setGaugeAndRegisterIfAbsent(TEMP_PERCENT_USAGE, msg.getInt(TEMP_PERCENT_USAGE));
        }

        if (msg.itemExists(MEMORY_LIMIT)) {
            setGaugeAndRegisterIfAbsent(MEMORY_LIMIT, msg.getLong(MEMORY_LIMIT));
        }
        if (msg.itemExists(STORE_LIMIT)) {
            setGaugeAndRegisterIfAbsent(STORE_LIMIT, msg.getLong(STORE_LIMIT));
        }
        if (msg.itemExists(TEMP_LIMIT)) {
            setGaugeAndRegisterIfAbsent(TEMP_LIMIT, msg.getLong(TEMP_LIMIT));
        }

        if (msg.itemExists(MEMORY_USAGE)) {
            setGaugeAndRegisterIfAbsent(MEMORY_USAGE, msg.getLong(MEMORY_USAGE));
        }
        if (msg.itemExists(STORE_USAGE)) {
            setGaugeAndRegisterIfAbsent(STORE_USAGE, msg.getLong(STORE_USAGE));
        }
        if (msg.itemExists(TEMP_USAGE)) {
            setGaugeAndRegisterIfAbsent(TEMP_USAGE, msg.getLong(TEMP_USAGE));
        }

        if (msg.itemExists(SIZE)) {
            setGaugeAndRegisterIfAbsent(SIZE, msg.getLong(SIZE));
        }
        if (msg.itemExists(ENQUEUE_COUNT)) {
            setGaugeAndRegisterIfAbsent(ENQUEUE_COUNT, msg.getLong(ENQUEUE_COUNT));
        }
        if (msg.itemExists(DEQUEUE_COUNT)) {
            setGaugeAndRegisterIfAbsent(DEQUEUE_COUNT, msg.getLong(DEQUEUE_COUNT));
        }
        if (msg.itemExists(INFLIGHT_COUNT)) {
            setGaugeAndRegisterIfAbsent(INFLIGHT_COUNT, msg.getLong(INFLIGHT_COUNT));
        }
        if (msg.itemExists(PRODUCER_COUNT)) {
            setGaugeAndRegisterIfAbsent(PRODUCER_COUNT, msg.getLong(PRODUCER_COUNT));
        }
        if (msg.itemExists(CONSUMER_COUNT)) {
            setGaugeAndRegisterIfAbsent(CONSUMER_COUNT, msg.getLong(CONSUMER_COUNT));
        }
        if (msg.itemExists(EXPIRED_COUNT)) {
            setGaugeAndRegisterIfAbsent(EXPIRED_COUNT, msg.getLong(EXPIRED_COUNT));
        }
        if (msg.itemExists(DISPATCH_COUNT)) {
            setGaugeAndRegisterIfAbsent(DISPATCH_COUNT, msg.getLong(DISPATCH_COUNT));
        }
        if (msg.itemExists(MESSAGES_CACHED)) {
            setGaugeAndRegisterIfAbsent(MESSAGES_CACHED, msg.getLong(MESSAGES_CACHED));
        }

        if (msg.itemExists(MIN_ENQUEUE_TIME)) {
            setGaugeAndRegisterIfAbsent(MIN_ENQUEUE_TIME, msg.getDouble(MIN_ENQUEUE_TIME));
        }
        if (msg.itemExists(MAX_ENQUEUE_TIME)) {
            setGaugeAndRegisterIfAbsent(MAX_ENQUEUE_TIME, msg.getDouble(MAX_ENQUEUE_TIME));
        }
        if (msg.itemExists(AVERAGE_ENQUEUE_TIME)) {
            setGaugeAndRegisterIfAbsent(AVERAGE_ENQUEUE_TIME, msg.getDouble(AVERAGE_ENQUEUE_TIME));
        }

        setGaugeAndRegisterIfAbsent(LAST_UPDATE, System.currentTimeMillis());
    }

    private void setGaugeAndRegisterIfAbsent(String name, long val) {
        String key = statsQueueName + "." + name;
        registeredGaugesOfLong.computeIfAbsent(key, any -> gaugeRegistry.settableGauge(key))
            .setValue(val);
    }

    private void setGaugeAndRegisterIfAbsent(String name, int val) {
        String key = statsQueueName + "." + name;
        registeredGaugesOfInteger.computeIfAbsent(key, any -> gaugeRegistry.settableGauge(key))
            .setValue(val);
    }

    private void setGaugeAndRegisterIfAbsent(String name, double val) {
        String key = statsQueueName + "." + name;
        registeredGaugesOfDouble.computeIfAbsent(key, any -> gaugeRegistry.settableGauge(key))
            .setValue(val);
    }

}
