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

package org.apache.james.mailbox.store.search;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.SearchQuery;

import reactor.core.publisher.Flux;

/**
 * An index which can be used to search for MailboxMessage UID's that match a {@link SearchQuery}.
 * 
 * A developer should think of building an inverse-index for that.
 * 
 */
public interface MessageSearchIndex {

    /**
     * Return all uids of the previous indexed {@link Mailbox}'s which match the {@link SearchQuery}
     */
    Stream<MessageUid> search(MailboxSession session, Mailbox mailbox, SearchQuery searchQuery) throws MailboxException;

    /**
     * Return all uids of all {@link Mailbox}'s the current user has access to which match the {@link SearchQuery}
     */
    Flux<MessageId> search(MailboxSession session, Collection<MailboxId> mailboxIds, SearchQuery searchQuery, long limit) throws MailboxException;

    EnumSet<MailboxManager.SearchCapabilities> getSupportedCapabilities(EnumSet<MailboxManager.MessageCapabilities> messageCapabilities);

    class SearchResult {
        private final Optional<MessageId> messageId;
        private final MailboxId mailboxId;
        private final MessageUid messageUid;

        public SearchResult(Optional<MessageId> messageId, MailboxId mailboxId, MessageUid messageUid) {
            this.messageId = messageId;
            this.mailboxId = mailboxId;
            this.messageUid = messageUid;
        }

        public Optional<MessageId> getMessageId() {
            return messageId;
        }

        public MailboxId getMailboxId() {
            return mailboxId;
        }

        public MessageUid getMessageUid() {
            return messageUid;
        }
    }

}
