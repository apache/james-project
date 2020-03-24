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

package org.apache.james.jmap;

import static io.netty.handler.codec.http.HttpHeaderNames.ACCEPT;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;

import org.reactivestreams.Publisher;

import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;

public class JMAPRoute {
    public interface Action {
        Publisher<Void> handleRequest(HttpServerRequest request, HttpServerResponse response);
    }

    private static final String JMAP_VERSION_HEADER = "jmapVersion=";

    private final Endpoint endpoint;
    private final Version version;
    private final Action action;

    public JMAPRoute(Endpoint endpoint, Version version, Action action) {
        this.endpoint = endpoint;
        this.version = version;
        this.action = action;
    }

    public Endpoint getEndpoint() {
        return endpoint;
    }

    public Version getVersion() {
        return version;
    }

    public Action getAction() {
        return action;
    }

    public boolean matches(HttpServerRequest request) {
        return getVersion().equals(extractRequestVersionHeader(request))
            && getEndpoint().matches(request);
    }

    private Version extractRequestVersionHeader(HttpServerRequest request) {
        return Optional.ofNullable(request.requestHeaders().get(ACCEPT))
                .map(s -> s.split(";"))
                .map(Arrays::stream)
                .orElse(Stream.of())
            .map(value -> value.trim().toLowerCase())
            .filter(value -> value.startsWith(JMAP_VERSION_HEADER.toLowerCase()))
            .map(value -> value.substring(JMAP_VERSION_HEADER.length()))
            .map(Version::of)
            .findFirst()
            .orElse(Version.DRAFT);
    }
}
