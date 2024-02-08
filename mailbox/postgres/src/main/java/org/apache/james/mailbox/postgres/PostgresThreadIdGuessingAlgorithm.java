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

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.ThreadNotFoundException;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.postgres.mail.dao.PostgresThreadDAO;
import org.apache.james.mailbox.store.ThreadInformation;
import org.apache.james.mailbox.store.mail.ThreadIdGuessingAlgorithm;

import javax.inject.Inject;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresThreadIdGuessingAlgorithm implements ThreadIdGuessingAlgorithm {
    private final PostgresThreadDAO.Factory threadDAOFactory;

    @Inject
    public PostgresThreadIdGuessingAlgorithm(PostgresThreadDAO.Factory threadDAOFactory) {
        this.threadDAOFactory = threadDAOFactory;
    }

    @Override
    public Mono<ThreadId> guessThreadIdReactive(MessageId messageId, ThreadInformation threadInformation, MailboxSession session) {
        PostgresThreadDAO threadDAO = threadDAOFactory.create(session.getUser().getDomainPart());

        ThreadInformation.Hashed hashed = threadInformation.hash();

        return threadDAO.findThreads(session.getUser(), hashed.getHashMimeMessageIds())
            .filter(pair -> pair.getLeft().equals(hashed.getHashBaseSubject()))
            .next()
            .map(Pair::getRight)
            .switchIfEmpty(Mono.just(ThreadId.fromBaseMessageId(messageId)))
            .flatMap(threadId -> threadDAO
                .insertSome(session.getUser(), PostgresMessageId.class.cast(messageId), threadId, hashed)
                .then(Mono.just(threadId)));
    }

    @Override
    public Flux<MessageId> getMessageIdsInThread(ThreadId threadId, MailboxSession session) {
        PostgresThreadDAO threadDAO = threadDAOFactory.create(session.getUser().getDomainPart());
        return threadDAO.findMessageIds(threadId, session.getUser())
            .switchIfEmpty(Flux.error(new ThreadNotFoundException(threadId)));
    }
}
