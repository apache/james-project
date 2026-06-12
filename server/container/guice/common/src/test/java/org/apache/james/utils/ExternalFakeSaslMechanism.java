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

import org.apache.james.protocols.api.sasl.SaslExchange;
import org.apache.james.protocols.api.sasl.SaslInitialRequest;
import org.apache.james.protocols.api.sasl.SaslMechanism;
import org.apache.james.protocols.api.sasl.SaslStep;

public class ExternalFakeSaslMechanism implements SaslMechanism {
    @Override
    public String name() {
        return "EXTERNAL-FAKE";
    }

    @Override
    public SaslExchange start(SaslInitialRequest request) {
        return new FixedStepExchange(new SaslStep.Failure("not implemented"));
    }

    private record FixedStepExchange(SaslStep step) implements SaslExchange {
        @Override
        public SaslStep firstStep() {
            return step;
        }

        @Override
        public SaslStep onResponse(byte[] clientResponse) {
            return step;
        }

        @Override
        public void abort() {
        }

        @Override
        public void close() {
        }
    }
}
