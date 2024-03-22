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

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

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
import org.apache.james.mailbox.store.mail.ThreadIdGuessingAlgorithm;
import org.apache.james.mailbox.store.mail.model.MimeMessageId;
import org.apache.james.mailbox.store.mail.model.Subject;
import org.apache.james.mailbox.store.search.SearchUtil;

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
    public Mono<ThreadId> guessThreadIdReactive(MessageId messageId, Optional<MimeMessageId> mimeMessageId, Optional<MimeMessageId> inReplyTo, Optional<List<MimeMessageId>> references, Optional<Subject> subject, MailboxSession session) {
        Set<Integer> hashMimeMessageIds = buildMimeMessageIdSet(mimeMessageId, inReplyTo, references)
            .stream()
            .map(mimeMessageId1 -> Hashing.murmur3_32_fixed().hashBytes(mimeMessageId1.getValue().getBytes()).asInt())
            .collect(Collectors.toSet());

        Optional<Integer> hashBaseSubject = subject.map(value -> new Subject(SearchUtil.getBaseSubject(value.getValue())))
            .map(subject1 -> Hashing.murmur3_32_fixed().hashBytes(subject1.getValue().getBytes()).asInt());

        return Flux.from(threadDAO.selectSome(session.getUser(), hashMimeMessageIds))
            .filter(pair -> pair.getLeft().equals(hashBaseSubject))
            .next()
            .map(Pair::getRight)
            .switchIfEmpty(Mono.just(ThreadId.fromBaseMessageId(messageId)))
            .flatMap(threadId -> threadDAO
                .insertSome(session.getUser(), hashMimeMessageIds, messageId, threadId, hashBaseSubject)
                .then(threadLookupDAO.insert(messageId, session.getUser(), hashMimeMessageIds))
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

    private Set<MimeMessageId> buildMimeMessageIdSet(Optional<MimeMessageId> mimeMessageId, Optional<MimeMessageId> inReplyTo, Optional<List<MimeMessageId>> references) {
        Set<MimeMessageId> mimeMessageIds = new HashSet<>();
        mimeMessageId.ifPresent(mimeMessageIds::add);
        inReplyTo.ifPresent(mimeMessageIds::add);
        references.ifPresent(mimeMessageIds::addAll);
        return mimeMessageIds;
    }
}