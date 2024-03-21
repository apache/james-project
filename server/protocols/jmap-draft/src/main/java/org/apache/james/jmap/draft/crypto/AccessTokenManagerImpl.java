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

package org.apache.james.jmap.draft.crypto;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.jmap.api.access.AccessTokenRepository;
import org.apache.james.jmap.api.access.exceptions.InvalidAccessToken;
import org.apache.james.jmap.draft.api.AccessTokenManager;

import com.google.common.base.Preconditions;

import reactor.core.publisher.Mono;

public class AccessTokenManagerImpl implements AccessTokenManager {

    private final AccessTokenRepository accessTokenRepository;

    @Inject
    AccessTokenManagerImpl(AccessTokenRepository accessTokenRepository) {
        this.accessTokenRepository = accessTokenRepository;
    }

    @Override
    public Mono<AccessToken> grantAccessToken(Username username) {
        Preconditions.checkNotNull(username);
        AccessToken accessToken = AccessToken.generate();

        return accessTokenRepository.addToken(username, accessToken)
            .thenReturn(accessToken);
    }

    @Override
    public Mono<Username> getUsernameFromToken(AccessToken token) throws InvalidAccessToken {
        return accessTokenRepository.getUsernameFromToken(token);
    }
    
    @Override
    public Mono<Boolean> isValid(AccessToken token) throws InvalidAccessToken {
        try {
            return getUsernameFromToken(token)
                .thenReturn(true);
        } catch (InvalidAccessToken e) {
            return Mono.just(false);
        }
    }

    @Override
    public Mono<Void> revoke(AccessToken token) {
        return accessTokenRepository.removeToken(token);
    }

}
