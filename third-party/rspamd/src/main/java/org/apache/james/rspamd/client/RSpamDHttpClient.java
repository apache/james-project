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

package org.apache.james.rspamd.client;

import static org.apache.james.rspamd.client.RSpamDClientConfiguration.DEFAULT_TIMEOUT_IN_SECONDS;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.apache.james.rspamd.exception.RSpamDUnexpectedException;
import org.apache.james.rspamd.exception.UnauthorizedException;
import org.apache.james.rspamd.model.AnalysisResult;
import org.apache.james.util.ReactorUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.github.fge.lambdas.Throwing;

import io.netty.buffer.Unpooled;
import reactor.core.publisher.Mono;
import reactor.netty.ByteBufMono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientResponse;

public class RSpamDHttpClient {
    public static final String CHECK_V2_ENDPOINT = "/checkV2";
    public static final String LEARN_SPAM_ENDPOINT = "/learnspam";
    public static final String LEARN_HAM_ENDPOINT = "/learnham";
    private static final int OK = 200;
    private static final int FORBIDDEN = 403;
    private static final int BUFFER_SIZE = 16384;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public RSpamDHttpClient(RSpamDClientConfiguration configuration) {
        httpClient = buildReactorNettyHttpClient(configuration);
        this.objectMapper = new ObjectMapper().registerModule(new Jdk8Module());
    }

    public Mono<AnalysisResult> checkV2(InputStream mimeMessage) {
        return httpClient.post()
            .uri(CHECK_V2_ENDPOINT)
            .send(ReactorUtils.toChunks(mimeMessage, BUFFER_SIZE)
                .map(Unpooled::wrappedBuffer))
            .responseSingle(this::checkMailHttpResponseHandler)
            .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER);
    }

    public Mono<Void> reportAsSpam(InputStream content) {
        return reportMail(content, LEARN_SPAM_ENDPOINT);
    }

    public Mono<Void> reportAsHam(InputStream content) {
        return reportMail(content, LEARN_HAM_ENDPOINT);
    }

    private HttpClient buildReactorNettyHttpClient(RSpamDClientConfiguration configuration) {
        return HttpClient.create()
            .disableRetry(true)
            .responseTimeout(Duration.ofSeconds(configuration.getTimeout().orElse(DEFAULT_TIMEOUT_IN_SECONDS)))
            .baseUrl(configuration.getUrl().toString())
            .headers(headers -> headers.add("Password", configuration.getPassword()));
    }

    private Mono<Void> reportMail(InputStream content, String endpoint) {
        return httpClient.post()
            .uri(endpoint)
            .send(ReactorUtils.toChunks(content, BUFFER_SIZE)
                .map(Unpooled::wrappedBuffer)
                .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER))
            .responseSingle(this::reportMailHttpResponseHandler);
    }

    private Mono<AnalysisResult> checkMailHttpResponseHandler(HttpClientResponse httpClientResponse, ByteBufMono byteBufMono) {
        switch (httpClientResponse.status().code()) {
            case OK:
                return byteBufMono.asString(StandardCharsets.UTF_8)
                    .map(Throwing.function(this::convertToAnalysisResult));
            case FORBIDDEN:
                return byteBufMono.asString(StandardCharsets.UTF_8)
                    .flatMap(responseBody -> Mono.error(() -> new UnauthorizedException(responseBody)));
            default:
                return byteBufMono.asString(StandardCharsets.UTF_8)
                    .flatMap(responseBody -> Mono.error(() -> new RSpamDUnexpectedException(responseBody)));
        }
    }

    private Mono<Void> reportMailHttpResponseHandler(HttpClientResponse httpClientResponse, ByteBufMono byteBufMono) {
        switch (httpClientResponse.status().code()) {
            case OK:
                return Mono.empty();
            case FORBIDDEN:
                return byteBufMono.asString(StandardCharsets.UTF_8)
                    .flatMap(responseBody -> Mono.error(() -> new UnauthorizedException(responseBody)));
            default:
                return byteBufMono.asString(StandardCharsets.UTF_8)
                    .flatMap(responseBody -> Mono.error(() -> new RSpamDUnexpectedException(responseBody)));
        }
    }

    private AnalysisResult convertToAnalysisResult(String json) throws JsonProcessingException {
        return objectMapper.readValue(json, AnalysisResult.class);
    }

}
