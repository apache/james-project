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
package org.apache.james.mailbox.mock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import org.apache.james.mailbox.MailboxSession;

public class MockMailboxSession implements MailboxSession {
    private final User user;
    private final Map<Object, Object> attrs = new HashMap<>();
    private static final Random RANDOM = new Random();
    private final SessionId sessionId;
    private final SessionType type;
    private boolean open;
    
    public MockMailboxSession(String username) {
        this(username, SessionId.of(RANDOM.nextLong()));
    }

    public MockMailboxSession(String username, SessionId sessionId) {
        this.user = new User() {

            @Override
            public String getUserName() {
                return username;
            }

            @Override
            public boolean isSameUser(String other) {
                if (username == null) {
                    return other == null;
                }
                return username.equalsIgnoreCase(other);
            }
        };
        this.sessionId = sessionId;
        this.open = true;
        type = SessionType.User;
    }

    @Override
    public List<Locale> getLocalePreferences() {
        return new ArrayList<>();
    }

    @Override
    public void close() {
        this.open = false;
    }

    @Override
    public Map<Object, Object> getAttributes() {
        return attrs;
    }

    @Override
    public String getOtherUsersSpace() {
        return null;
    }

    @Override
    public String getPersonalSpace() {
        return "";
    }

    @Override
    public SessionId getSessionId() {
        return sessionId;
    }

    @Override
    public Collection<String> getSharedSpaces() {
        return new ArrayList<>();
    }

    @Override
    public User getUser() {
        return user;
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public char getPathDelimiter() {
        return '.';
    }

    @Override
    public SessionType getType() {
        return type;
    }
}
