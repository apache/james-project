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
package org.apache.james.backends.rabbitmq;

import com.google.common.collect.ImmutableMap;

public class QueueArguments {
    private static final String SINGLE_ACTIVE_CONSUMER_ARGUMENT = "x-single-active-consumer";

    public static class Builder {
        @FunctionalInterface
        public interface RequiresReplicationFactor {
            Builder replicationFactor(int replicationFactor);
        }

        private final ImmutableMap.Builder<String, Object> arguments;

        public Builder() {
            arguments = ImmutableMap.builder();
        }

        public RequiresReplicationFactor quorumQueue() {
            arguments.put("x-queue-type", "quorum");
            return this::replicationFactor;
        }

        private Builder replicationFactor(int replicationFactor) {
            arguments.put("x-quorum-initial-group-size", replicationFactor);
            return this;
        }

        public Builder deadLetter(String deadLetterQueueName) {
            arguments.put("x-dead-letter-exchange", deadLetterQueueName);
            return this;
        }

        public Builder singleActiveConsumer() {
            arguments.put(SINGLE_ACTIVE_CONSUMER_ARGUMENT, true);
            return this;
        }

        public Builder queueTTL(long queueTTL) {
            arguments.put("x-expires", queueTTL);
            return this;
        }

        public Builder consumerTimeout(long consumerTimeoutInMillisecond) {
            arguments.put("x-consumer-timeout", consumerTimeoutInMillisecond);
            return this;
        }

        public Builder deliveryLimit(long deliveryLimit) {
            arguments.put("x-delivery-limit", deliveryLimit);
            return this;
        }

        public ImmutableMap<String, Object> build() {
            return arguments.build();
        }
    }

    public static final ImmutableMap<String, Object> NO_ARGUMENTS = ImmutableMap.of();

    public static Builder builder() {
        return new Builder();
    }
}
