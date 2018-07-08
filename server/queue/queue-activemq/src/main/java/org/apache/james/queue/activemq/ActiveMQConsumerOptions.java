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

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.apache.james.queue.api.MailQueue;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;

/**
 * The ActiveMQ specific consumer options.
 * <p>
 * See <a href="http://activemq.apache.org/destination-options.html">http://activemq.apache.org/destination-options.html</>
 * for more details.
 */
public class ActiveMQConsumerOptions implements MailQueue.ConsumerOptions {
    private final Optional<String> dequeueParams;

    private ActiveMQConsumerOptions(String dequeueParams) {
        this.dequeueParams = java.util.Optional.ofNullable(dequeueParams).map(StringUtils::stripToNull);
    }

    public static ActiveMQConsumerOptionsBuilder builder() {
        return new ActiveMQConsumerOptionsBuilder();
    }

    @Override
    public String applyForDequeue(String name) {
        return dequeueParams
                .map(params -> name + '?' + params)
                .orElse(name);
    }

    public static class ActiveMQConsumerOptionsBuilder {
        private ImmutableMap.Builder<String, String> optionsMap = ImmutableMap.builder();

        public ActiveMQConsumerOptionsBuilder dispatchAsync(boolean dispatchAsync) {
            optionsMap.put(ActiveMQSupport.CONSUMER_DISPATCH_ASYNC, String.valueOf(dispatchAsync));
            return this;
        }

        public ActiveMQConsumerOptionsBuilder exclusive(boolean exclusive) {
            optionsMap.put(ActiveMQSupport.CONSUMER_EXCLUSIVE, String.valueOf(exclusive));
            return this;
        }

        public ActiveMQConsumerOptionsBuilder maximumPendingMessageLimit(int limit) {
            optionsMap.put(ActiveMQSupport.CONSUMER_MAXIMUM_PENDING_MESSAGE_LIMIT, String.valueOf(limit));
            return this;
        }

        public ActiveMQConsumerOptionsBuilder noLocal(boolean noLocal) {
            optionsMap.put(ActiveMQSupport.CONSUMER_NO_LOCAL, String.valueOf(noLocal));
            return this;
        }

        public ActiveMQConsumerOptionsBuilder prefetchSize(int size) {
            optionsMap.put(ActiveMQSupport.CONSUMER_PREFETCH_SIZE, String.valueOf(size));
            return this;
        }

        public ActiveMQConsumerOptionsBuilder priority(int priority) {
            optionsMap.put(ActiveMQSupport.CONSUMER_PRIORITY, String.valueOf(priority));
            return this;
        }

        public ActiveMQConsumerOptionsBuilder retroactive(boolean retroactive) {
            optionsMap.put(ActiveMQSupport.CONSUMER_RETROACTIVE, String.valueOf(retroactive));
            return this;
        }

        public ActiveMQConsumerOptionsBuilder selector(String selector) {
            optionsMap.put(ActiveMQSupport.CONSUMER_SELECTOR, selector);
            return this;
        }

        public ActiveMQConsumerOptions build() {
            ImmutableMap<String, String> options = optionsMap.build();

            if (options.isEmpty()) {
                return new ActiveMQConsumerOptions(null);
            }

            return new ActiveMQConsumerOptions(Joiner.on('&')
                    .withKeyValueSeparator("=")
                    .join(options)
            );
        }
    }
}