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

package org.apache.james.mailbox.store.search.indexer.registrations;

import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.model.MailboxPath;

import java.util.concurrent.ConcurrentHashMap;

public class GlobalRegistration implements MailboxListener {

    private final ConcurrentHashMap<MailboxPath, Boolean> impactingEvents;

    public GlobalRegistration() {
        this.impactingEvents = new ConcurrentHashMap<MailboxPath, Boolean>();
    }

    public boolean indexThisPath(MailboxPath mailboxPath) {
        return impactingEvents.get(mailboxPath) != null;
    }

    @Override
    public void event(Event event) {
        if (event instanceof MailboxDeletion) {
            impactingEvents.putIfAbsent(event.getMailboxPath(), true);
        } else if (event instanceof Expunged) {
            impactingEvents.putIfAbsent(event.getMailboxPath(), true);
        }
    }
}
