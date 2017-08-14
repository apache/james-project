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
package org.apache.james.jmap;

import java.util.Optional;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

import org.apache.james.jmap.api.AccessTokenManager;
import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.jmap.exceptions.MailboxSessionCreationException;
import org.apache.james.jmap.exceptions.NoValidAuthHeaderException;
import org.apache.james.jmap.utils.HeadersAuthenticationExtractor;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

public class AccessTokenAuthenticationStrategy implements AuthenticationStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(AccessTokenAuthenticationStrategy.class);

    private final AccessTokenManager accessTokenManager;
    private final MailboxManager mailboxManager;
    private final HeadersAuthenticationExtractor authenticationExtractor;

    @Inject
    @VisibleForTesting
    AccessTokenAuthenticationStrategy(AccessTokenManager accessTokenManager, MailboxManager mailboxManager, HeadersAuthenticationExtractor authenticationExtractor) {
        this.accessTokenManager = accessTokenManager;
        this.mailboxManager = mailboxManager;
        this.authenticationExtractor = authenticationExtractor;
    }

    @Override
    public MailboxSession createMailboxSession(HttpServletRequest httpRequest) throws MailboxSessionCreationException, NoValidAuthHeaderException {

        Optional<String> username = authenticationExtractor.authHeaders(httpRequest)
            .map(AccessToken::fromString)
            .filter(accessTokenManager::isValid)
            .map(accessTokenManager::getUsernameFromToken)
            .findFirst();

        if (username.isPresent()) {
            try {
                return mailboxManager.createSystemSession(username.get());
            } catch (MailboxException e) {
                throw new MailboxSessionCreationException(e);
            }
        }
        throw new NoValidAuthHeaderException();
    }
}
