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
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.CREATED;
import static org.apache.james.jmap.HttpConstants.JSON_CONTENT_TYPE_UTF8;
import static org.apache.james.jmap.JMAPUrls.UPLOAD;
import static org.apache.james.jmap.http.LoggingHelper.jmapAction;
import static org.apache.james.jmap.http.LoggingHelper.jmapAuthContext;
import static org.apache.james.jmap.http.LoggingHelper.jmapContext;
import static org.apache.james.util.ReactorUtils.logOnError;

import java.io.EOFException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.stream.Stream;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.james.jmap.Endpoint;
import org.apache.james.jmap.JMAPRoute;
import org.apache.james.jmap.JMAPRoutes;
import org.apache.james.jmap.api.upload.UploadRepository;
import org.apache.james.jmap.draft.exceptions.BadRequestException;
import org.apache.james.jmap.draft.exceptions.InternalErrorException;
import org.apache.james.jmap.draft.model.UploadResponse;
import org.apache.james.jmap.exceptions.UnauthorizedException;
import org.apache.james.jmap.methods.BlobManagerImpl;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.ContentType;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.ReactorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;

import io.netty.handler.codec.http.HttpMethod;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;

public class UploadRoutes implements JMAPRoutes {
    private static final Logger LOGGER = LoggerFactory.getLogger(UploadRoutes.class);

    static class CancelledUploadException extends RuntimeException {

    }

    private final MetricFactory metricFactory;
    private final Authenticator authenticator;
    private final UploadRepository uploadRepository;
    private final ObjectMapper objectMapper;

    @Inject
    private UploadRoutes(MetricFactory metricFactory, @Named(InjectionKeys.DRAFT) Authenticator authenticator, UploadRepository uploadRepository, ObjectMapper objectMapper) {
        this.metricFactory = metricFactory;
        this.authenticator = authenticator;
        this.uploadRepository = uploadRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public Stream<JMAPRoute> routes() {
        return Stream.of(
            JMAPRoute.builder()
                .endpoint(new Endpoint(HttpMethod.POST, UPLOAD))
                .action(this::post)
                .corsHeaders(),
            JMAPRoute.builder()
                .endpoint(new Endpoint(HttpMethod.OPTIONS, UPLOAD))
                .action(CORS_CONTROL)
                .noCorsHeaders()
        );
    }

    private Mono<Void> post(HttpServerRequest request, HttpServerResponse response)  {
        String contentType = request.requestHeaders().get(CONTENT_TYPE);
        if (Strings.isNullOrEmpty(contentType)) {
            return response.status(BAD_REQUEST).send();
        } else {
            return authenticator.authenticate(request)
                .flatMap(session -> post(request, response, ContentType.of(contentType), session)
                    .contextWrite(jmapAuthContext(session)))
                .onErrorResume(CancelledUploadException.class, e -> handleCanceledUpload(response, e))
                .onErrorResume(BadRequestException.class, e -> handleBadRequest(response, e))
                .onErrorResume(UnauthorizedException.class, e -> handleAuthenticationFailure(response, LOGGER, e))
                .doOnEach(logOnError(e -> LOGGER.error("Unexpected error", e)))
                .onErrorResume(e -> handleInternalError(response, LOGGER, e))
                .contextWrite(jmapContext(request))
                .contextWrite(jmapAction("upload-get"))
                .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER);
        }
    }

    private Mono<Void> post(HttpServerRequest request, HttpServerResponse response, ContentType contentType, MailboxSession session) {
        InputStream content = ReactorUtils.toInputStream(request.receive()
            // Unwrapping to byte array needed to solve data races and buffer reordering when using .asByteBuffer()
            .asByteArray()
            .map(ByteBuffer::wrap)
            .subscribeOn(Schedulers.boundedElastic()));
        return Mono.from(metricFactory.decoratePublisherWithTimerMetric("JMAP-upload-post",
            handle(contentType, content, session, response)));
    }

    private Mono<Void> handle(ContentType contentType, InputStream content, MailboxSession mailboxSession, HttpServerResponse response) {
        return uploadContent(contentType, content, mailboxSession)
            .flatMap(storedContent -> {
                try {
                    byte[] bytes = objectMapper.writeValueAsBytes(storedContent);
                    return response.header(CONTENT_TYPE, JSON_CONTENT_TYPE_UTF8)
                        .status(CREATED)
                        .header(CONTENT_LENGTH, Integer.toString(bytes.length))
                        .sendByteArray(Mono.just(bytes))
                        .then();
                } catch (JsonProcessingException e) {
                    throw new InternalErrorException("Error serializing upload response", e);
                }
            });
    }

    private Mono<UploadResponse> uploadContent(ContentType contentType, InputStream inputStream, MailboxSession session) {
        return Mono.from(uploadRepository.upload(inputStream, contentType, session.getUser()))
            .map(upload -> UploadResponse.builder()
                .blobId(BlobManagerImpl.UPLOAD_PREFIX + upload.uploadId().asString())
                .type(upload.contentType().asString())
                .size(upload.sizeAsLong())
                .build())
            .onErrorMap(e -> e.getCause() instanceof EOFException, any -> new CancelledUploadException())
            .onErrorMap(e -> !(e instanceof CancelledUploadException), e -> new InternalErrorException("Error while uploading content", e));
    }

    private Mono<Void> handleCanceledUpload(HttpServerResponse response, CancelledUploadException e) {
        LOGGER.info("An upload has been canceled before the end", e);
        return response.send();
    }

    private Mono<Void> handleBadRequest(HttpServerResponse response, BadRequestException e) {
        LOGGER.warn("Invalid authentication request received.", e);
        return response.status(BAD_REQUEST).send();
    }
}
