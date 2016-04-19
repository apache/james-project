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

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import javax.inject.Inject;

import org.apache.james.jmap.api.ContinuationTokenManager;
import org.apache.james.jmap.model.ContinuationToken;
import org.apache.james.util.date.ZonedDateTimeProvider;

import com.google.common.base.Preconditions;

public class SignedContinuationTokenManager implements ContinuationTokenManager {

    private final SignatureHandler signatureHandler;
    private final ZonedDateTimeProvider zonedDateTimeProvider;

    @Inject
    public SignedContinuationTokenManager(SignatureHandler signatureHandler, ZonedDateTimeProvider zonedDateTimeProvider) {
        this.signatureHandler = signatureHandler;
        this.zonedDateTimeProvider = zonedDateTimeProvider;
    }

    @Override
    public ContinuationToken generateToken(String username) {
        Preconditions.checkNotNull(username);
        ZonedDateTime expirationTime = zonedDateTimeProvider.get().plusMinutes(15);
        return new ContinuationToken(username,
            expirationTime,
            signatureHandler.sign(username + ContinuationToken.SEPARATOR + DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(expirationTime)));
    }

    @Override
    public ContinuationTokenStatus getValidity(ContinuationToken token) {
        Preconditions.checkNotNull(token);
        if (! isCorrectlySigned(token)) {
            return ContinuationTokenStatus.INVALID;
        }
        if (isExpired(token)) {
            return ContinuationTokenStatus.EXPIRED;
        }
        return ContinuationTokenStatus.OK;
    }
    
    @Override
    public boolean isValid(ContinuationToken token) {
        Preconditions.checkNotNull(token);
        return ContinuationTokenStatus.OK.equals(getValidity(token));
    }

    private boolean isCorrectlySigned(ContinuationToken token) {
        return signatureHandler.verify(token.getContent(), token.getSignature());
    }

    private boolean isExpired(ContinuationToken token) {
        return token.getExpirationDate().isBefore(zonedDateTimeProvider.get());
    }
}
