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

import java.util.Arrays;
import java.util.List;

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.Tag;
import org.apache.james.imap.api.message.IdRange;

import com.google.common.base.Objects;

public abstract class AbstractMessageRangeRequest extends AbstractImapRequest {

    private final IdRange[] idSet;
    private final String mailboxName;
    private final boolean useUids;

    public AbstractMessageRangeRequest(ImapCommand command, IdRange[] idSet, String mailboxName, boolean useUids, Tag tag) {
        super(tag, command);
        this.idSet = idSet;
        this.mailboxName = mailboxName;
        this.useUids = useUids;
    }

    public final IdRange[] getIdSet() {
        return idSet;
    }

    public final String getMailboxName() {
        return mailboxName;
    }

    public final boolean isUseUids() {
        return useUids;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        AbstractMessageRangeRequest that = (AbstractMessageRangeRequest) o;

        return equals(this.idSet, that.idSet)
            && Objects.equal(this.mailboxName, that.mailboxName)
            && Objects.equal(this.useUids, that.useUids);
    }

    private boolean equals(IdRange[] idSet1, IdRange[] idSet2) {
        List<IdRange> idRanges1 = Arrays.asList(idSet1);
        List<IdRange> idRanges2 = Arrays.asList(idSet2);
        return Objects.equal(idRanges1, idRanges2);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(Arrays.hashCode(idSet), mailboxName, useUids);
    }
}
