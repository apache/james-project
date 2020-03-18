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

import static io.netty.handler.codec.http.HttpHeaderNames.ACCEPT;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;

import javax.annotation.PreDestroy;
import javax.inject.Inject;

import org.apache.james.lifecycle.api.Startable;
import org.apache.james.util.Port;
import org.slf4j.LoggerFactory;

import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;

import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerRoutes;

public class JMAPServer implements Startable {
    private static final int RANDOM_PORT = 0;
    private static final String JMAP_VERSION_HEADER = "jmapVersion=";

    private final JMAPConfiguration configuration;
    private final Set<JMAPRoutes> jmapRoutes;
    private Optional<DisposableServer> server;

    @Inject
    public JMAPServer(JMAPConfiguration configuration, Set<JMAPRoutes> jmapRoutes) {
        this.configuration = configuration;
        this.jmapRoutes = jmapRoutes;
        this.server = Optional.empty();
    }

    public Port getPort() {
        return server.map(DisposableServer::port)
            .map(Port::of)
            .orElseThrow(() -> new IllegalStateException("port is not available because server is not started or disabled"));
    }

    public void start() {
        ImmutableListMultimap<Endpoint, JMAPRoute> collect = jmapRoutes.stream()
            .flatMap(JMAPRoutes::routes)
            .collect(Guavate.toImmutableListMultimap(JMAPRoute::getEndpoint));

        if (configuration.isEnabled()) {
            server = Optional.of(HttpServer.create()
                .port(configuration.getPort()
                    .map(Port::getValue)
                    .orElse(RANDOM_PORT))
                .route(routes -> jmapRoutes.forEach(jmapRoute -> collect.asMap().forEach(
                    (endpoint, route) -> injectRoutes(routes, endpoint, route))))
                .wiretap(wireTapEnabled())
                .bindNow());
        }
    }

    private boolean wireTapEnabled() {
        return LoggerFactory.getLogger("org.apache.james.jmap.wire").isTraceEnabled();
    }

    private HttpServerRoutes injectRoutes(HttpServerRoutes builder, Endpoint endpoint, Collection<JMAPRoute> routesList) {
        if (routesList.size() == 1) {
            JMAPRoute next = routesList.iterator().next();

            return endpoint.registerRoute(builder, (req, res) ->
                getExistingRoute(extractRequestVersionHeader(req), next).apply(req, res));
        } else if (routesList.size() == 2) {
            ImmutableList<JMAPRoute> sorted = routesList.stream()
                .sorted(Comparator.comparing(JMAPRoute::getVersion))
                .collect(Guavate.toImmutableList());
            JMAPRoute draftRoute = sorted.get(0);
            JMAPRoute rfc8621Route = sorted.get(1);

            return endpoint.registerRoute(builder, (req, res) ->
                chooseVersionRoute(extractRequestVersionHeader(req), draftRoute, rfc8621Route).apply(req, res));
        }
        return builder;
    }

    private JMAPRoute.Action getExistingRoute(String version, JMAPRoute route) {
        try {
            if (Version.of(version).equals(route.getVersion())) {
                return route.getAction();
            }
        } catch (IllegalArgumentException e) {
            return (req, res) -> res.status(BAD_REQUEST).send();
        }
        return (req, res) -> res.status(NOT_FOUND).send();
    }

    private JMAPRoute.Action chooseVersionRoute(String version, JMAPRoute draftRoute, JMAPRoute rfc8621Route) {
        try {
            if (hasRfc8621AcceptHeader(version)) {
                return rfc8621Route.getAction();
            }
        } catch (IllegalArgumentException e) {
            return (req, res) -> res.status(BAD_REQUEST).send();
        }
        return draftRoute.getAction();
    }

    private boolean hasRfc8621AcceptHeader(String version) {
        return Version.of(version).equals(Version.RFC8621);
    }

    private String extractRequestVersionHeader(HttpServerRequest request) {
        return Arrays.stream(request.requestHeaders()
                .get(ACCEPT)
                .split(";"))
            .map(value -> value.trim().toLowerCase())
            .filter(value -> value.startsWith(JMAP_VERSION_HEADER.toLowerCase()))
            .map(value -> value.substring(JMAP_VERSION_HEADER.length()))
            .findFirst()
            .orElse(Version.DRAFT.getVersion());
    }

    @PreDestroy
    public void stop() {
        server.ifPresent(DisposableServer::disposeNow);
    }
}
