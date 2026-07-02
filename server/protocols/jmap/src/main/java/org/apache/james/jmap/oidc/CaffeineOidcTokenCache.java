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

import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;
import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class CaffeineOidcTokenCache implements OidcTokenCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(CaffeineOidcTokenCache.class);
    private static final long DEFAULT_TOKEN_CACHE_MAX_SIZE = 10_000;

    private final AsyncLoadingCache<Token, TokenInfo> cacheToken;
    private final SetMultimap<Sid, Token> sidToTokens = Multimaps.synchronizedSetMultimap(HashMultimap.create());

    @Inject
    public CaffeineOidcTokenCache(TokenInfoResolver tokenInfoResolver, OidcTokenCacheConfiguration configuration) {
        AsyncCacheLoader<Token, TokenInfo> cacheLoader = (token, executor) -> tokenInfoResolver.apply(token)
            .map(tokenInfo -> {
                tokenInfo.sid().ifPresentOrElse(sidValue -> sidToTokens.put(sidValue, token),
                    () -> LOGGER.warn("OIDC token of user {} does not have a sid, this will break backchannel logout. Please review OIDC configuration.", tokenInfo.email()));
                return tokenInfo;
            })
            .subscribeOn(Schedulers.fromExecutor(executor))
            .toFuture();

        cacheToken = Caffeine.newBuilder()
            .expireAfterWrite(configuration.expiration())
            .maximumSize(configuration.tokenCacheMaxSize().orElse(DEFAULT_TOKEN_CACHE_MAX_SIZE))
            .removalListener((Token token, TokenInfo tokenInfo, RemovalCause cause) -> {
                if (cause.wasEvicted() && tokenInfo != null) {
                    tokenInfo.sid().ifPresent(sid -> sidToTokens.remove(sid, token));
                }
            })
            .buildAsync(cacheLoader);
    }

    @Override
    public Mono<Void> invalidate(Sid sid) {
        List<Token> snapshot;
        synchronized (sidToTokens) {
            snapshot = List.copyOf(sidToTokens.get(sid));
        }
        return Mono.fromRunnable(() -> cacheToken.synchronous().invalidateAll(snapshot))
            .subscribeOn(Schedulers.boundedElastic())
            .then(Mono.fromRunnable(() -> sidToTokens.removeAll(sid)))
            .then();
    }

    @Override
    public Mono<TokenInfo> associatedInformation(Token token) {
        return Mono.fromFuture(cacheToken.get(token));
    }

    @VisibleForTesting
    Optional<Username> getUsernameFromCache(Token token) {
        return Optional.ofNullable(cacheToken.synchronous().getIfPresent(token))
            .map(TokenInfo::email)
            .map(Username::of);
    }
}
