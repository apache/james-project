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

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.commons.lang.NotImplementedException;
import org.apache.james.jmap.model.AuthenticatedProtocolRequest;
import org.apache.james.jmap.model.ProtocolResponse;
import org.apache.james.mailbox.MailboxSession;
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

    public Stream<ProtocolResponse> handle(AuthenticatedProtocolRequest request) {
        return Optional.ofNullable(methods.get(request.getMethodName()))
                        .map(extractAndProcess(request))
                        .map(jmapResponseWriter::formatMethodResponse)
                        .orElseThrow(() -> new IllegalStateException("unknown method"));
    }
    
    private Function<Method, Stream<JmapResponse>> extractAndProcess(AuthenticatedProtocolRequest request) {
        MailboxSession mailboxSession = request.getMailboxSession();
        return (Method method) -> {
                    try {
                        JmapRequest jmapRequest = jmapRequestParser.extractJmapRequest(request, method.requestType());
                        return method.process(jmapRequest, request.getClientId(), mailboxSession);
                    } catch (IOException e) {
                        LOGGER.error("Error occured while parsing the request.", e);
                        if (e.getCause() instanceof NotImplementedException) {
                            return error(request, "Not yet implemented");
                        }
                        return error(request, "invalidArguments");
                    } catch (NotImplementedException e) {
                        return error(request, "Not yet implemented");
                    }
                };
    }

    private Stream<JmapResponse> error(AuthenticatedProtocolRequest request, String message) {
        return Stream.of(JmapResponse.builder().clientId(request.getClientId()).error(message).build());
    }
}
