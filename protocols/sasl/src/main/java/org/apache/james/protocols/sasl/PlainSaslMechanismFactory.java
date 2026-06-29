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
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.protocols.api.sasl.SaslMechanism;
import org.apache.james.protocols.api.sasl.SaslMechanismFactory;
import org.apache.james.protocols.sasl.plain.PlainSaslMechanism;

public class PlainSaslMechanismFactory implements SaslMechanismFactory {
    public static final boolean IGNORE_REQUIRE_SSL_CONFIGURATION = false;
    private static final boolean PLAIN_AUTH_DISALLOWED_DEFAULT = true;
    private static final boolean PLAIN_AUTH_ENABLED_DEFAULT = true;

    private final boolean requireSslDefault;
    private final boolean honorRequireSslConfiguration;

    public PlainSaslMechanismFactory() {
        this(PLAIN_AUTH_DISALLOWED_DEFAULT);
    }

    public PlainSaslMechanismFactory(boolean requireSslDefault) {
        this(requireSslDefault, true);
    }

    public PlainSaslMechanismFactory(boolean requireSslDefault, boolean honorRequireSslConfiguration) {
        this.requireSslDefault = requireSslDefault;
        this.honorRequireSslConfiguration = honorRequireSslConfiguration;
    }

    @Override
    public SaslMechanism create(HierarchicalConfiguration<ImmutableNode> serverConfiguration) {
        return new PlainSaslMechanism(plainAuthEnabled(serverConfiguration), requiresSsl(serverConfiguration));
    }

    protected boolean plainAuthEnabled(HierarchicalConfiguration<ImmutableNode> serverConfiguration) {
        return serverConfiguration.getBoolean("auth.plainAuthEnabled", PLAIN_AUTH_ENABLED_DEFAULT);
    }

    protected boolean requiresSsl(HierarchicalConfiguration<ImmutableNode> serverConfiguration) {
        if (!honorRequireSslConfiguration) {
            return requireSslDefault;
        }
        return serverConfiguration.getBoolean("auth.requireSSL",
            serverConfiguration.getBoolean("plainAuthDisallowed", requireSslDefault));
    }
}
