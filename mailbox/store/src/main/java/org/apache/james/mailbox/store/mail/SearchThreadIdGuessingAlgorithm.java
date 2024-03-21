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

package org.apache.james.mailbox.store.mail;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.inject.Inject;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.exception.ThreadNotFoundException;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.MultimailboxesSearchQuery;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.store.mail.model.MimeMessageId;
import org.apache.james.mailbox.store.mail.model.Subject;
import org.apache.james.mailbox.store.search.SearchUtil;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class SearchThreadIdGuessingAlgorithm implements ThreadIdGuessingAlgorithm {
    private final MailboxManager mailboxManager;
    private final MessageIdManager messageIdManager;

    @Inject
    public SearchThreadIdGuessingAlgorithm(MailboxManager mailboxManager, MessageIdManager messageIdManager) {
        this.mailboxManager = mailboxManager;
        this.messageIdManager = messageIdManager;
    }

    @Override
    public Mono<ThreadId> guessThreadIdReactive(MessageId messageId, Optional<MimeMessageId> mimeMessageId, Optional<MimeMessageId> inReplyTo, Optional<List<MimeMessageId>> references, Optional<Subject> subject, MailboxSession session) {
        MultimailboxesSearchQuery expression = buildSearchQuery(mimeMessageId, inReplyTo, references, subject);

        return Flux.from(mailboxManager.search(expression, session, 1))
            .collectList()
            .flatMapMany(messageIds -> messageIdManager.getMessagesReactive(messageIds, FetchGroup.MINIMAL, session))
            .map(MessageResult::getThreadId)
            .next()
            .switchIfEmpty(Mono.just(ThreadId.fromBaseMessageId(messageId)));
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

    private MultimailboxesSearchQuery buildSearchQuery(Optional<MimeMessageId> mimeMessageId, Optional<MimeMessageId> inReplyTo, Optional<List<MimeMessageId>> references, Optional<Subject> subject) {
        Set<MimeMessageId> mimeMessageIds = buildMimeMessageIdSet(mimeMessageId, inReplyTo, references);

        ImmutableList.Builder<SearchQuery.Criterion> mimeMessageIdCriteriaBuilder = ImmutableList.builder();
        mimeMessageIds.forEach(value -> {
            mimeMessageIdCriteriaBuilder.add(SearchQuery.mimeMessageID(value.getValue()));
            mimeMessageIdCriteriaBuilder.add(SearchQuery.headerContains("In-Reply-To", value.getValue()));
            mimeMessageIdCriteriaBuilder.add(SearchQuery.headerContains("References", value.getValue()));
        });
        SearchQuery.Criterion mimeMessageIdCriterion = SearchQuery.or(mimeMessageIdCriteriaBuilder.build());

        SearchQuery.Criterion finalCriterion = subject.map(value -> SearchQuery.and(mimeMessageIdCriterion, SearchQuery.subject(SearchUtil.getBaseSubject(value.getValue()))))
            .orElse(mimeMessageIdCriterion);

        return MultimailboxesSearchQuery
            .from(SearchQuery.of(finalCriterion))
            .build();
    }

    private Set<MimeMessageId> buildMimeMessageIdSet(Optional<MimeMessageId> mimeMessageId, Optional<MimeMessageId> inReplyTo, Optional<List<MimeMessageId>> references) {
        Set<MimeMessageId> mimeMessageIds = new HashSet<>();
        mimeMessageId.ifPresent(mimeMessageIds::add);
        inReplyTo.ifPresent(mimeMessageIds::add);
        references.ifPresent(mimeMessageIds::addAll);
        return mimeMessageIds;
    }
}
