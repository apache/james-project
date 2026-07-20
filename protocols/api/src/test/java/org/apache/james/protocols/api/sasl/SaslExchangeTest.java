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

package org.apache.james.protocols.api.sasl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.james.core.Username;
import org.junit.jupiter.api.Test;

class SaslExchangeTest {
    private static final Username USER = Username.of("user@example.com");
    private static final SaslIdentity IDENTITY = new SaslIdentity(USER, USER);

    private static class RecordingExchange implements SaslExchange {
        protected final List<String> lifecycleEvents = new ArrayList<>();

        @Override
        public SaslStep firstStep() {
            return new SaslStep.Challenge(Optional.empty());
        }

        @Override
        public SaslStep onResponse(byte[] clientResponse) {
            return new SaslStep.Success(IDENTITY, Optional.empty());
        }

        @Override
        public void close() {
            lifecycleEvents.add("close");
        }
    }

    private static class OverridingAbortExchange extends RecordingExchange {
        @Override
        public void abort() {
            lifecycleEvents.add("abort");
            close();
        }
    }

    private static class ThrowingAbortExchange extends OverridingAbortExchange {
        @Override
        public void abort() {
            super.abort();
            throw new IllegalStateException("boom");
        }
    }

    @Test
    void abortShouldCloseExchangeByDefault() {
        RecordingExchange exchange = new RecordingExchange();

        exchange.abort();

        assertThat(exchange.lifecycleEvents).containsExactly("close");
    }

    @Test
    void abortShouldUseExchangeSpecificAbortWhenOverridden() {
        OverridingAbortExchange exchange = new OverridingAbortExchange();

        exchange.abort();

        assertThat(exchange.lifecycleEvents).containsExactly("abort", "close");
    }

    @Test
    void abortShouldPropagateExchangeSpecificAbortFailure() {
        ThrowingAbortExchange exchange = new ThrowingAbortExchange();

        assertThatThrownBy(exchange::abort)
            .isInstanceOf(IllegalStateException.class);

        assertThat(exchange.lifecycleEvents).containsExactly("abort", "close");
    }
}
