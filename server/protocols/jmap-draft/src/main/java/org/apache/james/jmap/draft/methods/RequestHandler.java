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

package org.apache.james.jmap.draft.methods;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.jmap.draft.exceptions.JmapFieldNotSupportedException;
import org.apache.james.jmap.draft.model.AuthenticatedRequest;
import org.apache.james.jmap.draft.model.InvocationResponse;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.util.MDCBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class RequestHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestHandler.class);

    private final JmapRequestParser jmapRequestParser;
    private final JmapResponseWriter jmapResponseWriter;
    private final Map<Method.Request.Name, Method> methods;

    @Inject
    public RequestHandler(Set<Method> methods, JmapRequestParser jmapRequestParser, JmapResponseWriter jmapResponseWriter) {
        this.jmapRequestParser = jmapRequestParser;
        this.jmapResponseWriter = jmapResponseWriter;
        this.methods = methods.stream()
                .collect(Collectors.toMap(Method::requestHandled, Function.identity()));
    }

    public Flux<InvocationResponse> handle(AuthenticatedRequest request) {
        Optional<MailboxSession> mailboxSession = Optional.ofNullable(request.getMailboxSession());
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addToContextIfPresent(MDCBuilder.USER, mailboxSession.map(MailboxSession::getUser).map(Username::asString))
                     .addToContextIfPresent(MDCBuilder.SESSION_ID, mailboxSession.map(MailboxSession::getSessionId)
                        .map(MailboxSession.SessionId::getValue)
                        .map(l -> Long.toString(l)))
                     .addToContext(MDCBuilder.ACTION, request.getMethodName().getName())
                     .build()) {
            return Optional.ofNullable(methods.get(request.getMethodName()))
                .map(extractAndProcess(request))
                .map(jmapResponseWriter::formatMethodResponse)
                .orElseThrow(() -> new IllegalStateException("unknown method " + request.getMethodName()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    private Function<Method, Flux<JmapResponse>> extractAndProcess(AuthenticatedRequest request) {
        MailboxSession mailboxSession = request.getMailboxSession();
        return (Method method) ->
            Mono.fromCallable(() -> jmapRequestParser.extractJmapRequest(request, method.requestType()))
                .flatMapMany(jmapRequest -> method.process(jmapRequest, request.getMethodCallId(), mailboxSession))
                .onErrorResume(JmapFieldNotSupportedException.class, e -> errorNotImplemented(e, request))
                .onErrorResume(
                    e -> e.getCause() instanceof JmapFieldNotSupportedException,
                    e -> errorNotImplemented((JmapFieldNotSupportedException) e.getCause(), request))
                .onErrorResume(IOException.class, e -> error(request, generateInvalidArgumentError(e.getMessage())));
    }

    public ErrorResponse generateInvalidArgumentError(String description) {
        return ErrorResponse.builder()
            .type("invalidArguments")
            .description(description)
            .build();
    }

    private Flux<JmapResponse> errorNotImplemented(JmapFieldNotSupportedException error, AuthenticatedRequest request) {
        return Flux.just(
                JmapResponse.builder()
                    .methodCallId(request.getMethodCallId())
                    .error(generateInvalidArgumentError("The field '" + error.getField() + "' of '" + error.getIssuer() + "' is not supported"))
                    .build());
    }

    private Flux<JmapResponse> error(AuthenticatedRequest request, ErrorResponse error) {
        return Flux.just(
                JmapResponse.builder()
                    .methodCallId(request.getMethodCallId())
                    .error(error)
                    .build());
    }
}
