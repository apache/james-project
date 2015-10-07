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

import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.SearchQuery.ConjunctionCriterion;
import org.apache.james.mailbox.model.SearchQuery.Criterion;
import org.apache.james.mailbox.model.SearchQuery.NumericRange;
import org.apache.james.mailbox.model.SearchQuery.UidCriterion;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.MessageMapper.FetchType;
import org.apache.james.mailbox.store.mail.MessageMapperFactory;
import org.apache.james.mailbox.store.mail.model.MailboxId;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.Message;

/**
 * {@link MessageSearchIndex} which just fetch {@link Message}'s from the {@link MessageMapper} and use {@link MessageSearcher}
 * to match them against the {@link SearchQuery}.
 * 
 * This works with every implementation but is SLOW.
 * 
 *
 * @param <Id>
 */
public class SimpleMessageSearchIndex<Id extends MailboxId> implements MessageSearchIndex<Id> {

    private final MessageMapperFactory<Id> factory;
    public SimpleMessageSearchIndex(MessageMapperFactory<Id> factory) {
        this.factory = factory;
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
    public Iterator<Long> search(MailboxSession session, Mailbox<Id> mailbox, SearchQuery query) throws MailboxException {
        MessageMapper<Id> mapper = factory.getMessageMapper(session);

        final SortedSet<Message<?>> hitSet = new TreeSet<Message<?>>();

        UidCriterion uidCrit = findConjugatedUidCriterion(query.getCriterias());
        if (uidCrit != null) {
            // if there is a conjugated uid range criterion in the query tree we can optimize by
            // only fetching this uid range
            NumericRange[] ranges = uidCrit.getOperator().getRange();
            for (int i = 0; i < ranges.length; i++) {
                NumericRange r = ranges[i];
                Iterator<Message<Id>> it = mapper.findInMailbox(mailbox, MessageRange.range(r.getLowValue(), r.getHighValue()), FetchType.Metadata, -1);
                while(it.hasNext()) {
                	hitSet.add(it.next());
                }
            }
        } else {
        	// we have to fetch all messages
            Iterator<Message<Id>> messages = mapper.findInMailbox(mailbox, MessageRange.all(), FetchType.Full, -1);
            while(messages.hasNext()) {
            	Message<Id> m = messages.next();
            	hitSet.add(m);
            }
        }
        
        // MessageSearches does the filtering for us
        if (session == null) {
			return new MessageSearches(hitSet.iterator(), query).iterator();
		} else {
			return new MessageSearches(hitSet.iterator(), query, session.getLog()).iterator();
		}
    }

}
