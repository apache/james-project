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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.jms.JMSException;
import javax.jms.MapMessage;

import org.apache.james.metrics.api.GaugeRegistry;

import com.google.common.util.concurrent.AtomicDouble;

public class ActiveMQBrokerStatistics {

    public static final String STATS_QUEUE_NAME = "ActiveMQ.Statistics.Broker";

    private static final String TEMP_PERCENT_USAGE = "tempPercentUsage";
    private static final String STORE_PERCENT_USAGE = "storePercentUsage";
    private static final String MEMORY_PERCENT_USAGE = "memoryPercentUsage";

    private static final String STORE_LIMIT = "storeLimit";
    private static final String MEMORY_LIMIT = "memoryLimit";
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

    private final AtomicLong lastUpdate = new AtomicLong();

    private final AtomicInteger memoryPercentUsage = new AtomicInteger();
    private final AtomicInteger storePercentUsage = new AtomicInteger();
    private final AtomicInteger tempPercentUsage = new AtomicInteger();

    private final AtomicLong memoryLimit = new AtomicLong();
    private final AtomicLong storeLimit = new AtomicLong();
    private final AtomicLong tempLimit = new AtomicLong();

    private final AtomicLong memoryUsage = new AtomicLong();
    private final AtomicLong storeUsage = new AtomicLong();
    private final AtomicLong tempUsage = new AtomicLong();

    private final AtomicLong size = new AtomicLong();
    private final AtomicLong enqueueCount = new AtomicLong();
    private final AtomicLong dequeueCount = new AtomicLong();
    private final AtomicLong inflightCount = new AtomicLong();
    private final AtomicLong producerCount = new AtomicLong();
    private final AtomicLong consumerCount = new AtomicLong();
    private final AtomicLong expiredCount = new AtomicLong();
    private final AtomicLong dispatchCount = new AtomicLong();
    private final AtomicLong messagesCached = new AtomicLong();

    private final AtomicDouble minEnqueueTime = new AtomicDouble();
    private final AtomicDouble maxEnqueueTime = new AtomicDouble();
    private final AtomicDouble averageEnqueueTime = new AtomicDouble();

    public ActiveMQBrokerStatistics(GaugeRegistry gaugeRegistry) {
        gaugeRegistry.register(STATS_QUEUE_NAME + "." + MEMORY_PERCENT_USAGE, memoryPercentUsage::get);
        gaugeRegistry.register(STATS_QUEUE_NAME + "." + STORE_PERCENT_USAGE, storePercentUsage::get);
        gaugeRegistry.register(STATS_QUEUE_NAME + "." + TEMP_PERCENT_USAGE, tempPercentUsage::get);

        gaugeRegistry.register(STATS_QUEUE_NAME + "." + MEMORY_LIMIT, memoryLimit::get);
        gaugeRegistry.register(STATS_QUEUE_NAME + "." + STORE_LIMIT, storeLimit::get);
        gaugeRegistry.register(STATS_QUEUE_NAME + "." + TEMP_LIMIT, tempLimit::get);

        gaugeRegistry.register(STATS_QUEUE_NAME + "." + MEMORY_USAGE, memoryUsage::get);
        gaugeRegistry.register(STATS_QUEUE_NAME + "." + STORE_USAGE, storeUsage::get);
        gaugeRegistry.register(STATS_QUEUE_NAME + "." + TEMP_USAGE, tempUsage::get);

        gaugeRegistry.register(STATS_QUEUE_NAME + "." + SIZE, size::get);
        gaugeRegistry.register(STATS_QUEUE_NAME + "." + ENQUEUE_COUNT, enqueueCount::get);
        gaugeRegistry.register(STATS_QUEUE_NAME + "." + DEQUEUE_COUNT, dequeueCount::get);
        gaugeRegistry.register(STATS_QUEUE_NAME + "." + INFLIGHT_COUNT, inflightCount::get);
        gaugeRegistry.register(STATS_QUEUE_NAME + "." + PRODUCER_COUNT, producerCount::get);
        gaugeRegistry.register(STATS_QUEUE_NAME + "." + CONSUMER_COUNT, consumerCount::get);
        gaugeRegistry.register(STATS_QUEUE_NAME + "." + EXPIRED_COUNT, expiredCount::get);
        gaugeRegistry.register(STATS_QUEUE_NAME + "." + DISPATCH_COUNT, dispatchCount::get);
        gaugeRegistry.register(STATS_QUEUE_NAME + "." + MESSAGES_CACHED, messagesCached::get);

        gaugeRegistry.register(STATS_QUEUE_NAME + "." + MIN_ENQUEUE_TIME, minEnqueueTime::get);
        gaugeRegistry.register(STATS_QUEUE_NAME + "." + MAX_ENQUEUE_TIME, maxEnqueueTime::get);
        gaugeRegistry.register(STATS_QUEUE_NAME + "." + AVERAGE_ENQUEUE_TIME, averageEnqueueTime::get);

        gaugeRegistry.register(STATS_QUEUE_NAME + ".lastUpdate", lastUpdate::get);
    }

    public void update(MapMessage msg) throws JMSException {
        if (msg.itemExists(MEMORY_PERCENT_USAGE)) {
            memoryPercentUsage.set(msg.getInt(MEMORY_PERCENT_USAGE));
        }
        if (msg.itemExists(STORE_PERCENT_USAGE)) {
            storePercentUsage.set(msg.getInt(STORE_PERCENT_USAGE));
        }
        if (msg.itemExists(TEMP_PERCENT_USAGE)) {
            tempPercentUsage.set(msg.getInt(TEMP_PERCENT_USAGE));
        }

        if (msg.itemExists(MEMORY_LIMIT)) {
            memoryLimit.set(msg.getLong(MEMORY_LIMIT));
        }
        if (msg.itemExists(STORE_LIMIT)) {
            storeLimit.set(msg.getLong(STORE_LIMIT));
        }
        if (msg.itemExists(TEMP_LIMIT)) {
            tempLimit.set(msg.getLong(TEMP_LIMIT));
        }

        if (msg.itemExists(MEMORY_USAGE)) {
            memoryUsage.set(msg.getLong(MEMORY_USAGE));
        }
        if (msg.itemExists(STORE_USAGE)) {
            storeUsage.set(msg.getLong(STORE_USAGE));
        }
        if (msg.itemExists(TEMP_USAGE)) {
            tempUsage.set(msg.getLong(TEMP_USAGE));
        }

        if (msg.itemExists(SIZE)) {
            size.set(msg.getLong(SIZE));
        }
        if (msg.itemExists(ENQUEUE_COUNT)) {
            enqueueCount.set(msg.getLong(ENQUEUE_COUNT));
        }
        if (msg.itemExists(DEQUEUE_COUNT)) {
            dequeueCount.set(msg.getLong(DEQUEUE_COUNT));
        }
        if (msg.itemExists(INFLIGHT_COUNT)) {
            inflightCount.set(msg.getLong(INFLIGHT_COUNT));
        }
        if (msg.itemExists(PRODUCER_COUNT)) {
            producerCount.set(msg.getLong(PRODUCER_COUNT));
        }
        if (msg.itemExists(CONSUMER_COUNT)) {
            consumerCount.set(msg.getLong(CONSUMER_COUNT));
        }
        if (msg.itemExists(EXPIRED_COUNT)) {
            expiredCount.set(msg.getLong(EXPIRED_COUNT));
        }
        if (msg.itemExists(DISPATCH_COUNT)) {
            dispatchCount.set(msg.getLong(DISPATCH_COUNT));
        }
        if (msg.itemExists(MESSAGES_CACHED)) {
            messagesCached.set(msg.getLong(MESSAGES_CACHED));
        }

        if (msg.itemExists(MIN_ENQUEUE_TIME)) {
            minEnqueueTime.set(msg.getDouble(MIN_ENQUEUE_TIME));
        }
        if (msg.itemExists(MAX_ENQUEUE_TIME)) {
            maxEnqueueTime.set(msg.getDouble(MAX_ENQUEUE_TIME));
        }
        if (msg.itemExists(AVERAGE_ENQUEUE_TIME)) {
            averageEnqueueTime.set(msg.getDouble(AVERAGE_ENQUEUE_TIME));
        }

        lastUpdate.set(System.currentTimeMillis());
    }

    public long getLastUpdate() {
        return lastUpdate.get();
    }

    /*
    vm=vm://localhost
    - memoryUsage=0
    - storeUsage=3330
    - tempPercentUsage=0
    ssl=
    openwire=tcp://localhost:50059
    brokerId=ID:bigmac-50057-1253605065511-0:0
    - consumerCount=2
    brokerName=localhost
    - expiredCount=0
    - dispatchCount=1
    - maxEnqueueTime=5.0
    - storePercentUsage=0
    - dequeueCount=0
    - inflightCount=1
    - messagesCached=0
    - tempLimit=107374182400
    - averageEnqueueTime=5.0
    stomp+ssl=
    - memoryPercentUsage=0
    - size=10
    - tempUsage=0
    - producerCount=1
    - minEnqueueTime=5.0
    dataDirectory=/Users/rajdavies/dev/projects/activemq/activemq-core/activemq-data
    - enqueueCount=10
    stomp=
    - storeLimit=107374182400
    - memoryLimit=67108864
     */


}
