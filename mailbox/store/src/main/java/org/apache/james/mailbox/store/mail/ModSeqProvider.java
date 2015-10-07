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
 * Take care of provide mod-seqences for a given {@link Mailbox}. Be aware that implementations
 * need to be thread-safe!
 * 
 *
 * @param <Id>
 */
public interface ModSeqProvider<Id extends MailboxId> {

    /**
     * Return the next mod-sequence which can be used for the {@link Mailbox}.
     * Its important that the returned mod-sequence is higher then the last used and that the next call of this method does return a higher
     * one. 
     * 
     * The first mod-seq must be >= 1
     * 
     * @param session
     * @param mailbox
     * @return modSeq
     * @throws MailboxException
     */
    public long nextModSeq(MailboxSession session, Mailbox<Id> mailbox) throws MailboxException;
    
    /**
     * Return the highest mod-sequence which were used for the {@link Mailbox}
     * 
     * @param session
     * @param mailbox
     * @return highest
     * @throws MailboxException
     */
    public long highestModSeq(MailboxSession session, Mailbox<Id> mailbox) throws MailboxException;
}
