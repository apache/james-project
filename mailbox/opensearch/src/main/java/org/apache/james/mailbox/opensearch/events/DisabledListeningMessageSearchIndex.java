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
package org.apache.james.mailbox.opensearch.events;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import jakarta.inject.Inject;
import jakarta.mail.Flags;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.events.Event;
import org.apache.james.events.EventDeadLetters;
import org.apache.james.events.Group;
import org.apache.james.mailbox.MailboxManager.MessageCapabilities;
import org.apache.james.mailbox.MailboxManager.SearchCapabilities;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.events.MailboxEvents;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.search.ListeningMessageSearchIndex;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class DisabledListeningMessageSearchIndex extends ListeningMessageSearchIndex {

    private static final Group GROUP = new OpenSearchListeningMessageSearchIndex.OpenSearchListeningMessageSearchIndexGroup();
    private static final ImmutableList<Class<? extends Event>> INTERESTING_EVENTS = ImmutableList.of(MailboxEvents.Added.class, MailboxEvents.Expunged.class, MailboxEvents.FlagsUpdated.class, MailboxEvents.MailboxDeletion.class);


    private final EventDeadLetters eventDeadLetters;
    private final SessionProvider sessionProvider;
    private final MailboxSessionMapperFactory factory;

    @Inject
    public DisabledListeningMessageSearchIndex(MailboxSessionMapperFactory factory,
                                               Set<ListeningMessageSearchIndex.SearchOverride> searchOverrides,
                                               EventDeadLetters eventDeadLetters,
                                               SessionProvider sessionProvider) {
        super(factory, searchOverrides, sessionProvider);

        this.eventDeadLetters = eventDeadLetters;
        this.sessionProvider = sessionProvider;
        this.factory = factory;
    }

    @Override
    public boolean isHandling(Event event) {
        return INTERESTING_EVENTS.contains(event.getClass());
    }

    @Override
    public Group getDefaultGroup() {
        return GROUP;
    }

    @Override
    public EnumSet<SearchCapabilities> getSupportedCapabilities(EnumSet<MessageCapabilities> messageCapabilities) {
        return EnumSet.of(SearchCapabilities.MultimailboxSearch,
            SearchCapabilities.Text,
            SearchCapabilities.FullText,
            SearchCapabilities.Attachment,
            SearchCapabilities.AttachmentFileName,
            SearchCapabilities.PartialEmailMatch);
    }

    @Override
    public Mono<Void> reactiveEvent(Event event) {
        MailboxSession systemSession = sessionProvider.createSystemSession(event.getUsername());
        return handleMailboxEvent(event)
            .then(Mono.fromRunnable(() -> factory.endProcessingRequest(systemSession)));
    }

    private Mono<Void> handleMailboxEvent(Event event) {
        if (event instanceof MailboxEvents.Added ||
            event instanceof MailboxEvents.Expunged ||
            event instanceof MailboxEvents.FlagsUpdated ||
            event instanceof MailboxEvents.MailboxDeletion) {

            return eventDeadLetters.store(GROUP, event)
                .then();
        } else {
            return Mono.empty();
        }
    }

    @Override
    protected Flux<MessageUid> doSearch(MailboxSession session, Mailbox mailbox, SearchQuery searchQuery) {
        Preconditions.checkArgument(session != null, "'session' is mandatory");

        return Flux.error(new NotImplementedException());
    }
    
    @Override
    public Flux<MessageId> search(MailboxSession session, Collection<MailboxId> mailboxIds, SearchQuery searchQuery, long limit) {
        Preconditions.checkArgument(session != null, "'session' is mandatory");

        return Flux.error(new NotImplementedException());
    }

    @Override
    public Mono<Void> add(MailboxSession session, Mailbox mailbox, MailboxMessage message) {
        return Mono.error(new NotImplementedException());
    }


    @Override
    public Mono<Void> delete(MailboxSession session, MailboxId mailboxId, Collection<MessageUid> expungedUids) {
        return Mono.error(new NotImplementedException());
    }

    @Override
    public Mono<Void> deleteAll(MailboxSession session, MailboxId mailboxId) {
        return Mono.error(new NotImplementedException());
    }

    @Override
    public Mono<Void> update(MailboxSession session, MailboxId mailboxId, List<UpdatedFlags> updatedFlagsList) {
        return Mono.error(new NotImplementedException());
    }

    @Override
    public Mono<Flags> retrieveIndexedFlags(Mailbox mailbox, MessageUid uid) {
        return Mono.error(new NotImplementedException());
    }
}
