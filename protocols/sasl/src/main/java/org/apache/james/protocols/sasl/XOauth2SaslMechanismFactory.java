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

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.jwt.OidcJwtTokenVerifier;
import org.apache.james.jwt.OidcSASLConfiguration;
import org.apache.james.protocols.api.sasl.SaslMechanism;
import org.apache.james.protocols.api.sasl.SaslMechanismNames;
import org.apache.james.protocols.sasl.oidc.OAuthSaslMechanism;

public class XOauth2SaslMechanismFactory extends OidcSaslMechanismFactory {
    @Override
    public SaslMechanism create(HierarchicalConfiguration<ImmutableNode> serverConfiguration) throws ConfigurationException {
        OidcSASLConfiguration oidcConfiguration = parseConfiguration(serverConfiguration);
        return new OAuthSaslMechanism(SaslMechanismNames.XOAUTH2, new OidcJwtTokenVerifier(oidcConfiguration),
            requiresSsl(serverConfiguration), invalidTokenResponse(oidcConfiguration));
    }
}
