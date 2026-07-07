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

package org.apache.james.utils;

import org.apache.james.protocols.api.sasl.SaslAuthenticator;
import org.apache.james.protocols.api.sasl.SaslExchange;
import org.apache.james.protocols.api.sasl.SaslFailure;
import org.apache.james.protocols.api.sasl.SaslInitialRequest;
import org.apache.james.protocols.api.sasl.SaslMechanism;
import org.apache.james.protocols.api.sasl.SaslStep;

public class FixedNameSaslMechanism implements SaslMechanism {
    private final String name;

    public FixedNameSaslMechanism(String name) {
        this.name = name;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public SaslExchange start(SaslInitialRequest request, SaslAuthenticator authenticator) {
        return new FixedStepExchange();
    }

    private record FixedStepExchange() implements SaslExchange {
        @Override
        public SaslStep firstStep() {
            return failure();
        }

        @Override
        public SaslStep onResponse(byte[] clientResponse) {
            return failure();
        }

        @Override
        public void abort() {
        }

        @Override
        public void close() {
        }

        private SaslStep failure() {
            return new SaslStep.Failure(SaslFailure.malformed("not implemented"));
        }
    }
}
