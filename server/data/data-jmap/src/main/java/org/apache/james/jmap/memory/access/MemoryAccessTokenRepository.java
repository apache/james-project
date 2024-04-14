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

package org.apache.james.jmap.memory.access;

import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.commons.collections4.map.PassiveExpiringMap;
import org.apache.james.core.Username;
import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.jmap.api.access.AccessTokenRepository;
import org.apache.james.jmap.api.access.exceptions.InvalidAccessToken;

import com.google.common.base.Preconditions;

import reactor.core.publisher.Mono;

public class MemoryAccessTokenRepository implements AccessTokenRepository {
    private final PassiveExpiringMap<AccessToken, Username> tokensExpirationDates;

    @Inject
    public MemoryAccessTokenRepository(@Named(TOKEN_EXPIRATION_IN_MS) long durationInMilliseconds) {
        tokensExpirationDates = new PassiveExpiringMap<>(durationInMilliseconds);
    }

    @Override
    public Mono<Void> addToken(Username username, AccessToken accessToken) {
        Preconditions.checkNotNull(username);
        Preconditions.checkNotNull(accessToken);
        synchronized (tokensExpirationDates) {
            tokensExpirationDates.put(accessToken, username);
        }
        return Mono.empty();
    }

    @Override
    public Mono<Void> removeToken(AccessToken accessToken) {
        Preconditions.checkNotNull(accessToken);
        synchronized (tokensExpirationDates) {
            tokensExpirationDates.remove(accessToken);
        }
        return Mono.empty();
    }

    @Override
    public Mono<Username> getUsernameFromToken(AccessToken accessToken) throws InvalidAccessToken {
        Preconditions.checkNotNull(accessToken);
        synchronized (tokensExpirationDates) {
            return Mono.just(
                Optional.ofNullable(tokensExpirationDates.get(accessToken))
                    .orElseThrow(() -> new InvalidAccessToken(accessToken)));
        }
    }

}
