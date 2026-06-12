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

import java.util.Optional;

/**
 * Protocol-neutral initial SASL request.
 *
 * @param mechanismName requested SASL mechanism name
 * @param initialResponse decoded initial client response, when supplied by the client
 */
public record SaslInitialRequest(String mechanismName, Optional<byte[]> initialResponse) {
    public SaslInitialRequest(String mechanismName, Optional<byte[]> initialResponse) {
        this.mechanismName = mechanismName;
        this.initialResponse = initialResponse.map(byte[]::clone);
    }

    /**
     * Returns a defensive copy of the decoded initial client response.
     */
    public Optional<byte[]> initialResponse() {
        return initialResponse.map(byte[]::clone);
    }
}
