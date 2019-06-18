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

package org.apache.james;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.lang.NotImplementedException;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.events.Group;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.search.ListeningMessageSearchIndex;

public class FakeMessageSearchIndex extends ListeningMessageSearchIndex {
    private static class FakeMessageSearchIndexGroup extends Group {}

    private static final FakeMessageSearchIndexGroup GROUP = new FakeMessageSearchIndexGroup();

    public FakeMessageSearchIndex() {
        super(null, null);
    }

    @Override
    public void add(MailboxSession session, Mailbox mailbox, MailboxMessage message) throws Exception {
        throw new NotImplementedException();
    }

    @Override
    public void delete(MailboxSession session, Mailbox mailbox, Collection<MessageUid> expungedUids) throws Exception {
        throw new NotImplementedException();
    }

    @Override
    public void deleteAll(MailboxSession session, Mailbox mailbox) throws Exception {
        throw new NotImplementedException();
    }

    @Override
    public void update(MailboxSession session, Mailbox mailbox, List<UpdatedFlags> updatedFlagsList) throws Exception {
        throw new NotImplementedException();
    }

    @Override
    public Group getDefaultGroup() {
        return GROUP;
    }

    @Override
    public Stream<MessageUid> search(MailboxSession session, Mailbox mailbox, SearchQuery searchQuery) throws MailboxException {
        throw new NotImplementedException();
    }

    @Override
    public List<MessageId> search(MailboxSession session, Collection<MailboxId> mailboxIds, SearchQuery searchQuery, long limit) throws MailboxException {
        throw new NotImplementedException();
    }

    @Override
    public EnumSet<MailboxManager.SearchCapabilities> getSupportedCapabilities(EnumSet<MailboxManager.MessageCapabilities> messageCapabilities) {
        throw new NotImplementedException();
    }

    @Override
    public ExecutionMode getExecutionMode() {
        throw new NotImplementedException();
    }
}
