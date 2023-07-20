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

import static io.netty.handler.codec.http.HttpHeaderNames.ACCEPT;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpResponseStatus.CREATED;
import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpResponseStatus.NO_CONTENT;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpResponseStatus.UNAUTHORIZED;
import static org.apache.james.jmap.HttpConstants.JSON_CONTENT_TYPE;
import static org.apache.james.jmap.HttpConstants.JSON_CONTENT_TYPE_UTF8;
import static org.apache.james.jmap.JMAPUrls.AUTHENTICATION;
import static org.apache.james.jmap.http.LoggingHelper.jmapAction;
import static org.apache.james.jmap.http.LoggingHelper.jmapAuthContext;
import static org.apache.james.jmap.http.LoggingHelper.jmapContext;
import static org.apache.james.util.ReactorUtils.log;
import static org.apache.james.util.ReactorUtils.logOnError;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.james.core.Username;
import org.apache.james.jmap.Endpoint;
import org.apache.james.jmap.JMAPRoute;
import org.apache.james.jmap.JMAPRoutes;
import org.apache.james.jmap.JMAPUrls;
import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.jmap.draft.api.AccessTokenManager;
import org.apache.james.jmap.draft.api.SimpleTokenFactory;
import org.apache.james.jmap.draft.api.SimpleTokenManager;
import org.apache.james.jmap.draft.exceptions.BadRequestException;
import org.apache.james.jmap.draft.exceptions.InternalErrorException;
import org.apache.james.jmap.draft.json.MultipleObjectMapperBuilder;
import org.apache.james.jmap.draft.model.AccessTokenRequest;
import org.apache.james.jmap.draft.model.AccessTokenResponse;
import org.apache.james.jmap.draft.model.ContinuationTokenRequest;
import org.apache.james.jmap.draft.model.ContinuationTokenResponse;
import org.apache.james.jmap.draft.model.EndPointsResponse;
import org.apache.james.jmap.exceptions.UnauthorizedException;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.util.ReactorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.netty.handler.codec.http.HttpMethod;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;

public class AuthenticationRoutes implements JMAPRoutes {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationRoutes.class);

    private final ObjectMapper mapper;
    private final UsersRepository usersRepository;
    private final SimpleTokenManager simpleTokenManager;
    private final AccessTokenManager accessTokenManager;
    private final SimpleTokenFactory simpleTokenFactory;
    private final MetricFactory metricFactory;
    private final Authenticator authenticator;

    @Inject
    public AuthenticationRoutes(UsersRepository usersRepository, SimpleTokenManager simpleTokenManager, AccessTokenManager accessTokenManager, SimpleTokenFactory simpleTokenFactory, MetricFactory metricFactory, @Named(InjectionKeys.DRAFT) Authenticator authenticator) {
        this.mapper = new MultipleObjectMapperBuilder()
            .registerClass(ContinuationTokenRequest.UNIQUE_JSON_PATH, ContinuationTokenRequest.class)
            .registerClass(AccessTokenRequest.UNIQUE_JSON_PATH, AccessTokenRequest.class)
            .build();
        this.usersRepository = usersRepository;
        this.simpleTokenManager = simpleTokenManager;
        this.accessTokenManager = accessTokenManager;
        this.simpleTokenFactory = simpleTokenFactory;
        this.metricFactory = metricFactory;
        this.authenticator = authenticator;
    }

    @Override
    public Stream<JMAPRoute> routes() {
        return Stream.of(
            JMAPRoute.builder()
                .endpoint(Endpoint.ofFixedPath(HttpMethod.POST, AUTHENTICATION))
                .action(this::post)
                .corsHeaders(),
            JMAPRoute.builder()
                .endpoint(Endpoint.ofFixedPath(HttpMethod.GET, AUTHENTICATION))
                .action(this::returnEndPointsResponse)
                .corsHeaders(),
            JMAPRoute.builder()
                .endpoint(Endpoint.ofFixedPath(HttpMethod.DELETE, AUTHENTICATION))
                .action(this::delete)
                .corsHeaders(),
            JMAPRoute.builder()
                .endpoint(Endpoint.ofFixedPath(HttpMethod.OPTIONS, AUTHENTICATION))
                .action(CORS_CONTROL)
                .noCorsHeaders()
        );
    }

    private Mono<Void> post(HttpServerRequest request, HttpServerResponse response) {
        return Mono.from(metricFactory.decoratePublisherWithTimerMetric("JMAP-authentication-post",
            Mono.just(request)
                .map(this::assertJsonContentType)
                .map(this::assertAcceptJsonOnly)
                .flatMap(this::deserialize)
                .flatMap(objectRequest -> {
                    if (objectRequest instanceof ContinuationTokenRequest) {
                        return handleContinuationTokenRequest((ContinuationTokenRequest) objectRequest, response);
                    } else if (objectRequest instanceof AccessTokenRequest) {
                        return handleAccessTokenRequest((AccessTokenRequest) objectRequest, response);
                    } else {
                        throw new RuntimeException(objectRequest.getClass() + " " + objectRequest);
                    }
                })))
            .onErrorResume(BadRequestException.class, e -> handleBadRequest(response, LOGGER, e))
            .doOnEach(logOnError(e -> LOGGER.error("Unexpected error", e)))
            .onErrorResume(e -> handleInternalError(response, LOGGER, e))
            .contextWrite(jmapContext(request))
            .contextWrite(jmapAction("auth-post"))
            .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER);
    }

    private Mono<Void> returnEndPointsResponse(HttpServerRequest req, HttpServerResponse resp) {
            return authenticator.authenticate(req)
                .flatMap(session -> returnEndPointsResponse(resp)
                    .contextWrite(jmapAuthContext(session)))
                .onErrorResume(IllegalArgumentException.class, e -> handleBadRequest(resp, LOGGER, e))
                .onErrorResume(BadRequestException.class, e -> handleBadRequest(resp, LOGGER, e))
                .doOnEach(logOnError(e -> LOGGER.error("Unexpected error", e)))
                .onErrorResume(InternalErrorException.class, e -> handleInternalError(resp, LOGGER, e))
                .onErrorResume(UnauthorizedException.class, e -> handleAuthenticationFailure(resp, LOGGER, e))
                .contextWrite(jmapContext(req))
                .contextWrite(jmapAction("returnEndPoints"))
                .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER);
    }

    private Mono<Void> returnEndPointsResponse(HttpServerResponse resp) {
        try {
            byte[] bytes = mapper.writeValueAsBytes(EndPointsResponse
                .builder()
                .api(JMAPUrls.JMAP)
                .eventSource(JMAPUrls.NOT_IMPLEMENTED)
                .upload(JMAPUrls.UPLOAD)
                .download(JMAPUrls.DOWNLOAD)
                .build());

            return resp.status(OK)
                .header(CONTENT_TYPE, JSON_CONTENT_TYPE_UTF8)
                .header(CONTENT_LENGTH, Integer.toString(bytes.length))
                .sendByteArray(Mono.just(bytes))
                .then();
        } catch (JsonProcessingException e) {
            throw new InternalErrorException("Error serializing endpoint response", e);
        }
    }

    private Mono<Void> delete(HttpServerRequest req, HttpServerResponse resp) {
        String authorizationHeader = req.requestHeaders().get("Authorization");

        return authenticator.authenticate(req)
            .flatMap(session -> Mono.from(accessTokenManager.revoke(AccessToken.fromString(authorizationHeader)))
                    .then(resp.status(NO_CONTENT).send().then())
                .contextWrite(jmapAuthContext(session)))
            .onErrorResume(UnauthorizedException.class, e -> handleAuthenticationFailure(resp, LOGGER, e))
            .contextWrite(jmapContext(req))
            .contextWrite(jmapAction("auth-delete"))
            .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER);
    }

    private HttpServerRequest assertJsonContentType(HttpServerRequest req) {
        if (!Objects.equals(req.requestHeaders().get(CONTENT_TYPE), JSON_CONTENT_TYPE_UTF8)) {
            throw new BadRequestException("Request ContentType header must be set to: " + JSON_CONTENT_TYPE_UTF8);
        }
        return req;
    }

    private HttpServerRequest assertAcceptJsonOnly(HttpServerRequest req) {
        String accept = req.requestHeaders().get(ACCEPT);
        if (accept == null || !accept.contains(JSON_CONTENT_TYPE)) {
            throw new BadRequestException("Request Accept header must be set to JSON content type");
        }
        return req;
    }

    private Mono<Object> deserialize(HttpServerRequest req) {
        return req.receive().aggregate().asInputStream()
            .map(inputStream -> {
                try {
                    return mapper.readValue(inputStream, Object.class);
                } catch (IOException e) {
                    throw new BadRequestException("Request can't be deserialized", e);
                }
            })
            .switchIfEmpty(Mono.error(() -> new BadRequestException("Empty body")));
    }

    private Mono<Void> handleContinuationTokenRequest(ContinuationTokenRequest request, HttpServerResponse resp) {
        try {
            Mono<byte[]> tokenResponseMono = Mono.fromCallable(() -> ContinuationTokenResponse
                .builder()
                .continuationToken(simpleTokenFactory.generateContinuationToken(request.getUsername()))
                .methods(ContinuationTokenResponse.AuthenticationMethod.PASSWORD)
                .build())
                .map(token -> {
                    try {
                        return mapper.writeValueAsBytes(token);
                    } catch (JsonProcessingException e) {
                        throw new InternalErrorException("error serialising JMAP API response json");
                    }
                })
                .subscribeOn(Schedulers.parallel());

            return tokenResponseMono
                .flatMap(bytes -> resp.header(CONTENT_TYPE, JSON_CONTENT_TYPE_UTF8)
                    .header(CONTENT_LENGTH, Integer.toString(bytes.length))
                    .sendByteArray(Mono.just(bytes))
                    .then());
        } catch (Exception e) {
            throw new InternalErrorException("Error while responding to continuation token", e);
        }
    }

    private Mono<Void> handleAccessTokenRequest(AccessTokenRequest request, HttpServerResponse resp) {
        SimpleTokenManager.TokenStatus validity = simpleTokenManager.getValidity(request.getToken());
        switch (validity) {
            case EXPIRED:
                return returnForbiddenAuthentication(resp);
            case INVALID:
                return returnUnauthorizedResponse(resp)
                    .doOnEach(log(() -> LOGGER.warn("Use of an invalid ContinuationToken : {}", request.getToken().serialize())));
            case OK:
                return manageAuthenticationResponse(request, resp);
            default:
                throw new InternalErrorException(String.format("Validity %s is not implemented", validity));
        }
    }

    private Mono<Void> manageAuthenticationResponse(AccessTokenRequest request, HttpServerResponse resp) {
        Username username = Username.of(request.getToken().getUsername());

        return authenticate(request, username)
            .flatMap(loggedInUser -> loggedInUser.map(value -> returnAccessTokenResponse(resp, value))
                .orElseGet(() -> returnUnauthorizedResponse(resp)
                    .doOnEach(log(() -> LOGGER.info("Authentication failure for {}", username)))));
    }

    private Mono<Optional<Username>> authenticate(AccessTokenRequest request, Username username) {
        return Mono.fromCallable(() -> {
            try {
                return usersRepository.test(username, request.getPassword());
            } catch (UsersRepositoryException e) {
                LOGGER.error("Error while trying to validate authentication for user '{}'", username, e);
                return Optional.<Username>empty();
            }
        }).subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER);
    }

    private Mono<Void> returnAccessTokenResponse(HttpServerResponse resp, Username username) {
        return Mono.from(accessTokenManager.grantAccessToken(username))
            .map(accessToken -> AccessTokenResponse.builder()
                .accessToken(accessToken)
                .api(JMAPUrls.JMAP)
                .eventSource(JMAPUrls.NOT_IMPLEMENTED)
                .upload(JMAPUrls.UPLOAD)
                .download(JMAPUrls.DOWNLOAD)
                .build())
            .flatMap(accessTokenResponse -> {
                try {
                    byte[] bytes = mapper.writeValueAsBytes(accessTokenResponse);
                    return resp.status(CREATED)
                        .header(CONTENT_TYPE, JSON_CONTENT_TYPE_UTF8)
                        .header(CONTENT_LENGTH, Integer.toString(bytes.length))
                        .sendByteArray(Mono.just(bytes))
                        .then();
                } catch (JsonProcessingException e) {
                    throw new InternalErrorException("Could not serialize access token response", e);
                }
            });
    }

    private Mono<Void> returnUnauthorizedResponse(HttpServerResponse resp) {
        return resp.status(UNAUTHORIZED).send().then();
    }

    private Mono<Void> returnForbiddenAuthentication(HttpServerResponse resp) {
        return resp.status(FORBIDDEN).send().then();
    }
}
