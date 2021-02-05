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

package org.apache.james.backends.es.v7;

import static org.apache.james.backends.es.v7.DockerElasticSearch.Fixture.ES_HTTP_PORT;

import java.net.MalformedURLException;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Optional;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.http.HttpStatus;
import org.apache.james.backends.es.v7.ElasticSearchConfiguration.Credential;
import org.apache.james.backends.es.v7.ElasticSearchConfiguration.HostScheme;
import org.apache.james.util.Host;
import org.apache.james.util.docker.DockerContainer;
import org.apache.james.util.docker.Images;
import org.apache.james.util.docker.RateLimiters;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;
import org.testcontainers.images.builder.ImageFromDockerfile;

import com.google.common.collect.ImmutableMap;

import feign.Client;
import feign.Feign;
import feign.Logger;
import feign.RequestLine;
import feign.Response;
import feign.auth.BasicAuthRequestInterceptor;
import feign.slf4j.Slf4jLogger;

public interface DockerElasticSearch {

    interface ElasticSearchAPI {

        class Builder {
            private static final HostnameVerifier ACCEPT_ANY_HOST = (hostname, sslSession) -> true;
            private static final TrustManager[] TRUST_ALL = new TrustManager[] {
                new X509TrustManager() {

                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }

                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    }

                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    }
                }
            };

            private final Feign.Builder requestBuilder;
            private final URL esURL;

            public Builder(URL esURL) {
                this.esURL = esURL;
                this.requestBuilder = Feign.builder()
                    .logger(new Slf4jLogger(ElasticSearchAPI.class))
                    .logLevel(Logger.Level.FULL);
            }

            public Builder credential(Credential credential) {
                requestBuilder.requestInterceptor(
                    new BasicAuthRequestInterceptor(credential.getUsername(), String.valueOf(credential.getPassword())));
                return this;
            }

            public Builder disableSSLValidation() throws Exception {
                SSLContext sc = SSLContext.getInstance("SSL");
                sc.init(null, TRUST_ALL, new java.security.SecureRandom());
                SSLSocketFactory factory = sc.getSocketFactory();
                HttpsURLConnection.setDefaultSSLSocketFactory(factory);
                Client ignoredSSLClient = new Client.Default(factory, ACCEPT_ANY_HOST);

                requestBuilder.client(ignoredSSLClient);

                return this;
            }

            public ElasticSearchAPI build() {
                return requestBuilder.target(ElasticSearchAPI.class, esURL.toString());
            }
        }

        static Builder builder(URL esURL) {
            return new Builder(esURL);
        }

        @RequestLine("DELETE /_all")
        Response deleteAllIndexes();

        @RequestLine("POST /_flush?force&wait_if_ongoing=true")
        Response flush();
    }

    interface Fixture {
        int ES_HTTP_PORT = 9200;
    }

    class NoAuth implements DockerElasticSearch {

        static DockerContainer defaultContainer(String imageName) {
            return DockerContainer.fromName(imageName)
                .withTmpFs(ImmutableMap.of("/usr/share/elasticsearch/data", "rw,size=200m"))
                .withExposedPorts(ES_HTTP_PORT)
                .withEnv("discovery.type", "single-node")
                .withAffinityToContainer()
                .waitingFor(new HostPortWaitStrategy().withRateLimiter(RateLimiters.TWENTIES_PER_SECOND));
        }

        private final DockerContainer eSContainer;

        public NoAuth() {
            this(Images.ELASTICSEARCH_6);
        }

        public NoAuth(String imageName) {
            this.eSContainer = defaultContainer(imageName);
        }

        public NoAuth(DockerContainer eSContainer) {
            this.eSContainer = eSContainer;
        }

        public void start() {
            if (!isRunning()) {
                eSContainer.start();
            }
        }

        public void stop() {
            eSContainer.stop();
        }

        public int getHttpPort() {
            return eSContainer.getMappedPort(ES_HTTP_PORT);
        }

        public String getIp() {
            return eSContainer.getHostIp();
        }

        public Host getHttpHost() {
            return Host.from(getIp(), getHttpPort());
        }

        public void pause() {
            eSContainer.pause();
        }

        public void unpause() {
            eSContainer.unpause();
        }

        @Override
        public boolean isRunning() {
            return eSContainer.isRunning();
        }
    }

    class WithAuth implements DockerElasticSearch {

        private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(WithAuth.class);

        private static final String DEFAULT_USERNAME = "elasticsearch";
        private static final String DEFAULT_PASSWORD = "secret";
        public static final Credential DEFAULT_CREDENTIAL =
            Credential.of(DEFAULT_USERNAME, DEFAULT_PASSWORD);

        private final DockerElasticSearch.NoAuth elasticSearch;
        private final DockerContainer nginx;
        private final Network network;

        public WithAuth() {
            this(Images.ELASTICSEARCH_6);
        }

        WithAuth(String imageName) {
            this.network = Network.newNetwork();
            this.elasticSearch = new DockerElasticSearch.NoAuth(
                DockerElasticSearch.NoAuth
                    .defaultContainer(imageName)
                    .withLogConsumer(frame -> LOGGER.debug("[ElasticSearch] " + frame.getUtf8String()))
                    .withNetwork(network)
                    .withNetworkAliases("elasticsearch"));

            this.nginx = new DockerContainer(
                    new GenericContainer<>(
                        new ImageFromDockerfile()
                        .withFileFromClasspath("conf/nginx-conf/", "auth-es/nginx-conf/")
                        .withFileFromClasspath("conf/default.crt", "auth-es/default.crt")
                        .withFileFromClasspath("conf/default.key", "auth-es/default.key")
                        .withFileFromClasspath("Dockerfile", "auth-es/NginxDockerfile")))
                .withExposedPorts(ES_HTTP_PORT)
                .withLogConsumer(frame -> LOGGER.debug("[NGINX] " + frame.getUtf8String()))
                .withNetwork(network);
        }


        public void start() {
            elasticSearch.start();
            nginx.start();
        }

        public void stop() {
            nginx.stop();
            elasticSearch.stop();
        }

        public int getHttpPort() {
            return nginx.getMappedPort(ES_HTTP_PORT);
        }

        public String getIp() {
            return nginx.getHostIp();
        }

        @Override
        public ElasticSearchAPI esAPI() {
            try {
                return ElasticSearchAPI.builder(getUrl())
                    .credential(DEFAULT_CREDENTIAL)
                    .disableSSLValidation()
                    .build();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public URL getUrl() {
            try {
                return new URL("https://" + getIp() + ":" + getHttpPort());
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public ElasticSearchConfiguration configuration(Optional<Duration> requestTimeout) {
            return configurationBuilder(requestTimeout)
                .hostScheme(Optional.of(HostScheme.HTTPS))
                .build();
        }

        public void pause() {
            nginx.pause();
            elasticSearch.pause();
        }

        public void unpause() {
            elasticSearch.unpause();
            nginx.unpause();
        }

        @Override
        public boolean isRunning() {
            return nginx.isRunning() && elasticSearch.isRunning();
        }
    }

    void start();

    void stop();

    int getHttpPort();

    String getIp();

    void pause();

    void unpause();

    boolean isRunning();

    default URL getUrl() {
        try {
            return new URL("http://" + getIp() + ":" + getHttpPort());
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    default Host getHttpHost() {
        return Host.from(getIp(), getHttpPort());
    }

    default void cleanUpData() {
        if (esAPI().deleteAllIndexes().status() != HttpStatus.SC_OK) {
            throw new IllegalStateException("Failed to delete all data from ElasticSearch");
        }
    }

    default void flushIndices() {
        if (esAPI().flush().status() != HttpStatus.SC_OK) {
            throw new IllegalStateException("Failed to flush ElasticSearch");
        }
    }

    default ElasticSearchConfiguration configuration(Optional<Duration> requestTimeout) {
        return configurationBuilder(requestTimeout)
            .build();
    }

    default ElasticSearchConfiguration.Builder configurationBuilder(Optional<Duration> requestTimeout) {
        return ElasticSearchConfiguration.builder()
            .addHost(getHttpHost())
            .requestTimeout(requestTimeout);
    }

    default ElasticSearchConfiguration configuration() {
        return configuration(Optional.empty());
    }

    default ClientProvider clientProvider() {
        return new ClientProvider(configuration(Optional.empty()));
    }

    default ClientProvider clientProvider(Duration requestTimeout) {
        return new ClientProvider(configuration(Optional.of(requestTimeout)));
    }

    default ElasticSearchAPI esAPI() {
        return ElasticSearchAPI.builder(getUrl())
            .build();
    }
}
