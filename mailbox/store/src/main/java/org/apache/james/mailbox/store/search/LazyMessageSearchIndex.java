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
package org.apache.james.mailbox.store.search;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.mail.Flags;

import org.apache.james.events.Group;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxManager.SearchCapabilities;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.UnsupportedSearchException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.mail.MessageMapper.FetchType;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * {@link ListeningMessageSearchIndex} implementation which wraps another {@link ListeningMessageSearchIndex} and will forward all calls to it.
 * 
 * The only special thing about this is that it will index all the mails in the mailbox on the first call of {@link #search(MailboxSession, Mailbox, SearchQuery)}
 * 
 * This class is mostly useful for in-memory indexes or for indexed that should be recreated on every server restart.
 * 
 *
 */
public class LazyMessageSearchIndex extends ListeningMessageSearchIndex {
    public static class LazyMessageSearchIndexGroup extends Group {

    }

    private static final Logger LOGGER = LoggerFactory.getLogger(LazyMessageSearchIndex.class);
    private static final Group GROUP = new LazyMessageSearchIndexGroup();

    private final ListeningMessageSearchIndex index;
    private final ConcurrentHashMap<MailboxId, Object> indexed = new ConcurrentHashMap<>();
    private final MailboxSessionMapperFactory factory;

    public LazyMessageSearchIndex(ListeningMessageSearchIndex index, MailboxSessionMapperFactory factory, SessionProvider sessionProvider) {
        super(factory, ImmutableSet.of(), sessionProvider);
        this.index = index;
        this.factory = factory;
    }

    @Override
    public Group getDefaultGroup() {
        return GROUP;
    }
    
    @Override
    public EnumSet<SearchCapabilities> getSupportedCapabilities(EnumSet<MailboxManager.MessageCapabilities> messageCapabilities) {
        return EnumSet.noneOf(SearchCapabilities.class);
    }

    @Override
    public Mono<Void> add(MailboxSession session, Mailbox mailbox, MailboxMessage message) {
        return index.add(session, mailbox, message);
    }

    @Override
    public Mono<Void> delete(MailboxSession session, MailboxId mailboxId, Collection<MessageUid> expungedUids) {
        return index.delete(session, mailboxId, expungedUids);
    }

    @Override
    public Mono<Void> deleteAll(MailboxSession session, MailboxId mailboxId) {
        return index.deleteAll(session, mailboxId);
    }

    /**
     * Lazy index the mailbox on first search request if it was not indexed before. After indexing is done it delegate the search request to the wrapped
     * {@link MessageSearchIndex}. Be aware that concurrent search requests are blocked on the same "not-yet-indexed" mailbox till it the index process was 
     * complete
     * 
     */
    @Override
    public Flux<MessageUid> doSearch(MailboxSession session, Mailbox mailbox, SearchQuery searchQuery) throws MailboxException {
        Preconditions.checkArgument(session != null, "'session' is mandatory");
        MailboxId id = mailbox.getMailboxId();
        
        Object done = indexed.get(id);
        if (done == null) {
            done = new Object();
            Object oldDone = indexed.putIfAbsent(id, done);
            if (oldDone != null) {
                done = oldDone;
            }
            synchronized (done) {
                Iterator<MailboxMessage> messages = factory.getMessageMapper(session).findInMailbox(mailbox, MessageRange.all(), FetchType.FULL, UNLIMITED);
                while (messages.hasNext()) {
                    final MailboxMessage message = messages.next();
                    try {
                        add(session, mailbox, message).block();
                    } catch (Exception e) {
                        LOGGER.error("Unable to index message {} in mailbox {}", message.getUid(), mailbox.getName(), e);
                    }
                }
            }
        }
       
        return index.search(session, mailbox, searchQuery);
    }

    @Override
    public Mono<Void> update(MailboxSession session, MailboxId mailboxId, List<UpdatedFlags> updatedFlagsList) {
        return index.update(session, mailboxId, updatedFlagsList);
    }
    

    @Override
    public Flux<MessageId> search(MailboxSession session, Collection<MailboxId> mailboxIds, SearchQuery searchQuery, long limit) throws MailboxException {
        throw new UnsupportedSearchException();
    }

    @Override
    public Mono<Flags> retrieveIndexedFlags(Mailbox mailbox, MessageUid uid) {
        return index.retrieveIndexedFlags(mailbox, uid);
    }
}
