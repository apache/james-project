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
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;

import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.CredentialsStore;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.DefaultHostnameVerifier;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.ssl.TrustStrategy;
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
        private static final AuthScope ANY = new AuthScope(null, null, -1, null, null);
        private static final TrustStrategy TRUST_ALL = (x509Certificates, authType) -> true;
        private static final HostnameVerifier ACCEPT_ANY_HOSTNAME = (hostname, sslSession) -> true;

        private final OpenSearchConfiguration configuration;

        private HttpAsyncClientConfigurer(OpenSearchConfiguration configuration) {
            this.configuration = configuration;
        }

        private HttpAsyncClientBuilder configure(HttpAsyncClientBuilder builder) {
            configureAuthentication(builder);
            configureHostScheme(builder);
            configureTimeout(builder);

            builder.setThreadFactory(NamedThreadFactory.withName("OpenSearch-driver"));

            return builder;
        }

        private void configureHostScheme(HttpAsyncClientBuilder builder) {
            OpenSearchConfiguration.HostScheme scheme = configuration.getHostScheme();

            switch (scheme) {
                case HTTP:
                    return;
                case HTTPS:
                    configureSSLOptions(builder);
                    return;
                default:
                    throw new NotImplementedException(
                        String.format("unrecognized hostScheme '%s'", scheme.name()));
            }
        }

        private void configureSSLOptions(HttpAsyncClientBuilder builder) {
            builder.setConnectionManager(connectionManager());
        }

        private void configureTimeout(HttpAsyncClientBuilder builder) {
            builder.setDefaultRequestConfig(requestConfig());
        }

        private PoolingAsyncClientConnectionManager connectionManager() {
            PoolingAsyncClientConnectionManagerBuilder builder = PoolingAsyncClientConnectionManagerBuilder
                .create()
                .setTlsStrategy(tlsStrategy());

            configuration.getMaxConnections().ifPresent(builder::setMaxConnTotal);
            configuration.getMaxConnectionsPerHost().ifPresent(builder::setMaxConnPerRoute);

            return builder.build();
        }

        private TlsStrategy tlsStrategy() {
            try {
                return ClientTlsStrategyBuilder.create()
                    .setSslContext(sslContext())
                    .setHostnameVerifier(hostnameVerifier())
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

        private RequestConfig requestConfig() {
            return RequestConfig.custom()
                    .setConnectTimeout(Math.toIntExact(configuration.getRequestTimeout().toMillis()), TimeUnit.MILLISECONDS)
                    .setConnectionRequestTimeout(Math.toIntExact(configuration.getRequestTimeout().toMillis()), TimeUnit.MILLISECONDS)
                    .setResponseTimeout(Math.toIntExact(configuration.getRequestTimeout().toMillis()), TimeUnit.MILLISECONDS)
                    .build();
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
                    CredentialsStore credentialsProvider = new BasicCredentialsProvider();
                    credentialsProvider.setCredentials(ANY,
                        new UsernamePasswordCredentials(credential.getUsername(), credential.getPassword()));
                    builder.setDefaultCredentialsProvider(credentialsProvider);
                });
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientProvider.class);

    private final OpenSearchConfiguration configuration;
    private final OpenSearchTransport openSearchTransport;
    private final OpenSearchAsyncClient openSearchClient;
    private final HttpAsyncClientConfigurer httpAsyncClientConfigurer;
    private final ReactorOpenSearchClient client;

    @Inject
    public ClientProvider(OpenSearchConfiguration configuration) {
        this.httpAsyncClientConfigurer = new HttpAsyncClientConfigurer(configuration);
        this.configuration = configuration;
        this.openSearchTransport = buildTransport();
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

        return new OpenSearchAsyncClient(openSearchTransport);
    }

    private HttpHost[] hostsToHttpHosts() {
        return configuration.getHosts().stream()
            .map(host -> new HttpHost(configuration.getHostScheme().name(), host.getHostName(), host.getPort()))
            .toArray(HttpHost[]::new);
    }

    @Override
    public ReactorOpenSearchClient get() {
        return client;
    }

    @PreDestroy
    public void close() throws IOException {
        openSearchTransport.close();
    }
}
