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

import jakarta.inject.Inject;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdDAO;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.store.search.ListeningMessageSearchIndex;

import reactor.core.publisher.Flux;

public class AllSearchOverride implements ListeningMessageSearchIndex.SearchOverride {
    private final CassandraMessageIdDAO dao;

    @Inject
    public AllSearchOverride(CassandraMessageIdDAO dao) {
        this.dao = dao;
    }

    @Override
    public boolean applicable(SearchQuery searchQuery, MailboxSession session) {
        return isAll(searchQuery)
            || isFromOne(searchQuery)
            || isEmpty(searchQuery);
    }

    private boolean isAll(SearchQuery searchQuery) {
        return searchQuery.getCriteria().size() == 1
            && searchQuery.getCriteria().get(0).equals(SearchQuery.uid(new SearchQuery.UidRange(MessageUid.MIN_VALUE, MessageUid.MAX_VALUE)))
            && searchQuery.getSorts().equals(SearchQuery.DEFAULT_SORTS);
    }

    private boolean isFromOne(SearchQuery searchQuery) {
        return searchQuery.getCriteria().size() == 1
            && searchQuery.getCriteria().get(0).equals(SearchQuery.all())
            && searchQuery.getSorts().equals(SearchQuery.DEFAULT_SORTS);
    }

    private boolean isEmpty(SearchQuery searchQuery) {
        return searchQuery.getCriteria().isEmpty()
            && searchQuery.getSorts().equals(SearchQuery.DEFAULT_SORTS);
    }

    @Override
    public Flux<MessageUid> search(MailboxSession session, Mailbox mailbox, SearchQuery searchQuery) {
        return dao.listUids((CassandraId) mailbox.getMailboxId());
    }
}
