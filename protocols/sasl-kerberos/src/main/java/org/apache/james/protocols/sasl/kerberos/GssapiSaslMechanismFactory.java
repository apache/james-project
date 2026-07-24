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

import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.SaslServer;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.protocols.api.sasl.SaslMechanism;
import org.apache.james.protocols.api.sasl.SaslMechanismFactory;

public class GssapiSaslMechanismFactory implements SaslMechanismFactory {
    private final KeyTabPrincipalVerifier keyTabPrincipalVerifier;
    private final KerberosLoginContextFactory loginContextFactory;
    private final GssapiSaslServerFactory saslServerFactory;

    public GssapiSaslMechanismFactory() {
        this(new KeyTabPrincipalVerifier(), new KerberosLoginContextFactory(), new JdkSaslServerFactory());
    }

    GssapiSaslMechanismFactory(KeyTabPrincipalVerifier keyTabPrincipalVerifier,
                               KerberosLoginContextFactory loginContextFactory,
                               GssapiSaslServerFactory saslServerFactory) {
        this.keyTabPrincipalVerifier = keyTabPrincipalVerifier;
        this.loginContextFactory = loginContextFactory;
        this.saslServerFactory = saslServerFactory;
    }

    @Override
    public SaslMechanism create(HierarchicalConfiguration<ImmutableNode> serverConfiguration) throws ConfigurationException {
        GssapiSaslConfiguration configuration = GssapiSaslConfiguration.from(serverConfiguration);
        keyTabPrincipalVerifier.verify(configuration);
        probeAcceptorCredentials(configuration);
        return new GssapiSaslMechanism(configuration, loginContextFactory, saslServerFactory);
    }

    private void probeAcceptorCredentials(GssapiSaslConfiguration configuration) throws ConfigurationException {
        try (KerberosLoginContext loginContext = loginContextFactory.login(configuration)) {
            SaslServer saslServer = SubjectSaslServer.create(loginContext.subject(), saslServerFactory, configuration, callbacks -> {
                if (callbacks.length > 0) {
                    throw new UnsupportedCallbackException(callbacks[0]);
                }
            });
            SubjectSaslServer.dispose(loginContext.subject(), saslServer);
        } catch (Exception e) {
            throw new ConfigurationException("Unable to acquire configured GSSAPI acceptor credentials", e);
        }
    }
}
