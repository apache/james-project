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

package org.apache.james.imap.message.request;

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.message.IdRange;
import org.apache.james.imap.api.message.request.ImapRequest;

/**
 * {@link ImapRequest} which selects a Mailbox. 
 * 
 * This supports also the <code>CONDSTORE</code> and the <code>QRESYNC</code> extension
 */
public abstract class AbstractMailboxSelectionRequest extends AbstractImapRequest {
    private final String mailboxName;
    private final boolean condstore;
    private Long lastKnownUidValidity;
    private Long knownModSeq;
    private IdRange[] uidSet;
    private IdRange[] knownUidSet;
    private IdRange[] knownSequenceSet;

    public AbstractMailboxSelectionRequest(final ImapCommand command, final String mailboxName, final boolean condstore, final Long lastKnownUidValidity, final Long knownModSeq, final IdRange[] uidSet, final IdRange[] knownUidSet, final IdRange[] knownSequenceSet, final String tag) {
        super(tag, command);
        this.mailboxName = mailboxName;
        this.condstore = condstore;
        this.lastKnownUidValidity = lastKnownUidValidity;
        this.knownModSeq = knownModSeq;
        if ((lastKnownUidValidity == null && knownModSeq != null) || (this.lastKnownUidValidity != null && knownModSeq == null)) {
            throw new IllegalArgumentException();
        }
        this.uidSet = uidSet;
        this.knownUidSet = knownUidSet;
        this.knownSequenceSet = knownSequenceSet;
    }

    /**
     * Return the mailbox to select
     * 
     * @return mailboxName
     */
    public final String getMailboxName() {
        return mailboxName;
    }
    
    /**
     * Return true if the <code>CONDSTORE</code> option was used while selecting the mailbox
     * 
     * @return condstore
     */
    public final boolean getCondstore() {
        return condstore;
    }
    
    /**
     * Return the last known <code>UIDVALIDITY</code> or null if it was not given. This is a MUST parameter when
     * using the <code>QRESYNC</code> option. So if this returns null you can be sure the <code>QRESYNC</code> was not used
     * 
     * @return lastKnownUidValidity
     */
    public final Long getLastKnownUidValidity() {
        return lastKnownUidValidity;
    }
    
    /**
     * Return the known <code>MODSEQ</code> or null if it was not given. This is a MUST parameter when
     * using the <code>QRESYNC</code> option. So if this returns null you can be sure the <code>QRESYNC</code> was not used
     * 
     * @return knownModSeq
     */
    public final Long getKnownModSeq() {
        return knownModSeq;
    }
    
   
    /**
     * Return the known uid set or null if it was not given. This is a OPTIONAL parameter when
     * using the <code>QRESYNC</code> option. 
     * 
     * @return uidSet
     */
    public final IdRange[] getUidSet() {
        return uidSet;
    }
    
    /**
     * Return the known sequence-set or null if it was not given. This known sequence-set has the corresponding uids in {@link #getKnownUidSet()}. This is a OPTIONAL parameter when
     * using the <code>QRESYNC</code> option. 
     * 
     * @return knownSequenceSet
     */
    public final IdRange[] getKnownSequenceSet() {
        return knownSequenceSet;
    }
    
    
    /**
     * Return the known uid set or null if it was not given. This known uid set has the corresponding message numbers in {@link #getKnownSequenceSet()}. This is a OPTIONAL parameter when
     * using the <code>QRESYNC</code> option. 
     * 
     * @return knownUidSet
     */
    public final IdRange[] getKnownUidSet() {
        return knownUidSet;
    }
}
