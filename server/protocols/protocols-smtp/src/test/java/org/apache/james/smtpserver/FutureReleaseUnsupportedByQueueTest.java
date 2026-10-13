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
package org.apache.james.smtpserver;

import static org.apache.james.smtpserver.SMTPServerTestSystem.CLOCK;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.queue.api.RawMailQueueItemDecoratorFactory;
import org.apache.james.queue.memory.MemoryMailQueueFactory;
import org.junit.jupiter.api.Test;

class FutureReleaseUnsupportedByQueueTest {
    private final SMTPServerTestSystem testSystem = new SMTPServerTestSystem() {
        @Override
        protected MemoryMailQueueFactory createMailQueueFactory() {
            return new MemoryMailQueueFactory(new RawMailQueueItemDecoratorFactory(), CLOCK) {
                @Override
                public boolean supportsDelays() {
                    return false;
                }
            };
        }
    };

    @Test
    void serverShouldNotStartWhenFutureReleaseIsConfiguredOnAQueueNotSupportingDelays() {
        assertThatThrownBy(() -> testSystem.setUp("smtpserver-futurerelease.xml"))
            .hasStackTraceContaining("FUTURERELEASE hooks are configured but the mail queue does not support delays");
    }
}
