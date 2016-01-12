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
package org.apache.james.mailbox.store.mail.model;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

public class DefaultMessageId implements MessageId {

    private final MailboxId mailboxId;
    private final long messageUid;

    public DefaultMessageId(MailboxId mailboxId, long messageUid) {
        Preconditions.checkNotNull(mailboxId);
        this.mailboxId = mailboxId;
        this.messageUid = messageUid;
    }
    
    @Override
    public String serialize() {
        return String.format("%s-%d", mailboxId.serialize(), messageUid);
    }
    
    @Override
    public final boolean equals(Object obj) {
        if (obj instanceof DefaultMessageId) {
            DefaultMessageId other = (DefaultMessageId) obj;
            return Objects.equal(mailboxId, other.mailboxId) &&
                    Objects.equal(messageUid, other.messageUid);
            
        }
        return false;
    }
    
    @Override
    public final int hashCode() {
        return Objects.hashCode(mailboxId, messageUid);
    }
}
