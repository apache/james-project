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

import org.apache.commons.configuration2.BaseConfiguration;
import org.apache.commons.configuration2.Configuration;
import org.apache.james.queue.activemq.metric.ActiveMQMetricConfiguration;

public class ActiveMQConfiguration {
    private static final String ADJUST_USAGE_LIMITS = "adjust.usage.limits";
    private static final boolean ADJUST_USAGE_LIMITS_DEFAULT = false;

    private final ActiveMQMetricConfiguration metricConfiguration;
    private final boolean adjustUsageLimits;

    public static ActiveMQConfiguration getDefault() {
        return from(new BaseConfiguration());
    }

    public static ActiveMQConfiguration from(Configuration configuration) {
        return new ActiveMQConfiguration(
            ActiveMQMetricConfiguration.from(configuration),
            configuration.getBoolean(ADJUST_USAGE_LIMITS, ADJUST_USAGE_LIMITS_DEFAULT));
    }

    private ActiveMQConfiguration(ActiveMQMetricConfiguration metricConfiguration, boolean adjustUsageLimits) {
        this.metricConfiguration = metricConfiguration;
        this.adjustUsageLimits = adjustUsageLimits;
    }

    public ActiveMQMetricConfiguration getMetricConfiguration() {
        return metricConfiguration;
    }

    public boolean isAdjustUsageLimits() {
        return adjustUsageLimits;
    }
}
