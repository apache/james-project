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

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.TrustStrategy;
import org.apache.james.backends.opensearch.json.jackson.JacksonJsonpMapper;
import org.apache.james.util.concurrent.NamedThreadFactory;
import org.opensearch.client.RestClient;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.transport.rest_client.RestClientTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

public class ClientProvider implements Provider<ReactorOpenSearchClient> {

    private static class HttpAsyncClientConfigurer {

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

            configuration.getMaxConnections().ifPresent(builder::setMaxConnTotal);
            configuration.getMaxConnectionsPerHost().ifPresent(builder::setMaxConnPerRoute);

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
            try {
                builder
                    .setSSLContext(sslContext())
                    .setSSLHostnameVerifier(hostnameVerifier());
            } catch (NoSuchAlgorithmException | KeyManagementException | KeyStoreException | CertificateException | IOException e) {
                throw new RuntimeException("Cannot set SSL options to the builder", e);
            }
        }

        private void configureTimeout(HttpAsyncClientBuilder builder) {
            builder.setDefaultRequestConfig(requestConfig());
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
                    .setConnectTimeout(Math.toIntExact(configuration.getRequestTimeout().toMillis()))
                    .setConnectionRequestTimeout(Math.toIntExact(configuration.getRequestTimeout().toMillis()))
                    .setSocketTimeout(Math.toIntExact(configuration.getRequestTimeout().toMillis()))
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
                    CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                    credentialsProvider.setCredentials(AuthScope.ANY,
                        new UsernamePasswordCredentials(credential.getUsername(), String.valueOf(credential.getPassword())));
                    builder.setDefaultCredentialsProvider(credentialsProvider);
                });
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientProvider.class);

    private final OpenSearchConfiguration configuration;
    private final RestClient lowLevelRestClient;
    private final OpenSearchAsyncClient openSearchClient;
    private final HttpAsyncClientConfigurer httpAsyncClientConfigurer;
    private final ReactorOpenSearchClient client;

    @Inject
    public ClientProvider(OpenSearchConfiguration configuration) {
        this.httpAsyncClientConfigurer = new HttpAsyncClientConfigurer(configuration);
        this.configuration = configuration;
        this.lowLevelRestClient = buildRestClient();
        this.openSearchClient = connect();
        this.client = new ReactorOpenSearchClient(this.openSearchClient, lowLevelRestClient);
    }

    private RestClient buildRestClient() {
        return RestClient.builder(hostsToHttpHosts())
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

        RestClientTransport transport = new RestClientTransport(lowLevelRestClient, new JacksonJsonpMapper());

        return new OpenSearchAsyncClient(transport);
    }

    private HttpHost[] hostsToHttpHosts() {
        return configuration.getHosts().stream()
            .map(host -> new HttpHost(host.getHostName(), host.getPort(), configuration.getHostScheme().name()))
            .toArray(HttpHost[]::new);
    }

    @Override
    public ReactorOpenSearchClient get() {
        return client;
    }

    @PreDestroy
    public void close() throws IOException {
        lowLevelRestClient.close();
    }
}
