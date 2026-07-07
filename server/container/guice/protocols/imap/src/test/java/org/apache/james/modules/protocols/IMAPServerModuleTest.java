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
import org.apache.james.protocols.sasl.OauthBearerSaslMechanismFactory;
import org.apache.james.protocols.sasl.PlainSaslMechanismFactory;
import org.apache.james.protocols.sasl.XOauth2SaslMechanismFactory;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

class IMAPServerModuleTest {
    private final IMAPServerModule testee = new IMAPServerModule();

    @Test
    void provideDefaultImapSaslMechanismFactoriesShouldReturnJamesDefaults() {
        // GIVEN no auth.saslMechanisms configuration
        // WHEN IMAP provides its default SASL factories

        // THEN existing James IMAP defaults are preserved in order
        assertThat(testee.provideDefaultImapSaslMechanismFactories(
                new PlainSaslMechanismFactory(),
                new OauthBearerSaslMechanismFactory(),
                new XOauth2SaslMechanismFactory()))
            .map(factory -> factory.getClass().getSimpleName())
            .containsExactly(
                PlainSaslMechanismFactory.class.getSimpleName(),
                OauthBearerSaslMechanismFactory.class.getSimpleName(),
                XOauth2SaslMechanismFactory.class.getSimpleName());
    }

    @Test
    void retrieveSaslMechanismFactoryClassNamesShouldReturnEmptyWhenAbsent() throws Exception {
        // GIVEN no auth.saslMechanisms configuration.
        // The empty configured list lets the resolver use the Guice-provided default factory list.
        // Community custom IMAP packages can override that default factory list to avoid breaking changes.
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();

        // WHEN auth.saslMechanisms is absent
        ImmutableList<String> mechanismFactoryClassNames = testee.retrieveSaslMechanismFactoryClassNames(configuration);

        // THEN there is no configured override
        assertThat(mechanismFactoryClassNames).isEmpty();
    }

    @Test
    void retrieveSaslMechanismFactoryClassNamesShouldReturnConfiguredSaslFactoryList() throws Exception {
        // GIVEN an explicit server-specific SASL factory list
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("auth.saslMechanisms",
            "PlainSaslMechanismFactory,com.example.CustomSaslMechanismFactory,PlainSaslMechanismFactory");

        // WHEN IMAP resolves configured factory class names
        ImmutableList<String> mechanismFactoryClassNames = testee.retrieveSaslMechanismFactoryClassNames(configuration);

        // THEN the exact configured order is passed to the resolver
        assertThat(mechanismFactoryClassNames)
            .containsExactly("PlainSaslMechanismFactory", "com.example.CustomSaslMechanismFactory", "PlainSaslMechanismFactory");
    }

    @Test
    void retrieveSaslMechanismFactoryClassNamesShouldRejectBlankConfiguredList() {
        // GIVEN auth.saslMechanisms is present but blank
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("auth.saslMechanisms", " ");

        // WHEN resolving factory class names
        // THEN startup fails instead of silently disabling all mechanisms
        assertThatThrownBy(() -> testee.retrieveSaslMechanismFactoryClassNames(configuration))
            .isInstanceOf(ConfigurationException.class);
    }

    @Test
    void retrieveSaslMechanismFactoryClassNamesShouldRejectBlankEntry() {
        // GIVEN auth.saslMechanisms contains a blank entry
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("auth.saslMechanisms", "PlainSaslMechanismFactory,,XOauth2SaslMechanismFactory");

        // WHEN resolving factory class names
        // THEN startup fails with an invalid configured list
        assertThatThrownBy(() -> testee.retrieveSaslMechanismFactoryClassNames(configuration))
            .isInstanceOf(ConfigurationException.class);
    }
}
