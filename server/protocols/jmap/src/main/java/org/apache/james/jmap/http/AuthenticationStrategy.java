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

import java.util.Optional;

import org.apache.james.mailbox.MailboxSession;

import com.google.common.base.Preconditions;

import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerRequest;

public interface AuthenticationStrategy {
    Mono<MailboxSession> createMailboxSession(HttpServerRequest httpRequest);

    Mono<MailboxSession> createMailboxSession(Authenticator.Authorization authorization);

    String AUTHORIZATION_HEADERS = "Authorization";

    default Optional<Authenticator.Authorization> authHeaders(HttpServerRequest httpRequest) {
        Preconditions.checkArgument(httpRequest != null, "'httpRequest' is mandatory");
        Preconditions.checkArgument(httpRequest.requestHeaders().getAll(AUTHORIZATION_HEADERS).size() <= 1, "Only one set of credential is allowed");

        return Optional.ofNullable(httpRequest.requestHeaders().get(AUTHORIZATION_HEADERS))
            .map(Authenticator.Authorization::of);
    }
}
