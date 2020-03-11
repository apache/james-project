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

import javax.inject.Inject;

import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.jmap.draft.api.AccessTokenManager;
import org.apache.james.jmap.draft.exceptions.NoValidAuthHeaderException;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;

import com.google.common.annotations.VisibleForTesting;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerRequest;

public class AccessTokenAuthenticationStrategy implements AuthenticationStrategy {
    private final AccessTokenManager accessTokenManager;
    private final MailboxManager mailboxManager;

    @Inject
    @VisibleForTesting
    AccessTokenAuthenticationStrategy(AccessTokenManager accessTokenManager, MailboxManager mailboxManager) {
        this.accessTokenManager = accessTokenManager;
        this.mailboxManager = mailboxManager;
    }

    @Override
    public Mono<MailboxSession> createMailboxSession(HttpServerRequest httpRequest) throws NoValidAuthHeaderException {
        return Flux.fromStream(authHeaders(httpRequest))
            .map(AccessToken::fromString)
            .filterWhen(accessTokenManager::isValid)
            .flatMap(accessTokenManager::getUsernameFromToken)
            .map(mailboxManager::createSystemSession)
            .singleOrEmpty();
    }
}
