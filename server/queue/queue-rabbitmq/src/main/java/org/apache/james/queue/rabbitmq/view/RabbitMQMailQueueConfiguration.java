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

package org.apache.james.queue.rabbitmq.view;

import java.util.Objects;
import java.util.Optional;

import org.apache.commons.configuration2.Configuration;

public class RabbitMQMailQueueConfiguration {
    private static final boolean DEFAULT_SIZE_METRICS_DISABLED = false;

    public static class Builder {
        private Optional<Boolean> sizeMetricsEnabled;

        public Builder sizeMetricsEnabled(boolean sizeMetricsEnabled) {
            this.sizeMetricsEnabled = Optional.of(sizeMetricsEnabled);
            return this;
        }

        public Builder sizeMetricsEnabled(Optional<Boolean> sizeMetricsEnabled) {
            this.sizeMetricsEnabled = sizeMetricsEnabled;
            return this;
        }

        public RabbitMQMailQueueConfiguration build() {
            return new RabbitMQMailQueueConfiguration(sizeMetricsEnabled.orElse(DEFAULT_SIZE_METRICS_DISABLED));
        }
    }

    public static final String SIZE_METRICS_ENABLED_PROPERTY = "mailqueue.size.metricsEnabled";

    public static Builder builder() {
        return new Builder();
    }

    public static RabbitMQMailQueueConfiguration from(Configuration configuration) {
        return builder()
            .sizeMetricsEnabled(Optional.ofNullable(configuration.getBoolean(SIZE_METRICS_ENABLED_PROPERTY, null)))
            .build();
    }

    public static RabbitMQMailQueueConfiguration sizeMetricsEnabled() {
        return builder()
            .sizeMetricsEnabled(true)
            .build();
    }

    public static RabbitMQMailQueueConfiguration sizeMetricsDisabled() {
        return builder()
            .sizeMetricsEnabled(false)
            .build();
    }

    private final boolean sizeMetricsEnabled;

    private RabbitMQMailQueueConfiguration(boolean sizeMetricsEnabled) {
        this.sizeMetricsEnabled = sizeMetricsEnabled;
    }

    public boolean isSizeMetricsEnabled() {
        return sizeMetricsEnabled;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof RabbitMQMailQueueConfiguration) {
            RabbitMQMailQueueConfiguration that = (RabbitMQMailQueueConfiguration) o;

            return Objects.equals(this.sizeMetricsEnabled, that.sizeMetricsEnabled);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(sizeMetricsEnabled);
    }
}
