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

package org.apache.james.mailbox;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import jakarta.mail.Flags;

import org.apache.james.mailbox.MessageManager.FlagsUpdateMode;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.DeleteResult;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageResult;
import org.reactivestreams.Publisher;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface MessageIdManager {
    default Publisher<ComposedMessageIdWithMetaData> messageMetadata(MessageId id, MailboxSession session) {
        return messagesMetadata(ImmutableList.of(id), session);
    }

    Publisher<ComposedMessageIdWithMetaData> messagesMetadata(Collection<MessageId> id, MailboxSession session);

    Set<MessageId> accessibleMessages(Collection<MessageId> messageIds, final MailboxSession mailboxSession) throws MailboxException;

    Publisher<Set<MessageId>> accessibleMessagesReactive(Collection<MessageId> messageIds, final MailboxSession mailboxSession);

    void setFlags(Flags newState, FlagsUpdateMode replace, MessageId messageId, List<MailboxId> mailboxIds, MailboxSession mailboxSession) throws MailboxException;

    Publisher<Void> setFlagsReactive(Flags newState, FlagsUpdateMode replace, MessageId messageId, List<MailboxId> mailboxIds, MailboxSession mailboxSession);

    List<MessageResult> getMessages(Collection<MessageId> messageIds, FetchGroup minimal, MailboxSession mailboxSession) throws MailboxException;

    default Publisher<MessageResult> getMessagesReactive(Collection<MessageId> messageIds, FetchGroup minimal, MailboxSession mailboxSession) {
        try {
            return Flux.fromIterable(getMessages(messageIds, minimal, mailboxSession));
        } catch (MailboxException e) {
            return Flux.error(e);
        }
    }

    DeleteResult delete(MessageId messageId, List<MailboxId> mailboxIds, MailboxSession mailboxSession) throws MailboxException;

    Publisher<DeleteResult> deleteReactive(List<MessageId> messageId, List<MailboxId> mailboxIds, MailboxSession mailboxSession);

    Publisher<DeleteResult> delete(Set<MessageId> messageId, MailboxSession mailboxSession);

    void setInMailboxes(MessageId messageId, Collection<MailboxId> mailboxIds, MailboxSession mailboxSession) throws MailboxException;

    Publisher<Void> setInMailboxesReactive(MessageId messageId, Collection<MailboxId> mailboxIds, MailboxSession mailboxSession);

    default List<MessageResult> getMessage(MessageId messageId, FetchGroup fetchGroup, MailboxSession mailboxSession) throws MailboxException {
        return getMessages(ImmutableList.of(messageId), fetchGroup, mailboxSession);
    }

    default DeleteResult delete(MessageId messageId, MailboxSession mailboxSession) {
        return Mono.from(delete(ImmutableSet.of(messageId), mailboxSession))
            .block();
    }

}
