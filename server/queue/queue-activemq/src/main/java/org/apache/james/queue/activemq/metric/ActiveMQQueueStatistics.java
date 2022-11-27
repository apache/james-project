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

import java.util.concurrent.atomic.AtomicLong;

import javax.jms.JMSException;
import javax.jms.MapMessage;

import org.apache.james.metrics.api.GaugeRegistry;

import com.google.common.util.concurrent.AtomicDouble;

public class ActiveMQQueueStatistics implements ActiveMQStatistics {
    protected static final String MEMORY_LIMIT = "memoryLimit";

    protected static final String SIZE = "size";
    protected static final String ENQUEUE_COUNT = "enqueueCount";
    protected static final String DEQUEUE_COUNT = "dequeueCount";
    protected static final String INFLIGHT_COUNT = "inflightCount";
    protected static final String PRODUCER_COUNT = "producerCount";
    protected static final String CONSUMER_COUNT = "consumerCount";
    protected static final String EXPIRED_COUNT = "expiredCount";
    protected static final String DISPATCH_COUNT = "dispatchCount";
    protected static final String MESSAGES_CACHED = "messagesCached";

    protected static final String MIN_ENQUEUE_TIME = "minEnqueueTime";
    protected static final String MAX_ENQUEUE_TIME = "maxEnqueueTime";
    protected static final String AVERAGE_ENQUEUE_TIME = "averageEnqueueTime";

    protected static final String LAST_UPDATE = "lastUpdate";

    private final AtomicLong memoryLimit = new AtomicLong();

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

    private final AtomicLong lastUpdate = new AtomicLong();

    protected final String statsQueueName;

    public static ActiveMQQueueStatistics from(String queueName) {
        return new ActiveMQQueueStatistics("ActiveMQ.Statistics.Destination." + queueName);
    }

    public ActiveMQQueueStatistics(String statsQueueName) {
        this.statsQueueName = statsQueueName;
    }

    @Override
    public String getName() {
        return statsQueueName;
    }

    @Override
    public void registerMetrics(GaugeRegistry gaugeRegistry) {
        String prefix = statsQueueName + ".";

        gaugeRegistry.register(prefix + MEMORY_LIMIT, memoryLimit::get);

        gaugeRegistry.register(prefix + SIZE, size::get);
        gaugeRegistry.register(prefix + ENQUEUE_COUNT, enqueueCount::get);
        gaugeRegistry.register(prefix + DEQUEUE_COUNT, dequeueCount::get);
        gaugeRegistry.register(prefix + INFLIGHT_COUNT, inflightCount::get);
        gaugeRegistry.register(prefix + PRODUCER_COUNT, producerCount::get);
        gaugeRegistry.register(prefix + CONSUMER_COUNT, consumerCount::get);
        gaugeRegistry.register(prefix + EXPIRED_COUNT, expiredCount::get);
        gaugeRegistry.register(prefix + DISPATCH_COUNT, dispatchCount::get);
        gaugeRegistry.register(prefix + MESSAGES_CACHED, messagesCached::get);

        gaugeRegistry.register(prefix + MIN_ENQUEUE_TIME, minEnqueueTime::get);
        gaugeRegistry.register(prefix + MAX_ENQUEUE_TIME, maxEnqueueTime::get);
        gaugeRegistry.register(prefix + AVERAGE_ENQUEUE_TIME, averageEnqueueTime::get);

        gaugeRegistry.register(prefix + LAST_UPDATE, lastUpdate::get);
    }

    @Override
    public void update(MapMessage msg) throws JMSException {
        if (msg.itemExists(MEMORY_LIMIT)) {
            memoryLimit.set(msg.getLong(MEMORY_LIMIT));
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
}
