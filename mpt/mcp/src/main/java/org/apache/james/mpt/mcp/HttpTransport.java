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

package org.apache.james.mpt.mcp;

import static io.netty.handler.codec.http.HttpHeaderNames.ALLOW;
import static io.netty.handler.codec.http.HttpHeaderNames.AUTHORIZATION;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderNames.WWW_AUTHENTICATE;
import static io.netty.handler.codec.http.HttpResponseStatus.ACCEPTED;
import static io.netty.handler.codec.http.HttpResponseStatus.METHOD_NOT_ALLOWED;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE;
import static io.netty.handler.codec.http.HttpResponseStatus.UNAUTHORIZED;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;

import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;

/**
 * Minimal MCP "Streamable HTTP" transport: JSON-RPC over <code>POST /mcp</code>, answered with
 * <code>application/json</code> (no SSE stream). <code>GET /health</code> is a liveness probe.
 */
public class HttpTransport {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpTransport.class);
    private static final int MAX_BODY_BYTES = 4 * 1024 * 1024;
    private static final String MCP_PATH = "/mcp";
    private static final String HEALTH_PATH = "/health";
    private static final String TEXT_PLAIN = "text/plain; charset=utf-8";
    private static final String APPLICATION_JSON = "application/json; charset=utf-8";

    private static class PayloadTooLargeException extends RuntimeException {
    }

    private final McpHandler handler;
    private final int port;
    private final Optional<String> authToken;
    private DisposableServer server;

    public HttpTransport(McpHandler handler, int port, Optional<String> authToken) {
        this.handler = handler;
        this.port = port;
        this.authToken = authToken;
    }

    public HttpTransport start() {
        server = HttpServer.create()
            .port(port)
            .route(routes -> routes
                .post(MCP_PATH, (req, res) -> authenticated(req, res, this::handlePost))
                .delete(MCP_PATH, (req, res) -> authenticated(req, res, (request, response) -> response.status(OK).send()))
                .get(MCP_PATH, (req, res) -> authenticated(req, res, this::methodNotAllowed))
                .get(HEALTH_PATH, (req, res) -> res.status(OK).header(CONTENT_TYPE, TEXT_PLAIN).sendString(Mono.just("OK"))))
            .bindNow();
        LOGGER.info("MCP MPT server listening on http://0.0.0.0:{}{}", port(), MCP_PATH);
        return this;
    }

    public int port() {
        return server.port();
    }

    public void stop() {
        if (server != null) {
            server.disposeNow();
        }
    }

    private Publisher<Void> authenticated(HttpServerRequest request, HttpServerResponse response,
                                          BiFunction<HttpServerRequest, HttpServerResponse, Publisher<Void>> next) {
        if (!authorized(request)) {
            return response.status(UNAUTHORIZED)
                .header(WWW_AUTHENTICATE, "Bearer")
                .header(CONTENT_TYPE, TEXT_PLAIN)
                .sendString(Mono.just("Unauthorized"));
        }
        return next.apply(request, response);
    }

    private Publisher<Void> methodNotAllowed(HttpServerRequest request, HttpServerResponse response) {
        return response.status(METHOD_NOT_ALLOWED)
            .header(ALLOW, "POST, DELETE")
            .header(CONTENT_TYPE, TEXT_PLAIN)
            .sendString(Mono.just("Method not allowed"));
    }

    private Publisher<Void> handlePost(HttpServerRequest request, HttpServerResponse response) {
        return readBody(request)
            // script runs are blocking and may last minutes: keep them off the event loop
            .flatMap(payload -> Mono.fromCallable(() -> handler.handle(payload))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(answer -> {
                    if (payload.contains("\"initialize\"")) {
                        response.header("Mcp-Session-Id", UUID.randomUUID().toString());
                    }
                    if (answer.isEmpty()) {
                        return Mono.from(response.status(ACCEPTED).send());
                    }
                    return Mono.from(response.status(OK)
                        .header(CONTENT_TYPE, APPLICATION_JSON)
                        .sendString(Mono.just(answer.get())));
                }))
            .onErrorResume(PayloadTooLargeException.class, e -> Mono.from(response.status(REQUEST_ENTITY_TOO_LARGE)
                .header(CONTENT_TYPE, TEXT_PLAIN)
                .sendString(Mono.just("Payload too large"))));
    }

    private Mono<String> readBody(HttpServerRequest request) {
        return request.receive()
            .asByteArray()
            .reduce(new ByteArrayOutputStream(), (buffer, bytes) -> {
                if (buffer.size() + bytes.length > MAX_BODY_BYTES) {
                    throw new PayloadTooLargeException();
                }
                buffer.writeBytes(bytes);
                return buffer;
            })
            .map(buffer -> buffer.toString(UTF_8));
    }

    private boolean authorized(HttpServerRequest request) {
        if (authToken.isEmpty()) {
            return true;
        }
        String header = Optional.ofNullable(request.requestHeaders().get(AUTHORIZATION)).orElse("");
        byte[] expected = ("Bearer " + authToken.get()).getBytes(UTF_8);
        return MessageDigest.isEqual(expected, header.getBytes(UTF_8));
    }
}
