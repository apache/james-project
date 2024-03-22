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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import jakarta.inject.Inject;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicHeaderValueParser;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import reactor.netty.http.server.HttpServerRequest;

public class VersionParser {
    private static final String JMAP_VERSION_HEADER = "jmapVersion";

    private final Map<String, Version> supportedVersions;
    private final JMAPConfiguration jmapConfiguration;

    @Inject
    public VersionParser(Set<Version> supportedVersions, JMAPConfiguration jmapConfiguration) {
        this.jmapConfiguration = jmapConfiguration;
        Preconditions.checkArgument(supportedVersions.contains(jmapConfiguration.getDefaultVersion()),
                "%s is not a supported JMAP version", jmapConfiguration);

        this.supportedVersions = supportedVersions.stream()
            .collect(ImmutableMap.toImmutableMap(version -> version.asString().toLowerCase(Locale.US), Function.identity()));
    }

    public Set<Version> getSupportedVersions() {
        return ImmutableSet.copyOf(supportedVersions.values());
    }

    @VisibleForTesting
    Version parse(String version) {
        Preconditions.checkNotNull(version);

        return Optional.ofNullable(supportedVersions.get(version.toLowerCase(Locale.US)))
            .orElseThrow(() -> new IllegalArgumentException(version + " is not a supported version"));
    }

    Version parseRequestVersionHeader(HttpServerRequest request) {
        return acceptParameters(request)
            .filter(nameValuePair -> nameValuePair.getName().equals(JMAP_VERSION_HEADER))
            .map(NameValuePair::getValue)
            .map(this::parse)
            .findFirst()
            .orElse(jmapConfiguration.getDefaultVersion());
    }

    private Stream<NameValuePair> acceptParameters(HttpServerRequest request) {
        return extractValueParameters(request.requestHeaders()
            .get(ACCEPT));
    }

    private Stream<NameValuePair> extractValueParameters(String value) {
        return Optional.ofNullable(value)
            .map(nonNull -> Arrays.stream(BasicHeaderValueParser.parseParameters(value, BasicHeaderValueParser.INSTANCE)))
            .orElse(Stream.empty());
    }
}
