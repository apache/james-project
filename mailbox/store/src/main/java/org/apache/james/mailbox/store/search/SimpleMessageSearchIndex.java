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
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.james.mailbox.MailboxManager.MessageCapabilities;
import org.apache.james.mailbox.MailboxManager.SearchCapabilities;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.SearchQuery.ConjunctionCriterion;
import org.apache.james.mailbox.model.SearchQuery.Criterion;
import org.apache.james.mailbox.model.SearchQuery.UidCriterion;
import org.apache.james.mailbox.model.SearchQuery.UidRange;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.MailboxMapperFactory;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.MessageMapper.FetchType;
import org.apache.james.mailbox.store.mail.MessageMapperFactory;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;

import com.github.fge.lambdas.Throwing;
import com.github.steveash.guavate.Guavate;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;

/**
 * {@link MessageSearchIndex} which just fetch {@link MailboxMessage}'s from the {@link MessageMapper} and use {@link MessageSearcher}
 * to match them against the {@link SearchQuery}.
 * 
 * This works with every implementation but is SLOW.
 * 
 *
 */
public class SimpleMessageSearchIndex implements MessageSearchIndex {
    private final MessageMapperFactory messageMapperFactory;
    private final MailboxMapperFactory mailboxMapperFactory;
    private final TextExtractor textExtractor;
    
    @Inject
    public SimpleMessageSearchIndex(MessageMapperFactory messageMapperFactory, MailboxMapperFactory mailboxMapperFactory, TextExtractor textExtractor) {
        this.messageMapperFactory = messageMapperFactory;
        this.mailboxMapperFactory = mailboxMapperFactory;
        this.textExtractor = textExtractor;
    }
    
    @Override
    public EnumSet<SearchCapabilities> getSupportedCapabilities(EnumSet<MessageCapabilities> messageCapabilities) {
        if (messageCapabilities.contains(MessageCapabilities.Attachment)) {
            return EnumSet.of(SearchCapabilities.MultimailboxSearch,
                SearchCapabilities.Text,
                SearchCapabilities.Attachment,
                SearchCapabilities.PartialEmailMatch);
        }
        return EnumSet.of(SearchCapabilities.MultimailboxSearch,
            SearchCapabilities.Text);
    }
    
    /**
     * Walks down the query tree's conjunctions to find a UidCriterion
     * @param crits - list of Criterion to search from
     * @return
     *      first UidCriterion found
     *      null - if not found
     */
    private static UidCriterion findConjugatedUidCriterion(List<Criterion> crits) {
        for (Criterion crit : crits) {
            if (crit instanceof UidCriterion) {
                return (UidCriterion) crit;
            } else if (crit instanceof ConjunctionCriterion) {
                return findConjugatedUidCriterion(((ConjunctionCriterion) crit)
                        .getCriteria());
            }
        }
        return null;
    }
    
    @Override
    public Iterator<MessageUid> search(MailboxSession session, final Mailbox mailbox, SearchQuery query) throws MailboxException {
        Preconditions.checkArgument(session != null, "'session' is mandatory");
        return searchResults(session, ImmutableList.of(mailbox).stream(), query)
            .stream()
            .filter(searchResult -> searchResult.getMailboxId().equals(mailbox.getMailboxId()))
            .map(SearchResult::getMessageUid)
            .iterator();
    }

    private List<SearchResult> searchResults(MailboxSession session, Mailbox mailbox, SearchQuery query) throws MailboxException {
        MessageMapper mapper = messageMapperFactory.getMessageMapper(session);

        final SortedSet<MailboxMessage> hitSet = new TreeSet<>();

        UidCriterion uidCrit = findConjugatedUidCriterion(query.getCriterias());
        if (uidCrit != null) {
            // if there is a conjugated uid range criterion in the query tree we can optimize by
            // only fetching this uid range
            UidRange[] ranges = uidCrit.getOperator().getRange();
            for (UidRange r : ranges) {
                Iterator<MailboxMessage> it = mapper.findInMailbox(mailbox, MessageRange.range(r.getLowValue(), r.getHighValue()), FetchType.Metadata, -1);
                while (it.hasNext()) {
                    hitSet.add(it.next());
                }
            }
        } else {
            // we have to fetch all messages
            Iterator<MailboxMessage> messages = mapper.findInMailbox(mailbox, MessageRange.all(), FetchType.Full, -1);
            while (messages.hasNext()) {
                MailboxMessage m = messages.next();
                hitSet.add(m);
            }
        }
        return ImmutableList.copyOf(new MessageSearches(hitSet.iterator(), query, textExtractor).iterator());
    }

    @Override
    public List<MessageId> search(MailboxSession session, final Collection<MailboxId> mailboxIds, SearchQuery searchQuery, long limit) throws MailboxException {
        MailboxMapper mailboxManager = mailboxMapperFactory.getMailboxMapper(session);

        Stream<Mailbox> filteredMailboxes = mailboxIds
            .stream()
            .map(Throwing.function(mailboxManager::findMailboxById).sneakyThrow());

        return getAsMessageIds(searchResults(session, filteredMailboxes, searchQuery), limit);
    }

    private List<SearchResult> searchResults(MailboxSession session, Stream<Mailbox> mailboxes, SearchQuery query) throws MailboxException {
        return mailboxes.flatMap(mailbox -> getSearchResultStream(session, query, mailbox))
            .collect(Guavate.toImmutableList());
    }

    private Stream<? extends SearchResult> getSearchResultStream(MailboxSession session, SearchQuery query, Mailbox mailbox) {
        try {
            return searchResults(session, mailbox, query).stream();
        } catch (MailboxException e) {
            throw Throwables.propagate(e);
        }
    }

    private List<MessageId> getAsMessageIds(List<SearchResult> temp, long limit) {
        return temp.stream()
            .map(searchResult -> searchResult.getMessageId().get())
            .filter(SearchUtil.distinct())
            .limit(Long.valueOf(limit).intValue())
            .collect(Guavate.toImmutableList());
    }

}
