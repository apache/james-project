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

import static org.apache.james.mailbox.store.mail.AbstractMessageMapper.UNLIMITED;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.james.mailbox.AttachmentContentLoader;
import org.apache.james.mailbox.MailboxManager.MessageCapabilities;
import org.apache.james.mailbox.MailboxManager.SearchCapabilities;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.model.Mailbox;
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
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.search.comparator.CombinedComparator;
import org.apache.james.util.ReactorUtils;
import org.apache.james.util.streams.Iterators;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
    private final AttachmentContentLoader attachmentContentLoader;

    @Inject
    public SimpleMessageSearchIndex(MessageMapperFactory messageMapperFactory, MailboxMapperFactory mailboxMapperFactory, TextExtractor textExtractor, AttachmentContentLoader attachmentContentLoader) {
        this.messageMapperFactory = messageMapperFactory;
        this.mailboxMapperFactory = mailboxMapperFactory;
        this.textExtractor = textExtractor;
        this.attachmentContentLoader = attachmentContentLoader;
    }
    
    @Override
    public EnumSet<SearchCapabilities> getSupportedCapabilities(EnumSet<MessageCapabilities> messageCapabilities) {
        return EnumSet.of(SearchCapabilities.MultimailboxSearch,
            SearchCapabilities.Text,
            SearchCapabilities.Attachment,
            SearchCapabilities.PartialEmailMatch,
            SearchCapabilities.AttachmentFileName);
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
    
    /**
     * Walks down the query tree's conjunctions to find the highest necessary mail fetch type.
     * @param crits - list of Criterion to search from
     * @return required fetch type - metadata, headers, or full
     */
    private static FetchType getFetchTypeForCriteria(List<Criterion> crits) {
        return crits.stream()
            .map(SimpleMessageSearchIndex::getFetchTypeForCriterion)
            .reduce(SimpleMessageSearchIndex::maxFetchType)
            .orElse(FetchType.METADATA);
    }

    private static FetchType getFetchTypeForCriterion(Criterion crit) {
        if (crit instanceof ConjunctionCriterion) {
            return getFetchTypeForCriteria(((ConjunctionCriterion) crit).getCriteria());
        }
        if (crit instanceof SearchQuery.AllCriterion || crit instanceof SearchQuery.TextCriterion) {
            return FetchType.FULL;
        }
        if (crit instanceof SearchQuery.HeaderCriterion || crit instanceof SearchQuery.MimeMessageIDCriterion
            || crit instanceof SearchQuery.SubjectCriterion) {
            return FetchType.HEADERS;
        }
        return FetchType.METADATA;
    }
    
    /**
     * Searches a list of query sort options for the highest necessary mail fetch type.
     * @param sorts - list of Sort to search
     * @return required fetch type - metadata or headers
     */
    private static FetchType getFetchTypeForSorts(List<SearchQuery.Sort> sorts) {
        return sorts.stream()
            .map(SimpleMessageSearchIndex::getFetchTypeForSort)
            .reduce(FetchType.METADATA, SimpleMessageSearchIndex::maxFetchType);
    }

    private static FetchType getFetchTypeForSort(SearchQuery.Sort sort) {
        switch (sort.getSortClause()) {
            case Arrival:
            case Size:
            case Uid:
            case Id:
                return FetchType.METADATA;
            case MailboxCc:
            case MailboxFrom:
            case MailboxTo:
            case BaseSubject:
            case SentDate:
                return FetchType.HEADERS;
            default:
                throw new IllegalArgumentException("cannot determine fetch type for sort option " + sort.getSortClause());
        }
    }

    @VisibleForTesting
    static FetchType maxFetchType(FetchType a, FetchType b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    @Override
    public Flux<MessageUid> search(MailboxSession session, final Mailbox mailbox, SearchQuery query) {
        Preconditions.checkArgument(session != null, "'session' is mandatory");
        return searchResults(session, Flux.just(mailbox), query)
            .filter(searchResult -> searchResult.getMailboxId().equals(mailbox.getMailboxId()))
            .map(SearchResult::getMessageUid);
    }

    private Set<MailboxMessage> searchResults(MailboxSession session, Mailbox mailbox, SearchQuery query) throws MailboxException {
        MessageMapper mapper = messageMapperFactory.getMessageMapper(session);

        final SortedSet<MailboxMessage> hitSet = new TreeSet<>();

        UidCriterion uidCrit = findConjugatedUidCriterion(query.getCriteria());
        if (uidCrit != null) {
            // if there is a conjugated uid range criterion in the query tree we can optimize by
            // only fetching this uid range
            FetchType fetchType = maxFetchType(FetchType.METADATA, getFetchTypeForSorts(query.getSorts()));
            UidRange[] ranges = uidCrit.getOperator().getRange();
            for (UidRange r : ranges) {
                Iterator<MailboxMessage> it = mapper.findInMailbox(mailbox, MessageRange.range(r.getLowValue(), r.getHighValue()), fetchType, UNLIMITED);
                while (it.hasNext()) {
                    hitSet.add(it.next());
                }
            }
        } else {
            // we have to fetch all messages; try to limit their memory requirements
            FetchType fetchType = maxFetchType(getFetchTypeForCriteria(query.getCriteria()), getFetchTypeForSorts(query.getSorts()));
            Iterator<MailboxMessage> messages = mapper.findInMailbox(mailbox, MessageRange.all(), fetchType, UNLIMITED);
            while (messages.hasNext()) {
                MailboxMessage m = messages.next();
                hitSet.add(m);
            }
        }
        return hitSet;
    }

    @Override
    public Flux<MessageId> search(MailboxSession session, final Collection<MailboxId> mailboxIds, SearchQuery searchQuery, long limit) throws MailboxException {
        MailboxMapper mailboxMapper = mailboxMapperFactory.getMailboxMapper(session);

        Flux<Mailbox> filteredMailboxes =
            Flux.fromIterable(mailboxIds)
            .concatMap(mailboxMapper::findMailboxById);

        return getAsMessageIds(searchResults(session, filteredMailboxes, searchQuery), limit);
    }

    private Flux<? extends SearchResult> searchResults(MailboxSession session, Flux<Mailbox> mailboxes, SearchQuery query) {
        return mailboxes.concatMap(mailbox -> Mono.fromCallable(() -> getSearchResultStream(session, query, mailbox))
                .flatMapMany(Flux::fromStream)
                .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER))
            .collectSortedList(CombinedComparator.create(query.getSorts()))
            .flatMapMany(list -> Iterators.toFlux(new MessageSearches(list.iterator(), query, textExtractor, attachmentContentLoader, session).iterator()))
            .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER);
    }

    private Stream<MailboxMessage> getSearchResultStream(MailboxSession session, SearchQuery query, Mailbox mailbox) {
        try {
            return searchResults(session, mailbox, query).stream();
        } catch (MailboxException e) {
            throw new RuntimeException(e);
        }
    }

    private Flux<MessageId> getAsMessageIds(Flux<? extends SearchResult> temp, long limit) {
        return temp.map(searchResult -> searchResult.getMessageId().get())
            .filter(SearchUtil.distinct())
            .take(Long.valueOf(limit).intValue());
    }

}
