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
package org.apache.james.smtpserver;

import java.util.Optional;

import javax.inject.Inject;
import javax.mail.internet.AddressException;

import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.jwt.OidcJwtTokenVerifier;
import org.apache.james.jwt.introspection.IntrospectionEndpoint;
import org.apache.james.mailbox.Authorizator;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.protocols.api.OIDCSASLParser;
import org.apache.james.protocols.api.OidcSASLConfiguration;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.AuthHook;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Mono;

/**
 * This Auth hook can be used to authenticate against the james user repository
 */
public class UsersRepositoryAuthHook implements AuthHook {
    private static final Logger LOGGER = LoggerFactory.getLogger(UsersRepositoryAuthHook.class);

    private final UsersRepository users;
    private final Authorizator authorizator;

    @Inject
    public UsersRepositoryAuthHook(UsersRepository users,
                                   Authorizator authorizator) {
        this.users = users;
        this.authorizator = authorizator;
    }

    @Override
    public HookResult doAuth(SMTPSession session, Username username, String password) {
        try {
            if (users.test(username, password)) {
                session.setUsername(username);
                session.setRelayingAllowed(true);
                return HookResult.builder()
                    .hookReturnCode(HookReturnCode.ok())
                    .smtpDescription("Authentication Successful")
                    .build();
            }
        } catch (UsersRepositoryException e) {
            LOGGER.info("Unable to access UsersRepository", e);
        }
        return HookResult.DECLINED;
    }

    @Override
    public HookResult doSasl(SMTPSession session, OidcSASLConfiguration configuration, String initialResponse) {
        return OIDCSASLParser.parse(initialResponse)
            .flatMap(oidcInitialResponseValue -> validateToken(configuration, oidcInitialResponseValue.getToken())
                .map(authenticatedUser -> {
                    Username associatedUser = Username.of(oidcInitialResponseValue.getAssociatedUser());
                    if (!associatedUser.equals(authenticatedUser)) {
                        return doAuthWithDelegation(session, authenticatedUser, associatedUser);
                    } else {
                        return saslSuccess(session, authenticatedUser);
                    }
                })
            )
            .orElse(HookResult.DECLINED);
    }

    private HookResult doAuthWithDelegation(SMTPSession session, Username authenticatedUser, Username associatedUser) {
        try {
            if (Authorizator.AuthorizationState.ALLOWED.equals(authorizator.canLoginAsOtherUser(authenticatedUser, associatedUser))) {
                return saslSuccess(session, associatedUser);
            }
        } catch (MailboxException e) {
            LOGGER.info("Unable to authorization", e);
        }
        return HookResult.DECLINED;
    }

    private HookResult saslSuccess(SMTPSession session, Username username) {
        try {
            users.assertValid(username);
            session.setUsername(username);
            session.setRelayingAllowed(true);
            return HookResult.builder()
                .hookReturnCode(HookReturnCode.ok())
                .smtpDescription("Authentication successful.")
                .build();
        } catch (UsersRepositoryException e) {
            LOGGER.warn("Invalid username", e);
            return HookResult.DECLINED;
        }
    }

    private Optional<Username> validateToken(OidcSASLConfiguration oidcSASLConfiguration, String token) {
        return Mono.from(OidcJwtTokenVerifier.verifyWithMaybeIntrospection(token,
                oidcSASLConfiguration.getJwksURL(),
                oidcSASLConfiguration.getClaim(),
                oidcSASLConfiguration.getIntrospectionEndpoint()
                    .map(endpoint -> new IntrospectionEndpoint(endpoint, oidcSASLConfiguration.getIntrospectionEndpointAuthorization()))))
            .blockOptional()
            .flatMap(this::extractUserFromClaim);
    }

    private Optional<Username> extractUserFromClaim(String claimValue) {
        try {
            return Optional.of(Username.fromMailAddress(new MailAddress(claimValue)));
        } catch (AddressException e) {
            return Optional.empty();
        }
    }
}
