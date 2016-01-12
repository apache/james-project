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

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.store.mail.model.MailboxId;
import org.apache.james.mailbox.store.mail.model.Mailbox;

/**
 * Take care of provide uids for a given {@link Mailbox}. Be aware that implementations
 * need to be thread-safe!
 * 
 *
 * @param <Id>
 */
public interface UidProvider<Id extends MailboxId> {

    /**
     * Return the next uid which can be used while append a MailboxMessage to the {@link Mailbox}.
     * Its important that the returned uid is higher then the last used and that the next call of this method does return a higher
     * one
     * 
     * @param session
     * @param mailbox
     * @return nextUid
     * @throws MailboxException
     */
    public long nextUid(MailboxSession session, Mailbox<Id> mailbox) throws MailboxException;
    
    /**
     * Return the last uid which were used for storing a MailboxMessage in the {@link Mailbox}
     * 
     * @param session
     * @param mailbox
     * @return lastUid
     * @throws MailboxException
     */
    public long lastUid(MailboxSession session, Mailbox<Id> mailbox) throws MailboxException;
}
