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
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static org.apache.james.jmap.HttpConstants.TEXT_PLAIN_CONTENT_TYPE;
import static org.apache.james.jmap.JMAPUrls.DOWNLOAD;
import static org.apache.james.jmap.http.LoggingHelper.jmapAction;
import static org.apache.james.jmap.http.LoggingHelper.jmapAuthContext;
import static org.apache.james.jmap.http.LoggingHelper.jmapContext;
import static org.apache.james.util.ReactorUtils.logOnError;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.stream.Stream;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.james.jmap.Endpoint;
import org.apache.james.jmap.JMAPRoute;
import org.apache.james.jmap.JMAPRoutes;
import org.apache.james.jmap.draft.api.SimpleTokenFactory;
import org.apache.james.jmap.draft.exceptions.BadRequestException;
import org.apache.james.jmap.draft.exceptions.InternalErrorException;
import org.apache.james.jmap.draft.model.AttachmentAccessToken;
import org.apache.james.jmap.draft.utils.DownloadPath;
import org.apache.james.jmap.exceptions.BlobNotFoundException;
import org.apache.james.jmap.exceptions.UnauthorizedException;
import org.apache.james.jmap.methods.BlobManager;
import org.apache.james.jmap.model.BlobId;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.ContentType;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.mime4j.codec.EncoderUtil;
import org.apache.james.mime4j.codec.EncoderUtil.Usage;
import org.apache.james.util.ReactorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableList;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpHeaderValidationUtil;
import io.netty.handler.codec.http.HttpMethod;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;

public class DownloadRoutes implements JMAPRoutes {
    private static final Logger LOGGER = LoggerFactory.getLogger(DownloadRoutes.class);
    static final String BLOB_ID_PATH_PARAM = "blobId";
    private static final String NAME_PATH_PARAM = "name";
    private static final String DOWNLOAD_FROM_ID = String.format("%s/{%s}", DOWNLOAD, BLOB_ID_PATH_PARAM);
    private static final String DOWNLOAD_FROM_ID_AND_NAME = String.format("%s/{%s}/{%s}", DOWNLOAD, BLOB_ID_PATH_PARAM, NAME_PATH_PARAM);
    private static final int BUFFER_SIZE = 16 * 1024;

    private final BlobManager blobManager;
    private final SimpleTokenFactory simpleTokenFactory;
    private final MetricFactory metricFactory;
    private final Authenticator authenticator;

    @Inject
    @VisibleForTesting
    DownloadRoutes(BlobManager blobManager, SimpleTokenFactory simpleTokenFactory, MetricFactory metricFactory, @Named(InjectionKeys.DRAFT) Authenticator authenticator) {
        this.blobManager = blobManager;
        this.simpleTokenFactory = simpleTokenFactory;
        this.metricFactory = metricFactory;
        this.authenticator = authenticator;
    }

    @Override
    public Stream<JMAPRoute> routes() {
        return Stream.of(
            JMAPRoute.builder()
                .endpoint(new Endpoint(HttpMethod.POST, DOWNLOAD_FROM_ID))
                .action(this::postFromId)
                .corsHeaders(),
            JMAPRoute.builder()
                .endpoint(new Endpoint(HttpMethod.GET, DOWNLOAD_FROM_ID))
                .action(this::getFromId)
                .corsHeaders(),
            JMAPRoute.builder()
                .endpoint(new Endpoint(HttpMethod.POST, DOWNLOAD_FROM_ID_AND_NAME))
                .action(this::postFromIdAndName)
                .corsHeaders(),
            JMAPRoute.builder()
                .endpoint(new Endpoint(HttpMethod.GET, DOWNLOAD_FROM_ID_AND_NAME))
                .action(this::getFromIdAndName)
                .corsHeaders(),
            JMAPRoute.builder()
                .endpoint(new Endpoint(HttpMethod.OPTIONS, DOWNLOAD_FROM_ID))
                .action(CORS_CONTROL)
                .noCorsHeaders(),
            JMAPRoute.builder()
                .endpoint(new Endpoint(HttpMethod.OPTIONS, DOWNLOAD_FROM_ID_AND_NAME))
                .action(CORS_CONTROL)
                .noCorsHeaders()
        );
    }

    private Mono<Void> postFromId(HttpServerRequest request, HttpServerResponse response) {
        String blobId = request.param(BLOB_ID_PATH_PARAM);
        DownloadPath downloadPath = DownloadPath.ofBlobId(blobId);
        return post(request, response, downloadPath);
    }

    private Mono<Void> postFromIdAndName(HttpServerRequest request, HttpServerResponse response) {
        String blobId = request.param(BLOB_ID_PATH_PARAM);
        String name = request.param(NAME_PATH_PARAM);
        DownloadPath downloadPath = DownloadPath.of(blobId, name);
        return post(request, response, downloadPath);
    }

    private Mono<Void> post(HttpServerRequest request, HttpServerResponse response, DownloadPath downloadPath) {
        return authenticator.authenticate(request)
            .flatMap(session -> Mono.from(metricFactory.decoratePublisherWithTimerMetric("JMAP-download-post",
                    respondAttachmentAccessToken(session, downloadPath, response)))
                .contextWrite(jmapAuthContext(session)))
            .onErrorResume(UnauthorizedException.class, e -> handleAuthenticationFailure(response, LOGGER, e))
            .doOnEach(logOnError(e -> LOGGER.error("Unexpected error", e)))
            .onErrorResume(e -> handleInternalError(response, LOGGER, e))
            .contextWrite(jmapContext(request))
            .contextWrite(jmapAction("download-post"))
            .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER);
    }

    private Mono<Void> getFromId(HttpServerRequest request, HttpServerResponse response) {
        String blobId = request.param(BLOB_ID_PATH_PARAM);
        DownloadPath downloadPath = DownloadPath.ofBlobId(blobId);
        return get(request, response, downloadPath);
    }

    private Mono<Void> getFromIdAndName(HttpServerRequest request, HttpServerResponse response) {
        String blobId = request.param(BLOB_ID_PATH_PARAM);
        try {
            String name = URLDecoder.decode(request.param(NAME_PATH_PARAM), StandardCharsets.UTF_8.toString());
            DownloadPath downloadPath = DownloadPath.of(blobId, name);
            return get(request, response, downloadPath);
        } catch (UnsupportedEncodingException e) {
            throw new BadRequestException("Wrong url encoding", e);
        }
    }

    private Mono<Void> get(HttpServerRequest request, HttpServerResponse response, DownloadPath downloadPath) {
        return authenticator.authenticate(request)
            .flatMap(session -> Mono.from(metricFactory.decoratePublisherWithTimerMetric("JMAP-download-get",
                    download(session, downloadPath, response)))
                .contextWrite(jmapAuthContext(session)))
            .onErrorResume(UnauthorizedException.class, e -> handleAuthenticationFailure(response, LOGGER, e))
            .doOnEach(logOnError(e -> LOGGER.error("Unexpected error", e)))
            .onErrorResume(IllegalArgumentException.class, e -> handleBadRequest(response, LOGGER, e))
            .onErrorResume(e -> handleInternalError(response, LOGGER, e))
            .contextWrite(jmapContext(request))
            .contextWrite(jmapAction("download-get"))
            .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER);
    }

    private Mono<Void> respondAttachmentAccessToken(MailboxSession mailboxSession, DownloadPath downloadPath, HttpServerResponse resp) {
        String blobId = downloadPath.getBlobId();

        return attachmentExists(mailboxSession, blobId)
            .flatMap(exists -> {
                if (exists) {
                    AttachmentAccessToken attachmentAccessToken = simpleTokenFactory.generateAttachmentAccessToken(mailboxSession.getUser().asString(), blobId);
                    byte[] bytes = attachmentAccessToken.serialize().getBytes(StandardCharsets.UTF_8);
                    return resp.header(CONTENT_TYPE, TEXT_PLAIN_CONTENT_TYPE)
                        .status(OK)
                        .header(CONTENT_LENGTH, Integer.toString(bytes.length))
                        .sendByteArray(Mono.just(bytes))
                        .then();
                } else {
                    return resp.status(NOT_FOUND).send();
                }
            });
    }

    private Mono<Boolean> attachmentExists(MailboxSession mailboxSession, String blobId) {
        return Flux.from(blobManager.retrieve(ImmutableList.of(BlobId.of(blobId)), mailboxSession))
            .hasElements();
    }

    @VisibleForTesting
    Mono<Void> download(MailboxSession mailboxSession, DownloadPath downloadPath, HttpServerResponse response) {
        String blobId = downloadPath.getBlobId();

        return Mono.from(blobManager.retrieve(ImmutableList.of(BlobId.of(blobId)), mailboxSession))
            .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
            .switchIfEmpty(Mono.error(() -> new BlobNotFoundException(BlobId.of(blobId))))
            .flatMap(blob -> Mono.usingWhen(
                blob.getStreamReactive(),
                stream -> downloadBlob(downloadPath.getName(), response, blob.getSize(), blob.getContentType(), stream),
                stream -> Mono.fromRunnable(Throwing.runnable(stream::close).sneakyThrow()))
                .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER))
            .onErrorResume(BlobNotFoundException.class, e -> {
                LOGGER.info("Attachment '{}' not found", blobId, e);
                return response.status(NOT_FOUND).send();
            }).onErrorResume(MailboxException.class, e -> Mono.error(new InternalErrorException("Error while downloading", e)));
    }

    private Mono<Void> downloadBlob(Optional<String> optionalName, HttpServerResponse response, long blobSize, ContentType blobContentType, InputStream stream) {
        return addContentDispositionHeader(optionalName, response)
            .header("Content-Length", String.valueOf(blobSize))
            .header(CONTENT_TYPE, sanitizeHeaderValue(blobContentType.asString()))
            .status(OK)
            .send(ReactorUtils.toChunks(stream, BUFFER_SIZE)
                .map(Unpooled::wrappedBuffer)
                .subscribeOn(Schedulers.boundedElastic()))
            .then();
    }

    public String sanitizeHeaderValue(String s) {
        if (HttpHeaderValidationUtil.validateValidHeaderValue(s) == -1) {
            return s;
        }
        return "application/octet-stream";
    }

    private HttpServerResponse addContentDispositionHeader(Optional<String> optionalName, HttpServerResponse resp) {
        return optionalName.map(name -> addContentDispositionHeaderRegardingEncoding(name, resp))
            .orElse(resp);
    }

    private HttpServerResponse addContentDispositionHeaderRegardingEncoding(String name, HttpServerResponse resp) {
        if (CharMatcher.ascii().matchesAllOf(name)) {
            return resp.header("Content-Disposition", "attachment; filename=\"" + name + "\"");
        } else {
            return resp.header("Content-Disposition", "attachment; filename*=\"" + EncoderUtil.encodeEncodedWord(name, Usage.TEXT_TOKEN) + "\"");
        }
    }
}
