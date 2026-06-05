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

import java.util.Optional;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.protocols.api.sasl.SaslAuthenticationServiceFactory;
import org.apache.james.protocols.api.sasl.SaslProtocol;
import org.apache.james.protocols.api.sasl.SaslSessionContext;
import org.apache.james.utils.ClassName;
import org.apache.james.utils.GuiceLoader;
import org.apache.james.utils.NamingScheme;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Module;

class IMAPServerModuleTest {
    private static final JamesDefaultImapSaslMechanismClassNamesProvider JAMES_DEFAULT_PROVIDER = new JamesDefaultImapSaslMechanismClassNamesProvider();
    private static final GuiceLoader GUICE_LOADER = new GuiceLoader() {
        @Override
        public <T> T instantiate(ClassName className) throws ClassNotFoundException {
            if (className.getName().equals(CustomAuthenticationServiceFactoryProvider.class.getName())) {
                return (T) new CustomAuthenticationServiceFactoryProvider();
            }
            throw new ClassNotFoundException(className.getName());
        }

        @Override
        public <T> InvocationPerformer<T> withNamingSheme(NamingScheme namingSheme) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> InvocationPerformer<T> withChildModule(Module childModule) {
            throw new UnsupportedOperationException();
        }
    };

    private record CustomAuthenticationServiceFactoryProvider() implements ImapSaslAuthenticationServiceFactoryProvider {
        @Override
        public ImmutableList<SaslAuthenticationServiceFactory<?>> provide(HierarchicalConfiguration<ImmutableNode> configuration) {
            return ImmutableList.of(new CustomAuthenticationServiceFactory(configuration.getString("auth.custom.realm")));
        }
    }

    private record CustomAuthenticationServiceFactory(String realm) implements SaslAuthenticationServiceFactory<CustomAuthenticationService> {
        @Override
        public SaslProtocol protocol() {
            return SaslProtocol.IMAP;
        }

        @Override
        public Class<CustomAuthenticationService> serviceType() {
            return CustomAuthenticationService.class;
        }

        @Override
        public Optional<CustomAuthenticationService> create(SaslSessionContext context) {
            return Optional.of(new CustomAuthenticationService(realm));
        }
    }

    private record CustomAuthenticationService(String realm) {
    }

    private final IMAPServerModule testee = new IMAPServerModule();

    @Test
    void retrieveSaslMechanismClassNamesShouldReturnDefaultsWhenAbsent() throws Exception {
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();

        assertThat(testee.retrieveSaslMechanismClassNames(configuration, JAMES_DEFAULT_PROVIDER))
            .containsExactly("PlainSaslMechanism", "OauthBearerSaslMechanism", "XOauth2SaslMechanism");
    }

    @Test
    void retrieveSaslMechanismClassNamesShouldUseDefaultProviderWhenAbsent() throws Exception {
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        DefaultImapSaslMechanismClassNamesProvider defaultProvider = ignored -> ImmutableList.of("com.example.CustomSaslMechanism");

        assertThat(testee.retrieveSaslMechanismClassNames(configuration, defaultProvider))
            .containsExactly("com.example.CustomSaslMechanism");
    }

    @Test
    void retrieveSaslMechanismClassNamesShouldReturnConfiguredList() throws Exception {
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("auth.saslMechanisms",
            "PlainSaslMechanism,com.example.CustomSaslMechanism,PlainSaslMechanism");

        assertThat(testee.retrieveSaslMechanismClassNames(configuration, JAMES_DEFAULT_PROVIDER))
            .containsExactly("PlainSaslMechanism", "com.example.CustomSaslMechanism", "PlainSaslMechanism");
    }

    @Test
    void retrieveSaslMechanismClassNamesShouldIgnoreDefaultProviderWhenConfigured() throws Exception {
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("auth.saslMechanisms", "PlainSaslMechanism");
        DefaultImapSaslMechanismClassNamesProvider defaultProvider = ignored -> ImmutableList.of("com.example.CustomSaslMechanism");

        assertThat(testee.retrieveSaslMechanismClassNames(configuration, defaultProvider))
            .containsExactly("PlainSaslMechanism");
    }

    @Test
    void retrieveSaslMechanismClassNamesShouldRejectBlankConfiguredList() {
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("auth.saslMechanisms", " ");

        assertThatThrownBy(() -> testee.retrieveSaslMechanismClassNames(configuration, JAMES_DEFAULT_PROVIDER))
            .isInstanceOf(ConfigurationException.class);
    }

    @Test
    void retrieveSaslMechanismClassNamesShouldRejectBlankEntry() {
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("auth.saslMechanisms", "PlainSaslMechanism,,XOauth2SaslMechanism");

        assertThatThrownBy(() -> testee.retrieveSaslMechanismClassNames(configuration, JAMES_DEFAULT_PROVIDER))
            .isInstanceOf(ConfigurationException.class);
    }

    @Test
    void extensionSaslMechanismShouldLoadItsOwnAuthConfigurationFromBoundProvider() throws Exception {
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("auth.custom.realm", "james.example");
        ImapSaslAuthenticationServiceFactoryProvider provider = new CustomAuthenticationServiceFactoryProvider();

        assertThat(testee.retrieveSaslAuthenticationServiceFactories(configuration, GUICE_LOADER, ImmutableSet.of(provider)))
            .containsExactly(new CustomAuthenticationServiceFactory("james.example"));
    }

    @Test
    void extensionSaslMechanismShouldLoadItsOwnAuthConfigurationFromConfiguredProvider() throws Exception {
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("auth.custom.realm", "james.example");
        configuration.addProperty("auth.saslAuthenticationServiceFactoryProviderExtensions", CustomAuthenticationServiceFactoryProvider.class.getName());

        assertThat(testee.retrieveSaslAuthenticationServiceFactories(configuration, GUICE_LOADER, ImmutableSet.of()))
            .containsExactly(new CustomAuthenticationServiceFactory("james.example"));
    }

    @Test
    void retrieveConfiguredSaslAuthenticationServiceFactoryProvidersShouldRejectBlankConfiguredList() {
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("auth.saslAuthenticationServiceFactoryProviderExtensions", " ");

        assertThatThrownBy(() -> testee.retrieveConfiguredSaslAuthenticationServiceFactoryProviders(configuration, GUICE_LOADER))
            .isInstanceOf(ConfigurationException.class);
    }

    @Test
    void retrieveConfiguredSaslAuthenticationServiceFactoryProvidersShouldFailWhenClassIsUnknown() {
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("auth.saslAuthenticationServiceFactoryProviderExtensions", "com.example.MissingProvider");

        assertThatThrownBy(() -> testee.retrieveConfiguredSaslAuthenticationServiceFactoryProviders(configuration, GUICE_LOADER))
            .isInstanceOf(ConfigurationException.class);
    }
}
