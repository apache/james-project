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

import javax.inject.Inject;

import org.apache.commons.lang.NotImplementedException;
import org.apache.james.jmap.methods.JmapResponse.Builder;
import org.apache.james.jmap.model.AuthenticatedProtocolRequest;
import org.apache.james.jmap.model.ProtocolResponse;
import org.apache.james.mailbox.MailboxSession;

public class RequestHandler {

    private final JmapRequestParser jmapRequestParser;
    private final JmapResponseWriter jmapResponseWriter;
    private final Map<String, Method> methods;

    @Inject
    public RequestHandler(Set<Method> methods, JmapRequestParser jmapRequestParser, JmapResponseWriter jmapResponseWriter) {
        this.jmapRequestParser = jmapRequestParser;
        this.jmapResponseWriter = jmapResponseWriter;
        this.methods = methods.stream()
                .collect(Collectors.toMap(Method::methodName, method -> method));
    }

    public ProtocolResponse handle(AuthenticatedProtocolRequest request) {
        Builder responseBuilder = JmapResponse.forRequest(request);
        return Optional.ofNullable(methods.get(request.getMethod()))
                        .map(extractAndProcess(request, responseBuilder))
                        .map(jmapResponseWriter::formatMethodResponse)
                        .orElseThrow(() -> new IllegalStateException("unknown method"));
    }
    
    private Function<Method, JmapResponse> extractAndProcess(AuthenticatedProtocolRequest request, JmapResponse.Builder responseBuilder) {
        MailboxSession mailboxSession = request.getMailboxSession();
        return (Method method) -> {
                    try {
                        JmapRequest jmapRequest = jmapRequestParser.extractJmapRequest(request, method.requestType());
                        return method.process(jmapRequest, mailboxSession, responseBuilder);
                    } catch (IOException e) {
                        if (e.getCause() instanceof NotImplementedException) {
                            return responseBuilder.error("Not yet implemented").build();
                        }
                        return responseBuilder.error("invalidArguments").build();
                    }
                };
        
    }
}
