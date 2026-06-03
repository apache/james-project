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

package org.apache.james.protocols.sasl;

import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.mailbox.Authenticator;
import org.apache.james.mailbox.Authorizator;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.exception.BadCredentialsException;
import org.apache.james.mailbox.exception.ForbiddenDelegationException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.UserDoesNotExistException;
import org.apache.james.protocols.api.sasl.SaslAuthenticationResult;
import org.apache.james.protocols.api.sasl.SaslAuthenticator;
import org.apache.james.protocols.api.sasl.SaslFailure;
import org.apache.james.protocols.api.sasl.SaslIdentity;

public class JamesSaslAuthenticator implements SaslAuthenticator {
    public static JamesSaslAuthenticator jamesSaslAuthenticator(MailboxManager mailboxManager) {
        Authenticator authenticator = (username, password) -> {
            try {
                return Optional.of(mailboxManager.authenticate(username, password.toString()).withoutDelegation().getUser());
            } catch (BadCredentialsException e) {
                return Optional.empty();
            }
        };
        Authorizator authorizator = (username, otherUsername) -> {
            try {
                mailboxManager.authenticate(username).as(otherUsername);
                return Authorizator.AuthorizationState.ALLOWED;
            } catch (UserDoesNotExistException e) {
                return Authorizator.AuthorizationState.UNKNOWN_USER;
            } catch (ForbiddenDelegationException | BadCredentialsException e) {
                return Authorizator.AuthorizationState.FORBIDDEN;
            }
        };
        return new JamesSaslAuthenticator(authenticator, authorizator);
    }

    private final Authenticator authenticator;
    private final Authorizator authorizator;

    @Inject
    public JamesSaslAuthenticator(Authenticator authenticator, Authorizator authorizator) {
        this.authenticator = authenticator;
        this.authorizator = authorizator;
    }

    public JamesSaslAuthenticator withExtraAuthorizator(Authorizator extraAuthorizator) {
        return new JamesSaslAuthenticator(authenticator, Authorizator.combine(authorizator, extraAuthorizator));
    }

    @Override
    public SaslAuthenticationResult authenticatePassword(Username authenticationId,
                                                         Optional<Username> authorizationId,
                                                         String password) {
        try {
            Optional<Username> authenticatedUser = authenticator.isAuthentic(authenticationId, password);
            if (authenticatedUser.isEmpty()) {
                return failure(SaslFailure.invalidCredentials(authenticationId, authorizationId,
                    "Password authentication failed because of bad credentials."));
            }
            Username targetUser = authorizationId.orElse(authenticatedUser.get());
            return authorize(new SaslIdentity(authenticatedUser.get(), targetUser));
        } catch (MailboxException e) {
            return failure(SaslFailure.serverError(Optional.of(authenticationId), authorizationId, "Authentication failed.", e));
        }
    }

    @Override
    public SaslAuthenticationResult authorize(SaslIdentity identity) {
        if (identity.authenticationId().equals(identity.authorizationId())) {
            return success(identity);
        }

        try {
            return switch (authorizator.user(identity.authenticationId()).canLoginAs(identity.authorizationId())) {
                case ALLOWED -> success(identity);
                case UNKNOWN_USER -> failure(SaslFailure.userDoesNotExist(identity.authenticationId(), identity.authorizationId(),
                    "Delegation target user does not exist."));
                case FORBIDDEN -> failure(SaslFailure.delegationForbidden(identity.authenticationId(), identity.authorizationId(),
                    "Requested delegation is forbidden."));
            };
        } catch (MailboxException e) {
            return failure(SaslFailure.serverError(Optional.of(identity.authenticationId()), Optional.of(identity.authorizationId()),
                "Authentication failed.", e));
        }
    }

    private SaslAuthenticationResult success(SaslIdentity identity) {
        return new SaslAuthenticationResult.Success(identity);
    }

    private SaslAuthenticationResult failure(SaslFailure failure) {
        return new SaslAuthenticationResult.Failure(failure);
    }
}
