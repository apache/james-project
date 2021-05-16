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

import javax.inject.Inject;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicHeaderValueParser;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import reactor.netty.http.server.HttpServerRequest;

public class VersionParser {
    private static final String JMAP_VERSION_HEADER = "jmapVersion";

    private final Set<Version> supportedVersions;
    private final JMAPConfiguration jmapConfiguration;

    @Inject
    public VersionParser(Set<Version> supportedVersions, JMAPConfiguration jmapConfiguration) {
        this.jmapConfiguration = jmapConfiguration;
        Preconditions.checkArgument(supportedVersions.contains(jmapConfiguration.getDefaultVersion()),
                "%s is not a supported JMAP version", jmapConfiguration);

        this.supportedVersions = supportedVersions;
    }

    public Set<Version> getSupportedVersions() {
        return supportedVersions;
    }

    @VisibleForTesting
    Version parse(String version) {
        Preconditions.checkNotNull(version);

        return supportedVersions.stream()
            .filter(jmapVersion -> jmapVersion.asString().equalsIgnoreCase(version))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(version + " is not a supported version"));
    }

    Version parseRequestVersionHeader(HttpServerRequest request) {
        return asVersion(request)
            .filter(nameValuePair -> nameValuePair.getName().equals(JMAP_VERSION_HEADER))
            .map(NameValuePair::getValue)
            .map(this::parse)
            .findFirst()
            .orElse(jmapConfiguration.getDefaultVersion());
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
