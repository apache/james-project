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
package org.apache.james.jmap.draft;

import java.util.Optional;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

import org.apache.james.core.Username;
import org.apache.james.jmap.draft.api.SimpleTokenManager;
import org.apache.james.jmap.draft.exceptions.UnauthorizedException;
import org.apache.james.jmap.draft.model.AttachmentAccessToken;
import org.apache.james.jmap.draft.utils.DownloadPath;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;

import com.google.common.annotations.VisibleForTesting;

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
    public MailboxSession createMailboxSession(HttpServletRequest httpRequest) {

        return getAccessToken(httpRequest)
            .filter(tokenManager::isValid)
            .map(AttachmentAccessToken::getUsername)
            .map(Username::of)
            .map(mailboxManager::createSystemSession)
            .orElseThrow(UnauthorizedException::new);
    }

    private Optional<AttachmentAccessToken> getAccessToken(HttpServletRequest httpRequest) {
        try {
            return Optional.of(AttachmentAccessToken.from(httpRequest.getParameter(AUTHENTICATION_PARAMETER), getBlobId(httpRequest)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private String getBlobId(HttpServletRequest httpRequest) {
        String pathInfo = httpRequest.getPathInfo();
        return DownloadPath.from(pathInfo).getBlobId();
    }
}
