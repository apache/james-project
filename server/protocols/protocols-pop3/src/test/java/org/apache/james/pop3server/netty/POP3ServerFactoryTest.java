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

package org.apache.james.pop3server.netty;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.james.protocols.api.sasl.SaslMechanism;
import org.apache.james.protocols.api.sasl.SaslMechanismNames;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

class POP3ServerFactoryTest {
    private static final boolean CLEAR_TEXT_TRANSPORT = false;
    private static final boolean ENCRYPTED_TRANSPORT = true;

    @Test
    void defaultLoaderShouldEnableOnlyPlainWithoutOidcConfiguration() throws Exception {
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();

        ImmutableList<SaslMechanism> mechanisms = POP3ServerFactory.Pop3SaslMechanismLoader.defaultLoader().load(configuration);

        assertThat(mechanisms)
            .extracting(SaslMechanism::name)
            .containsExactly(SaslMechanismNames.PLAIN);
    }

    @Test
    void defaultLoaderShouldEnableOidcMechanismsWithOidcConfiguration() throws Exception {
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("auth.oidc.jwksURL", "https://example.com/jwks");
        configuration.addProperty("auth.oidc.claim", "email");
        configuration.addProperty("auth.oidc.oidcConfigurationURL", "https://example.com/.well-known/openid-configuration");
        configuration.addProperty("auth.oidc.scope", "openid email");
        configuration.addProperty("auth.oidc.aud", "james");

        ImmutableList<SaslMechanism> mechanisms = POP3ServerFactory.Pop3SaslMechanismLoader.defaultLoader().load(configuration);

        assertThat(mechanisms)
            .extracting(SaslMechanism::name)
            .containsExactly(SaslMechanismNames.PLAIN, SaslMechanismNames.OAUTHBEARER, SaslMechanismNames.XOAUTH2);
    }

    @Test
    void defaultPlainMechanismShouldRequireTls() throws Exception {
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        SaslMechanism plain = POP3ServerFactory.Pop3SaslMechanismLoader.defaultLoader().load(configuration).getFirst();

        assertThat(plain.isAvailableOnTransport(CLEAR_TEXT_TRANSPORT)).isFalse();
        assertThat(plain.isAvailableOnTransport(ENCRYPTED_TRANSPORT)).isTrue();
    }

    @Test
    void defaultPlainMechanismShouldHonorRequireSslFalse() throws Exception {
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("auth.requireSSL", false);
        SaslMechanism plain = POP3ServerFactory.Pop3SaslMechanismLoader.defaultLoader().load(configuration).getFirst();

        assertThat(plain.isAvailableOnTransport(CLEAR_TEXT_TRANSPORT)).isTrue();
    }
}
