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

import java.util.stream.Stream;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

import org.apache.james.jmap.exceptions.MailboxSessionCreationException;
import org.apache.james.jmap.exceptions.NoValidAuthHeaderException;
import org.apache.james.jmap.utils.HeadersAuthenticationExtractor;
import org.apache.james.jwt.JwtTokenVerifier;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;

import com.google.common.annotations.VisibleForTesting;

public class JWTAuthenticationStrategy implements AuthenticationStrategy {

    @VisibleForTesting static final String AUTHORIZATION_HEADER_PREFIX = "Bearer ";
    private final JwtTokenVerifier tokenManager;
    private final MailboxManager mailboxManager;
    private final HeadersAuthenticationExtractor authenticationExtractor;

    @Inject
    @VisibleForTesting
    JWTAuthenticationStrategy(JwtTokenVerifier tokenManager, MailboxManager mailboxManager, HeadersAuthenticationExtractor authenticationExtractor) {
        this.tokenManager = tokenManager;
        this.mailboxManager = mailboxManager;
        this.authenticationExtractor = authenticationExtractor;
    }

    @Override
    public MailboxSession createMailboxSession(HttpServletRequest httpRequest) throws MailboxSessionCreationException, NoValidAuthHeaderException {

        Stream<String> userLoginStream = extractTokensFromAuthHeaders(authenticationExtractor.authHeaders(httpRequest))
                .filter(tokenManager::verify)
                .map(tokenManager::extractLogin);

        Stream<MailboxSession> mailboxSessionStream = userLoginStream
                .map(l -> {
                    try {
                        return mailboxManager.createSystemSession(l);
                    } catch (MailboxException e) {
                        throw new MailboxSessionCreationException(e);
                    }
                });

        return mailboxSessionStream
                .findFirst()
                .orElseThrow(NoValidAuthHeaderException::new);
    }

    private Stream<String> extractTokensFromAuthHeaders(Stream<String> authHeaders) {
        return authHeaders
                .filter(h -> h.startsWith(AUTHORIZATION_HEADER_PREFIX))
                .map(h -> h.substring(AUTHORIZATION_HEADER_PREFIX.length()));
    }
}
