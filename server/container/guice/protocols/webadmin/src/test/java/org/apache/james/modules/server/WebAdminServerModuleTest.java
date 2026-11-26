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

package org.apache.james.modules.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.james.utils.PropertiesProvider;
import org.apache.james.webadmin.TlsConfiguration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class WebAdminServerModuleTest {
    private static final boolean WEBADMIN_ENABLED = true;
    private static final boolean WEBADMIN_DISABLED = false;
    private static final WebAdminServerModule.PasswordGenerationDefault GENERATION_ENABLED_BY_DEFAULT =
        new WebAdminServerModule.PasswordGenerationDefault(true);
    private static final WebAdminServerModule.PasswordGenerationDefault GENERATION_DISABLED_BY_DEFAULT =
        new WebAdminServerModule.PasswordGenerationDefault(false);

    @Test
    void shouldReturnEmptyWhenNoField() throws Exception {
        Configuration configuration = getConfiguration("webadmin-none");

        assertThat(new WebAdminServerModule().additionalRoutes(configuration))
            .isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenEmptyField() throws Exception {
        Configuration configuration = getConfiguration("webadmin-empty");

        assertThat(new WebAdminServerModule().additionalRoutes(configuration))
            .isEmpty();
    }

    @Test
    void shouldReturnOneRoutes() throws Exception {
        Configuration configuration = getConfiguration("webadmin-one");

        assertThat(new WebAdminServerModule().additionalRoutes(configuration))
            .containsOnly("org.apache.custom.webadmin.CustomRoute");
    }

    @Test
    void shouldReturnSeveralRoutes() throws Exception {
        Configuration configuration = getConfiguration("webadmin-two");

        assertThat(new WebAdminServerModule().additionalRoutes(configuration))
            .containsOnly("org.apache.custom.webadmin.CustomRoute", "org.apache.custom.webadmin.AnotherCustomRoute");
    }

    private Configuration getConfiguration(String name) throws Exception {
        return PropertiesProvider.forTesting().getConfiguration(name);
    }

    @Nested
    class PasswordGeneration {
        @Test
        void passwordShouldBeGeneratedByDefault() {
            assertThat(new WebAdminServerModule().password(new PropertiesConfiguration(), WEBADMIN_ENABLED, GENERATION_ENABLED_BY_DEFAULT))
                .isNotEmpty();
        }

        @Test
        void passwordShouldBeEmptyWhenGenerationIsDisabledByDefault() {
            assertThat(new WebAdminServerModule().password(new PropertiesConfiguration(), WEBADMIN_ENABLED, GENERATION_DISABLED_BY_DEFAULT))
                .isEmpty();
        }

        @Test
        void passwordShouldBeEmptyWhenGenerationIsDisabled() {
            assertThat(new WebAdminServerModule().password(configuration("password.generate", false), WEBADMIN_ENABLED, GENERATION_ENABLED_BY_DEFAULT))
                .isEmpty();
        }

        @Test
        void passwordShouldBeGeneratedWhenGenerationIsEnabled() {
            assertThat(new WebAdminServerModule().password(configuration("password.generate", true), WEBADMIN_ENABLED, GENERATION_DISABLED_BY_DEFAULT))
                .isNotEmpty();
        }

        @Test
        void generatedPasswordShouldNotContainThePasswordSeparator() {
            assertThat(new WebAdminServerModule().password(configuration("password.generate", true), WEBADMIN_ENABLED, GENERATION_ENABLED_BY_DEFAULT))
                .hasValueSatisfying(password -> assertThat(password).doesNotContain(","));
        }

        @Test
        void generatedPasswordsShouldBeRandom() {
            Optional<String> firstPassword = new WebAdminServerModule().password(configuration("password.generate", true), WEBADMIN_ENABLED, GENERATION_ENABLED_BY_DEFAULT);
            Optional<String> secondPassword = new WebAdminServerModule().password(configuration("password.generate", true), WEBADMIN_ENABLED, GENERATION_ENABLED_BY_DEFAULT);

            assertThat(firstPassword).isNotEqualTo(secondPassword);
        }

        @Test
        void configuredPasswordShouldTakePrecedenceOverGeneration() {
            PropertiesConfiguration configuration = configuration("password.generate", true);
            configuration.addProperty("password", "secret");

            assertThat(new WebAdminServerModule().password(configuration, WEBADMIN_ENABLED, GENERATION_ENABLED_BY_DEFAULT))
                .contains("secret");
        }

        @Test
        void passwordShouldNotBeGeneratedWhenWebAdminIsDisabled() {
            assertThat(new WebAdminServerModule().password(configuration("password.generate", true), WEBADMIN_DISABLED, GENERATION_ENABLED_BY_DEFAULT))
                .isEmpty();
        }

        @Test
        void passwordShouldNotBeGeneratedWhenJwtIsEnabled() {
            PropertiesConfiguration configuration = configuration("password.generate", true);
            configuration.addProperty("jwt.enabled", true);

            assertThat(new WebAdminServerModule().password(configuration, WEBADMIN_ENABLED, GENERATION_ENABLED_BY_DEFAULT))
                .isEmpty();
        }

        private PropertiesConfiguration configuration(String key, Object value) {
            PropertiesConfiguration configuration = new PropertiesConfiguration();
            configuration.addProperty(key, value);
            return configuration;
        }
    }

    @Nested
    class HttpsConfigurationTest {
        @Test
        void shouldReturnEmptyWhenHttpsDisabled() {
            PropertiesConfiguration configuration = new PropertiesConfiguration();
            configuration.addProperty("https.enabled", false);

            assertThat(new WebAdminServerModule().readHttpsConfiguration(configuration))
                .isEmpty();
        }

        @Test
        void shouldReadKeystoreConfiguration() {
            PropertiesConfiguration configuration = new PropertiesConfiguration();
            configuration.addProperty("https.enabled", true);
            configuration.addProperty("https.keystore", "conf/keystore.jks");
            configuration.addProperty("https.password", "secret");

            Optional<TlsConfiguration> tlsConfiguration = new WebAdminServerModule().readHttpsConfiguration(configuration);

            assertThat(tlsConfiguration).isPresent();
            assertThat(tlsConfiguration.get().isPem()).isFalse();
            assertThat(tlsConfiguration.get().getKeystoreFilePath()).isEqualTo("conf/keystore.jks");
            assertThat(tlsConfiguration.get().getKeystorePassword()).isEqualTo("secret");
        }

        @Test
        void shouldReadPemConfiguration() {
            PropertiesConfiguration configuration = new PropertiesConfiguration();
            configuration.addProperty("https.enabled", true);
            configuration.addProperty("https.certificates", "conf/fullchain.pem");
            configuration.addProperty("https.privateKey", "conf/privkey.pem");
            configuration.addProperty("https.password", "keySecret");

            Optional<TlsConfiguration> tlsConfiguration = new WebAdminServerModule().readHttpsConfiguration(configuration);

            assertThat(tlsConfiguration).isPresent();
            assertThat(tlsConfiguration.get().isPem()).isTrue();
            assertThat(tlsConfiguration.get().getCertificatesFilePath()).isEqualTo("conf/fullchain.pem");
            assertThat(tlsConfiguration.get().getPrivateKeyFilePath()).isEqualTo("conf/privkey.pem");
            assertThat(tlsConfiguration.get().getPrivateKeyPassword()).isEqualTo("keySecret");
        }

        @Test
        void shouldReadPemConfigurationWithoutPassword() {
            PropertiesConfiguration configuration = new PropertiesConfiguration();
            configuration.addProperty("https.enabled", true);
            configuration.addProperty("https.certificates", "conf/fullchain.pem");
            configuration.addProperty("https.privateKey", "conf/privkey.pem");

            Optional<TlsConfiguration> tlsConfiguration = new WebAdminServerModule().readHttpsConfiguration(configuration);

            assertThat(tlsConfiguration).isPresent();
            assertThat(tlsConfiguration.get().isPem()).isTrue();
            assertThat(tlsConfiguration.get().getCertificatesFilePath()).isEqualTo("conf/fullchain.pem");
            assertThat(tlsConfiguration.get().getPrivateKeyFilePath()).isEqualTo("conf/privkey.pem");
            assertThat(tlsConfiguration.get().getPrivateKeyPassword()).isNull();
        }
    }
}