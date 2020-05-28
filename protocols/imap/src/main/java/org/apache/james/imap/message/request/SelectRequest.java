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

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.Tag;
import org.apache.james.imap.api.message.IdRange;
import org.apache.james.imap.api.message.UidRange;

import com.google.common.base.MoreObjects;

public class SelectRequest extends AbstractMailboxSelectionRequest {
    public SelectRequest(String mailboxName, boolean condstore, ClientSpecifiedUidValidity lastKnownUidValidity, Long knownModSeq, UidRange[] uidSet, UidRange[] knownUidSet, IdRange[] knownSequenceSet, Tag tag) {
        super(ImapConstants.SELECT_COMMAND, mailboxName, condstore, lastKnownUidValidity, knownModSeq, uidSet, knownUidSet, knownSequenceSet, tag);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("mailboxName", getMailboxName())
            .add("condstore", getCondstore())
            .add("lastKnownUidValidity", getLastKnownUidValidity())
            .add("knownModSeq", getKnownModSeq())
            .add("uidSet", getUidSet())
            .add("knownUidSet", getKnownUidSet())
            .add("knownSequenceSet", getKnownSequenceSet())
            .toString();
    }
}
