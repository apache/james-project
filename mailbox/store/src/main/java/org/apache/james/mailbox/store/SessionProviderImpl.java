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

package org.apache.james.mailbox.store;

import java.util.ArrayList;
import java.util.Optional;
import java.util.function.Predicate;

import javax.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.mailbox.Authenticator;
import org.apache.james.mailbox.Authorizator;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionIdGenerator;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.exception.BadCredentialsException;
import org.apache.james.mailbox.exception.ForbiddenDelegationException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.UserDoesNotExistException;
import org.apache.james.mailbox.model.MailboxConstants;

public class SessionProviderImpl implements SessionProvider {
    private final MailboxSessionIdGenerator idGenerator;
    private final Authenticator authenticator;
    private final Authorizator authorizator;

    @Inject
    public SessionProviderImpl(Authenticator authenticator, Authorizator authorizator) {
        this.idGenerator = new RandomMailboxSessionIdGenerator();
        this.authenticator = authenticator;
        this.authorizator = authorizator;
    }

    @Override
    public MailboxSession createSystemSession(Username userName) {
        return createSession(userName, Optional.empty(), MailboxSession.SessionType.System);
    }

    @Override
    public AuthorizationStep authenticate(Username loginUsername, String passwd) {
        return new AuthorizationStep() {
            @Override
            public MailboxSession as(Username otherUserId) throws MailboxException {
                Optional<Username> loggedInUser = isValidLogin(loginUsername, passwd);
                if (loggedInUser.isEmpty()) {
                    throw new BadCredentialsException();
                }
                return authenticate(loggedInUser.get()).as(otherUserId);
            }

            @Override
            public MailboxSession withoutDelegation() throws MailboxException {
                Optional<Username> loggedInUser = isValidLogin(loginUsername, passwd);
                if (loggedInUser.isPresent()) {
                    return createSession(loggedInUser.get(), loggedInUser, MailboxSession.SessionType.User);
                } else {
                    throw new BadCredentialsException();
                }
            }

            @Override
            public MailboxSession forMatchingUser(Predicate<Username> otherPredicate) throws MailboxException {
                Username loggedInUser = isValidLogin(loginUsername, passwd)
                    .orElseThrow(BadCredentialsException::new);

                return authorizator.delegatedUsers(loggedInUser)
                    .stream()
                    .filter(otherPredicate)
                    .findFirst()
                    .map(otherUserId -> createSession(otherUserId, Optional.of(loggedInUser), MailboxSession.SessionType.System))
                    .orElseThrow(() -> new ForbiddenDelegationException(loggedInUser));
            }
        };
    }

    @Override
    public AuthorizationStep authenticate(Username givenUserid) {
        return new AuthorizationStep() {
            @Override
            public MailboxSession as(Username otherUserId) throws MailboxException {
                Authorizator.AuthorizationState authorizationState = authorizator.user(givenUserid).canLoginAs(otherUserId);
                switch (authorizationState) {
                    case ALLOWED:
                        return createSession(otherUserId, Optional.of(givenUserid), MailboxSession.SessionType.System);
                    case FORBIDDEN:
                        throw new ForbiddenDelegationException(givenUserid, otherUserId);
                    case UNKNOWN_USER:
                        throw new UserDoesNotExistException(otherUserId);
                    default:
                        throw new RuntimeException("Unknown AuthorizationState " + authorizationState);
                }
            }

            @Override
            public MailboxSession withoutDelegation() {
                return createSession(givenUserid, Optional.of(givenUserid), MailboxSession.SessionType.System);
            }

            @Override
            public MailboxSession forMatchingUser(Predicate<Username> otherPredicate) throws MailboxException {
                return authorizator.delegatedUsers(givenUserid)
                    .stream()
                    .filter(otherPredicate)
                    .findFirst()
                    .map(otherUserId -> createSession(otherUserId, Optional.of(givenUserid), MailboxSession.SessionType.System))
                    .orElseThrow(() -> new ForbiddenDelegationException(givenUserid));
            }
        };
    }

    private MailboxSession createSession(Username userName, Optional<Username> loggedInUser, MailboxSession.SessionType type) {
        return new MailboxSession(newSessionId(), userName, loggedInUser, new ArrayList<>(), MailboxConstants.DEFAULT_DELIMITER, type);
    }

    private MailboxSession.SessionId newSessionId() {
        return MailboxSession.SessionId.of(randomId());
    }

    private long randomId() {
        return idGenerator.nextId();
    }

    /**
     * Log in the user with the given userid and password
     *
     * @param userid the username
     * @param passwd the password
     * @return success true if login success false otherwise
     */
    private Optional<Username> isValidLogin(Username userid, String passwd) throws MailboxException {
        return authenticator.isAuthentic(userid, passwd);
    }
}
