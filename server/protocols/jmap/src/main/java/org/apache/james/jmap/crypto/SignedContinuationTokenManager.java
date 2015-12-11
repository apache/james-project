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

import com.google.common.base.Preconditions;
import org.apache.james.jmap.api.ContinuationTokenManager;
import org.apache.james.jmap.model.ContinuationToken;
import org.apache.james.jmap.utils.ZonedDateTimeProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SignatureException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class SignedContinuationTokenManager implements ContinuationTokenManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(SignedContinuationTokenManager.class);

    private final SignatureHandler signatureHandler;
    private final ZonedDateTimeProvider zonedDateTimeProvider;

    public SignedContinuationTokenManager(SignatureHandler signatureHandler, ZonedDateTimeProvider zonedDateTimeProvider) {
        this.signatureHandler = signatureHandler;
        this.zonedDateTimeProvider = zonedDateTimeProvider;
    }

    @Override
    public ContinuationToken generateToken(String username) throws Exception {
        Preconditions.checkNotNull(username);
        ZonedDateTime expirationTime = zonedDateTimeProvider.provide().plusMinutes(15);
        return new ContinuationToken(username,
            expirationTime,
            signatureHandler.sign(username + ContinuationToken.SEPARATOR + DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(expirationTime)));
    }

    @Override
    public boolean isValid(ContinuationToken token) throws Exception {
        Preconditions.checkNotNull(token);
        try {
            return ! isTokenOutdated(token)
                && isCorrectlySigned(token);
        } catch (SignatureException e) {
            LOGGER.warn("Attempt to use a malformed signature for user " + token.getUsername(), e);
            return false;
        }
    }

    private boolean isCorrectlySigned(ContinuationToken token) throws Exception {
        return signatureHandler.verify(token.getUsername()
            + ContinuationToken.SEPARATOR
            + DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(token.getExpirationDate()),
            token.getSignature());
    }

    private boolean isTokenOutdated(ContinuationToken token) {
        return token.getExpirationDate().isBefore(zonedDateTimeProvider.provide());
    }
}
