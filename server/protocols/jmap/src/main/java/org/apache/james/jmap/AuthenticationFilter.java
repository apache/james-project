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

import java.io.IOException;
import java.util.Optional;

import javax.inject.Inject;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.james.jmap.api.AccessTokenManager;
import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.jmap.api.access.exceptions.NotAnUUIDException;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.BadCredentialsException;
import org.apache.james.mailbox.exception.MailboxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

public class AuthenticationFilter implements Filter {
    
    private static final Logger LOG = LoggerFactory.getLogger(AuthenticationFilter.class);
    public static final String MAILBOX_SESSION = "mailboxSession";

    private final AccessTokenManager accessTokenManager;
    private final MailboxManager mailboxManager;

    @Inject
    public AuthenticationFilter(AccessTokenManager accessTokenManager, MailboxManager mailboxManager) {
        this.accessTokenManager = accessTokenManager;
        this.mailboxManager = mailboxManager;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        Optional<String> authHeader = Optional.ofNullable(httpRequest.getHeader("Authorization"));
        if (!checkAuthorizationHeader(authHeader)) {
            httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        addSessionToRequest(httpRequest, httpResponse, authHeader);

        chain.doFilter(httpRequest, response);
    }

    private void addSessionToRequest(HttpServletRequest httpRequest, HttpServletResponse httpResponse, Optional<String> authHeader) throws IOException {
        try {
            MailboxSession mailboxSession = createMailboxSession(authHeader);
            httpRequest.setAttribute(MAILBOX_SESSION, mailboxSession);
        } catch (MailboxException e) {
            httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }

    @VisibleForTesting MailboxSession createMailboxSession(Optional<String> authHeader) throws BadCredentialsException, MailboxException {
        String username = authHeader
            .map(AccessToken::fromString)
            .map(accessTokenManager::getUsernameFromToken)
            .orElseThrow(() -> new BadCredentialsException());
        return mailboxManager.createSystemSession(username, LOG);
    }

    private boolean checkAuthorizationHeader(Optional<String> authHeader) throws IOException {
        try {
            return authHeader
                    .map(AccessToken::fromString)
                    .map(accessTokenManager::isValid)
                    .orElse(false);
        } catch (NotAnUUIDException e) {
            return false;
        }
    }

    @Override
    public void destroy() {
    }

}
