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

import javax.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.mailbox.Authenticator;
import org.apache.james.mailbox.Authorizator;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionIdGenerator;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.exception.BadCredentialsException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.NotAdminException;
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
    public char getDelimiter() {
        return MailboxConstants.DEFAULT_DELIMITER;
    }

    @Override
    public MailboxSession createSystemSession(Username userName) {
        return createSession(userName, MailboxSession.SessionType.System);
    }

    @Override
    public MailboxSession login(Username userid, String passwd) throws MailboxException {
        if (isValidLogin(userid, passwd)) {
            return createSession(userid, MailboxSession.SessionType.User);
        } else {
            throw new BadCredentialsException();
        }
    }

    @Override
    public MailboxSession loginAsOtherUser(Username adminUserid, String passwd, Username otherUserId) throws MailboxException {
        if (! isValidLogin(adminUserid, passwd)) {
            throw new BadCredentialsException();
        }
        Authorizator.AuthorizationState authorizationState = authorizator.canLoginAsOtherUser(adminUserid, otherUserId);
        switch (authorizationState) {
            case ALLOWED:
                return createSystemSession(otherUserId);
            case NOT_ADMIN:
                throw new NotAdminException();
            case UNKNOWN_USER:
                throw new UserDoesNotExistException(otherUserId);
            default:
                throw new RuntimeException("Unknown AuthorizationState " + authorizationState);
        }
    }

    @Override
    public void logout(MailboxSession session) {
        if (session != null) {
            session.close();
        }
    }

    private MailboxSession createSession(Username userName, MailboxSession.SessionType type) {
        return new MailboxSession(newSessionId(), userName, new ArrayList<>(), getDelimiter(), type);
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
    private boolean isValidLogin(Username userid, String passwd) throws MailboxException {
        return authenticator.isAuthentic(userid, passwd);
    }
}
