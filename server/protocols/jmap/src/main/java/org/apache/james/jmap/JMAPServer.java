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

package org.apache.james.jmap;

import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static reactor.netty.Metrics.HTTP_CLIENT_PREFIX;
import static reactor.netty.Metrics.HTTP_SERVER_PREFIX;
import static reactor.netty.Metrics.URI;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.lifecycle.api.Startable;
import org.apache.james.util.Port;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Multimap;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.config.MeterFilter;
import io.netty.handler.codec.http.HttpMethod;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.netty.http.server.HttpServerRequest;

public class JMAPServer implements Startable {
    public static final boolean REACTOR_NETTY_METRICS_ENABLE = Boolean.parseBoolean(System.getProperty("james.jmap.reactor.netty.metrics.enabled", "false"));
    private static final int REACTOR_NETTY_METRICS_MAX_URI_TAGS = 100;
    private static final int RANDOM_PORT = 0;
    @VisibleForTesting
    static final String OTHER_URI_TAG = "other";
    private static final AtomicBoolean REACTOR_NETTY_METRICS_CONFIGURED = new AtomicBoolean(false);

    private final JMAPConfiguration configuration;
    private final VersionParser versionParser;
    private final Multimap<Version, JMAPRoute> routes;
    private final List<JMAPRoute> corsRoutes;
    private final List<Endpoint> endpoints;
    private Optional<DisposableServer> server;

    @Inject
    public JMAPServer(JMAPConfiguration configuration, Set<JMAPRoutesHandler> jmapRoutesHandlers, VersionParser versionParser) {
        this.configuration = configuration;
        this.versionParser = versionParser;
        this.server = Optional.empty();

        this.routes = versionParser.getSupportedVersions()
            .stream()
            .flatMap(version -> jmapRoutesHandlers.stream()
                .flatMap(handler -> handler.routes(version)
                    .map(route -> Pair.of(version, route))))
            .filter(route -> !route.getRight().getEndpoint().getMethod().equals(HttpMethod.OPTIONS))
            .collect(ImmutableListMultimap.toImmutableListMultimap(
                Pair::getKey,
                Pair::getValue));
        this.corsRoutes = versionParser.getSupportedVersions()
            .stream()
            .flatMap(version -> jmapRoutesHandlers.stream()
                .flatMap(handler -> handler.routes(version)))
            .filter(route -> route.getEndpoint().getMethod().equals(HttpMethod.OPTIONS))
            .collect(ImmutableList.toImmutableList());
        this.endpoints = Stream.concat(routes.values().stream(), corsRoutes.stream())
            .map(JMAPRoute::getEndpoint)
            .distinct()
            .collect(ImmutableList.toImmutableList());
    }

    public Port getPort() {
        return server.map(DisposableServer::port)
            .map(Port::of)
            .orElseThrow(() -> new IllegalStateException("port is not available because server is not started or disabled"));
    }

    public void start() {
        if (configuration.isEnabled()) {
            if (REACTOR_NETTY_METRICS_ENABLE) {
                configureReactorNettyMetrics();
            }
            server = Optional.of(HttpServer.create()
                .port(configuration.getPort()
                    .map(Port::getValue)
                    .orElse(RANDOM_PORT))
                .handle((request, response) -> handleVersionRoute(request).handleRequest(request, response))
                .wiretap(wireTapEnabled())
                .metrics(REACTOR_NETTY_METRICS_ENABLE, this::uriTagValue)
                .bindNow());
        }
    }

    private static void configureReactorNettyMetrics() {
        // The filters apply to the global registry: register them once, before any meter is created
        if (!REACTOR_NETTY_METRICS_CONFIGURED.compareAndSet(false, true)) {
            return;
        }
        Metrics.globalRegistry
            .config()
            .meterFilter(MeterFilter.maximumAllowableTags(HTTP_CLIENT_PREFIX, URI, REACTOR_NETTY_METRICS_MAX_URI_TAGS, MeterFilter.deny()))
            .meterFilter(MeterFilter.maximumAllowableTags(HTTP_SERVER_PREFIX, URI, REACTOR_NETTY_METRICS_MAX_URI_TAGS, MeterFilter.deny()));
    }

    /**
     * The URI tag of the server metrics is the path template of the matching route, so that a client requesting
     * random paths can not create new meters.
     */
    @VisibleForTesting
    String uriTagValue(String path) {
        return endpoints.stream()
            .filter(endpoint -> endpoint.getUriMatcher().matches(path))
            .map(Endpoint::getPath)
            .findFirst()
            .orElse(OTHER_URI_TAG);
    }

    private boolean wireTapEnabled() {
        return LoggerFactory.getLogger("org.apache.james.jmap.wire").isTraceEnabled();
    }

    private JMAPRoute.Action handleVersionRoute(HttpServerRequest request) {
        if (request.method().equals(HttpMethod.OPTIONS)) {
            return retrieveMatchingAction(request, corsRoutes.stream());
        }
        try {
            Version version = versionParser.parseRequestVersionHeader(request);

            return retrieveMatchingAction(request, routes.get(version).stream());
        } catch (IllegalArgumentException e) {
            return (req, res) -> res.status(BAD_REQUEST).send();
        }
    }

    private JMAPRoute.Action retrieveMatchingAction(HttpServerRequest request, Stream<JMAPRoute> routeStream) {
        return routeStream
            .filter(jmapRoute -> jmapRoute.matches(request))
            .map(JMAPRoute::getAction)
            .findFirst()
            .orElse((req, res) -> res.status(NOT_FOUND).send());
    }

    @PreDestroy
    public void stop() {
        server.ifPresent(DisposableServer::disposeNow);
    }
}
