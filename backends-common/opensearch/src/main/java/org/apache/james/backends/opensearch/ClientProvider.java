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
package org.apache.james.backends.opensearch;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;

import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.DefaultHostnameVerifier;
import org.apache.hc.client5.http.ssl.HostnameVerificationPolicy;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.pool.PoolConcurrencyPolicy;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.ssl.TrustStrategy;
import org.apache.hc.core5.util.Timeout;
import org.apache.james.util.concurrent.NamedThreadFactory;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

public class ClientProvider implements Provider<ReactorOpenSearchClient> {

    private static class HttpAsyncClientConfigurer {
        // Same defaults as the OpenSearch low level clients. Building our own connection manager would otherwise
        // fall back to HttpClient 5 defaults (5 per route / 25 total), throttling concurrent requests.
        private static final int DEFAULT_MAX_CONN_PER_ROUTE = ApacheHttpClient5TransportBuilder.DEFAULT_MAX_CONN_PER_ROUTE;
        private static final int DEFAULT_MAX_CONN_TOTAL = ApacheHttpClient5TransportBuilder.DEFAULT_MAX_CONN_TOTAL;
        private static final AuthScope ANY = new AuthScope(null, -1);
        private static final TrustStrategy TRUST_ALL = (x509Certificates, authType) -> true;
        private static final HostnameVerifier ACCEPT_ANY_HOSTNAME = (hostname, sslSession) -> true;

        private final OpenSearchConfiguration configuration;

        private HttpAsyncClientConfigurer(OpenSearchConfiguration configuration) {
            this.configuration = configuration;
        }

        private HttpAsyncClientBuilder configure(HttpAsyncClientBuilder builder) {
            configureAuthentication(builder);

            builder.setConnectionManager(connectionManager())
                .setDefaultRequestConfig(requestConfig())
                .setThreadFactory(NamedThreadFactory.withName("OpenSearch-driver"));

            return builder;
        }

        private PoolingAsyncClientConnectionManager connectionManager() {
            PoolingAsyncClientConnectionManagerBuilder builder = PoolingAsyncClientConnectionManagerBuilder.create()
                .setMaxConnTotal(configuration.getMaxConnections().orElse(DEFAULT_MAX_CONN_TOTAL))
                .setMaxConnPerRoute(configuration.getMaxConnectionsPerHost().orElse(DEFAULT_MAX_CONN_PER_ROUTE))
                // STRICT enforces both limits. LAX would silently ignore the total limit (opensearch.max.connections)
                .setPoolConcurrencyPolicy(PoolConcurrencyPolicy.STRICT)
                .setDefaultConnectionConfig(connectionConfig())
                // OpenSearch REST API is consumed over HTTP/1.1, skip HTTP/2 negotiation over TLS
                .setDefaultTlsConfig(TlsConfig.custom()
                    .setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_1)
                    .build());

            configureHostScheme(builder);

            return builder.build();
        }

        private void configureHostScheme(PoolingAsyncClientConnectionManagerBuilder builder) {
            OpenSearchConfiguration.HostScheme scheme = configuration.getHostScheme();

            switch (scheme) {
                case HTTP:
                    return;
                case HTTPS:
                    builder.setTlsStrategy(tlsStrategy());
                    return;
                default:
                    throw new NotImplementedException(
                        String.format("unrecognized hostScheme '%s'", scheme.name()));
            }
        }

        private TlsStrategy tlsStrategy() {
            try {
                return ClientTlsStrategyBuilder.create()
                    .setSslContext(sslContext())
                    .setHostnameVerifier(hostnameVerifier())
                    // Only rely on our HostnameVerifier: the default (BOTH) also enables JSSE built-in endpoint
                    // identification, which would reject hosts accepted by ACCEPT_ANY_HOSTNAME
                    .setHostVerificationPolicy(HostnameVerificationPolicy.CLIENT)
                    .build();
            } catch (NoSuchAlgorithmException | KeyManagementException | KeyStoreException | CertificateException | IOException e) {
                throw new RuntimeException("Cannot set SSL options to the builder", e);
            }
        }

        private SSLContext sslContext() throws KeyStoreException, NoSuchAlgorithmException, KeyManagementException,
            CertificateException, IOException {

            SSLContextBuilder sslContextBuilder = new SSLContextBuilder();

            OpenSearchConfiguration.SSLConfiguration.SSLValidationStrategy strategy = configuration.getSslConfiguration()
                .getStrategy();

            switch (strategy) {
                case DEFAULT:
                    return sslContextBuilder.build();
                case IGNORE:
                    return sslContextBuilder.loadTrustMaterial(TRUST_ALL)
                        .build();
                case OVERRIDE:
                    return applyTrustStore(sslContextBuilder)
                        .build();
                default:
                    throw new NotImplementedException(
                        String.format("unrecognized strategy '%s'", strategy.name()));
            }
        }

        private HostnameVerifier hostnameVerifier() {
            OpenSearchConfiguration.SSLConfiguration.HostNameVerifier hostnameVerifier = configuration.getSslConfiguration()
                .getHostNameVerifier();

            switch (hostnameVerifier) {
                case DEFAULT:
                    return new DefaultHostnameVerifier();
                case ACCEPT_ANY_HOSTNAME:
                    return ACCEPT_ANY_HOSTNAME;
                default:
                    throw new NotImplementedException(
                        String.format("unrecognized HostNameVerifier '%s'", hostnameVerifier.name()));
            }
        }

        private ConnectionConfig connectionConfig() {
            Timeout timeout = requestTimeout();
            return ConnectionConfig.custom()
                .setConnectTimeout(timeout)
                .setSocketTimeout(timeout)
                .build();
        }

        private RequestConfig requestConfig() {
            Timeout timeout = requestTimeout();
            return RequestConfig.custom()
                .setConnectionRequestTimeout(timeout)
                .setResponseTimeout(timeout)
                .build();
        }

        private Timeout requestTimeout() {
            return Timeout.of(configuration.getRequestTimeout());
        }

        private SSLContextBuilder applyTrustStore(SSLContextBuilder sslContextBuilder) throws CertificateException, NoSuchAlgorithmException,
            KeyStoreException, IOException {

            OpenSearchConfiguration.SSLConfiguration.SSLTrustStore trustStore = configuration.getSslConfiguration()
                .getTrustStore()
                .orElseThrow(() -> new IllegalStateException("SSLTrustStore cannot to be empty"));

            return sslContextBuilder
                .loadTrustMaterial(trustStore.getFile(), trustStore.getPassword());
        }

        private void configureAuthentication(HttpAsyncClientBuilder builder) {
            configuration.getCredential()
                .ifPresent(credential -> {
                    BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                    credentialsProvider.setCredentials(ANY,
                        new UsernamePasswordCredentials(credential.getUsername(), credential.getPassword()));
                    builder.setDefaultCredentialsProvider(credentialsProvider);
                });
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientProvider.class);

    private final OpenSearchConfiguration configuration;
    private final OpenSearchTransport transport;
    private final OpenSearchAsyncClient openSearchClient;
    private final HttpAsyncClientConfigurer httpAsyncClientConfigurer;
    private final ReactorOpenSearchClient client;

    @Inject
    public ClientProvider(OpenSearchConfiguration configuration) {
        this.httpAsyncClientConfigurer = new HttpAsyncClientConfigurer(configuration);
        this.configuration = configuration;
        this.transport = buildTransport();
        this.openSearchClient = connect();
        this.client = new ReactorOpenSearchClient(this.openSearchClient);
    }

    private OpenSearchTransport buildTransport() {
        return ApacheHttpClient5TransportBuilder.builder(hostsToHttpHosts())
            .setHttpClientConfigCallback(httpAsyncClientConfigurer::configure)
            .build();
    }

    private OpenSearchAsyncClient connect() {
        Duration waitDelay = Duration.ofMillis(configuration.getMinDelay());
        boolean suppressLeadingZeroElements = true;
        boolean suppressTrailingZeroElements = true;
        return Mono.fromCallable(this::connectToCluster)
            .doOnError(e -> LOGGER.warn("Error establishing OpenSearch connection. Next retry scheduled in {}",
                DurationFormatUtils.formatDurationWords(waitDelay.toMillis(), suppressLeadingZeroElements, suppressTrailingZeroElements), e))
            .retryWhen(Retry.backoff(configuration.getMaxRetries(), waitDelay).scheduler(Schedulers.boundedElastic()))
            .publishOn(Schedulers.boundedElastic())
            .block();
    }

    private OpenSearchAsyncClient connectToCluster() {
        LOGGER.info("Trying to connect to OpenSearch service at {}", LocalDateTime.now());

        return new OpenSearchAsyncClient(transport);
    }

    private HttpHost[] hostsToHttpHosts() {
        String scheme = configuration.getHostScheme().name().toLowerCase(Locale.US);
        return configuration.getHosts().stream()
            .map(host -> new HttpHost(scheme, host.getHostName(), host.getPort()))
            .toArray(HttpHost[]::new);
    }

    @Override
    public ReactorOpenSearchClient get() {
        return client;
    }

    @PreDestroy
    public void close() throws IOException {
        transport.close();
    }
}
