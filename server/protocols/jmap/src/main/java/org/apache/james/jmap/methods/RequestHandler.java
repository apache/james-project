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

package org.apache.james.jmap.methods;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.james.core.User;
import org.apache.james.jmap.JmapFieldNotSupportedException;
import org.apache.james.jmap.model.AuthenticatedProtocolRequest;
import org.apache.james.jmap.model.ProtocolResponse;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.util.MDCBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    public Stream<ProtocolResponse> handle(AuthenticatedProtocolRequest request) throws IOException {
        Optional<MailboxSession> mailboxSession = Optional.ofNullable(request.getMailboxSession());
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addContext(MDCBuilder.USER, mailboxSession.map(MailboxSession::getUser).map(User::asString))
                     .addContext(MDCBuilder.SESSION_ID, mailboxSession.map(MailboxSession::getSessionId))
                     .addContext(MDCBuilder.ACTION, request.getMethodName().getName())
                     .build()) {
            return Optional.ofNullable(methods.get(request.getMethodName()))
                .map(extractAndProcess(request))
                .map(jmapResponseWriter::formatMethodResponse)
                .orElseThrow(() -> new IllegalStateException("unknown method " + request.getMethodName()));
        }
    }
    
    private Function<Method, Stream<JmapResponse>> extractAndProcess(AuthenticatedProtocolRequest request) {
        MailboxSession mailboxSession = request.getMailboxSession();
        return (Method method) -> {
                    try {
                        JmapRequest jmapRequest = jmapRequestParser.extractJmapRequest(request, method.requestType());
                        return method.process(jmapRequest, request.getClientId(), mailboxSession);
                    } catch (IOException e) {
                        LOGGER.error("Error occured while parsing the request.", e);
                        if (e.getCause() instanceof JmapFieldNotSupportedException) {
                            return errorNotImplemented((JmapFieldNotSupportedException) e.getCause(), request);
                        }
                        return error(request, generateInvalidArgumentError(e.getMessage()));
                    } catch (JmapFieldNotSupportedException e) {
                        return errorNotImplemented(e, request);
                    }
                };
    }

    public ErrorResponse generateInvalidArgumentError(String description) {
        return ErrorResponse.builder()
            .type("invalidArguments")
            .description(description)
            .build();
    }

    private Stream<JmapResponse> errorNotImplemented(JmapFieldNotSupportedException error, AuthenticatedProtocolRequest request) {
        return Stream.of(
                JmapResponse.builder()
                    .clientId(request.getClientId())
                    .error(generateInvalidArgumentError("The field '" + error.getField() + "' of '" + error.getIssuer() + "' is not supported"))
                    .build());
    }

    private Stream<JmapResponse> error(AuthenticatedProtocolRequest request, ErrorResponse error) {
        return Stream.of(
                JmapResponse.builder()
                    .clientId(request.getClientId())
                    .error(error)
                    .build());
    }
}
