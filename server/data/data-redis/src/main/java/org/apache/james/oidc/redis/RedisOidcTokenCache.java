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

package org.apache.james.oidc.redis;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.apache.james.oidc.Aud;
import org.apache.james.oidc.OidcTokenCache;
import org.apache.james.oidc.OidcTokenCacheConfiguration;
import org.apache.james.oidc.Sid;
import org.apache.james.oidc.Token;
import org.apache.james.oidc.TokenInfo;
import org.apache.james.oidc.TokenInfoResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.hash.Hashing;

import io.lettuce.core.KeyValue;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class RedisOidcTokenCache implements OidcTokenCache {
    public static class TokenParseException extends RuntimeException {
        public TokenParseException(String message, Throwable cause) {
            super(message, cause);
        }

        public TokenParseException(String message) {
            super(message);
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisOidcTokenCache.class);

    private static final String AUD_DELIMITER = ";";

    public interface TokenFields {
        String EMAIL = "email";
        String EXP = "exp";
        String AUD = "aud";
        String SID = "sid";
    }

    private final RedisTokenCacheCommands redisCommands;
    private final TokenInfoResolver tokenInfoResolver;
    private final OidcTokenCacheConfiguration tokenCacheConfiguration;
    private final RedisOidcTokenCacheKeyPrefix keyPrefix;

    public RedisOidcTokenCache(TokenInfoResolver tokenInfoResolver,
                               OidcTokenCacheConfiguration oidcTokenCacheConfiguration,
                               RedisTokenCacheCommands redisCommands,
                               RedisOidcTokenCacheKeyPrefix keyPrefix) {
        this.tokenInfoResolver = tokenInfoResolver;
        this.tokenCacheConfiguration = oidcTokenCacheConfiguration;
        this.redisCommands = redisCommands;
        this.keyPrefix = keyPrefix;
    }

    @Override
    public Mono<Void> invalidate(Sid sid) {
        String sidRedisKey = resolveSidRedisKey(sid);
        return redisCommands.lrange(sidRedisKey)
            .collectList()
            .filter(tokenRedisKeyList -> !tokenRedisKeyList.isEmpty())
            .flatMap(tokenRedisKeyList -> redisCommands.del(tokenRedisKeyList.toArray(String[]::new)))
            .then(redisCommands.del(sidRedisKey))
            .then()
            .onErrorResume(error -> {
                LOGGER.warn("Failed to invalidate token cache for sid={}", sid.value(), error);
                return Mono.empty();
            });
    }

    @Override
    public Mono<TokenInfo> associatedInformation(Token token) {
        return Mono.fromCallable(() -> resolveTokenRedisKey(token))
            .flatMap(tokenRedisKey -> getTokenInfoFromCache(tokenRedisKey)
                .onErrorResume(error -> {
                    LOGGER.warn("Failed to get token information from cache for redisKey={}", tokenRedisKey, error);
                    return Mono.empty();
                })
                .switchIfEmpty(Mono.defer(() -> resolveTokenInfoAndCache(token, tokenRedisKey))));
    }

    public Mono<TokenInfo> getTokenInfoFromCache(String tokenRedisKey) {
        return redisCommands.hgetall(tokenRedisKey)
            .collectMap(KeyValue::getKey, KeyValue::getValue)
            .filter(mapData -> !mapData.isEmpty())
            .map(this::parseTokenInfo);
    }

    private TokenInfo parseTokenInfo(Map<String, String> mapData) throws TokenParseException {
        String rawEmail = mapData.get(TokenFields.EMAIL);
        String rawSid = mapData.get(TokenFields.SID);
        String rawExp = mapData.get(TokenFields.EXP);
        String rawAud = mapData.get(TokenFields.AUD);

        if (StringUtils.isBlank(rawEmail) || StringUtils.isBlank(rawExp)) {
            throw new TokenParseException("Missing required fields in token data: " + mapData);
        }
        try {
            Optional<Sid> sid = Optional.ofNullable(rawSid)
                .filter(StringUtils::isNotBlank)
                .map(Sid::new);
            Instant exp = Instant.ofEpochSecond(Long.parseLong(rawExp));

            Optional<List<Aud>> maybeAudList = Optional.ofNullable(rawAud)
                .filter(StringUtils::isNotBlank)
                .map(value -> Splitter.on(AUD_DELIMITER)
                    .omitEmptyStrings()
                    .splitToStream(value)
                    .map(Aud::new)
                    .toList());

            return new TokenInfo(rawEmail, sid, exp, maybeAudList);
        } catch (Exception e) {
            throw new TokenParseException("Failed to parse token fields from cache: " + mapData, e);
        }
    }

    private Mono<TokenInfo> resolveTokenInfoAndCache(Token token, String tokenRedisKey) {
        return tokenInfoResolver.apply(token)
            .publishOn(Schedulers.parallel())
            .flatMap(tokenInfo -> cacheTokenInfo(tokenRedisKey, tokenInfo)
                .thenReturn(tokenInfo));
    }

    private Mono<Void> cacheTokenInfo(String tokenRedisKey, TokenInfo tokenInfo) {
        return cacheAssociatedInformation(tokenRedisKey, tokenInfo)
            .then(Mono.justOrEmpty(tokenInfo.sid())
                .flatMap(sidValue -> cacheSidInfo(sidValue, tokenRedisKey)))
            .then()
            .onErrorResume(error -> {
                LOGGER.warn("Failed to cache token info: {}", tokenInfo.asString(), error);
                return Mono.empty();
            });
    }

    private Mono<Void> cacheAssociatedInformation(String tokenRedisKey, TokenInfo tokenInfo) {
        Map<String, String> mapData = ImmutableMap.of(
            TokenFields.EMAIL, tokenInfo.email(),
            TokenFields.EXP, String.valueOf(tokenInfo.exp().getEpochSecond()),
            TokenFields.AUD, tokenInfo.aud()
                .map(audList -> Joiner.on(AUD_DELIMITER).skipNulls().join(Lists.transform(audList, Aud::value)))
                .orElse(StringUtils.EMPTY),
            TokenFields.SID, tokenInfo.sid().map(Sid::value).orElse(StringUtils.EMPTY));

        return redisCommands.hset(tokenRedisKey, mapData)
            .then(redisCommands.expire(tokenRedisKey, tokenCacheConfiguration.expiration()));
    }

    private Mono<String> cacheSidInfo(Sid sid, String tokenRedisKey) {
        return Mono.fromCallable(() -> resolveSidRedisKey(sid))
            .flatMap(sidRedisKey -> redisCommands.rpush(sidRedisKey, tokenRedisKey)
                .then(redisCommands.expire(sidRedisKey, tokenCacheConfiguration.expiration()))
                .thenReturn(sidRedisKey));
    }

    private String resolveTokenRedisKey(Token token) {
        return keyPrefix.tokenPrefix() + Hashing.sha512().hashString(token.value(), StandardCharsets.UTF_8);
    }

    private String resolveSidRedisKey(Sid sid) {
        return keyPrefix.sidPrefix() + sid.value();
    }
}
