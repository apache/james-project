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
package org.apache.james.jmap.http;

import static org.apache.james.jmap.http.DownloadRoutes.BLOB_ID_PATH_PARAM;

import javax.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.jmap.draft.api.SimpleTokenManager;
import org.apache.james.jmap.draft.exceptions.UnauthorizedException;
import org.apache.james.jmap.draft.model.AttachmentAccessToken;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;

import com.google.common.annotations.VisibleForTesting;

import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerRequest;

public class QueryParameterAccessTokenAuthenticationStrategy implements AuthenticationStrategy {
    private static final String AUTHENTICATION_PARAMETER = "access_token";

    private final SimpleTokenManager tokenManager;
    private final MailboxManager mailboxManager;

    @Inject
    @VisibleForTesting
    QueryParameterAccessTokenAuthenticationStrategy(SimpleTokenManager tokenManager, MailboxManager mailboxManager) {
        this.tokenManager = tokenManager;
        this.mailboxManager = mailboxManager;
    }

    @Override
    public Mono<MailboxSession> createMailboxSession(HttpServerRequest httpRequest) {
        return getAccessToken(httpRequest)
            .filter(tokenManager::isValid)
            .map(AttachmentAccessToken::getUsername)
            .map(Username::of)
            .map(mailboxManager::createSystemSession)
            .switchIfEmpty(Mono.error(new UnauthorizedException()));
    }

    private Mono<AttachmentAccessToken> getAccessToken(HttpServerRequest httpRequest) {
        try {
            return Mono.justOrEmpty(httpRequest.param(BLOB_ID_PATH_PARAM))
                .map(blobId -> AttachmentAccessToken.from(httpRequest.param(AUTHENTICATION_PARAMETER), blobId));
        } catch (IllegalArgumentException e) {
            return Mono.empty();
        }
    }
}
