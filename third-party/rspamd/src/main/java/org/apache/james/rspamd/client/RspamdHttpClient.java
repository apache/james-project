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

import static org.apache.james.rspamd.client.RspamdClientConfiguration.DEFAULT_TIMEOUT_IN_SECONDS;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

import javax.inject.Inject;
import javax.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.rspamd.exception.RspamdUnexpectedException;
import org.apache.james.rspamd.exception.UnauthorizedException;
import org.apache.james.rspamd.model.AnalysisResult;
import org.apache.james.server.core.MimeMessageInputStream;
import org.apache.james.util.ReactorUtils;
import org.apache.mailet.AttributeName;
import org.apache.mailet.Mail;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.github.fge.lambdas.Throwing;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;

import io.netty.buffer.Unpooled;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.ByteBufMono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientResponse;

public class RspamdHttpClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(RspamdHttpClient.class);

    public static class Options {
        public static final String DELIVER_TO_HEADER = "Deliver-To";
        public static final Options NONE = new Options(Optional.empty());

        public static Options forUser(Username username) {
            return new Options(username);
        }

        public static Options forMailAddress(MailAddress username) {
            return new Options(Username.fromMailAddress(username));
        }

        private final Optional<Username> username;

        public Options(Optional<Username> username) {
            this.username = username;
        }

        public Options(Username username) {
            this.username = Optional.of(username);
        }

        private HttpClient decorate(HttpClient httpClient) {
            return username.map(user -> httpClient.headers(h -> h.add(DELIVER_TO_HEADER, user.asString())))
                .orElse(httpClient);
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof Options) {
                Options options = (Options) o;
                return Objects.equal(username, options.username);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hashCode(username);
        }
    }

    public static final String CHECK_V2_ENDPOINT = "/checkV2";
    public static final String LEARN_SPAM_ENDPOINT = "/learnspam";
    public static final String LEARN_HAM_ENDPOINT = "/learnham";
    private static final int OK = 200;
    private static final int NO_CONTENT = 204;
    private static final int UNAUTHORIZED = 401;
    private static final int BUFFER_SIZE = 16384;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Inject
    public RspamdHttpClient(RspamdClientConfiguration configuration) {
        httpClient = buildReactorNettyHttpClient(configuration);
        this.objectMapper = new ObjectMapper().registerModule(new Jdk8Module());
    }

    public Mono<AnalysisResult> checkV2(Mail mail) throws MessagingException {
        return checkV2(mail, Options.NONE);
    }

    public Mono<AnalysisResult> checkV2(Mail mail, Options options) throws MessagingException {
        return options.decorate(httpClient)
            .headers(headers -> transportInformationToHeaders(mail, headers))
            .post()
            .uri(CHECK_V2_ENDPOINT)
            .send(ReactorUtils.toChunks(new MimeMessageInputStream(mail.getMessage()), BUFFER_SIZE)
                .map(Unpooled::wrappedBuffer))
            .responseSingle(this::checkMailHttpResponseHandler)
            .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER);
    }

    public Mono<Void> reportAsSpam(Publisher<ByteBuffer> content) {
        return reportMail(content, LEARN_SPAM_ENDPOINT, Options.NONE);
    }

    public Mono<Void> reportAsSpam(Publisher<ByteBuffer> content, Options options) {
        return reportMail(content, LEARN_SPAM_ENDPOINT, options);
    }

    public Mono<Void> reportAsHam(Publisher<ByteBuffer> content) {
        return reportMail(content, LEARN_HAM_ENDPOINT, Options.NONE);
    }

    public Mono<Void> reportAsHam(Publisher<ByteBuffer> content, Options options) {
        return reportMail(content, LEARN_HAM_ENDPOINT, options);
    }

    // CF https://rspamd.com/doc/architecture/protocol.html#http-headers
    // Adding SMTP transport information improves Rspamd accuracy
    private void transportInformationToHeaders(Mail mail, io.netty.handler.codec.http.HttpHeaders headers) {
        // IP: Defines IP from which this message is received.
        Optional.ofNullable(mail.getRemoteAddr()).ifPresent(ip -> headers.add("IP", ip));

        // HELO: Defines SMTP helo
        mail.getAttribute(Mail.SMTP_HELO)
            .map(attr -> attr.getValue().value())
            .filter(String.class::isInstance)
            .map(String.class::cast)
            .ifPresent(helo -> headers.add("HELO", helo));

        // From: Defines SMTP mail from command data
        mail.getMaybeSender().asOptional().ifPresent(from -> headers.add("From", from.asString()));

        // Rcpt: Defines SMTP recipient (there may be several Rcpt headers)
        Optional.ofNullable(mail.getRecipients()).orElse(ImmutableList.of())
            .forEach(rcpt -> headers.add("Rcpt", rcpt.asString()));

        // User: Defines username for authenticated SMTP client.
        mail.getAttribute(Mail.SMTP_AUTH_USER)
            .or(() -> mail.getAttribute(AttributeName.of("org.apache.james.jmap.send.MailMetaData.username")))
            .map(attr -> attr.getValue().value())
            .filter(String.class::isInstance)
            .map(String.class::cast)
            .ifPresent(user -> headers.add("User", user));
    }

    private HttpClient buildReactorNettyHttpClient(RspamdClientConfiguration configuration) {
        return HttpClient.create()
            .disableRetry(true)
            .responseTimeout(Duration.ofSeconds(configuration.getTimeout().orElse(DEFAULT_TIMEOUT_IN_SECONDS)))
            .baseUrl(configuration.getUrl().toString())
            .headers(headers -> headers.add("Password", configuration.getPassword()));
    }

    private Mono<Void> reportMail(Publisher<ByteBuffer> content, String endpoint, Options options) {
        return options.decorate(httpClient)
            .post()
            .uri(endpoint)
            .send(Flux.from(content).map(Unpooled::wrappedBuffer))
            .responseSingle(this::reportMailHttpResponseHandler);
    }

    private Mono<AnalysisResult> checkMailHttpResponseHandler(HttpClientResponse httpClientResponse, ByteBufMono byteBufMono) {
        switch (httpClientResponse.status().code()) {
            case OK:
                return byteBufMono.asString(StandardCharsets.UTF_8)
                    .map(Throwing.function(this::convertToAnalysisResult));
            case UNAUTHORIZED:
                return byteBufMono.asString(StandardCharsets.UTF_8)
                    .flatMap(responseBody -> Mono.error(() -> new UnauthorizedException(responseBody)));
            default:
                return byteBufMono.asString(StandardCharsets.UTF_8)
                    .flatMap(responseBody -> Mono.error(() -> new RspamdUnexpectedException(responseBody)));
        }
    }

    private Mono<Void> reportMailHttpResponseHandler(HttpClientResponse httpClientResponse, ByteBufMono byteBufMono) {
        switch (httpClientResponse.status().code()) {
            case NO_CONTENT:
            case OK:
                return Mono.empty();
            case UNAUTHORIZED:
                return byteBufMono.asString(StandardCharsets.UTF_8)
                    .flatMap(responseBody -> Mono.error(() -> new UnauthorizedException(responseBody)));
            default:
                return byteBufMono.asString(StandardCharsets.UTF_8)
                    .flatMap(responseBody -> {
                        if (responseBody.contains(" has been already learned as ham, ignore it")) {
                            LOGGER.debug(responseBody);
                            return Mono.empty();
                        }
                        if (responseBody.contains(" has been already learned as spam, ignore it")) {
                            LOGGER.debug(responseBody);
                            return Mono.empty();
                        }
                        return Mono.error(() -> new RspamdUnexpectedException(responseBody));
                    });
        }
    }

    private AnalysisResult convertToAnalysisResult(String json) throws JsonProcessingException {
        return objectMapper.readValue(json, AnalysisResult.class);
    }

}
