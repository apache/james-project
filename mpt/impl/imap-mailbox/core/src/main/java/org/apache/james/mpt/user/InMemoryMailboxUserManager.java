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

package org.apache.james.mpt.user;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.exception.SubscriptionException;

/**
 * Stores users in memory.
 */
public class InMemoryMailboxUserManager implements SubscriptionManager {

    private final Map<String, MailboxUser> users;

    public InMemoryMailboxUserManager() {
        this.users = new HashMap<>();
    }

    public boolean isAuthentic(String userid, CharSequence password) {
        MailboxUser user = (MailboxUser) users.get(userid);
        final boolean result;
        if (user == null) {
            result = false;
        } else {
            result = user.isPassword(password);
        }
        return result;
    }

    public void subscribe(MailboxSession session, String mailbox)
            throws SubscriptionException {
        MailboxSession.User u = session.getUser();
        MailboxUser user = (MailboxUser) users.get(u.getUserName());
        if (user == null) {
            user = new MailboxUser(u.getUserName());
            users.put(u.getUserName(), user);
        }
        user.addSubscription(mailbox);
    }

    public Collection<String> subscriptions(org.apache.james.mailbox.MailboxSession session) throws SubscriptionException {
        MailboxSession.User u = session.getUser();
        MailboxUser user = (MailboxUser) users.get(u.getUserName());
        if (user == null) {
            user = new MailboxUser(u.getUserName());
            users.put(u.getUserName(), user);
        }
        return user.getSubscriptions();
    }

    public void unsubscribe(org.apache.james.mailbox.MailboxSession session, String mailbox)
            throws SubscriptionException {
        MailboxSession.User u = session.getUser();
        MailboxUser user = (MailboxUser) users.get(u.getUserName());
        if (user == null) {
            user = new MailboxUser(u.getUserName());
            users.put(u.getUserName(), user);
        }
        user.removeSubscription(mailbox);
    }

    public void addUser(String userid, CharSequence password) {
        MailboxUser user = (MailboxUser) users.get(userid);
        if (user == null) {
            user = new MailboxUser(userid);
            users.put(userid, user);
        }
        user.setPassword(password);
    }

    public void endProcessingRequest(MailboxSession session) {
        
    }

    public void startProcessingRequest(MailboxSession session) {
        
    }

}
