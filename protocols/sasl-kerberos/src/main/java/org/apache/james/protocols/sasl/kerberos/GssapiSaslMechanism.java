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

package org.apache.james.protocols.sasl.kerberos;

import org.apache.james.protocols.api.sasl.SaslAuthenticator;
import org.apache.james.protocols.api.sasl.SaslExchange;
import org.apache.james.protocols.api.sasl.SaslInitialRequest;
import org.apache.james.protocols.api.sasl.SaslMechanism;
import org.apache.james.protocols.api.sasl.SaslMechanismNames;

public class GssapiSaslMechanism implements SaslMechanism {
    public static final String NAME = SaslMechanismNames.GSSAPI;

    private final GssapiSaslConfiguration configuration;
    private final KerberosLoginContextFactory loginContextFactory;
    private final GssapiSaslServerFactory saslServerFactory;

    GssapiSaslMechanism(GssapiSaslConfiguration configuration,
                        KerberosLoginContextFactory loginContextFactory,
                        GssapiSaslServerFactory saslServerFactory) {
        this.configuration = configuration;
        this.loginContextFactory = loginContextFactory;
        this.saslServerFactory = saslServerFactory;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isAvailableOnTransport(boolean channelEncrypted) {
        return !configuration.requireSSL() || channelEncrypted;
    }

    @Override
    public SaslExchange start(SaslInitialRequest request, SaslAuthenticator authenticator) {
        return new GssapiSaslExchange(request, authenticator, configuration, loginContextFactory, saslServerFactory);
    }
}
