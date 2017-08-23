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
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.webadmin.authentication.AuthenticationFilter;
import org.apache.james.webadmin.authentication.NoAuthenticationFilter;
import org.apache.james.webadmin.mdc.MDCCleanupFilter;
import org.apache.james.webadmin.mdc.MDCFilter;
import org.apache.james.webadmin.metric.MetricPostFilter;
import org.apache.james.webadmin.metric.MetricPreFilter;
import org.apache.james.webadmin.routes.CORSRoute;
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
    private final AuthenticationFilter authenticationFilter;
    private final MetricFactory metricFactory;

    // Spark do not allow to retrieve allocated port when using a random port. Thus we generate the port.
    @Inject
    private WebAdminServer(WebAdminConfiguration configuration, Set<Routes> routesList, AuthenticationFilter authenticationFilter,
                           MetricFactory metricFactory) {
        this.configuration = configuration;
        this.routesList = routesList;
        this.authenticationFilter = authenticationFilter;
        this.metricFactory = metricFactory;
        this.service = Service.ignite();
    }

    @VisibleForTesting
    public WebAdminServer(MetricFactory metricFactory, Routes... routes) throws IOException {
        this(WebAdminConfiguration.testingConfiguration(),
            ImmutableSet.copyOf(routes),
            new NoAuthenticationFilter(),
            metricFactory);
    }

    @Override
    public void configure(HierarchicalConfiguration config) throws ConfigurationException {
        if (configuration.isEnabled()) {
            service.port(configuration.getPort().toInt());
            configureHTTPS();
            configureCORS();
            configureMetrics();
            service.before(authenticationFilter);
            configureMDC();
            routesList.forEach(routes -> routes.define(service));
            service.awaitInitialization();
            LOGGER.info("Web admin server started");
        }
    }

    private void configureMDC() {
        service.before(new MDCFilter());
        service.after(new MDCCleanupFilter());
    }

    private void configureMetrics() {
        service.before(new MetricPreFilter(metricFactory));
        service.after(new MetricPostFilter());
    }

    private void configureHTTPS() {
        if (configuration.isTlsEnabled()) {
            TlsConfiguration tlsConfiguration = configuration.getTlsConfiguration();
            service.secure(tlsConfiguration.getKeystoreFilePath(),
                tlsConfiguration.getKeystorePassword(),
                tlsConfiguration.getTruststoreFilePath(),
                tlsConfiguration.getTruststorePassword());
            LOGGER.info("Web admin set up to use HTTPS");
        }
    }

    private void configureCORS() {
        if (configuration.isEnabled()) {
            service.before(new CORSFilter(configuration.getUrlCORSOrigin()));
            new CORSRoute().define(service);
            LOGGER.info("Web admin set up to enable CORS from " + configuration.getUrlCORSOrigin());
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
