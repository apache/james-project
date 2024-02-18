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

package org.apache.james.mailbox.cassandra.search;

import javax.inject.Inject;

import jakarta.mail.Flags;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.mail.CassandraDeletedMessageDAO;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.store.search.ListeningMessageSearchIndex;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;

public class DeletedWithRangeSearchOverride implements ListeningMessageSearchIndex.SearchOverride {
    private final CassandraDeletedMessageDAO dao;

    @Inject
    public DeletedWithRangeSearchOverride(CassandraDeletedMessageDAO dao) {
        this.dao = dao;
    }

    @Override
    public boolean applicable(SearchQuery searchQuery, MailboxSession session) {
        return searchQuery.getCriteria().size() == 2
            && searchQuery.getCriteria().contains(SearchQuery.flagIsSet(Flags.Flag.DELETED))
            && searchQuery.getCriteria().stream()
                .anyMatch(criterion -> criterion instanceof SearchQuery.UidCriterion)
            && searchQuery.getSorts().equals(SearchQuery.DEFAULT_SORTS);
    }

    @Override
    public Flux<MessageUid> search(MailboxSession session, Mailbox mailbox, SearchQuery searchQuery) {
        SearchQuery.UidCriterion uidArgument = searchQuery.getCriteria().stream()
            .filter(criterion -> criterion instanceof SearchQuery.UidCriterion)
            .map(SearchQuery.UidCriterion.class::cast)
            .findAny()
            .orElseThrow(() -> new RuntimeException("Missing Uid argument"));

        SearchQuery.UidRange[] uidRanges = uidArgument.getOperator().getRange();

        return Flux.fromIterable(ImmutableList.copyOf(uidRanges))
            .concatMap(range -> dao.retrieveDeletedMessage((CassandraId) mailbox.getMailboxId(),
                MessageRange.range(range.getLowValue(), range.getHighValue())));
    }
}
