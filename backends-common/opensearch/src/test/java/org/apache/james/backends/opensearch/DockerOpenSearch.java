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

import static java.net.HttpURLConnection.HTTP_OK;
import static org.apache.james.backends.opensearch.DockerOpenSearch.Fixture.OS_HTTP_PORT;
import static org.apache.james.backends.opensearch.DockerOpenSearch.Fixture.OS_MEMORY;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.http.HttpStatus;
import org.apache.james.backends.opensearch.OpenSearchConfiguration.Credential;
import org.apache.james.backends.opensearch.OpenSearchConfiguration.HostScheme;
import org.apache.james.util.Host;
import org.apache.james.util.docker.DockerContainer;
import org.apache.james.util.docker.Images;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.containers.wait.strategy.WaitStrategy;
import org.testcontainers.images.builder.ImageFromDockerfile;

import com.google.common.collect.ImmutableMap;

import feign.Client;
import feign.Feign;
import feign.Logger;
import feign.RequestLine;
import feign.Response;
import feign.auth.BasicAuthRequestInterceptor;
import feign.slf4j.Slf4jLogger;

public interface DockerOpenSearch {

    interface OpenSearchAPI {

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
                    .logger(new Slf4jLogger(OpenSearchAPI.class))
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

            public OpenSearchAPI build() {
                return requestBuilder.target(OpenSearchAPI.class, esURL.toString());
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
        int OS_HTTP_PORT = 9200;
        int OS_MEMORY = 1024;
    }

    class NoAuth implements DockerOpenSearch {
        private static WaitStrategy openSearchWaitStrategy() {
            return new HttpWaitStrategy()
                .forPort(OS_HTTP_PORT)
                .forStatusCodeMatching(response -> response == HTTP_OK)
                .withReadTimeout(Duration.ofSeconds(10))
                .withStartupTimeout(Duration.ofMinutes(3));
        }

        static DockerContainer defaultContainer(String imageName) {
            return DockerContainer.fromName(imageName)
                .withTmpFs(ImmutableMap.of("/usr/share/opensearch/data/nodes/0", "rw,size=200m"))
                .withExposedPorts(OS_HTTP_PORT)
                .withEnv("discovery.type", "single-node")
                .withEnv("DISABLE_INSTALL_DEMO_CONFIG", "true")
                .withEnv("DISABLE_SECURITY_PLUGIN", "true")
                .withEnv("OPENSEARCH_JAVA_OPTS", "-Xms" + OS_MEMORY + "m -Xmx" + OS_MEMORY + "m")
                .withAffinityToContainer()
                .withName("james-testing-opensearch-" + UUID.randomUUID())
                .waitingFor(openSearchWaitStrategy());
        }

        private final DockerContainer eSContainer;

        public NoAuth() {
            this(Images.OPENSEARCH);
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
            return eSContainer.getMappedPort(OS_HTTP_PORT);
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

    class WithAuth implements DockerOpenSearch {

        private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(WithAuth.class);

        private static final String DEFAULT_USERNAME = "elasticsearch";
        private static final String DEFAULT_PASSWORD = "secret";
        public static final Credential DEFAULT_CREDENTIAL =
            Credential.of(DEFAULT_USERNAME, DEFAULT_PASSWORD);

        private final DockerOpenSearch.NoAuth openSearch;
        private final DockerContainer nginx;
        private final Network network;

        public WithAuth() {
            this(Images.OPENSEARCH);
        }

        WithAuth(String imageName) {
            this.network = Network.newNetwork();
            this.openSearch = new DockerOpenSearch.NoAuth(
                DockerOpenSearch.NoAuth
                    .defaultContainer(imageName)
                    .withLogConsumer(frame -> LOGGER.debug("[OpenSearch] " + frame.getUtf8String()))
                    .withNetwork(network)
                    .withNetworkAliases("elasticsearch"));

            this.nginx = new DockerContainer(
                    new GenericContainer<>(
                        new ImageFromDockerfile()
                        .withFileFromClasspath("conf/nginx-conf/", "auth-es/nginx-conf/")
                        .withFileFromClasspath("conf/default.crt", "auth-es/default.crt")
                        .withFileFromClasspath("conf/default.key", "auth-es/default.key")
                        .withFileFromClasspath("Dockerfile", "auth-es/NginxDockerfile")))
                .withExposedPorts(OS_HTTP_PORT)
                .withTmpFs(ImmutableMap.of("/usr/share/opensearch/data/nodes/0", "rw,size=200m"))
                .withLogConsumer(frame -> LOGGER.debug("[NGINX] " + frame.getUtf8String()))
                .withNetwork(network)
                .withName("james-testing-nginx-" + UUID.randomUUID());
        }


        public void start() {
            openSearch.start();
            nginx.start();
        }

        public void stop() {
            nginx.stop();
            openSearch.stop();
        }

        public int getHttpPort() {
            return nginx.getMappedPort(OS_HTTP_PORT);
        }

        public String getIp() {
            return nginx.getHostIp();
        }

        @Override
        public OpenSearchAPI esAPI() {
            try {
                return OpenSearchAPI.builder(getUrl())
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
                return new URI("https://" + getIp() + ":" + getHttpPort()).toURL();
            } catch (MalformedURLException | URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public OpenSearchConfiguration configuration(Optional<Duration> requestTimeout) {
            return configurationBuilder(requestTimeout)
                .hostScheme(Optional.of(HostScheme.HTTPS))
                .build();
        }

        public void pause() {
            nginx.pause();
            openSearch.pause();
        }

        public void unpause() {
            openSearch.unpause();
            nginx.unpause();
        }

        @Override
        public boolean isRunning() {
            return nginx.isRunning() && openSearch.isRunning();
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
            return new URI("http://" + getIp() + ":" + getHttpPort()).toURL();
        } catch (MalformedURLException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    default Host getHttpHost() {
        return Host.from(getIp(), getHttpPort());
    }

    default void cleanUpData() {
        if (esAPI().deleteAllIndexes().status() != HttpStatus.SC_OK) {
            throw new IllegalStateException("Failed to delete all data from OpenSearch");
        }
    }

    default void flushIndices() {
        if (esAPI().flush().status() != HttpStatus.SC_OK) {
            throw new IllegalStateException("Failed to flush OpenSearch");
        }
    }

    default OpenSearchConfiguration configuration(Optional<Duration> requestTimeout) {
        return configurationBuilder(requestTimeout)
            .build();
    }

    default OpenSearchConfiguration.Builder configurationBuilder(Optional<Duration> requestTimeout) {
        return OpenSearchConfiguration.builder()
            .addHost(getHttpHost())
            .requestTimeout(requestTimeout)
            .nbReplica(0)
            .nbShards(1);
    }

    default OpenSearchConfiguration configuration() {
        return configuration(Optional.empty());
    }

    default ClientProvider clientProvider() {
        return new ClientProvider(configuration(Optional.empty()));
    }

    default ClientProvider clientProvider(Duration requestTimeout) {
        return new ClientProvider(configuration(Optional.of(requestTimeout)));
    }

    default OpenSearchAPI esAPI() {
        return OpenSearchAPI.builder(getUrl())
            .build();
    }
}
