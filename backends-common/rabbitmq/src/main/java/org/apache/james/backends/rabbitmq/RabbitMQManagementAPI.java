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

package org.apache.james.backends.rabbitmq;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.TrustStrategy;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.base.MoreObjects;

import feign.Client;
import feign.Feign;
import feign.Logger;
import feign.Param;
import feign.RequestLine;
import feign.RetryableException;
import feign.Retryer;
import feign.auth.BasicAuthRequestInterceptor;
import feign.codec.ErrorDecoder;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.slf4j.Slf4jLogger;

public interface RabbitMQManagementAPI {

    class MessageQueue {
        @JsonProperty("name")
        String name;

        @JsonProperty("vhost")
        String vhost;

        @JsonProperty("auto_delete")
        boolean autoDelete;

        @JsonProperty("durable")
        boolean durable;

        @JsonProperty("exclusive")
        boolean exclusive;

        @JsonProperty("arguments")
        Map<String, String> arguments;

        public String getName() {
            return name;
        }

        public String getVhost() {
            return vhost;
        }

        public boolean isAutoDelete() {
            return autoDelete;
        }

        public boolean isDurable() {
            return durable;
        }

        public boolean isExclusive() {
            return exclusive;
        }

        public Map<String, String> getArguments() {
            return arguments;
        }
    }

    class MessageQueueDetails {
        @JsonProperty("name")
        String name;

        @JsonProperty("vhost")
        String vhost;

        @JsonProperty("auto_delete")
        boolean autoDelete;

        @JsonProperty("durable")
        boolean durable;

        @JsonProperty("exclusive")
        boolean exclusive;

        @JsonProperty("arguments")
        Map<String, String> arguments;

        @JsonProperty("consumer_details")
        List<ConsumerDetails> consumerDetails;

        public String getName() {
            return name;
        }

        public String getVhost() {
            return vhost;
        }

        public boolean isAutoDelete() {
            return autoDelete;
        }

        public boolean isDurable() {
            return durable;
        }

        public boolean isExclusive() {
            return exclusive;
        }

        public Map<String, String> getArguments() {
            return arguments;
        }

        public List<ConsumerDetails> getConsumerDetails() {
            return consumerDetails;
        }
    }

    class ConsumerDetails {
        @JsonProperty("consumer_tag")
        String tag;

        @JsonProperty("activity_status")
        ActivityStatus status;

        public ActivityStatus getStatus() {
            return this.status;
        }

        public String getTag() {
            return this.tag;
        }
    }

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    enum ActivityStatus {
        Waiting("waiting"),
        SingleActive("single_active");

        private final String value;

        ActivityStatus(String value) {
            this.value = value;
        }

        @JsonValue
        String getValue() {
            return value;
        }
    }

    class Exchange {

        @JsonProperty("name")
        String name;

        @JsonProperty("type")
        String type;

        @JsonProperty("auto_delete")
        boolean autoDelete;

        @JsonProperty("durable")
        boolean durable;

        @JsonProperty("internal")
        boolean internal;

        @JsonProperty("arguments")
        Map<String, String> arguments;

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }

        public boolean isAutoDelete() {
            return autoDelete;
        }

        public boolean isDurable() {
            return durable;
        }

        public boolean isInternal() {
            return internal;
        }

        public Map<String, String> getArguments() {
            return arguments;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("name", name)
                .add("type", type)
                .add("autoDelete", autoDelete)
                .add("durable", durable)
                .add("internal", internal)
                .add("arguments", arguments)
                .toString();
        }
    }

    class BindingSource {
        private final String source;
        private final String vhost;
        private final String destination;
        private final String destinationType;
        private final String routingKey;
        private final Map<String, String> arguments;
        private final String propertiesKey;

        public BindingSource(@JsonProperty("source") String source,
                             @JsonProperty("vhost") String vhost,
                             @JsonProperty("destination") String destination,
                             @JsonProperty("destination_type") String destinationType,
                             @JsonProperty("routing_key") String routingKey,
                             @JsonProperty("arguments") Map<String, String> arguments,
                             @JsonProperty("properties_key") String propertiesKey) {
            this.source = source;
            this.vhost = vhost;
            this.destination = destination;
            this.destinationType = destinationType;
            this.routingKey = routingKey;
            this.arguments = arguments;
            this.propertiesKey = propertiesKey;
        }

        public String getSource() {
            return source;
        }

        public String getVhost() {
            return vhost;
        }

        public String getDestination() {
            return destination;
        }

        public String getDestinationType() {
            return destinationType;
        }

        public String getRoutingKey() {
            return routingKey;
        }

        public Map<String, String> getArguments() {
            return arguments;
        }

        public String getPropertiesKey() {
            return propertiesKey;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof BindingSource) {
                BindingSource that = (BindingSource) o;

                return Objects.equals(this.source, that.source)
                    && Objects.equals(this.vhost, that.vhost)
                    && Objects.equals(this.destination, that.destination)
                    && Objects.equals(this.destinationType, that.destinationType)
                    && Objects.equals(this.routingKey, that.routingKey)
                    && Objects.equals(this.arguments, that.arguments)
                    && Objects.equals(this.propertiesKey, that.propertiesKey);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(source, vhost, destination, destinationType, routingKey, arguments,
                propertiesKey);
        }
    }

    static RabbitMQManagementAPI from(RabbitMQConfiguration configuration) {
        try {
            RabbitMQConfiguration.ManagementCredentials credentials =
                configuration.getManagementCredentials();
            RabbitMQManagementAPI rabbitMQManagementAPI = Feign.builder()
                .client(getClient(configuration))
                .requestInterceptor(new BasicAuthRequestInterceptor(credentials.getUser(),
                    new String(credentials.getPassword())))
                .logger(new Slf4jLogger(RabbitMQManagementAPI.class))
                .logLevel(Logger.Level.FULL)
                .encoder(new JacksonEncoder())
                .decoder(new JacksonDecoder())
                .retryer(new Retryer.Default())
                .errorDecoder(RETRY_500)
                .target(RabbitMQManagementAPI.class, configuration.getManagementUri().toString());

            return rabbitMQManagementAPI;
        } catch (KeyManagementException | NoSuchAlgorithmException | CertificateException | KeyStoreException | IOException | UnrecoverableKeyException e) {
            throw new RuntimeException(e);
        }
    }

    private static Client getClient(RabbitMQConfiguration configuration)
        throws KeyManagementException, NoSuchAlgorithmException, CertificateException,
        KeyStoreException, IOException, UnrecoverableKeyException {
        if (configuration.useSslManagement()) {
            SSLContextBuilder sslContextBuilder = new SSLContextBuilder();

            setupSslValidationStrategy(sslContextBuilder, configuration);

            setupClientCertificateAuthentication(sslContextBuilder, configuration);

            SSLContext sslContext = sslContextBuilder.build();

            return new Client.Default(sslContext.getSocketFactory(),
                getHostNameVerifier(configuration));
        } else {
            return new Client.Default(null, null);
        }

    }

    private static HostnameVerifier getHostNameVerifier(RabbitMQConfiguration configuration) {
        switch (configuration.getSslConfiguration().getHostNameVerifier()) {
            case ACCEPT_ANY_HOSTNAME:
                return ((hostname, session) -> true);
            default:
                return new DefaultHostnameVerifier();
        }
    }

    private static void setupClientCertificateAuthentication(SSLContextBuilder sslContextBuilder,
                                                             RabbitMQConfiguration configuration)
        throws NoSuchAlgorithmException, KeyStoreException, UnrecoverableKeyException,
        CertificateException, IOException {
        Optional<RabbitMQConfiguration.SSLConfiguration.SSLKeyStore> keyStore =
            configuration.getSslConfiguration().getKeyStore();

        if (keyStore.isPresent()) {
            RabbitMQConfiguration.SSLConfiguration.SSLKeyStore sslKeyStore = keyStore.get();

            sslContextBuilder.loadKeyMaterial(sslKeyStore.getFile(), sslKeyStore.getPassword(),
                sslKeyStore.getPassword());
        }
    }

    private static void setupSslValidationStrategy(SSLContextBuilder sslContextBuilder,
                                                   RabbitMQConfiguration configuration)
        throws NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException {
        RabbitMQConfiguration.SSLConfiguration.SSLValidationStrategy strategy = configuration
            .getSslConfiguration()
            .getStrategy();

        final TrustStrategy trustAll = (x509Certificates, authType) -> true;

        switch (strategy) {
            case DEFAULT:
                break;
            case IGNORE:
                sslContextBuilder.loadTrustMaterial(trustAll);
                break;
            case OVERRIDE:
                applyTrustStore(sslContextBuilder, configuration);
                break;
            default:
                throw new NotImplementedException(
                    String.format("unrecognized strategy '%s'", strategy.name()));
        }
    }

    private static SSLContextBuilder applyTrustStore(SSLContextBuilder sslContextBuilder,
                                                     RabbitMQConfiguration configuration)
        throws CertificateException, NoSuchAlgorithmException,
        KeyStoreException, IOException {

        RabbitMQConfiguration.SSLConfiguration.SSLTrustStore trustStore =
            configuration.getSslConfiguration()
                .getTrustStore()
                .orElseThrow(() -> new IllegalStateException("SSLTrustStore cannot to be empty"));

        return sslContextBuilder
            .loadTrustMaterial(trustStore.getFile(), trustStore.getPassword());
    }

    ErrorDecoder RETRY_500 = (methodKey, response) -> {
        if (response.status() == 500) {
            throw new RetryableException(response.status(), "Error encountered, scheduling retry",
                response.request().httpMethod(), new Date(), response.request());
        }
        throw new RuntimeException("Non recoverable exception status: " + response.status());
    };

    @RequestLine("GET /api/queues")
    List<MessageQueue> listQueues();

    @RequestLine(value = "GET /api/queues/{vhost}/{name}", decodeSlash = false)
    MessageQueueDetails queueDetails(@Param("vhost") String vhost, @Param("name") String name);

    @RequestLine(value = "DELETE /api/queues/{vhost}/{name}", decodeSlash = false)
    void deleteQueue(@Param("vhost") String vhost, @Param("name") String name);

    @RequestLine(value = "DELETE /api/queues/{vhost}/{name}/contents", decodeSlash = false)
    void purgeQueue(@Param("vhost") String vhost, @Param("name") String name);

    @RequestLine(value = "GET /api/exchanges/{vhost}/{name}/bindings/source", decodeSlash = false)
    List<BindingSource> listBindings(@Param("vhost") String vhost, @Param("name") String name);

    @RequestLine("GET /api/exchanges")
    List<Exchange> listExchanges();
}