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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MockMailboxSession implements MailboxSession{

    private User user;
    private boolean close;
    private Map<Object, Object> attrs = new HashMap<Object, Object>();
    private Logger log = LoggerFactory.getLogger("MockLog");
    private final static Random RANDOM = new Random();

    private long sessionId = RANDOM.nextLong();
    
    public MockMailboxSession(final String username) {
        this.user = new User() {
            
            public String getUserName() {
                return username;
            }
            
            public String getPassword() {
                return null;
            }
            
            public List<Locale> getLocalePreferences() {
                return new ArrayList<Locale>();
            }
        };
    }
    
    public void close() {
        this.close = true;
    }

    public Map<Object, Object> getAttributes() {
        return attrs;
    }

    public Logger getLog() {
        return log;
    }

    public String getOtherUsersSpace() {
        return null;
    }

    public String getPersonalSpace() {
        return "";
    }

    public long getSessionId() {
        return sessionId;
    }

    public Collection<String> getSharedSpaces() {
        return new ArrayList<String>();
    }

    public User getUser() {
        return user;
    }

    public boolean isOpen() {
        return close == false;
    }

	public char getPathDelimiter() {
		return '.';
	}

    public SessionType getType() {
        return SessionType.User;
    }

}
