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
import java.util.Set;
import java.util.stream.Stream;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicHeaderValueParser;

import com.google.common.collect.ImmutableSet;

import reactor.netty.http.server.HttpServerRequest;

public class JMAPRoutesHandler {
    private static String JMAP_VERSION_HEADER = "jmapVersion";

    private final Version version;
    private final Set<JMAPRoutes> routes;

    public JMAPRoutesHandler(Version version, JMAPRoutes... routes) {
        this.version = version;
        this.routes = ImmutableSet.copyOf(routes);
    }

    Stream<JMAPRoute> routes(HttpServerRequest request) {
        if (matches(request)) {
            return routes.stream()
                .flatMap(JMAPRoutes::routes);
        }
        return Stream.of();
    }

    private boolean matches(HttpServerRequest request) {
        return version.equals(extractRequestVersionHeader(request));
    }

    private Version extractRequestVersionHeader(HttpServerRequest request) {
        return asVersion(request)
            .filter(nameValuePair -> nameValuePair.getName().equals(JMAP_VERSION_HEADER))
            .map(NameValuePair::getValue)
            .map(Version::of)
            .findFirst()
            .orElse(Version.DRAFT);
    }

    private Stream<NameValuePair> asVersion(HttpServerRequest request) {
        return request.requestHeaders()
            .getAll(ACCEPT)
            .stream()
            .flatMap(this::extractValueParameters);
    }

    private Stream<NameValuePair> extractValueParameters(String value) {
        return Arrays.stream(BasicHeaderValueParser.parseParameters(value, BasicHeaderValueParser.INSTANCE));
    }
}
