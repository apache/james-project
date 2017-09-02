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
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

class MailboxUser {
    private final String userName;

    private CharSequence password;

    private final Set<String> subscriptions;

    public MailboxUser(String userName) {
        this.userName = userName;
        this.subscriptions = new HashSet<>();
    }

    public String getUserName() {
        return userName;
    }
    
    public void setPassword(CharSequence password) {
        this.password = password;
    }

    public Collection<String> getSubscriptions() {
        return Collections.unmodifiableSet(subscriptions);
    }

    public void addSubscription(String subscription) {
        this.subscriptions.add(subscription);
    }

    public void removeSubscription(String mailbox) {
        this.subscriptions.remove(mailbox);
    }

    public boolean isPassword(CharSequence password) {
        final boolean result;
        if (password == null) {
            result = this.password == null;
        } else if (this.password == null) {
            result = false;            
        } else if (this.password.length() == password.length()) {
            for (int i=0;i<password.length();i++) {
                if (password.charAt(i) != this.password.charAt(i)) {
                    return false;
                }
            }
            result = true;
        } else {
            result = false;
        }
        return result;
    }
}
