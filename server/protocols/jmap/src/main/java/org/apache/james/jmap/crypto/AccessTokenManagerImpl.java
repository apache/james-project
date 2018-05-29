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

package org.apache.james.jmap.crypto;

import java.util.concurrent.CompletionException;

import javax.inject.Inject;

import org.apache.james.jmap.api.AccessTokenManager;
import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.jmap.api.access.AccessTokenRepository;
import org.apache.james.jmap.api.access.exceptions.InvalidAccessToken;

import com.google.common.base.Preconditions;

public class AccessTokenManagerImpl implements AccessTokenManager {

    private final AccessTokenRepository accessTokenRepository;

    @Inject
    public AccessTokenManagerImpl(AccessTokenRepository accessTokenRepository) {
        this.accessTokenRepository = accessTokenRepository;
    }

    @Override
    public AccessToken grantAccessToken(String username) {
        Preconditions.checkNotNull(username);
        AccessToken accessToken = AccessToken.generate();
        accessTokenRepository.addToken(username, accessToken).join();
        return accessToken;
    }

    @Override
    public String getUsernameFromToken(AccessToken token) throws InvalidAccessToken {
        try {
            return accessTokenRepository.getUsernameFromToken(token).join();
        } catch (CompletionException completionException) {
            if (completionException.getCause() instanceof InvalidAccessToken) {
                throw (InvalidAccessToken) completionException.getCause();
            } else {
                throw new RuntimeException(completionException);
            }
        }
    }
    
    @Override
    public boolean isValid(AccessToken token) throws InvalidAccessToken {
        try {
            getUsernameFromToken(token);
            return true;
        } catch (InvalidAccessToken e) {
            return false;
        }
    }

    @Override
    public void revoke(AccessToken token) {
        accessTokenRepository.removeToken(token).join();
    }

}
