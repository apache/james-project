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

import static org.apache.james.webadmin.utils.ErrorResponder.ErrorType.INVALID_ARGUMENT;
import static org.apache.james.webadmin.utils.ErrorResponder.ErrorType.NOT_FOUND;
import static org.apache.james.webadmin.utils.ErrorResponder.ErrorType.SERVER_ERROR;
import static org.eclipse.jetty.http.HttpStatus.BAD_REQUEST_400;
import static org.eclipse.jetty.http.HttpStatus.INTERNAL_SERVER_ERROR_500;
import static org.eclipse.jetty.http.HttpStatus.NOT_FOUND_404;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Named;

import org.apache.james.lifecycle.api.Startable;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.Port;
import org.apache.james.webadmin.authentication.AuthenticationFilter;
import org.apache.james.webadmin.mdc.LoggingRequestFilter;
import org.apache.james.webadmin.mdc.LoggingResponseFilter;
import org.apache.james.webadmin.mdc.MDCCleanupFilter;
import org.apache.james.webadmin.mdc.MDCFilter;
import org.apache.james.webadmin.metric.MetricPostFilter;
import org.apache.james.webadmin.metric.MetricPreFilter;
import org.apache.james.webadmin.routes.CORSRoute;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonExtractException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

import spark.Service;

public class WebAdminServer implements Startable {
    private static final Logger LOGGER = LoggerFactory.getLogger(WebAdminServer.class);

    public static final int DEFAULT_PORT = 8080;
    private static final int DEFAULTS = -1;

    private final WebAdminConfiguration configuration;
    private final List<Routes> privateRoutes;
    private final List<PublicRoutes> publicRoutes;
    private final Service service;
    private final AuthenticationFilter authenticationFilter;
    private final MetricFactory metricFactory;
    private final LoggingRequestFilter loggingRequestFilter;

    @Inject
    protected WebAdminServer(WebAdminConfiguration configuration,
                             @Named("webAdminRoutes") List<Routes> routesList,
                             AuthenticationFilter authenticationFilter,
                             MetricFactory metricFactory,
                             LoggingRequestFilter loggingRequestFilter) {
        this.configuration = configuration;
        this.privateRoutes = privateRoutes(routesList);
        this.publicRoutes = publicRoutes(routesList);
        this.authenticationFilter = authenticationFilter;
        this.metricFactory = metricFactory;
        this.loggingRequestFilter = loggingRequestFilter;
        this.service = Service.ignite();
    }

    private static List<Routes> privateRoutes(List<Routes> routes) {
        return routes.stream()
            .filter(route -> !(route instanceof PublicRoutes))
            .collect(ImmutableList.toImmutableList());
    }

    private static List<PublicRoutes> publicRoutes(List<Routes>  routes) {
        return routes.stream()
            .filter(PublicRoutes.class::isInstance)
            .map(PublicRoutes.class::cast)
            .collect(ImmutableList.toImmutableList());
    }

    public WebAdminServer start() {
        service.initExceptionHandler(e -> {
            throw new RuntimeException(e);
        });
        if (configuration.isEnabled()) {
            service.threadPool(configuration.getMaxThreadCount().orElse(DEFAULTS), configuration.getMinThreadCount().orElse(DEFAULT_PORT), DEFAULTS);
            service.ipAddress(listeningIP());
            service.port(configuration.getPort().get().getValue());
            configureExceptionHanding();
            configureHTTPS();
            configureCORS();
            configureMetrics();
            service.before((request, response) -> response.type(Constants.JSON_CONTENT_TYPE));
            configureMDC();
            privateRoutes.forEach(routes -> {
                service.before(routes.getBasePath(), authenticationFilter);
                service.before(routes.getBasePath() + "/*", authenticationFilter);
                routes.define(service);
            });
            publicRoutes.forEach(routes -> routes.define(service));
            service.awaitInitialization();
            LOGGER.info("Web admin server started on port {}", service.port());
        }
        return this;
    }

    private String listeningIP() {
        try {
            if (configuration.getHost().equals("0.0.0.0")) {
                return configuration.getHost();
            }
            return InetAddress.getByName(configuration.getHost()).getHostAddress();
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    private void configureMDC() {
        service.before(new MDCFilter());
        service.before(loggingRequestFilter);
        service.after(new LoggingResponseFilter());
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
            LOGGER.info("Web admin set up to enable CORS from {}", configuration.getUrlCORSOrigin());
        }
    }

    private void configureExceptionHanding() {
        service.notFound((req, res) -> ErrorResponder.builder()
            .statusCode(NOT_FOUND_404)
            .type(NOT_FOUND)
            .message("%s %s can not be found", req.requestMethod(), req.pathInfo())
            .asString());

        service.internalServerError((req, res) -> ErrorResponder.builder()
            .statusCode(INTERNAL_SERVER_ERROR_500)
            .type(SERVER_ERROR)
            .message("WebAdmin encountered an unexpected internal error")
            .asString());

        service.exception(JsonExtractException.class, (ex, req, res) -> {
            res.status(BAD_REQUEST_400);
            res.body(ErrorResponder.builder()
                .statusCode(BAD_REQUEST_400)
                .type(INVALID_ARGUMENT)
                .message("JSON payload of the request is not valid")
                .cause(ex)
                .asString());
        });

        service.exception(IllegalArgumentException.class, (ex, req, res) -> {
            LOGGER.info("Invalid arguments supplied in the user request", ex);
            res.status(BAD_REQUEST_400);
            res.body(ErrorResponder.builder()
                .statusCode(BAD_REQUEST_400)
                .type(INVALID_ARGUMENT)
                .message("Invalid arguments supplied in the user request")
                .cause(ex)
                .asString());
        });
    }

    @PreDestroy
    public void destroy() {
        if (configuration.isEnabled()) {
            service.stop();
            service.awaitStop();
            LOGGER.info("Web admin server stopped");
        }
    }

    public Port getPort() {
        return new Port(service.port());
    }
}
