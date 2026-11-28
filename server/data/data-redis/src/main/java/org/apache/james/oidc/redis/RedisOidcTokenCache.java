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

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.james.oidc.Aud;
import org.apache.james.oidc.LoggedOutSessionException;
import org.apache.james.oidc.OidcTokenCache;
import org.apache.james.oidc.OidcTokenCacheConfiguration;
import org.apache.james.oidc.Sid;
import org.apache.james.oidc.Token;
import org.apache.james.oidc.TokenInfo;
import org.apache.james.oidc.TokenInfoResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
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
    private static final String ABSENT_AUD = "-";
    // Token keys are the token prefix followed by a hexadecimal hash: this suffix cannot collide with them.
    private static final String LOGGED_OUT_SID_SUFFIX = "logged_out_sid_";

    public interface TokenFields {
        String EMAIL = "email";
        String EXP = "exp";
        // Each audience is URL encoded and prefixed by the delimiter, so that the list reads back as resolved.
        // The former "aud" field, joining the audiences, is not read: such entries are cache misses.
        String AUD_LIST = "aud-list";
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
        // The mark is written before the sid list is read, and a token is pushed to the sid list before its mark is
        // checked: a token missed by this read is refused by resolveTokenInfoAndCache.
        return markLoggedOut(sid)
            .then(redisCommands.lrange(sidRedisKey)
                .collectList())
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
            // Entries written before the audience list field are read as a cache miss
            .filter(mapData -> mapData.containsKey(TokenFields.AUD_LIST))
            .map(this::parseTokenInfo);
    }

    private TokenInfo parseTokenInfo(Map<String, String> mapData) throws TokenParseException {
        String rawEmail = mapData.get(TokenFields.EMAIL);
        String rawSid = mapData.get(TokenFields.SID);
        String rawExp = mapData.get(TokenFields.EXP);
        String rawAud = mapData.get(TokenFields.AUD_LIST);

        if (StringUtils.isBlank(rawEmail) || StringUtils.isBlank(rawExp)) {
            throw new TokenParseException("Missing required fields in token data: " + mapData);
        }
        try {
            Optional<Sid> sid = Optional.ofNullable(rawSid)
                .filter(StringUtils::isNotBlank)
                .map(Sid::new);
            Instant exp = Instant.ofEpochSecond(Long.parseLong(rawExp));

            Optional<List<Aud>> maybeAudList = parseAudList(rawAud);

            return new TokenInfo(rawEmail, sid, exp, maybeAudList);
        } catch (Exception e) {
            throw new TokenParseException("Failed to parse token fields from cache: " + mapData, e);
        }
    }

    private static Optional<List<Aud>> parseAudList(String rawAud) {
        if (rawAud.equals(ABSENT_AUD)) {
            return Optional.empty();
        }
        if (rawAud.isEmpty()) {
            return Optional.of(List.of());
        }
        if (!rawAud.startsWith(AUD_DELIMITER)) {
            throw new IllegalArgumentException("Invalid audience list " + rawAud);
        }
        return Optional.of(Splitter.on(AUD_DELIMITER)
            .splitToStream(rawAud.substring(AUD_DELIMITER.length()))
            .map(value -> new Aud(URLDecoder.decode(value, StandardCharsets.UTF_8)))
            .toList());
    }

    private static String serializeAudList(Optional<List<Aud>> audList) {
        return audList
            .map(list -> list.stream()
                .map(aud -> AUD_DELIMITER + URLEncoder.encode(aud.value(), StandardCharsets.UTF_8))
                .collect(Collectors.joining()))
            .orElse(ABSENT_AUD);
    }

    private Mono<TokenInfo> resolveTokenInfoAndCache(Token token, String tokenRedisKey) {
        return tokenInfoResolver.apply(token)
            .publishOn(Schedulers.parallel())
            .flatMap(tokenInfo -> cacheTokenInfo(tokenRedisKey, tokenInfo)
                .then(refuseIfLoggedOut(tokenRedisKey, tokenInfo))
                .thenReturn(tokenInfo));
    }

    private Mono<Void> markLoggedOut(Sid sid) {
        return redisCommands.set(resolveLoggedOutSidRedisKey(sid), "1", tokenCacheConfiguration.expiration())
            .onErrorResume(error -> {
                LOGGER.warn("Failed to record the logout of the OIDC session {}", sid.value(), error);
                return Mono.empty();
            });
    }

    private Mono<Void> refuseIfLoggedOut(String tokenRedisKey, TokenInfo tokenInfo) {
        return Mono.justOrEmpty(tokenInfo.sid())
            .filterWhen(sid -> redisCommands.exists(resolveLoggedOutSidRedisKey(sid))
                .onErrorResume(error -> {
                    LOGGER.warn("Failed to check whether the OIDC session {} was logged out", sid.value(), error);
                    return Mono.just(false);
                }))
            .flatMap(sid -> redisCommands.del(tokenRedisKey)
                .onErrorResume(error -> {
                    LOGGER.warn("Failed to evict the token of the logged out OIDC session {} from the cache", sid.value(), error);
                    return Mono.empty();
                })
                .then(Mono.error(new LoggedOutSessionException(sid))));
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
            TokenFields.AUD_LIST, serializeAudList(tokenInfo.aud()),
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

    private String resolveLoggedOutSidRedisKey(Sid sid) {
        return keyPrefix.tokenPrefix() + LOGGED_OUT_SID_SUFFIX + sid.value();
    }
}
