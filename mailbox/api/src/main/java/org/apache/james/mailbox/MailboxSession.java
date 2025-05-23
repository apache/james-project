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

package org.apache.james.mailbox;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.apache.james.core.Username;

import com.google.common.base.MoreObjects;

/**
 * Mailbox session.
 */
public class MailboxSession {

    public static class SessionId {

        public static SessionId of(long sessionId) {
            return new SessionId(sessionId);
        }

        private final long sessionId;

        private SessionId(long sessionId) {
            this.sessionId = sessionId;
        }

        public long getValue() {
            return sessionId;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof SessionId) {
                SessionId that = (SessionId) o;

                return Objects.equals(this.sessionId, that.sessionId);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(sessionId);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("sessionId", sessionId)
                .toString();
        }
    }

    /**
     * Id which will be used for a System session
     */
    public static long SYSTEM_SESSION_ID = 0L;

    public static boolean isPrimaryAccount(MailboxSession mailboxSession) {
        return mailboxSession.loggedInUser
            .map(loggedInUser -> loggedInUser.equals(mailboxSession.getUser()))
            .orElse(false);
    }

    public enum SessionType {
        /**
         * Session was created via the System
         */
        System,
        
        /**
         * Session belongs to a specific user which was authenticated somehow
         */
        User
    }

    private final SessionId sessionId;
    private final Username userName;
    private final Optional<Username> loggedInUser;
    private final List<Locale> localePreferences;
    private final Map<Object, Object> attributes;
    private final char pathSeparator;
    private final SessionType type;

    public MailboxSession(SessionId sessionId, Username userName, Optional<Username> loggedInUser,
                                List<Locale> localePreferences, char pathSeparator, SessionType type) {
        this.sessionId = sessionId;
        this.userName = userName;
        this.type = type;

        this.localePreferences = localePreferences;
        this.attributes = new HashMap<>();
        this.pathSeparator = pathSeparator;
        this.loggedInUser = loggedInUser;
    }

    /**
     * Return if the {@link MailboxSession} is of type {@link SessionType#User} or {@link SessionType#System}
     */
    public SessionType getType() {
        return type;
    }
    
    /**
     * Gets the session ID.
     */
    public SessionId getSessionId() {
        return sessionId;
    }

    /**
     * Gets the user executing this session.
     * 
     * @return not null
     */
    public Username getUser() {
        return userName;
    }

    public Optional<Username> getLoggedInUser() {
        return loggedInUser;
    }

    public boolean isDelegated() {
        return loggedInUser.map(user -> !userName.equals(user)).orElse(false);
    }

    /**
     * Gets acceptable localisation for this user in preference order.<br>
     * When localising a phrase, each <code>Locale</code> should be tried in
     * order until an appropriate translation is obtained.
     *
     * @return not null, when empty the default local should be used
     */
    public List<Locale> getLocalePreferences() {
        return localePreferences;
    }

    /**
     * Return the stored attributes for this {@link MailboxSession}.
     */
    public Map<Object, Object> getAttributes() {
        return attributes;
    }

    /**
     * Return server side, folder path separator
     */
    public char getPathDelimiter() {
        return pathSeparator;
    }

    /**
     * Renders suitably for logging.
     *
     * @return a <code>String</code> representation of this object.
     */
    public String toString() {
        String tab = " ";

        return "MailboxSession ( " + "sessionId = "
            + this.sessionId + " )";
    }
}
