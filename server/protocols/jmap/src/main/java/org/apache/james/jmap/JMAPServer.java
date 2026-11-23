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
import static reactor.netty.Metrics.URI;

import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.filesystem.api.FileSystem;
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
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import nl.altindag.ssl.pem.util.PemUtils;
import nl.altindag.ssl.util.KeyManagerUtils;
import nl.altindag.ssl.util.KeyStoreUtils;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.netty.http.server.HttpServerRequest;

public class JMAPServer implements Startable {
    public static final boolean REACTOR_NETTY_METRICS_ENABLE = Boolean.parseBoolean(System.getProperty("james.jmap.reactor.netty.metrics.enabled", "false"));
    private static final int REACTOR_NETTY_METRICS_MAX_URI_TAGS = 100;
    private static final int RANDOM_PORT = 0;

    private final JMAPConfiguration configuration;
    private final VersionParser versionParser;
    private final FileSystem fileSystem;
    private final Multimap<Version, JMAPRoute> routes;
    private final List<JMAPRoute> corsRoutes;
    private Optional<DisposableServer> server;

    @Inject
    public JMAPServer(JMAPConfiguration configuration, Set<JMAPRoutesHandler> jmapRoutesHandlers, VersionParser versionParser, FileSystem fileSystem) {
        this.configuration = configuration;
        this.versionParser = versionParser;
        this.fileSystem = fileSystem;
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
    }

    @VisibleForTesting
    public JMAPServer(JMAPConfiguration configuration, Set<JMAPRoutesHandler> jmapRoutesHandlers, VersionParser versionParser) {
        this(configuration, jmapRoutesHandlers, versionParser, null);
    }

    public Port getPort() {
        return server.map(DisposableServer::port)
            .map(Port::of)
            .orElseThrow(() -> new IllegalStateException("port is not available because server is not started or disabled"));
    }

    public void start() {
        if (configuration.isEnabled()) {
            HttpServer httpServer = HttpServer.create()
                .port(configuration.getPort()
                    .map(Port::getValue)
                    .orElse(RANDOM_PORT))
                .handle((request, response) -> handleVersionRoute(request).handleRequest(request, response))
                .wiretap(wireTapEnabled())
                .metrics(REACTOR_NETTY_METRICS_ENABLE, Function.identity());

            if (configuration.isTlsEnabled()) {
                httpServer = httpServer.secure(sslContextSpec -> sslContextSpec.sslContext(buildSslContext()));
            }

            server = Optional.of(httpServer.bindNow());

            if (REACTOR_NETTY_METRICS_ENABLE) {
                configureReactorNettyMetrics();
            }
        }
    }

    private SslContext buildSslContext() {
        try {
            if (configuration.getKeystore().isPresent()) {
                char[] password = configuration.getSecret()
                    .map(String::toCharArray)
                    .orElse(new char[0]);
                KeyStore keyStore = KeyStoreUtils.loadKeyStore(
                    fileSystem.getResource(configuration.getKeystore().get()),
                    password,
                    configuration.getKeystoreType());
                return SslContextBuilder.forServer(
                        KeyManagerUtils.createKeyManager(keyStore, password))
                    .protocols("TLSv1.2", "TLSv1.3")
                    .build();
            }

            List<X509Certificate> certificates = PemUtils.loadCertificate(
                fileSystem.getResource(configuration.getCertificates().get()));
            PrivateKey privateKey = PemUtils.loadPrivateKey(
                fileSystem.getResource(configuration.getPrivateKey().get()),
                configuration.getSecret()
                    .map(String::toCharArray)
                    .orElse(null));
            return SslContextBuilder.forServer(privateKey, certificates.toArray(X509Certificate[]::new))
                .protocols("TLSv1.2", "TLSv1.3")
                .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize SSL context for JMAP server", e);
        }
    }

    private void configureReactorNettyMetrics() {
        Metrics.globalRegistry
            .config()
            .meterFilter(MeterFilter.maximumAllowableTags(HTTP_CLIENT_PREFIX, URI, REACTOR_NETTY_METRICS_MAX_URI_TAGS, MeterFilter.deny()));
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
