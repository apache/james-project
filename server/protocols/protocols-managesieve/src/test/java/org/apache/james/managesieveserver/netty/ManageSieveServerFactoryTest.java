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

package org.apache.james.managesieveserver.netty;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.managesieveserver.netty.ManageSieveServerFactory.ManageSieveSaslMechanismLoader;
import org.apache.james.protocols.api.sasl.SaslMechanism;
import org.apache.james.protocols.api.sasl.SaslMechanismNames;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

class ManageSieveServerFactoryTest {
    private static final String JWKS_URL = "https://example.com/oidc/jwks";
    private static final String OIDC_CONFIGURATION_URL = "https://example.com/.well-known/openid-configuration";

    @Test
    void defaultLoaderShouldEnableOnlyPlainWithoutOidcConfiguration() throws Exception {
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();

        ImmutableList<SaslMechanism> mechanisms = ManageSieveSaslMechanismLoader.defaultLoader().load(configuration);

        assertThat(mechanisms)
            .extracting(SaslMechanism::name)
            .containsExactly(SaslMechanismNames.PLAIN);
    }

    @Test
    void defaultPlainMechanismShouldPreserveClearTextAuthentication() throws Exception {
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();

        SaslMechanism plain = ManageSieveSaslMechanismLoader.defaultLoader().load(configuration).getFirst();

        assertThat(plain.isAvailableOnTransport(false)).isTrue();
    }

    @Test
    void defaultPlainMechanismShouldHonorRequireSsl() throws Exception {
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("auth.requireSSL", true);

        SaslMechanism plain = ManageSieveSaslMechanismLoader.defaultLoader().load(configuration).getFirst();

        assertThat(plain.isAvailableOnTransport(false)).isFalse();
        assertThat(plain.isAvailableOnTransport(true)).isTrue();
    }

    @Test
    void legacyOidcConfigurationShouldEnableSharedOauthMechanisms() throws Exception {
        BaseHierarchicalConfiguration configuration = oidcConfiguration("oidc.");

        ImmutableList<SaslMechanism> mechanisms = ManageSieveSaslMechanismLoader.defaultLoader().load(configuration);

        assertThat(mechanisms)
            .extracting(SaslMechanism::name)
            .containsExactly(SaslMechanismNames.PLAIN, SaslMechanismNames.OAUTHBEARER, SaslMechanismNames.XOAUTH2);
    }

    @Test
    void defaultLoaderShouldPreserveExplicitBuiltInFactoryOrder() throws Exception {
        BaseHierarchicalConfiguration configuration = oidcConfiguration("auth.oidc.");
        configuration.addProperty("auth.saslMechanisms", "XOauth2SaslMechanismFactory,PlainSaslMechanismFactory");

        ImmutableList<SaslMechanism> mechanisms = ManageSieveSaslMechanismLoader.defaultLoader().load(configuration);

        assertThat(mechanisms)
            .extracting(SaslMechanism::name)
            .containsExactly(SaslMechanismNames.XOAUTH2, SaslMechanismNames.PLAIN);
    }

    @Test
    void defaultLoaderShouldRejectExternalFactory() {
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("auth.saslMechanisms", "com.example.CustomSaslMechanismFactory");

        assertThatThrownBy(() -> ManageSieveSaslMechanismLoader.defaultLoader().load(configuration))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsupported SASL mechanism factory");
    }

    @Test
    void normalizationShouldPreserveServerPropertiesWithoutMutatingSource() throws Exception {
        BaseHierarchicalConfiguration configuration = oidcConfiguration("oidc.");
        configuration.addProperty("bind", "127.0.0.1:4190");

        HierarchicalConfiguration<ImmutableNode> normalized = ManageSieveServerFactory.normalizeSaslConfiguration(configuration);
        HierarchicalConfiguration<ImmutableNode> normalizedAgain = ManageSieveServerFactory.normalizeSaslConfiguration(normalized);

        assertThat(normalized.getString("bind")).isEqualTo("127.0.0.1:4190");
        assertThat(normalized.getString("auth.oidc.jwksURL")).isEqualTo(JWKS_URL);
        assertThat(normalized.containsKey("oidc.jwksURL")).isFalse();
        assertThat(normalizedAgain.getString("auth.oidc.jwksURL")).isEqualTo(JWKS_URL);
        assertThat(configuration.containsKey("auth.oidc.jwksURL")).isFalse();
    }

    @Test
    void normalizationShouldRejectBothOidcConfigurationPaths() {
        BaseHierarchicalConfiguration configuration = oidcConfiguration("oidc.");
        configuration.addProperty("auth.oidc.jwksURL", JWKS_URL);

        assertThatThrownBy(() -> ManageSieveServerFactory.normalizeSaslConfiguration(configuration))
            .isInstanceOf(ConfigurationException.class)
            .hasMessageContaining("Configure OIDC only once");
    }

    private static BaseHierarchicalConfiguration oidcConfiguration(String prefix) {
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty(prefix + "jwksURL", JWKS_URL);
        configuration.addProperty(prefix + "claim", "email");
        configuration.addProperty(prefix + "oidcConfigurationURL", OIDC_CONFIGURATION_URL);
        configuration.addProperty(prefix + "scope", "email");
        configuration.addProperty(prefix + "aud", "manage-sieve");
        return configuration;
    }
}
