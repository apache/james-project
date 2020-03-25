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

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.CREATED;
import static org.apache.james.jmap.HttpConstants.JSON_CONTENT_TYPE_UTF8;
import static org.apache.james.jmap.http.JMAPUrls.UPLOAD;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

import javax.inject.Inject;

import org.apache.james.jmap.JMAPRoutes;
import org.apache.james.jmap.draft.exceptions.BadRequestException;
import org.apache.james.jmap.draft.exceptions.InternalErrorException;
import org.apache.james.jmap.draft.exceptions.UnauthorizedException;
import org.apache.james.jmap.draft.model.UploadResponse;
import org.apache.james.mailbox.AttachmentManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.Attachment;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.ReactorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.google.common.io.ByteStreams;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;
import reactor.netty.http.server.HttpServerRoutes;

public class UploadRoutes implements JMAPRoutes {
    private static final Logger LOGGER = LoggerFactory.getLogger(UploadRoutes.class);

    static class CancelledUploadException extends RuntimeException {

    }

    private final MetricFactory metricFactory;
    private final Authenticator authenticator;
    private final AttachmentManager attachmentManager;
    private final ObjectMapper objectMapper;

    @Inject
    private UploadRoutes(MetricFactory metricFactory, Authenticator authenticator, AttachmentManager attachmentManager, ObjectMapper objectMapper) {
        this.metricFactory = metricFactory;
        this.authenticator = authenticator;
        this.attachmentManager = attachmentManager;
        this.objectMapper = objectMapper;
    }

    @Override
    public Logger logger() {
        return LOGGER;
    }

    @Override
    public HttpServerRoutes define(HttpServerRoutes builder) {
        return builder.post(UPLOAD, JMAPRoutes.corsHeaders(this::post))
            .options(UPLOAD, CORS_CONTROL);
    }

    private Mono<Void> post(HttpServerRequest request, HttpServerResponse response)  {
        String contentType = request.requestHeaders().get(CONTENT_TYPE);
        if (Strings.isNullOrEmpty(contentType)) {
            return response.status(BAD_REQUEST).send();
        } else {
            return authenticator.authenticate(request)
                .flatMap(session -> post(request, response, contentType, session))
                .onErrorResume(CancelledUploadException.class, e -> handleCanceledUpload(response, e))
                .onErrorResume(BadRequestException.class, e -> handleBadRequest(response, e))
                .onErrorResume(UnauthorizedException.class, e -> handleAuthenticationFailure(response, e))
                .onErrorResume(InternalErrorException.class, e -> handleInternalError(response, e))
                .subscribeOn(Schedulers.elastic());
        }
    }

    private Mono<Void> post(HttpServerRequest request, HttpServerResponse response, String contentType, MailboxSession session) {
        InputStream content = ReactorUtils.toInputStream(request.receive().asByteBuffer());
        return Mono.from(metricFactory.runPublishingTimerMetric("JMAP-upload-post",
            handle(contentType, content, session, response)));
    }

    private Mono<Void> handle(String contentType, InputStream content, MailboxSession mailboxSession, HttpServerResponse response) {
        return uploadContent(contentType, content, mailboxSession)
            .flatMap(storedContent -> {
                try {
                    return response.header(CONTENT_TYPE, JSON_CONTENT_TYPE_UTF8)
                        .status(CREATED)
                        .sendString(Mono.just(objectMapper.writeValueAsString(storedContent)))
                        .then();
                } catch (JsonProcessingException e) {
                    throw new InternalErrorException("Error serializing upload response", e);
                }
            });
    }

    private Mono<UploadResponse> uploadContent(String contentType, InputStream inputStream, MailboxSession session) {
        return toBytesArray(inputStream)
            .map(bytes -> Attachment.builder()
                .bytes(bytes)
                .type(contentType)
                .build())
            .flatMap(attachment -> Mono.from(attachmentManager.storeAttachment(attachment, session))
                .thenReturn(UploadResponse.builder()
                    .blobId(attachment.getAttachmentId().getId())
                    .type(attachment.getType())
                    .size(attachment.getSize())
                    .build()));
    }

    private Mono<byte[]> toBytesArray(InputStream inputStream) {
        return Mono.fromCallable(() -> {
            try {
                return ByteStreams.toByteArray(inputStream);
            } catch (IOException e) {
                if (e instanceof EOFException) {
                    throw new CancelledUploadException();
                } else {
                    throw new InternalErrorException("Error while uploading content", e);
                }
            }
        });
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
