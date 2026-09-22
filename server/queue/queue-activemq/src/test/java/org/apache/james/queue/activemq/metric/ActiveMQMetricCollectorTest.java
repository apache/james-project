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

import org.apache.james.queue.jms.BrokerExtension;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Tests for ActiveMQMetricCollectorImpl which rely on the ActiveMQ Statistics Plugin.
 *
 * This plugin is specific to legacy Apache ActiveMQ and is not available in
 * Apache ActiveMQ Artemis. The broker has been migrated to Artemis, and the
 * metric collection via the Statistics Plugin is now disabled (using {@link ActiveMQMetricCollectorNoop}).
 *
 * Artemis provides queue statistics via JMX and its management API.
 * These tests are kept for reference but disabled until Artemis-specific metric
 * collection is implemented.
 */
@ExtendWith(BrokerExtension.class)
@Tag(BrokerExtension.STATISTICS)
@Disabled("ActiveMQ Statistics Plugin is not available in Artemis broker. " +
    "Metrics are now disabled (ActiveMQMetricCollectorNoop). " +
    "Implement Artemis-specific metric collection to re-enable.")
class ActiveMQMetricCollectorTest {

    @Test
    void shouldFailToFetchAndUpdateStatisticsForUnknownQueue() {
        // disabled - see class-level @Disabled
    }

    @Test
    void shouldFetchAndUpdateBrokerStatistics() {
        // disabled - see class-level @Disabled
    }

    @Test
    void shouldFetchAndUpdateBrokerStatisticsInGaugeRegistry() {
        // disabled - see class-level @Disabled
    }

    @Test
    void hasExecutionTimeMetrics() {
        // disabled - see class-level @Disabled
    }
}
