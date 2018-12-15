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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.MailboxConstants;

/**
 * Describes a mailbox session.
 */
public class SimpleMailboxSession implements MailboxSession, MailboxSession.User {

    private final Collection<String> sharedSpaces;

    private final String otherUsersSpace;

    private final String personalSpace;
    
    private final SessionId sessionId;

    private final String userName;
    
    private boolean open = true;

    private final List<Locale> localePreferences;

    private final Map<Object, Object> attributes;
    
    private final char pathSeparator;

    private final SessionType type;

    
    public SimpleMailboxSession(SessionId sessionId, String userName,
                                List<Locale> localePreferences, char pathSeparator, SessionType type) {
        this(sessionId, userName, localePreferences, new ArrayList<>(), null, pathSeparator, type);
    }

    public SimpleMailboxSession(SessionId sessionId, String userName,
                                List<Locale> localePreferences, List<String> sharedSpaces, String otherUsersSpace, char pathSeparator, SessionType type) {
        this.sessionId = sessionId;
        this.userName = userName;
        this.otherUsersSpace = otherUsersSpace;
        this.sharedSpaces = sharedSpaces;
        this.type = type;
        if (otherUsersSpace == null && (sharedSpaces == null || sharedSpaces.isEmpty())) {
            this.personalSpace = "";
        } else {
            this.personalSpace = MailboxConstants.USER_NAMESPACE;
        }

        this.localePreferences = localePreferences;
        this.attributes = new HashMap<>();
        this.pathSeparator = pathSeparator;
    }


    @Override
    public void close() {
        open = false;
    }

    @Override
    public SessionId getSessionId() {
        return sessionId;
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    /**
     * Renders suitably for logging.
     * 
     * @return a <code>String</code> representation of this object.
     */
    public String toString() {
        final String TAB = " ";

        return "MailboxSession ( " + "sessionId = "
                + this.sessionId + TAB + "open = " + this.open + TAB + " )";
    }
    
    /**
     * Gets the user executing this session.
     * @return not null
     */
    @Override
    public User getUser() {
        return this;
    }
    
    /**
     * Gets the name of the user executing this session.
     * 
     * @return not null
     */
    @Override
    public String getUserName() {
        return userName;
    }

    @Override
    public String getOtherUsersSpace() {
        return otherUsersSpace;
    }

    @Override
    public String getPersonalSpace() {
        return personalSpace;
    }

    @Override
    public Collection<String> getSharedSpaces() {
        return sharedSpaces;
    }

    @Override
    public List<Locale> getLocalePreferences() {
        return localePreferences;
    }

    /**
     * @see org.apache.james.mailbox.MailboxSession#getAttributes()
     */
    @Override
    public Map<Object, Object> getAttributes() {
        return attributes;
    }

    @Override
    public char getPathDelimiter() {
        return pathSeparator;
    }

    @Override
    public SessionType getType() {
        return type;
    }

    @Override
    public boolean isSameUser(String username) {
        if (this.userName == null) {
            return username == null;
        }
        return this.userName.equalsIgnoreCase(username);
    }

}
