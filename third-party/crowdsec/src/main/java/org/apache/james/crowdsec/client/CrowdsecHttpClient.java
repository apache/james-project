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

package org.apache.james.crowdsec.client;

import java.util.List;
import java.util.concurrent.TimeoutException;

import jakarta.inject.Inject;

import org.apache.http.HttpStatus;
import org.apache.http.entity.ContentType;
import org.apache.james.crowdsec.model.CrowdsecDecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.google.common.collect.ImmutableList;

import io.netty.handler.codec.http.HttpHeaderNames;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

public class CrowdsecHttpClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(CrowdsecHttpClient.class);

    private static final String GET_DECISION = "/decisions";

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final CrowdsecClientConfiguration crowdsecConfiguration;

    @Inject
    public CrowdsecHttpClient(CrowdsecClientConfiguration configuration) {
        this.httpClient = buildReactorNettyHttpClient(configuration);
        this.mapper = new ObjectMapper().registerModule(new Jdk8Module());
        this.crowdsecConfiguration = configuration;
    }

    public Mono<List<CrowdsecDecision>> getCrowdsecDecisions() {
        return httpClient.get()
            .uri(GET_DECISION)
            .responseSingle((response, body) -> {
                switch (response.status().code()) {
                    case HttpStatus.SC_OK:
                        return body.asString().map(this::parseCrowdsecDecisions);
                    case HttpStatus.SC_FORBIDDEN:
                        return Mono.error(new RuntimeException("Invalid api-key bouncer"));
                    default:
                        return Mono.error(new RuntimeException("Request failed with status code " + response.status().code()));
                }
            })
            .timeout(crowdsecConfiguration.getTimeout())
            .onErrorResume(TimeoutException.class, e -> Mono.fromRunnable(() -> LOGGER.warn("Timeout while questioning to CrowdSec. May need to check the CrowdSec configuration."))
                .thenReturn(ImmutableList.of()));
    }

    private List<CrowdsecDecision> parseCrowdsecDecisions(String json) {
        if (noCrowdsecDecision(json)) {
            return ImmutableList.of();
        } else {
            try {
                return mapper.readValue(json, new TypeReference<>() {});
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static boolean noCrowdsecDecision(String json) {
        return json.equals("null");
    }

    private HttpClient buildReactorNettyHttpClient(CrowdsecClientConfiguration configuration) {
        return HttpClient.create()
            .disableRetry(true)
            .responseTimeout(configuration.getTimeout())
            .baseUrl(configuration.getUrl().toString())
            .headers(headers -> headers.add("X-Api-Key", configuration.getApiKey()))
            .headers(headers -> headers.add(HttpHeaderNames.ACCEPT, ContentType.APPLICATION_JSON.getMimeType()));
    }
}
