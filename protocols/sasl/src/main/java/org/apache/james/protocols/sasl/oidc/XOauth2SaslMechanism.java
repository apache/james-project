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

package org.apache.james.protocols.sasl.oidc;

import org.apache.james.jwt.OidcJwtTokenVerifier;
import org.apache.james.protocols.api.sasl.SaslAuthenticator;
import org.apache.james.protocols.api.sasl.SaslExchange;
import org.apache.james.protocols.api.sasl.SaslInitialRequest;
import org.apache.james.protocols.api.sasl.SaslMechanism;
import org.apache.james.protocols.api.sasl.SaslMechanismNames;

public class XOauth2SaslMechanism implements SaslMechanism {
    public static final String NAME = SaslMechanismNames.XOAUTH2;

    private final OidcJwtTokenVerifier verifier;

    public XOauth2SaslMechanism(OidcJwtTokenVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public SaslExchange start(SaslInitialRequest request, SaslAuthenticator authenticator) {
        return OidcSaslMechanisms.start(request.initialResponse(), verifier, authenticator);
    }
}
