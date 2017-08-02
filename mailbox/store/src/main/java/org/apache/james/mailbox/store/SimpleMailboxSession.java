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
import org.slf4j.Logger;

/**
 * Describes a mailbox session.
 */
public class SimpleMailboxSession implements MailboxSession, MailboxSession.User {

    private final Collection<String> sharedSpaces;

    private final String otherUsersSpace;

    private final String personalSpace;
    
    private final long sessionId;
    
    private final Logger log;

    private final String userName;
    
    private final String password;
    
    private boolean open = true;

    private final List<Locale> localePreferences;

    private final Map<Object, Object> attributes;
    
    private final char pathSeparator;

    private final SessionType type;

    
    public SimpleMailboxSession(long sessionId, String userName, String password,
            final Logger log, List<Locale> localePreferences, char pathSeparator, SessionType type) {
        this(sessionId, userName, password, log, localePreferences, new ArrayList<>(), null, pathSeparator, type);
    }

    public SimpleMailboxSession(long sessionId, String userName, String password,
            final Logger log, List<Locale> localePreferences, List<String> sharedSpaces, String otherUsersSpace, char pathSeparator, SessionType type) {
        this.sessionId = sessionId;
        this.log = log;
        this.userName = userName;
        this.password = password;
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
    
    /**
     * @see org.apache.james.mailbox.MailboxSession#getLog()
     */
    public Logger getLog() {
        return log;
    }

    /**
     * @see org.apache.james.mailbox.MailboxSession#close()
     */
    public void close() {
        open = false;
    }

    /**
     * @see org.apache.james.mailbox.MailboxSession#getSessionId()
     */
    public long getSessionId() {
        return sessionId;
    }

    /**
     * @see org.apache.james.mailbox.MailboxSession#isOpen()
     */
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
    public User getUser() {
        return this;
    }
    
    /**
     * Gets the name of the user executing this session.
     * 
     * @return not null
     */
    public String getUserName() {
        return userName;
    }

    /**
     * @see org.apache.james.mailbox.MailboxSession#getOtherUsersSpace()
     */
    public String getOtherUsersSpace() {
        return otherUsersSpace;
    }

    /**
     * @see org.apache.james.mailbox.MailboxSession#getPersonalSpace()
     */
    public String getPersonalSpace() {
        return personalSpace;
    }

    /**
     * @see org.apache.james.mailbox.MailboxSession#getSharedSpaces()
     */
    public Collection<String> getSharedSpaces() {
        return sharedSpaces;
    }

    /**
     * @see org.apache.james.mailbox.MailboxSession.User#getLocalePreferences()
     */
    public List<Locale> getLocalePreferences() {
        return localePreferences;
    }

    /**
     * @see org.apache.james.mailbox.MailboxSession#getAttributes()
     */
    public Map<Object, Object> getAttributes() {
        return attributes;
    }

    /**
     * @see org.apache.james.mailbox.MailboxSession.User#getPassword()
     */
    public String getPassword() {
        return password;
    }

    /**
     * @see org.apache.james.mailbox.MailboxSession#getPathDelimiter()
     */
    public char getPathDelimiter() {
        return pathSeparator;
    }

    /**
     * @see org.apache.james.mailbox.MailboxSession#getType()
     */
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
