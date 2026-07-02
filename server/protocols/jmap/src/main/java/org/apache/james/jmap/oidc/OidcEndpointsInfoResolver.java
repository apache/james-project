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

package org.apache.james.jmap.oidc;

import java.net.URL;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.james.core.Username;
import org.apache.james.jmap.exceptions.UnauthorizedException;
import org.apache.james.jwt.DefaultCheckTokenClient;
import org.apache.james.jwt.introspection.IntrospectionEndpoint;
import org.apache.james.jwt.introspection.TokenIntrospectionResponse;
import org.apache.james.jwt.userinfo.UserinfoResponse;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.streams.Iterators;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Mono;

public class OidcEndpointsInfoResolver implements TokenInfoResolver {
    private static final String SID_PROPERTY = "sid";

    private final DefaultCheckTokenClient checkTokenClient;
    private final MetricFactory metricFactory;
    private final URL userInfoURL;
    private final IntrospectionEndpoint introspectionEndpoint;
    private final JMAPOidcConfiguration configuration;

    @Inject
    public OidcEndpointsInfoResolver(DefaultCheckTokenClient checkTokenClient,
                                     MetricFactory metricFactory,
                                     @Named("userInfo") URL userInfoURL,
                                     IntrospectionEndpoint introspectionEndpoint,
                                     JMAPOidcConfiguration configuration) {
        this.checkTokenClient = checkTokenClient;
        this.metricFactory = metricFactory;
        this.userInfoURL = userInfoURL;
        this.introspectionEndpoint = introspectionEndpoint;
        this.configuration = configuration;
    }

    @Override
    public Mono<TokenInfo> apply(Token token) {
        return Mono.zip(
                Mono.from(metricFactory.decoratePublisherWithTimerMetric("userinfo-lookup", checkTokenClient.userInfo(userInfoURL, token.value()))),
                Mono.from(metricFactory.decoratePublisherWithTimerMetric("introspection-lookup", checkTokenClient.introspect(introspectionEndpoint, token.value()))))
            .flatMap(tokenInfos -> {
                UserinfoResponse userInfo = tokenInfos.getT1();
                TokenIntrospectionResponse introspectInfo = tokenInfos.getT2();

                Username sub = Username.of(userInfo.claimByPropertyName(configuration.getOidcClaim())
                    .orElseThrow(() -> new UnauthorizedException("Invalid OIDC token: userinfo needs to include " + configuration.getOidcClaim() + " claim")));

                return Mono.just(toTokenInfo(sub, userInfo, introspectInfo));
            });
    }

    private TokenInfo toTokenInfo(Username username, UserinfoResponse userinfoResponse, TokenIntrospectionResponse introspectionResponse) {
        return new TokenInfo(
            username.asString(),
            userinfoResponse.claimByPropertyName(SID_PROPERTY).map(Sid::new)
                .or(() -> introspectionResponse.claimByPropertyName(SID_PROPERTY).map(Sid::new)),
            Instant.ofEpochSecond(introspectionResponse.exp().orElseThrow(() -> new UnauthorizedException("Expiration claim ('exp') is required in the token"))),
            extractAudience(introspectionResponse));
    }

    private static Optional<List<Aud>> extractAudience(TokenIntrospectionResponse introspectionResponse) {
        return Optional.ofNullable(introspectionResponse.json().get("aud"))
            .map(audJson -> {
                if (audJson.isArray()) {
                    return Iterators.toStream(audJson.iterator())
                        .map(JsonNode::asText)
                        .map(Aud::new)
                        .toList();
                }
                return ImmutableList.of(new Aud(audJson.asText()));
            });
    }
}
