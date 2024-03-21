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

import org.apache.james.jmap.draft.api.SimpleTokenManager;
import org.apache.james.jmap.draft.model.SignedExpiringToken;
import org.apache.james.util.date.ZonedDateTimeProvider;

import com.google.common.base.Preconditions;

public class SignedTokenManager implements SimpleTokenManager {

    private final SignatureHandler signatureHandler;
    private final ZonedDateTimeProvider zonedDateTimeProvider;

    @Inject
    public SignedTokenManager(SignatureHandler signatureHandler, ZonedDateTimeProvider zonedDateTimeProvider) {
        this.signatureHandler = signatureHandler;
        this.zonedDateTimeProvider = zonedDateTimeProvider;
    }

    @Override
    public TokenStatus getValidity(SignedExpiringToken token) {
        Preconditions.checkNotNull(token);
        if (! isCorrectlySigned(token)) {
            return TokenStatus.INVALID;
        }
        if (isExpired(token)) {
            return TokenStatus.EXPIRED;
        }
        return TokenStatus.OK;
    }
    
    @Override
    public boolean isValid(SignedExpiringToken token) {
        Preconditions.checkNotNull(token);
        return TokenStatus.OK.equals(getValidity(token));
    }

    private boolean isCorrectlySigned(SignedExpiringToken token) {
        return signatureHandler.verify(token.getSignedContent(), token.getSignature());
    }

    private boolean isExpired(SignedExpiringToken token) {
        return token.getExpirationDate().isBefore(zonedDateTimeProvider.get());
    }
}
