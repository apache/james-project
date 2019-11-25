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

package org.apache.james.jmap.draft.methods;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import javax.inject.Inject;
import javax.mail.Flags;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager.FlagsUpdateMode;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.Header;
import org.apache.james.mailbox.model.Headers;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.MultimailboxesSearchQuery;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.util.streams.Iterators;
import org.apache.mailet.base.RFC2822Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.github.steveash.guavate.Guavate;
import com.google.common.collect.Iterables;

public class ReferenceUpdater {
    public static final String X_FORWARDED_ID_HEADER = "X-Forwarded-Message-Id";
    public static final Flags FORWARDED_FLAG = new Flags("$Forwarded");

    private static final Logger logger = LoggerFactory.getLogger(ReferenceUpdater.class);

    private final MessageIdManager messageIdManager;
    private final MailboxManager mailboxManager;

    @Inject
    public ReferenceUpdater(MessageIdManager messageIdManager, MailboxManager mailboxManager) {
        this.messageIdManager = messageIdManager;
        this.mailboxManager = mailboxManager;
    }

    public void updateReferences(Headers headers, MailboxSession session) throws MailboxException {
        Map<String, String> headersAsMap = Iterators.toStream(headers.headers())
            .collect(Guavate.toImmutableMap(Header::getName, Header::getValue));
        updateReferences(headersAsMap, session);
    }

    public void updateReferences(Map<String, String> headers, MailboxSession session) throws MailboxException {
        Optional<String> inReplyToId = Optional.ofNullable(headers.get(RFC2822Headers.IN_REPLY_TO));
        Optional<String> forwardedId = Optional.ofNullable(headers.get(X_FORWARDED_ID_HEADER));
        inReplyToId.ifPresent(Throwing.consumer((String id) -> updateAnswered(id, session)).sneakyThrow());
        forwardedId.ifPresent(Throwing.consumer((String id) -> updateForwarded(id, session)).sneakyThrow());
    }

    private void updateAnswered(String messageId, MailboxSession session) throws MailboxException {
        updateFlag(messageId, session, new Flags(Flags.Flag.ANSWERED));
    }

    private void updateForwarded(String messageId, MailboxSession session) throws MailboxException {
        updateFlag(messageId, session, FORWARDED_FLAG);
    }

    private void updateFlag(String messageId, MailboxSession session, Flags flag) throws MailboxException {
        int limit = 2;
        MultimailboxesSearchQuery searchByRFC822MessageId = MultimailboxesSearchQuery
            .from(new SearchQuery(SearchQuery.mimeMessageID(messageId)))
            .build();
        List<MessageId> references = mailboxManager.search(searchByRFC822MessageId, session, limit);
        try {
            MessageId reference = Iterables.getOnlyElement(references);
            List<MailboxId> mailboxIds = messageIdManager.getMessages(references, FetchGroup.MINIMAL, session).stream()
                .map(MessageResult::getMailboxId)
                .collect(Guavate.toImmutableList());
            messageIdManager.setFlags(flag, FlagsUpdateMode.ADD, reference, mailboxIds, session);
        } catch (NoSuchElementException e) {
            logger.info("Unable to find a message with this Mime Message Id: " + messageId);
        } catch (IllegalArgumentException e) {
            logger.info("Too many messages are matching this Mime Message Id: " + messageId);
        }
    }
}
