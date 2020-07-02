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

package org.apache.james.queue.rabbitmq;

import java.util.Objects;
import java.util.Optional;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

public final class MailQueueName {

    static class WorkQueueName {
        static Optional<WorkQueueName> fromString(String name) {
            Preconditions.checkNotNull(name);
            return Optional.of(name)
                .filter(WorkQueueName::isJamesWorkQueueName)
                .map(s -> s.substring(WORKQUEUE_PREFIX.length()))
                .map(WorkQueueName::new);
        }

        static boolean isJamesWorkQueueName(String name) {
            return name.startsWith(WORKQUEUE_PREFIX);
        }

        private final String name;

        private WorkQueueName(String name) {
            this.name = name;
        }

        String asString() {
            return WORKQUEUE_PREFIX + name;
        }

        MailQueueName toMailQueueName() {
            return MailQueueName.fromString(name);
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof WorkQueueName) {
                WorkQueueName that = (WorkQueueName) o;
                return Objects.equals(name, that.name);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(name);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("name", name)
                .toString();
        }
    }

    static class ExchangeName {
        private final String name;

        private ExchangeName(String name) {
            this.name = name;
        }

        String asString() {
            return EXCHANGE_PREFIX + name;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof ExchangeName) {
                ExchangeName that = (ExchangeName) o;
                return Objects.equals(name, that.name);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(name);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("name", name)
                .toString();
        }
    }

    private static final String PREFIX = "JamesMailQueue";
    private static final String EXCHANGE_PREFIX = PREFIX + "-exchange-";
    private static final String DEAD_LETTER_EXCHANGE_PREFIX = PREFIX + "-dead-letter-exchange-";
    private static final String DEAD_LETTER_QUEUE_PREFIX = PREFIX + "-dead-letter-queue-";
    @VisibleForTesting static final String WORKQUEUE_PREFIX = PREFIX + "-workqueue-";

    public static MailQueueName fromString(String name) {
        Preconditions.checkNotNull(name);
        return new MailQueueName(name);
    }

    static Optional<MailQueueName> fromRabbitWorkQueueName(String workQueueName) {
        return WorkQueueName.fromString(workQueueName)
            .map(WorkQueueName::toMailQueueName);
    }

    private final String name;

    private MailQueueName(String name) {
        this.name = name;
    }

    public String asString() {
        return name;
    }

    String toDeadLetterExchangeName() {
        return DEAD_LETTER_EXCHANGE_PREFIX + name;
    }

    String toDeadLetterQueueName() {
        return DEAD_LETTER_QUEUE_PREFIX + name;
    }

    ExchangeName toRabbitExchangeName() {
        return new ExchangeName(name);
    }

    WorkQueueName toWorkQueueName() {
        return new WorkQueueName(name);
    }

    org.apache.james.queue.api.MailQueueName toModel() {
        return org.apache.james.queue.api.MailQueueName.of(asString());
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof MailQueueName) {
            MailQueueName that = (MailQueueName) o;
            return Objects.equals(name, that.name);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("name", name)
            .toString();
    }
}
