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

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import jakarta.inject.Inject;

import org.apache.james.jmap.draft.api.SimpleTokenFactory;
import org.apache.james.jmap.draft.model.AttachmentAccessToken;
import org.apache.james.jmap.draft.model.ContinuationToken;
import org.apache.james.util.date.ZonedDateTimeProvider;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

public class SignedTokenFactory implements SimpleTokenFactory {

    private final SignatureHandler signatureHandler;
    private final ZonedDateTimeProvider zonedDateTimeProvider;

    @Inject
    public SignedTokenFactory(SignatureHandler signatureHandler, ZonedDateTimeProvider zonedDateTimeProvider) {
        this.signatureHandler = signatureHandler;
        this.zonedDateTimeProvider = zonedDateTimeProvider;
    }

    @Override
    public ContinuationToken generateContinuationToken(String username) {
        Preconditions.checkNotNull(username);
        ZonedDateTime expirationTime = zonedDateTimeProvider.get().plusMinutes(15);
        return new ContinuationToken(username,
            expirationTime,
            signatureHandler.sign(
                    Joiner.on(ContinuationToken.SEPARATOR)
                        .join(username,
                            DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(expirationTime))));
    }

    @Override
    public AttachmentAccessToken generateAttachmentAccessToken(String username, String blobId) {
        Preconditions.checkArgument(! Strings.isNullOrEmpty(blobId));
        ZonedDateTime expirationTime = zonedDateTimeProvider.get().plusMinutes(5);
        return AttachmentAccessToken.builder()
                .username(username)
                .blobId(blobId)
                .expirationDate(expirationTime)
                .signature(signatureHandler.sign(Joiner.on(AttachmentAccessToken.SEPARATOR)
                                                    .join(blobId,
                                                            username, 
                                                            DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(expirationTime))))
                .build();
    }
}
