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
package org.apache.james.jmap.http;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static org.apache.james.jmap.HttpConstants.JSON_CONTENT_TYPE;
import static org.apache.james.jmap.JMAPUrls.JMAP;
import static org.apache.james.jmap.http.LoggingHelper.jmapAuthContext;
import static org.apache.james.jmap.http.LoggingHelper.jmapContext;
import static org.apache.james.util.ReactorUtils.logOnError;

import java.io.IOException;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.james.jmap.Endpoint;
import org.apache.james.jmap.JMAPRoute;
import org.apache.james.jmap.JMAPRoutes;
import org.apache.james.jmap.draft.exceptions.BadRequestException;
import org.apache.james.jmap.draft.exceptions.InternalErrorException;
import org.apache.james.jmap.draft.methods.RequestHandler;
import org.apache.james.jmap.draft.model.AuthenticatedRequest;
import org.apache.james.jmap.draft.model.InvocationRequest;
import org.apache.james.jmap.draft.model.InvocationResponse;
import org.apache.james.jmap.exceptions.UnauthorizedException;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.metrics.api.MetricFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;

import io.netty.handler.codec.http.HttpMethod;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;

public class JMAPApiRoutes implements JMAPRoutes {
    public static final Logger LOGGER = LoggerFactory.getLogger(JMAPApiRoutes.class);

    private final ObjectMapper objectMapper;
    private final RequestHandler requestHandler;
    private final MetricFactory metricFactory;
    private final Authenticator authenticator;
    private final UserProvisioner userProvisioner;

    @Inject
    public JMAPApiRoutes(RequestHandler requestHandler, MetricFactory metricFactory, @Named(InjectionKeys.DRAFT) Authenticator authenticator, UserProvisioner userProvisioner) {
        this.requestHandler = requestHandler;
        this.metricFactory = metricFactory;
        this.authenticator = authenticator;
        this.userProvisioner = userProvisioner;
        this.objectMapper = new ObjectMapper();
        objectMapper.configure(Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true);
    }

    @Override
    public Stream<JMAPRoute> routes() {
        return Stream.of(
            JMAPRoute.builder()
                .endpoint(Endpoint.ofFixedPath(HttpMethod.POST, JMAP))
                .action(this::post)
                .corsHeaders(),
            JMAPRoute.builder()
                .endpoint(Endpoint.ofFixedPath(HttpMethod.OPTIONS, JMAP))
                .action(CORS_CONTROL)
                .noCorsHeaders()
        );
    }

    private Mono<Void> post(HttpServerRequest request, HttpServerResponse response) {
        return authenticator.authenticate(request)
            .flatMap(session -> userProvisioner.provisionUser(session)
                .then(Mono.from(metricFactory.decoratePublisherWithTimerMetric("JMAP-request",
                    post(request, response, session))))
                .contextWrite(jmapAuthContext(session)))
            .onErrorResume(BadRequestException.class, e -> handleBadRequest(response, LOGGER, e))
            .onErrorResume(UnauthorizedException.class, e -> handleAuthenticationFailure(response, LOGGER, e))
            .doOnEach(logOnError(e -> LOGGER.error("Unexpected error", e)))
            .onErrorResume(e -> response.status(INTERNAL_SERVER_ERROR).send())
            .contextWrite(jmapContext(request));
    }

    private Mono<Void> post(HttpServerRequest request, HttpServerResponse response, MailboxSession session) {
        Flux<Object[]> responses =
            requestAsJsonStream(request)
                .map(InvocationRequest::deserialize)
                .map(invocationRequest -> AuthenticatedRequest.decorate(invocationRequest, session))
                .concatMap(requestHandler::handle)
                .map(InvocationResponse::asProtocolSpecification);

        return sendResponses(response, responses);
    }

    private Mono<Void> sendResponses(HttpServerResponse response, Flux<Object[]> responses) {
        return responses.collectList()
            .map(objects -> {
                try {
                    return objectMapper.writeValueAsBytes(objects);
                } catch (JsonProcessingException e) {
                    throw new InternalErrorException("error serialising JMAP API response json");
                }
            })
            .flatMap(json -> response.status(OK)
                .header(CONTENT_TYPE, JSON_CONTENT_TYPE)
                .header(CONTENT_LENGTH, Integer.toString(json.length))
                .sendByteArray(Mono.just(json))
                .then());
    }

    private Flux<JsonNode[]> requestAsJsonStream(HttpServerRequest req) {
        return req.receive().aggregate().asInputStream()
            .map(inputStream -> {
                try {
                    return objectMapper.readValue(inputStream, JsonNode[][].class);
                } catch (IOException e) {
                    throw new BadRequestException("Error deserializing JSON", e);
                }
            })
            .flatMapIterable(ImmutableList::copyOf);
    }
}
