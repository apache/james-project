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

package org.apache.james.mailbox.maildir.mail;

import java.util.ArrayList;

import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.maildir.MaildirId;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailbox;

public class MailboxCache {
    /**
     * A request-scoped list of mailboxes in order to refer to them via id
     */
    private final ArrayList<Mailbox> mailboxCache = new ArrayList<>();

    /**
     * Stores a copy of a mailbox in a cache valid for one request. This is to enable
     * referring to renamed mailboxes via id.
     * @param mailbox The mailbox to cache
     * @return the cached mailbox
     */
    public synchronized Mailbox cacheMailbox(Mailbox mailbox) {
        mailboxCache.add(new SimpleMailbox(mailbox));
        int id = mailboxCache.size() - 1;
        mailbox.setMailboxId(MaildirId.of(id));
        return mailbox;
    }

    /**
     * Retrieves a mailbox from the cache
     * @param mailboxId The id of the mailbox to retrieve
     * @return The mailbox
     * @throws MailboxNotFoundException If the mailboxId is not in the cache
     */
    public synchronized Mailbox getCachedMailbox(MaildirId mailboxId) throws MailboxNotFoundException {
        if (mailboxId == null) {
            throw new MailboxNotFoundException("null");
        }
        try {
            return mailboxCache.get(mailboxId.getRawId());
        } catch (IndexOutOfBoundsException e) {
            throw new MailboxNotFoundException(mailboxId);
        }
    }

    public synchronized void clear() {
        mailboxCache.clear();
    }
}
