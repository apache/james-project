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

import org.apache.commons.lang3.tuple.Triple;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.RightManager;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.postgres.mail.dao.PostgresMailboxMessageDAO;
import org.apache.james.mailbox.store.ThreadInformation;
import org.apache.james.mailbox.store.mail.ThreadIdGuessingAlgorithm;

import javax.inject.Inject;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresThreadIdGuessingAlgorithm implements ThreadIdGuessingAlgorithm {
    private final PostgresMailboxMessageDAO.Factory mailboxMessageDAO;
    private final RightManager rightManager;

    @Inject
    public PostgresThreadIdGuessingAlgorithm(PostgresMailboxMessageDAO.Factory mailboxMessageDAO, RightManager rightManager) {
        this.rightManager = rightManager;
        this.mailboxMessageDAO = mailboxMessageDAO;
    }

    @Override
    public Mono<ThreadId> guessThreadIdReactive(MessageId messageId, ThreadInformation threadInformation, MailboxSession session) {
        return mailboxMessageDAO.create(session.getUser().getDomainPart())
            .retrieveByMimeMessageId(threadInformation.hash())
            .filterWhen(triple -> Mono.from(rightManager.myRights(triple.getMiddle(), session))
                .map(rights -> rights.contains(MailboxACL.Right.Lookup)))
            .map(Triple::getLeft)
            .next();
    }

    @Override
    public Flux<MessageId> getMessageIdsInThread(ThreadId threadId, MailboxSession session) {
        return mailboxMessageDAO.create(session.getUser().getDomainPart())
            .retrieveThread(threadId);
    }
}
