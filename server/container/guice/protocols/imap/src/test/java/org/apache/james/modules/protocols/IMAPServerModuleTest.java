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

package org.apache.james.modules.protocols;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

class IMAPServerModuleTest {
    private static final JamesDefaultImapSaslMechanismClassNamesProvider JAMES_DEFAULT_PROVIDER = new JamesDefaultImapSaslMechanismClassNamesProvider();

    private final IMAPServerModule testee = new IMAPServerModule();

    @Test
    void retrieveSaslMechanismClassNamesShouldReturnDefaultsWhenAbsent() throws Exception {
        // GIVEN no auth.saslMechanisms configuration
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();

        // WHEN IMAP resolves its SASL mechanism class names
        ImmutableList<String> mechanismClassNames = testee.retrieveSaslMechanismClassNames(configuration, JAMES_DEFAULT_PROVIDER);

        // THEN existing James IMAP defaults are preserved
        assertThat(mechanismClassNames)
            .containsExactly("PlainSaslMechanism", "OauthBearerSaslMechanism", "XOauth2SaslMechanism");
    }

    @Test
    void retrieveSaslMechanismClassNamesShouldUseConfiguredDefaultProviderOverJamesDefaultProviderWhenAbsent() throws Exception {
        // GIVEN a non-James default provider configured by Guice.
        // This allows community custom IMAP packages with custom authentication to provide
        // their own default SASL list and avoid breaking changes when auth.saslMechanisms is absent.
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        DefaultImapSaslMechanismClassNamesProvider communityDefaultProvider = ignored -> ImmutableList.of("com.example.CustomSaslMechanism");

        // WHEN auth.saslMechanisms is absent
        ImmutableList<String> mechanismClassNames = testee.retrieveSaslMechanismClassNames(configuration, communityDefaultProvider);

        // THEN IMAP uses the configured community default provider instead of James default mechanisms
        assertThat(mechanismClassNames)
            .containsExactly("com.example.CustomSaslMechanism");
    }

    @Test
    void retrieveSaslMechanismClassNamesShouldReturnConfiguredSaslMechanismList() throws Exception {
        // GIVEN an explicit server-specific SASL mechanism list
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("auth.saslMechanisms",
            "PlainSaslMechanism,com.example.CustomSaslMechanism,PlainSaslMechanism");

        // WHEN IMAP resolves configured class names
        ImmutableList<String> mechanismClassNames = testee.retrieveSaslMechanismClassNames(configuration, JAMES_DEFAULT_PROVIDER);

        // THEN the exact configured order is passed to the resolver
        assertThat(mechanismClassNames)
            .containsExactly("PlainSaslMechanism", "com.example.CustomSaslMechanism", "PlainSaslMechanism");
    }

    @Test
    void retrieveSaslMechanismClassNamesShouldRejectBlankConfiguredList() {
        // GIVEN auth.saslMechanisms is present but blank
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("auth.saslMechanisms", " ");

        // WHEN resolving class names
        // THEN startup fails instead of silently disabling all mechanisms
        assertThatThrownBy(() -> testee.retrieveSaslMechanismClassNames(configuration, JAMES_DEFAULT_PROVIDER))
            .isInstanceOf(ConfigurationException.class);
    }

    @Test
    void retrieveSaslMechanismClassNamesShouldRejectBlankEntry() {
        // GIVEN auth.saslMechanisms contains a blank entry
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("auth.saslMechanisms", "PlainSaslMechanism,,XOauth2SaslMechanism");

        // WHEN resolving class names
        // THEN startup fails with an invalid configured list
        assertThatThrownBy(() -> testee.retrieveSaslMechanismClassNames(configuration, JAMES_DEFAULT_PROVIDER))
            .isInstanceOf(ConfigurationException.class);
    }
}
