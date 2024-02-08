/******************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one     *
 * or more contributor license agreements.  See the NOTICE file   *
 * distributed with this work for additional information          *
 * regarding copyright ownership.  The ASF licenses this file     *
 * to you under the Apache License, Version 2.0 (the              *
 * "License"); you may not use this file except in compliance     *
 * with the License.  You may obtain a copy of the License at     *
 *                                                                *
 * http://www.apache.org/licenses/LICENSE-2.0                     *
 *                                                                *
 * Unless required by applicable law or agreed to in writing,     *
 * software distributed under the License is distributed on an    *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY         *
 * KIND, either express or implied.  See the License for the      *
 * specific language governing permissions and limitations        *
 * under the License.                                             *
 ******************************************************************/

package org.apache.james.mailbox.cassandra;

import javax.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.cassandra.mail.CassandraThreadDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraThreadLookupDAO;
import org.apache.james.mailbox.exception.ThreadNotFoundException;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MultimailboxesSearchQuery;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.store.ThreadInformation;
import org.apache.james.mailbox.store.mail.ThreadIdGuessingAlgorithm;

import com.google.common.hash.Hashing;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraThreadIdGuessingAlgorithm implements ThreadIdGuessingAlgorithm {
    private final MailboxManager mailboxManager;
    private final CassandraThreadDAO threadDAO;
    private final CassandraThreadLookupDAO threadLookupDAO;

    @Inject
    public CassandraThreadIdGuessingAlgorithm(MailboxManager mailboxManager, CassandraThreadDAO threadDAO, CassandraThreadLookupDAO threadLookupDAO) {
        this.mailboxManager = mailboxManager;
        this.threadDAO = threadDAO;
        this.threadLookupDAO = threadLookupDAO;
    }

    @Override
    public Mono<ThreadId> guessThreadIdReactive(MessageId messageId, ThreadInformation threadInformation, MailboxSession session) {
        ThreadInformation.Hashed hashed = threadInformation.hash();
        return Flux.from(threadDAO.selectSome(session.getUser(), hashed.getHashMimeMessageIds()))
            .filter(pair -> pair.getLeft().equals(hashed.getHashBaseSubject()))
            .next()
            .map(Pair::getRight)
            .switchIfEmpty(Mono.just(ThreadId.fromBaseMessageId(messageId)))
            .flatMap(threadId -> threadDAO
                .insertSome(session.getUser(), hashed.getHashMimeMessageIds(), messageId, threadId, hashed.getHashBaseSubject())
                .then(threadLookupDAO.insert(messageId, session.getUser(), hashed.getHashMimeMessageIds()))
                .then(Mono.just(threadId)));
    }

    @Override
    public Flux<MessageId> getMessageIdsInThread(ThreadId threadId, MailboxSession session) {
        SearchQuery searchQuery = SearchQuery.builder()
            .andCriteria(SearchQuery.threadId(threadId))
            .sorts(new SearchQuery.Sort(SearchQuery.Sort.SortClause.Arrival, SearchQuery.Sort.Order.NATURAL))
            .build();

        MultimailboxesSearchQuery expression = MultimailboxesSearchQuery
            .from(searchQuery)
            .build();

        return Flux.from(mailboxManager.search(expression, session, Integer.MAX_VALUE))
            .switchIfEmpty(Mono.error(() -> new ThreadNotFoundException(threadId)));
    }
}