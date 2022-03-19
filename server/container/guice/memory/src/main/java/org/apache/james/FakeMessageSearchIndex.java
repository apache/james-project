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

import jakarta.mail.Flags;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.events.Group;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.search.ListeningMessageSearchIndex;

import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class FakeMessageSearchIndex extends ListeningMessageSearchIndex {
    private static class FakeMessageSearchIndexGroup extends Group {

    }

    private static final FakeMessageSearchIndexGroup GROUP = new FakeMessageSearchIndexGroup();

    public FakeMessageSearchIndex() {
        super(null, ImmutableSet.of(), null);
    }

    @Override
    public Mono<Void> add(MailboxSession session, Mailbox mailbox, MailboxMessage message) {
        throw new NotImplementedException("not implemented");
    }

    @Override
    public Mono<Void> delete(MailboxSession session, MailboxId mailboxId, Collection<MessageUid> expungedUids) {
        throw new NotImplementedException("not implemented");
    }

    @Override
    public Mono<Void> deleteAll(MailboxSession session, MailboxId mailboxId) {
        throw new NotImplementedException("not implemented");
    }

    @Override
    public Mono<Void> update(MailboxSession session, MailboxId mailboxId, List<UpdatedFlags> updatedFlagsList) {
        throw new NotImplementedException("not implemented");
    }

    @Override
    public Group getDefaultGroup() {
        return GROUP;
    }

    @Override
    public Flux<MessageUid> doSearch(MailboxSession session, Mailbox mailbox, SearchQuery searchQuery) throws MailboxException {
        throw new NotImplementedException("not implemented");
    }

    @Override
    public Flux<MessageId> search(MailboxSession session, Collection<MailboxId> mailboxIds, SearchQuery searchQuery, long limit) {
        throw new NotImplementedException("not implemented");
    }

    @Override
    public EnumSet<MailboxManager.SearchCapabilities> getSupportedCapabilities(EnumSet<MailboxManager.MessageCapabilities> messageCapabilities) {
        throw new NotImplementedException("not implemented");
    }

    @Override
    public ExecutionMode getExecutionMode() {
        throw new NotImplementedException("not implemented");
    }

    @Override
    public Mono<Flags> retrieveIndexedFlags(Mailbox mailbox, MessageUid uid) {
        throw new NotImplementedException("not implemented");
    }
}
