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

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.store.mail.UidProvider;

public class InMemoryUidProvider implements UidProvider {

    private final ConcurrentMap<InMemoryId, AtomicLong> map = new ConcurrentHashMap<>();
    
    @Override
    public MessageUid nextUid(Mailbox mailbox) throws MailboxException {
        return nextUid(mailbox.getMailboxId());
    }

    @Override
    public MessageUid nextUid(MailboxId mailboxId) {
        InMemoryId memoryId = (InMemoryId) mailboxId;
        AtomicLong uid = getLast(memoryId);
        if (uid != null) {
            return MessageUid.of(uid.incrementAndGet());
        }
        AtomicLong initialUid = new AtomicLong(MessageUid.MIN_VALUE.asLong());
        AtomicLong previousUid = map.putIfAbsent(memoryId, initialUid);
        if (previousUid != null) {
            return MessageUid.of(previousUid.incrementAndGet());
        } else {
            return MessageUid.MIN_VALUE;
        }
    }

    @Override
    public Optional<MessageUid> lastUid(Mailbox mailbox) throws MailboxException {
        AtomicLong last = getLast((InMemoryId) mailbox.getMailboxId());
        if (last == null) {
            return Optional.empty();
        }
        return Optional.of(MessageUid.of(last.get()));
    }
    
    private AtomicLong getLast(InMemoryId id) {
        return map.get(id);
    }

}
