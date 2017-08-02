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

package org.apache.james.mailbox.inmemory.mail;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.store.mail.ModSeqProvider;
import org.apache.james.mailbox.store.mail.model.Mailbox;

public class InMemoryModSeqProvider implements ModSeqProvider {
    private final ConcurrentMap<InMemoryId, AtomicLong> map = new ConcurrentHashMap<>();

    @Override
    public long nextModSeq(MailboxSession session, Mailbox mailbox) throws MailboxException {
        return nextModSeq((InMemoryId) mailbox.getMailboxId());

    }

    @Override
    public long nextModSeq(MailboxSession session, MailboxId mailboxId) throws MailboxException {
        return nextModSeq((InMemoryId) mailboxId);
    }

    @Override
    public long highestModSeq(MailboxSession session, Mailbox mailbox) throws MailboxException {
        return getHighest((InMemoryId) mailbox.getMailboxId()).get();
    }

    @Override
    public long highestModSeq(MailboxSession session, MailboxId mailboxId) throws MailboxException {
        return getHighest((InMemoryId) mailboxId).get();
    }

    private AtomicLong getHighest(InMemoryId id) {
        AtomicLong uid = map.get(id);
        if (uid == null) {
            uid = new AtomicLong(0);
            AtomicLong u = map.putIfAbsent(id, uid);
            if (u != null) {
                uid = u;
            }
        }
        return uid;
    }

    private long nextModSeq(InMemoryId mailboxId) {
        return getHighest(mailboxId).incrementAndGet();
    }
}
