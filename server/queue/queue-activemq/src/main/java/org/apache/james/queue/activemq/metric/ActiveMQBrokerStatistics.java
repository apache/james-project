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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.jms.JMSException;
import javax.jms.MapMessage;

import org.apache.james.metrics.api.GaugeRegistry;

public class ActiveMQBrokerStatistics extends ActiveMQQueueStatistics {
    public static final String TEMP_PERCENT_USAGE = "tempPercentUsage";
    public static final String STORE_PERCENT_USAGE = "storePercentUsage";
    public static final String MEMORY_PERCENT_USAGE = "memoryPercentUsage";

    public static final String STORE_LIMIT = "storeLimit";
    public static final String TEMP_LIMIT = "tempLimit";

    public static final String MEMORY_USAGE = "memoryUsage";
    public static final String STORE_USAGE = "storeUsage";
    public static final String TEMP_USAGE = "tempUsage";

    private final AtomicInteger memoryPercentUsage = new AtomicInteger();
    private final AtomicInteger storePercentUsage = new AtomicInteger();
    private final AtomicInteger tempPercentUsage = new AtomicInteger();

    private final AtomicLong storeLimit = new AtomicLong();
    private final AtomicLong tempLimit = new AtomicLong();

    private final AtomicLong memoryUsage = new AtomicLong();
    private final AtomicLong storeUsage = new AtomicLong();
    private final AtomicLong tempUsage = new AtomicLong();

    public ActiveMQBrokerStatistics() {
        super("ActiveMQ.Statistics.Broker");
    }

    @Override
    public void registerMetrics(GaugeRegistry gaugeRegistry) {
        super.registerMetrics(gaugeRegistry);

        String prefix = getName() + ".";
        gaugeRegistry.register(prefix + MEMORY_PERCENT_USAGE, memoryPercentUsage::get);
        gaugeRegistry.register(prefix + STORE_PERCENT_USAGE, storePercentUsage::get);
        gaugeRegistry.register(prefix + TEMP_PERCENT_USAGE, tempPercentUsage::get);

        gaugeRegistry.register(prefix + STORE_LIMIT, storeLimit::get);
        gaugeRegistry.register(prefix + TEMP_LIMIT, tempLimit::get);

        gaugeRegistry.register(prefix + MEMORY_USAGE, memoryUsage::get);
        gaugeRegistry.register(prefix + STORE_USAGE, storeUsage::get);
        gaugeRegistry.register(prefix + TEMP_USAGE, tempUsage::get);
    }

    @Override
    public void update(MapMessage msg) throws JMSException {
        super.update(msg);

        if (msg.itemExists(MEMORY_PERCENT_USAGE)) {
            memoryPercentUsage.set(msg.getInt(MEMORY_PERCENT_USAGE));
        }
        if (msg.itemExists(STORE_PERCENT_USAGE)) {
            storePercentUsage.set(msg.getInt(STORE_PERCENT_USAGE));
        }
        if (msg.itemExists(TEMP_PERCENT_USAGE)) {
            tempPercentUsage.set(msg.getInt(TEMP_PERCENT_USAGE));
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
    }

}
