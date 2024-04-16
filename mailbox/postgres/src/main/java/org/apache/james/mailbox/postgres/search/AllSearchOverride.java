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

package org.apache.james.mailbox.postgres.search;

import jakarta.inject.Inject;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.postgres.PostgresMailboxId;
import org.apache.james.mailbox.postgres.mail.dao.PostgresMailboxMessageDAO;
import org.apache.james.mailbox.store.search.ListeningMessageSearchIndex;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class AllSearchOverride implements ListeningMessageSearchIndex.SearchOverride {
    private final PostgresExecutor.Factory executorFactory;

    @Inject
    public AllSearchOverride(PostgresExecutor.Factory executorFactory) {
        this.executorFactory = executorFactory;
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
        return Mono.fromCallable(() -> new PostgresMailboxMessageDAO(executorFactory.create(session.getUser().getDomainPart())))
            .flatMapMany(dao -> dao.listAllMessageUid((PostgresMailboxId) mailbox.getMailboxId()));
    }
}
