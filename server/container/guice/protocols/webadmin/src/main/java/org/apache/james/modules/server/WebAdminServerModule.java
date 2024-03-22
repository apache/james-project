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

import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.inject.Named;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.io.FileUtils;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.jwt.JwtConfiguration;
import org.apache.james.jwt.JwtTokenVerifier;
import org.apache.james.server.task.json.TaskExtensionModule;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.utils.ClassName;
import org.apache.james.utils.ExtensionConfiguration;
import org.apache.james.utils.GuiceGenericLoader;
import org.apache.james.utils.GuiceProbe;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;
import org.apache.james.utils.NamingScheme;
import org.apache.james.utils.PropertiesProvider;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.FixedPortSupplier;
import org.apache.james.webadmin.PortSupplier;
import org.apache.james.webadmin.RandomPortSupplier;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.TlsConfiguration;
import org.apache.james.webadmin.WebAdminConfiguration;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.authentication.AuthenticationFilter;
import org.apache.james.webadmin.authentication.JwtFilter;
import org.apache.james.webadmin.authentication.NoAuthenticationFilter;
import org.apache.james.webadmin.dto.DTOModuleInjections;
import org.apache.james.webadmin.mdc.RequestLogger;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.apache.james.webadmin.utils.JsonTransformerModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;

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
        install(new HealthCheckRoutesModule());
        install(new ServerRouteModule());

        bind(JsonTransformer.class).in(Scopes.SINGLETON);
        bind(WebAdminServer.class).in(Scopes.SINGLETON);

        Multibinder.newSetBinder(binder(), GuiceProbe.class).addBinding().to(WebAdminGuiceProbe.class);
        Multibinder.newSetBinder(binder(), JsonTransformerModule.class);

        Multibinder.newSetBinder(binder(), RequestLogger.class);
    }

    @Provides
    @Singleton
    @Named("webAdminRoutes")
    public List<Routes> provideRoutes(GuiceGenericLoader loader, WebAdminConfiguration configuration, Set<Routes> routesList) {
        List<Routes> customRoutes = configuration.getAdditionalRoutes()
            .stream()
            .map(ClassName::new)
            .map(Throwing.function(loader.<Routes>withNamingSheme(NamingScheme.IDENTITY)::instantiate))
            .peek(routes -> LOGGER.info("Loading WebAdmin route extension {}", routes.getClass().getCanonicalName()))
            .collect(ImmutableList.toImmutableList());

        return ImmutableList.<Routes>builder()
            .addAll(routesList)
            .addAll(customRoutes)
            .build();
    }

    @Provides
    @Singleton
    @Named(DTOModuleInjections.CUSTOM_WEBADMIN_DTO)
    public Set<AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends AdditionalInformationDTO>> provideAdditionalDTOs(GuiceGenericLoader loader, ExtensionConfiguration extensionConfiguration) {
        return extensionConfiguration.getTaskExtensions()
            .stream()
            .map(Throwing.function(loader.<TaskExtensionModule>withNamingSheme(NamingScheme.IDENTITY)::instantiate))
            .map(TaskExtensionModule::taskAdditionalInformationDTOModules)
            .flatMap(Collection::stream)
            .collect(Collectors.toSet());
    }

    @Provides
    @Singleton
    public WebAdminConfiguration provideWebAdminConfiguration(FileSystem fileSystem, PropertiesProvider propertiesProvider) throws Exception {
        try {
            Configuration configurationFile = propertiesProvider.getConfiguration("webadmin");

            List<String> additionalRoutes = additionalRoutes(configurationFile);

            return WebAdminConfiguration.builder()
                .enable(configurationFile.getBoolean("enabled", DEFAULT_DISABLED))
                .port(port(configurationFile))
                .tls(readHttpsConfiguration(configurationFile))
                .enableCORS(configurationFile.getBoolean("cors.enable", DEFAULT_CORS_DISABLED))
                .urlCORSOrigin(configurationFile.getString("cors.origin", DEFAULT_NO_CORS_ORIGIN))
                .host(configurationFile.getString("host", WebAdminConfiguration.DEFAULT_HOST))
                .additionalRoutes(additionalRoutes)
                .jwtPublicKeyPEM(loadPublicKey(fileSystem,
                    Optional.ofNullable(configurationFile.getString("jwt.publickeypem.url", null))))
                .maxThreadCount(Optional.ofNullable(configurationFile.getInteger("maxThreadCount", null)))
                .minThreadCount(Optional.ofNullable(configurationFile.getInteger("minThreadCount", null)))
                .build();
        } catch (FileNotFoundException e) {
            LOGGER.info("No webadmin.properties file. Disabling WebAdmin interface.");
            return DISABLED_CONFIGURATION;
        }
    }

    private PortSupplier port(Configuration configurationFile) {
        int portNumber = configurationFile.getInt("port", WebAdminServer.DEFAULT_PORT);

        if (portNumber == 0) {
            return new RandomPortSupplier();
        }
        return new FixedPortSupplier(portNumber);
    }

    @VisibleForTesting
    ImmutableList<String> additionalRoutes(Configuration configurationFile) {
        return ImmutableList.copyOf(configurationFile.getStringArray("extensions.routes"));
    }

    private Optional<String> loadPublicKey(FileSystem fileSystem, Optional<String> jwtPublickeyPemUrl) {
        return jwtPublickeyPemUrl.map(Throwing.function(url -> FileUtils.readFileToString(fileSystem.getFile(url), StandardCharsets.US_ASCII)));
    }

    @Provides
    @Singleton
    public AuthenticationFilter providesAuthenticationFilter(PropertiesProvider propertiesProvider,
                                                             @Named("webadmin") JwtTokenVerifier.Factory jwtTokenVerifier) throws Exception {
        try {
            Configuration configurationFile = propertiesProvider.getConfiguration("webadmin");
            if (configurationFile.getBoolean("jwt.enabled", DEFAULT_JWT_DISABLED)) {
                return new JwtFilter(jwtTokenVerifier);
            }
            return new NoAuthenticationFilter();
        } catch (FileNotFoundException e) {
            return new NoAuthenticationFilter();
        }
    }

    @Provides
    @Singleton
    @Named("webadmin")
    JwtTokenVerifier.Factory providesJwtTokenVerifier(WebAdminConfiguration webAdminConfiguration,
                                              @Named("jmap") Provider<JwtTokenVerifier> jmapTokenVerifier) {
        return () -> webAdminConfiguration.getJwtPublicKey()
            .map(keyPath -> new JwtConfiguration(ImmutableList.of(keyPath)))
            .map(JwtTokenVerifier::create)
            .orElseGet(jmapTokenVerifier::get);
    }

    private Optional<TlsConfiguration> readHttpsConfiguration(Configuration configurationFile) {
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

    @ProvidesIntoSet
    InitializationOperation workQueue(WebAdminServer instance) {
        return InitilizationOperationBuilder
            .forClass(WebAdminServer.class)
            .init(instance::start);
    }
}
