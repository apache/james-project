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

package org.apache.james.webadmin;

import java.io.IOException;
import java.util.Set;

import javax.annotation.PreDestroy;
import javax.inject.Inject;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.lifecycle.api.Configurable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;

import spark.Service;

public class WebAdminServer implements Configurable {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebAdminServer.class);
    public static final HierarchicalConfiguration NO_CONFIGURATION = null;
    public static final int DEFAULT_PORT = 8080;

    private final WebAdminConfiguration configuration;
    private final Set<Routes> routesList;
    private final Service service;

    // Spark do not allow to retrieve allocated port when using a random port. Thus we generate the port.
    @Inject
    private WebAdminServer(WebAdminConfiguration configuration, Set<Routes> routesList) {
        this.configuration = configuration;
        this.routesList = routesList;
        this.service = Service.ignite();
    }

    @VisibleForTesting
    public WebAdminServer(Routes... routes) throws IOException {
        this(WebAdminConfiguration.builder()
            .enabled()
            .port(new RandomPort())
            .build(),
            ImmutableSet.copyOf(routes));
    }

    @Override
    public void configure(HierarchicalConfiguration config) throws ConfigurationException {
        if (configuration.isEnabled()) {
            service.port(configuration.getPort().toInt());
            HttpsConfiguration httpsConfiguration = configuration.getHttpsConfiguration();
            if (httpsConfiguration.isEnabled()) {
                service.secure(httpsConfiguration.getKeystoreFilePath(),
                    httpsConfiguration.getKeystorePassword(),
                    httpsConfiguration.getTruststoreFilePath(),
                    httpsConfiguration.getTruststorePassword());
                LOGGER.info("Web admin set up to use HTTPS");
            }
            routesList.forEach(routes -> routes.define(service));
            LOGGER.info("Web admin server started");
        }
    }

    @PreDestroy
    public void destroy() {
        if (configuration.isEnabled()) {
            service.stop();
            LOGGER.info("Web admin server stopped");
        }
    }

    public void await() {
        service.awaitInitialization();
    }

    public Port getPort() {
        return configuration.getPort();
    }
}
