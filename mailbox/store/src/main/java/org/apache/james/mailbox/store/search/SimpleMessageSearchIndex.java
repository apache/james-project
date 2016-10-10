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

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.inject.Inject;

import org.apache.james.mailbox.MailboxManager.SearchCapabilities;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MultimailboxesSearchQuery;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.SearchQuery.ConjunctionCriterion;
import org.apache.james.mailbox.model.SearchQuery.Criterion;
import org.apache.james.mailbox.model.SearchQuery.UidCriterion;
import org.apache.james.mailbox.model.SearchQuery.UidRange;
import org.apache.james.mailbox.store.mail.MailboxMapperFactory;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.MessageMapper.FetchType;
import org.apache.james.mailbox.store.mail.MessageMapperFactory;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableMultimap.Builder;
import com.google.common.collect.Multimap;

/**
 * {@link MessageSearchIndex} which just fetch {@link MailboxMessage}'s from the {@link MessageMapper} and use {@link MessageSearcher}
 * to match them against the {@link SearchQuery}.
 * 
 * This works with every implementation but is SLOW.
 * 
 *
 */
public class SimpleMessageSearchIndex implements MessageSearchIndex {
    private static final String WILDCARD = "%";

    private final MessageMapperFactory messageMapperFactory;
    private final MailboxMapperFactory mailboxMapperFactory;
    
    @Inject
    public SimpleMessageSearchIndex(MessageMapperFactory messageMapperFactory, MailboxMapperFactory mailboxMapperFactory) {
        this.messageMapperFactory = messageMapperFactory;
        this.mailboxMapperFactory = mailboxMapperFactory;
    }
    
    @Override
    public EnumSet<SearchCapabilities> getSupportedCapabilities() {
        return EnumSet.of(SearchCapabilities.MultimailboxSearch, SearchCapabilities.Text);
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
    public Iterator<MessageUid> search(MailboxSession session, Mailbox mailbox, SearchQuery query) throws MailboxException {
        Preconditions.checkArgument(session != null, "'session' is mandatory");
        return searchMultimap(session, ImmutableList.of(mailbox), query)
                .get(mailbox.getMailboxId())
                .iterator();
    }
    
    private Multimap<MailboxId, MessageUid> searchMultimap(MailboxSession session, Iterable<Mailbox> mailboxes, SearchQuery query) throws MailboxException {
        Builder<MailboxId, MessageUid> multimap = ImmutableMultimap.builder();
        for (Mailbox mailbox: mailboxes) {
            multimap.putAll(searchMultimap(session, mailbox, query));
        }
        return multimap.build();

    }
    
    private Multimap<MailboxId, MessageUid> searchMultimap(MailboxSession session, Mailbox mailbox, SearchQuery query) throws MailboxException {
        if (!isMatchingUser(session, mailbox)) {
            return ImmutableMultimap.of();
        }
        MessageMapper mapper = messageMapperFactory.getMessageMapper(session);

        final SortedSet<MailboxMessage> hitSet = new TreeSet<MailboxMessage>();

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
            while(messages.hasNext()) {
            	MailboxMessage m = messages.next();
            	hitSet.add(m);
            }
        }
        
        // MessageSearches does the filtering for us
        return ImmutableMultimap.<MailboxId, MessageUid>builder()
                    .putAll(mailbox.getMailboxId(), ImmutableList.copyOf(new MessageSearches(hitSet.iterator(), query, session).iterator()))
                    .build();
    }

    private boolean isMatchingUser(MailboxSession session, Mailbox mailbox) {
        return mailbox.getUser().equals(session.getUser().getUserName());
    }

    @Override
    public Map<MailboxId, Collection<MessageUid>> search(MailboxSession session, final MultimailboxesSearchQuery searchQuery) throws MailboxException {
        List<Mailbox> allUserMailboxes = mailboxMapperFactory.getMailboxMapper(session)
                .findMailboxWithPathLike(new MailboxPath(session.getPersonalSpace(), session.getUser().getUserName(), WILDCARD));
        FluentIterable<Mailbox> filteredMailboxes = FluentIterable.from(allUserMailboxes).filter(new Predicate<Mailbox>() {
            @Override
            public boolean apply(Mailbox input) {
                return !searchQuery.getNotInMailboxes().contains(input.getMailboxId());
            }
        });
        if (searchQuery.getInMailboxes().isEmpty()) {
            return searchMultimap(session, filteredMailboxes, searchQuery.getSearchQuery())
                    .asMap();
        }
        List<Mailbox> queriedMailboxes = new ArrayList<Mailbox>();
        for (Mailbox mailbox: filteredMailboxes) {
            if (searchQuery.getInMailboxes().contains(mailbox.getMailboxId())) {
                queriedMailboxes.add(mailbox);
            }
        }
        return searchMultimap(session, queriedMailboxes, searchQuery.getSearchQuery())
                .asMap();
    }

}
