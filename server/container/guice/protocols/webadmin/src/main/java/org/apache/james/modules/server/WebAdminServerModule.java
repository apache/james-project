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

import static org.apache.james.webadmin.WebAdminConfiguration.DISABLED_CONFIGURATION;
import static org.apache.james.webadmin.WebAdminServer.NO_CONFIGURATION;

import java.io.FileNotFoundException;
import java.util.List;
import java.util.Optional;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.james.jwt.JwtTokenVerifier;
import org.apache.james.lifecycle.api.Configurable;
import org.apache.james.utils.ConfigurationPerformer;
import org.apache.james.utils.GuiceProbe;
import org.apache.james.utils.PropertiesProvider;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.FixedPortSupplier;
import org.apache.james.webadmin.TlsConfiguration;
import org.apache.james.webadmin.WebAdminConfiguration;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.authentication.AuthenticationFilter;
import org.apache.james.webadmin.authentication.JwtFilter;
import org.apache.james.webadmin.authentication.NoAuthenticationFilter;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;

public class WebAdminServerModule extends AbstractModule {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebAdminServerModule.class);
    private static final boolean DEFAULT_JWT_DISABLED = false;
    private static final boolean DEFAULT_DISABLED = false;
    private static final String DEFAULT_NO_CORS_ORIGIN = null;
    private static final boolean DEFAULT_CORS_DISABLED = false;
    private static final String DEFAULT_NO_KEYSTORE = null;
    private static final boolean DEFAULT_HTTPS_DISABLED = false;
    private static final String DEFAULT_NO_PASSWORD = null;
    private static final String DEFAULT_NO_TRUST_KEYSTORE = null;
    private static final String DEFAULT_NO_TRUST_PASSWORD = null;

    @Override
    protected void configure() {
        install(new TaskRoutesModule());

        bind(JsonTransformer.class).in(Scopes.SINGLETON);
        bind(WebAdminServer.class).in(Scopes.SINGLETON);

        Multibinder.newSetBinder(binder(), ConfigurationPerformer.class).addBinding().to(WebAdminServerModuleConfigurationPerformer.class);
        Multibinder.newSetBinder(binder(), GuiceProbe.class).addBinding().to(WebAdminGuiceProbe.class);
    }

    @Provides
    public WebAdminConfiguration provideWebAdminConfiguration(PropertiesProvider propertiesProvider) throws Exception {
        try {
            PropertiesConfiguration configurationFile = propertiesProvider.getConfiguration("webadmin");
            return WebAdminConfiguration.builder()
                .enable(configurationFile.getBoolean("enabled", DEFAULT_DISABLED))
                .port(new FixedPortSupplier(configurationFile.getInt("port", WebAdminServer.DEFAULT_PORT)))
                .tls(readHttpsConfiguration(configurationFile))
                .enableCORS(configurationFile.getBoolean("cors.enable", DEFAULT_CORS_DISABLED))
                .urlCORSOrigin(configurationFile.getString("cors.origin", DEFAULT_NO_CORS_ORIGIN))
                .host(configurationFile.getString("host", WebAdminConfiguration.DEFAULT_HOST))
                .build();
        } catch (FileNotFoundException e) {
            LOGGER.info("No webadmin.properties file. Disabling WebAdmin interface.");
            return DISABLED_CONFIGURATION;
        }
    }

    @Provides
    public AuthenticationFilter providesAuthenticationFilter(PropertiesProvider propertiesProvider,
                                                             JwtTokenVerifier jwtTokenVerifier) throws Exception {
        try {
            PropertiesConfiguration configurationFile = propertiesProvider.getConfiguration("webadmin");
            if (configurationFile.getBoolean("jwt.enabled", DEFAULT_JWT_DISABLED)) {
                return new JwtFilter(jwtTokenVerifier);
            }
            return new NoAuthenticationFilter();
        } catch (FileNotFoundException e) {
            return new NoAuthenticationFilter();
        }
    }

    private Optional<TlsConfiguration> readHttpsConfiguration(PropertiesConfiguration configurationFile) {
        boolean enabled = configurationFile.getBoolean("https.enabled", DEFAULT_HTTPS_DISABLED);
        if (enabled) {
            return Optional.of(TlsConfiguration.builder()
                .raw(configurationFile.getString("https.keystore", DEFAULT_NO_KEYSTORE),
                    configurationFile.getString("https.password", DEFAULT_NO_PASSWORD),
                    configurationFile.getString("https.trust.keystore", DEFAULT_NO_TRUST_KEYSTORE),
                    configurationFile.getString("https.trust.password", DEFAULT_NO_TRUST_PASSWORD))
                .build());
        }
        return Optional.empty();
    }

    @Singleton
    public static class WebAdminServerModuleConfigurationPerformer implements ConfigurationPerformer {

        private final WebAdminServer webAdminServer;

        @Inject
        public WebAdminServerModuleConfigurationPerformer(WebAdminServer webAdminServer) {
            this.webAdminServer = webAdminServer;
        }

        @Override
        public void initModule() {
            try {
                webAdminServer.configure(NO_CONFIGURATION);
            } catch (ConfigurationException e) {
                throw Throwables.propagate(e);
            }
        }

        @Override
        public List<Class<? extends Configurable>> forClasses() {
            return ImmutableList.of(WebAdminServer.class);
        }
    }

}
