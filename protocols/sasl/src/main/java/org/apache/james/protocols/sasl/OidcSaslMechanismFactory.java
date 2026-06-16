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

package org.apache.james.protocols.sasl;

import java.net.MalformedURLException;
import java.net.URISyntaxException;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.jwt.OidcJwtTokenVerifier;
import org.apache.james.jwt.OidcSASLConfiguration;
import org.apache.james.protocols.api.sasl.SaslMechanismFactory;

abstract class OidcSaslMechanismFactory implements SaslMechanismFactory {
    protected OidcJwtTokenVerifier parseVerifier(HierarchicalConfiguration<ImmutableNode> serverConfiguration) throws ConfigurationException {
        if (serverConfiguration.immutableConfigurationsAt("auth.oidc").isEmpty()) {
            throw new ConfigurationException("OAuth SASL mechanisms require an auth.oidc configuration");
        }
        try {
            return new OidcJwtTokenVerifier(OidcSASLConfiguration.parse(serverConfiguration.configurationAt("auth.oidc")));
        } catch (MalformedURLException | URISyntaxException | NullPointerException e) {
            throw new ConfigurationException("Failed to retrieve oauth component", e);
        }
    }
}
