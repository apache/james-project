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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.Security;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.util.Port;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;
import org.apache.james.utils.PropertiesProvider;
import org.apache.james.wkd.WebKeyDirectoryConfiguration;
import org.apache.james.wkd.WebKeyDirectoryModule;
import org.apache.james.wkd.WebKeyDirectoryRoutes;
import org.apache.james.wkd.WebKeyDirectoryServer;
import org.apache.james.wkd.http.DirectPubKeyRoutes;
import org.apache.james.wkd.http.PolicyRoutes;
import org.apache.james.wkd.http.SubmissionAddressRoutes;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;

public class WebKeyDirectoryServerModule extends AbstractModule {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebKeyDirectoryServerModule.class);

    private static final int DEFAULT_WKD_PORT = 443;

    @Override
    protected void configure() {
        install(new WebKeyDirectoryModule());
        Multibinder<WebKeyDirectoryRoutes> routesBinder = Multibinder.newSetBinder(binder(),
            WebKeyDirectoryRoutes.class);

        routesBinder.addBinding().to(DirectPubKeyRoutes.class);
        routesBinder.addBinding().to(PolicyRoutes.class);
        routesBinder.addBinding().to(SubmissionAddressRoutes.class);
    }

    @ProvidesIntoSet
    InitializationOperation startWebKeyDirectoryServer(WebKeyDirectoryServer server,
        WebKeyDirectoryConfiguration configuration) {
        return InitilizationOperationBuilder.forClass(WebKeyDirectoryServer.class).init(() -> {
            if (configuration.isEnabled()) {
                server.start();
                registerPEMWithSecurityProvider();
            }
        });
    }

    private void registerPEMWithSecurityProvider() {
        Security.addProvider(new BouncyCastleProvider());
    }

    @Provides
    @Singleton
    WebKeyDirectoryConfiguration provideConfiguration(PropertiesProvider propertiesProvider)
        throws ConfigurationException, IOException {
        try {
            Configuration configuration = propertiesProvider.getConfiguration("web-key-directory");
            return WebKeyDirectoryConfiguration.builder()
                .enabled(configuration.getBoolean("enabled", true))
                .port(Port.of(configuration.getInt("wkd.port", DEFAULT_WKD_PORT)))
                .keyStore(configuration.getString("keyStore"))
                .keyStorePassword(configuration.getString("keyStorePassword"))
            .build();
        } catch (FileNotFoundException e) {
            LOGGER.warn(
                "Could not find Web Key Directory configuration file. Web Key Directory server will not be enabled.");
            return WebKeyDirectoryConfiguration.builder().disable().build();
        }
    }
}
