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
package org.apache.james.mailbox.store.mail;

import org.apache.james.mailbox.MailboxPathLocker;
import org.apache.james.mailbox.MailboxPathLocker.LockAwareExecution;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.store.StoreMailboxPath;
import org.apache.james.mailbox.store.mail.model.MailboxId;
import org.apache.james.mailbox.store.mail.model.Mailbox;

/**
 * Abstract base implementation of {@link ModSeqProvider} which uses the given {@link MailboxPathLocker} to lock the {@link Mailbox} during the mod-seq generation.
 * 
 *
 * @param <Id>
 */
public abstract class AbstractLockingModSeqProvider<Id extends MailboxId> implements ModSeqProvider<Id>{

    private final MailboxPathLocker locker;

    public AbstractLockingModSeqProvider(MailboxPathLocker locker) {
        this.locker = locker;
    }
    
    @Override
    public long nextModSeq(final MailboxSession session, final Mailbox<Id> mailbox) throws MailboxException {
        return locker.executeWithLock(session, new StoreMailboxPath<Id>(mailbox), new LockAwareExecution<Long>() {

            @Override
            public Long execute() throws MailboxException {
                return lockedNextModSeq(session, mailbox);
            }
        }, true);
    }
    
    /**
     * Generate the next mod-seq for the given {@link Mailbox} while holding a lock on it.
     * 
     * @param session
     * @param mailbox
     * @return nextModSeq
     * @throws MailboxException
     */
    protected abstract long lockedNextModSeq(MailboxSession session, Mailbox<Id> mailbox) throws MailboxException;

}
