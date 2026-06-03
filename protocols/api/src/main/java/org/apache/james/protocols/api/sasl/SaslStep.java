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

import java.util.Objects;
import java.util.Optional;

/**
 * Server step produced by a SASL exchange.
 */
public interface SaslStep {
    /**
     * Server challenge to send back to the client.
     */
    record Challenge(Optional<byte[]> payload) implements SaslStep {
        public Challenge {
            payload = Objects.requireNonNull(payload)
                .map(byte[]::clone);
        }

        /**
         * Returns a defensive copy of the decoded challenge payload.
         */
        public Optional<byte[]> payload() {
            return payload.map(byte[]::clone);
        }
    }

    /**
     * Successful SASL exchange result.
     */
    record Success(SaslIdentity identity, Optional<byte[]> serverData, String log) implements SaslStep {
        public Success {
            identity = Objects.requireNonNull(identity);
            serverData = Objects.requireNonNull(serverData)
                .map(byte[]::clone);
            log = Objects.requireNonNull(log);
        }

        /**
         * Returns a defensive copy of the decoded final server data.
         */
        public Optional<byte[]> serverData() {
            return serverData.map(byte[]::clone);
        }
    }

    /**
     * Failed SASL exchange result.
     */
    record Failure(String log) implements SaslStep {
        public Failure {
            log = Objects.requireNonNull(log);
        }
    }
}
