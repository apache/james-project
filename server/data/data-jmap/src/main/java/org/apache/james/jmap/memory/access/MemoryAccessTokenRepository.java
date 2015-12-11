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

import com.google.common.base.Preconditions;
import org.apache.commons.collections4.map.PassiveExpiringMap;
import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.jmap.api.access.AccessTokenRepository;
import org.apache.james.jmap.api.access.exceptions.AccessTokenAlreadyStored;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class MemoryAccessTokenRepository implements AccessTokenRepository {

    private final PassiveExpiringMap<AccessToken, Boolean> tokensExpirationDates;

    @Inject
    public MemoryAccessTokenRepository(long durationInMilliseconds) {
        tokensExpirationDates = new PassiveExpiringMap<>(durationInMilliseconds);
    }

    @Override
    public void addToken(AccessToken accessToken) throws AccessTokenAlreadyStored{
        Preconditions.checkNotNull(accessToken);
        synchronized (tokensExpirationDates) {
            if (tokensExpirationDates.putIfAbsent(accessToken, true) != null) {
                throw new AccessTokenAlreadyStored(accessToken);
            }
        }
    }

    @Override
    public void removeToken(AccessToken accessToken) {
        Preconditions.checkNotNull(accessToken);
        synchronized (tokensExpirationDates) {
            tokensExpirationDates.remove(accessToken);
        }
    }

    @Override
    public boolean verifyToken(AccessToken accessToken) {
        Preconditions.checkNotNull(accessToken);
        synchronized (tokensExpirationDates) {
            return tokensExpirationDates.containsKey(accessToken);
        }
    }

}
