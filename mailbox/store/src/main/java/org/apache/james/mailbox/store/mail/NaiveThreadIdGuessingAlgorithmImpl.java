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

import java.util.List;
import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MultimailboxesSearchQuery;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.MimeMessageId;
import org.apache.james.mailbox.store.mail.model.Subject;
import org.apache.james.mailbox.store.search.SearchGuessingAlgorithm;

import reactor.core.publisher.Flux;

public class NaiveThreadIdGuessingAlgorithmImpl implements ThreadIdGuessingAlgorithm {
    private final SearchGuessingAlgorithm searchGuessingAlgorithm;

    @Inject
    public NaiveThreadIdGuessingAlgorithmImpl(SearchGuessingAlgorithm searchGuessingAlgorithm) {
        this.searchGuessingAlgorithm = searchGuessingAlgorithm;
    }

    @Override
    public ThreadId guessThreadId(Username username, MessageId messageId, Optional<MimeMessageId> thisMimeMessageId, Optional<MimeMessageId> inReplyTo,
                                  Optional<List<MimeMessageId>> references, Optional<Subject> subject, MailboxSession session) throws MailboxException {
        MultimailboxesSearchQuery allMailboxesQuery = MultimailboxesSearchQuery
            .from(SearchQuery.matchAll())
            .build();
        Flux<MailboxMessage> messageFlux = searchGuessingAlgorithm.searchMailboxMessages(allMailboxesQuery, session, Long.MAX_VALUE, MessageMapper.FetchType.Headers);

        return ThreadId.fromBaseMessageId(messageId);
    }
}
