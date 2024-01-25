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

package org.apache.james.mailbox.postgres;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.ThreadNotFoundException;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.postgres.mail.dao.PostgresThreadDAO;
import org.apache.james.mailbox.store.mail.ThreadIdGuessingAlgorithm;
import org.apache.james.mailbox.store.mail.model.MimeMessageId;
import org.apache.james.mailbox.store.mail.model.Subject;
import org.apache.james.mailbox.store.search.SearchUtil;

import com.google.common.hash.Hashing;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresThreadIdGuessingAlgorithm implements ThreadIdGuessingAlgorithm {
    private final PostgresThreadDAO.Factory threadDAOFactory;

    @Inject
    public PostgresThreadIdGuessingAlgorithm(PostgresThreadDAO.Factory threadDAOFactory) {
        this.threadDAOFactory = threadDAOFactory;
    }

    @Override
    public Mono<ThreadId> guessThreadIdReactive(MessageId messageId, Optional<MimeMessageId> mimeMessageId, Optional<MimeMessageId> inReplyTo,
                                                Optional<List<MimeMessageId>> references, Optional<Subject> subject, MailboxSession session) {
        PostgresThreadDAO threadDAO = threadDAOFactory.create(session.getUser().getDomainPart());

        Set<Integer> hashMimeMessageIds = buildMimeMessageIdSet(mimeMessageId, inReplyTo, references)
            .stream()
            .map(mimeMessageId1 -> Hashing.murmur3_32_fixed().hashBytes(mimeMessageId1.getValue().getBytes()).asInt())
            .collect(Collectors.toSet());

        Optional<Integer> hashBaseSubject = subject.map(value -> new Subject(SearchUtil.getBaseSubject(value.getValue())))
            .map(subject1 -> Hashing.murmur3_32_fixed().hashBytes(subject1.getValue().getBytes()).asInt());

        return threadDAO.findThreads(session.getUser(), hashMimeMessageIds)
            .filter(pair -> pair.getLeft().equals(hashBaseSubject))
            .next()
            .map(Pair::getRight)
            .switchIfEmpty(Mono.just(ThreadId.fromBaseMessageId(messageId)))
            .flatMap(threadId -> threadDAO
                .insertSome(session.getUser(), hashMimeMessageIds, PostgresMessageId.class.cast(messageId), threadId, hashBaseSubject)
                .then(Mono.just(threadId)));
    }

    @Override
    public Flux<MessageId> getMessageIdsInThread(ThreadId threadId, MailboxSession session) {
        PostgresThreadDAO threadDAO = threadDAOFactory.create(session.getUser().getDomainPart());
        return threadDAO.findMessageIds(threadId, session.getUser())
            .switchIfEmpty(Flux.error(new ThreadNotFoundException(threadId)));
    }

    private Set<MimeMessageId> buildMimeMessageIdSet(Optional<MimeMessageId> mimeMessageId, Optional<MimeMessageId> inReplyTo, Optional<List<MimeMessageId>> references) {
        Set<MimeMessageId> mimeMessageIds = new HashSet<>();
        mimeMessageId.ifPresent(mimeMessageIds::add);
        inReplyTo.ifPresent(mimeMessageIds::add);
        references.ifPresent(mimeMessageIds::addAll);
        return mimeMessageIds;
    }
}
