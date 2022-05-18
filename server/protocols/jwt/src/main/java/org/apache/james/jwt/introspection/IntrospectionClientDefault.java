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

package org.apache.james.jwt.introspection;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.reactivestreams.Publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.lambdas.Throwing;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;

import io.netty.handler.codec.http.HttpResponseStatus;
import reactor.core.publisher.Mono;
import reactor.netty.ByteBufMono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientResponse;
import reactor.netty.resources.ConnectionProvider;

public class IntrospectionClientDefault implements IntrospectionClient {

    public static class TokenIntrospectionConfiguration {

        public static TokenIntrospectionConfiguration parse(HierarchicalConfiguration<ImmutableNode> configuration) throws MalformedURLException {
            String introspectionTokenEndpoint = configuration.getString("introspectionTokenEndpoint", null);
            Preconditions.checkNotNull(introspectionTokenEndpoint, "`introspectionTokenEndpoint` property need to be specified inside the oidc tag");
            URL introspectionTokenEndpointURL = new URL(introspectionTokenEndpoint);
            Map<String, Object> formAttributes = Optional.ofNullable(configuration.getString("introspectionTokenFormAttrs", null))
                .map(TokenIntrospectionConfiguration::parseStringToMap)
                .orElse(Map.of());

            Map<String, Object> headerAttributes = Optional.ofNullable(configuration.getString("introspectionTokenHeaderAttrs", null))
                .map(TokenIntrospectionConfiguration::parseStringToMap)
                .orElse(Map.of());

            return new TokenIntrospectionConfiguration(introspectionTokenEndpointURL, formAttributes, headerAttributes);
        }

        private static Map<String, Object> parseStringToMap(String value) {
            return Splitter.on(",").withKeyValueSeparator("=").split(value)
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> (Object) e.getValue()));
        }

        private final URL endpoint;
        private final Map<String, Object> formAttributes;
        private final Map<String, Object> headerAttributes;

        public TokenIntrospectionConfiguration(URL endpoint) {
            this(endpoint, Map.of(), Map.of());
        }

        public TokenIntrospectionConfiguration(URL endpoint, Map<String, Object> formAttributes, Map<String, Object> headerAttributes) {
            this.endpoint = endpoint;
            this.formAttributes = formAttributes;
            this.headerAttributes = headerAttributes;
        }

        public URL endpoint() {
            return endpoint;
        }

        public Map<String, Object> formAttributes() {
            return formAttributes;
        }

        public Map<String, Object> headerAttributes() {
            return headerAttributes;
        }
    }

    public static final String TOKEN_ATTRIBUTE = "token";
    private final HttpClient httpClient;
    private final ObjectMapper deserializer;
    private final TokenIntrospectionConfiguration configuration;

    public IntrospectionClientDefault(TokenIntrospectionConfiguration configuration) {
        this.httpClient = HttpClient.create(ConnectionProvider.builder(this.getClass().getName())
                .build())
            .disableRetry(true)
            .headers(builder -> {
                builder.add("Accept", "application/json");
                builder.add("Content-Type", "application/x-www-form-urlencoded");
                configuration.headerAttributes().forEach(builder::add);
            });
        this.deserializer = new ObjectMapper();
        this.configuration = configuration;
    }

    @Override
    public Publisher<TokenIntrospectionResponse> introspect(String token) {
        return httpClient.post()
            .uri(configuration.endpoint.toString())
            .sendForm((req, form) -> {
                form.multipart(false)
                    .attr(TOKEN_ATTRIBUTE, token);
                configuration.formAttributes().forEach((key, value) -> form.attr(key, value.toString()));
            })
            .responseSingle(this::afterHTTPResponseHandler);
    }

    private Mono<TokenIntrospectionResponse> afterHTTPResponseHandler(HttpClientResponse httpClientResponse, ByteBufMono dataBuf) {
        return Mono.just(httpClientResponse.status())
            .filter(httpStatus -> httpStatus.equals(HttpResponseStatus.OK))
            .flatMap(httpStatus -> dataBuf.asByteArray())
            .map(Throwing.function(deserializer::readTree))
            .map(TokenIntrospectionResponse::parse)
            .onErrorResume(error -> Mono.error(new TokenIntrospectionException("Error when introspecting token.", error)))
            .switchIfEmpty(Mono.error(new TokenIntrospectionException(
                String.format("Error when introspecting token. \nResponse Status = %s, \n Response Body = %s",
                    httpClientResponse.status().code(), dataBuf.asString(StandardCharsets.UTF_8)))));
    }
}
