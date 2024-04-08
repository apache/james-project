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

import static org.apache.james.backends.rabbitmq.Constants.AUTO_DELETE;
import static org.apache.james.backends.rabbitmq.Constants.DURABLE;
import static org.apache.james.backends.rabbitmq.Constants.EXCLUSIVE;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ConstantsTest {
    private static final boolean NO_QUORUM_QUEUES = false;
    private static final boolean USE_QUORUM_QUEUES = true;

    @Nested
    class EvaluateAutoDelete {
        @Test
        void wantedAutoDeleteAndNotQuorumQueueShouldReturnTrue() {
            assertThat(Constants.evaluateAutoDelete(AUTO_DELETE, NO_QUORUM_QUEUES))
                .isTrue();
        }

        @Test
        void wantedAutoDeleteAndUseQuorumQueueShouldReturnFalse() {
            assertThat(Constants.evaluateAutoDelete(AUTO_DELETE, USE_QUORUM_QUEUES))
                .isFalse();
        }

        @Test
        void notWantedAutoDeleteAndNoQuorumQueueShouldReturnFalse() {
            assertThat(Constants.evaluateAutoDelete(!AUTO_DELETE, NO_QUORUM_QUEUES))
                .isFalse();
        }

        @Test
        void notWantedAutoDeleteAndUseQuorumQueueShouldReturnFalse() {
            assertThat(Constants.evaluateAutoDelete(!AUTO_DELETE, USE_QUORUM_QUEUES))
                .isFalse();
        }
    }

    @Nested
    class EvaluateDurable {
        @Test
        void wantedDurableAndNotQuorumQueueShouldReturnTrue() {
            assertThat(Constants.evaluateDurable(DURABLE, NO_QUORUM_QUEUES))
                .isTrue();
        }

        @Test
        void wantedDurableAndUseQuorumQueueShouldReturnTrue() {
            assertThat(Constants.evaluateDurable(DURABLE, USE_QUORUM_QUEUES))
                .isTrue();
        }

        @Test
        void notWantedDurableAndNoQuorumQueueShouldReturnFalse() {
            assertThat(Constants.evaluateDurable(!DURABLE, NO_QUORUM_QUEUES))
                .isFalse();
        }

        @Test
        void notWantedDurableAndUseQuorumQueueShouldReturnTrue() {
            assertThat(Constants.evaluateDurable(!DURABLE, USE_QUORUM_QUEUES))
                .isTrue();
        }
    }

    @Nested
    class EvaluateExclusive {
        @Test
        void wantedExclusiveAndNotQuorumQueueShouldReturnTrue() {
            assertThat(Constants.evaluateExclusive(EXCLUSIVE, NO_QUORUM_QUEUES))
                .isTrue();
        }

        @Test
        void wantedExclusiveAndUseQuorumQueueShouldReturnFalse() {
            assertThat(Constants.evaluateExclusive(EXCLUSIVE, USE_QUORUM_QUEUES))
                .isFalse();
        }

        @Test
        void notWantedExclusiveAndNoQuorumQueueShouldReturnFalse() {
            assertThat(Constants.evaluateExclusive(!EXCLUSIVE, NO_QUORUM_QUEUES))
                .isFalse();
        }

        @Test
        void notWantedExclusiveAndUseQuorumQueueShouldReturnFalse() {
            assertThat(Constants.evaluateExclusive(!EXCLUSIVE, USE_QUORUM_QUEUES))
                .isFalse();
        }
    }
}
