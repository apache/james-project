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

package org.apache.james.queue.api;

import java.util.Optional;
import java.util.Set;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

/**
 * Factory for {@link MailQueue}
 */
public interface MailQueueFactory<T extends MailQueue> {

    static PrefetchCount defaultPrefetchCount() {
        return prefetchCount(5);
    }

    static PrefetchCount prefetchCount(int count) {
        return new PrefetchCount(count);
    }

    /**
     * {@link PrefetchCount} provides producers insights about what kind of load consumers expect.
     * If you expect to consume the mailqueue with 10 concurrent workers, it's a good idea to
     * ensure the producer will fill the stream with at least 10 elements.
     */
    class PrefetchCount {
        private final int value;

        @VisibleForTesting
        PrefetchCount(int value) {
            Preconditions.checkArgument(value >= 0, "only non-negative values are allowed");
            this.value = value;
        }

        public int asInt() {
            return value;
        }

        @Override
        public String toString() {
            return "PrefetchCount = " + value;
        }
    }

    /**
     * {@link MailQueue} which is used for spooling the messages
     */
    MailQueueName SPOOL = MailQueueName.of("spool");

    /**
     * Return the {@link MailQueue} for the name.
     * 
     * @param name
     * @return queue
     */
    default Optional<T> getQueue(MailQueueName name) {
        return getQueue(name, defaultPrefetchCount());
    }

    Optional<T> getQueue(MailQueueName name, PrefetchCount count);

    default T createQueue(MailQueueName name) {
        return createQueue(name, defaultPrefetchCount());
    }

    T createQueue(MailQueueName name, PrefetchCount count);

    Set<MailQueueName> listCreatedMailQueues();
}
