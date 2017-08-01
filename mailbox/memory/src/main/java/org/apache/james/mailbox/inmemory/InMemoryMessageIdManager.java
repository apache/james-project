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

package org.apache.james.mailbox.inmemory;

import java.util.HashSet;
import java.util.List;

import javax.inject.Inject;
import javax.mail.Flags;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageManager.FlagsUpdateMode;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.FetchGroupImpl;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MailboxQuery;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.MessageResult.FetchGroup;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;

public class InMemoryMessageIdManager implements MessageIdManager {

    private final MailboxManager mailboxManager;

    @Inject
    public InMemoryMessageIdManager(MailboxManager mailboxManager) {
        this.mailboxManager = mailboxManager;
    }
    
    @Override
    public void setFlags(Flags newState, FlagsUpdateMode flagsUpdateMode, MessageId messageId, List<MailboxId> mailboxIds, MailboxSession mailboxSession) throws MailboxException {
        for (MailboxId mailboxId: mailboxIds) {
            Optional<MessageResult> message = findMessageWithId(mailboxId, messageId, FetchGroupImpl.MINIMAL, mailboxSession);
            if (message.isPresent()) {
                mailboxManager.getMailbox(mailboxId, mailboxSession)
                    .setFlags(newState, flagsUpdateMode, message.get().getUid().toRange(), mailboxSession);
            }
        }
    }

    @Override
    public List<MessageResult> getMessages(List<MessageId> messages, FetchGroup fetchGroup, final MailboxSession mailboxSession) throws MailboxException {
        ImmutableList.Builder<MessageResult> builder = ImmutableList.builder();
        List<MailboxId> userMailboxIds = getUsersMailboxIds(mailboxSession);
        for (MailboxId mailboxId: userMailboxIds) {
            builder.addAll(retrieveMailboxMessages(mailboxId, messages, fetchGroup, mailboxSession));
        }
        return builder.build();
    }

    @Override
    public void delete(MessageId messageId, List<MailboxId> mailboxIds, MailboxSession mailboxSession) throws MailboxException {
        for (MailboxId mailboxId: mailboxIds) {
            Optional<MessageResult> maybeMessage = findMessageWithId(mailboxId, messageId, FetchGroupImpl.MINIMAL, mailboxSession);
            if (maybeMessage.isPresent()) {
                MessageRange range = maybeMessage.get().getUid().toRange();
                MessageManager messageManager = mailboxManager.getMailbox(mailboxId, mailboxSession);
                messageManager.setFlags(new Flags(Flags.Flag.DELETED), FlagsUpdateMode.ADD, range, mailboxSession);
                messageManager.expunge(range, mailboxSession);
            }
        }
    }

    @Override
    public void setInMailboxes(MessageId messageId, List<MailboxId> mailboxIds, MailboxSession mailboxSession) throws MailboxException {
        List<MessageResult> messages = getMessages(ImmutableList.of(messageId), FetchGroupImpl.MINIMAL, mailboxSession);

        filterOnMailboxSession(mailboxIds, mailboxSession);

        if (!messages.isEmpty()) {
            ImmutableSet<MailboxId> currentMailboxes = currentMailboxes(messages).toSet();

            HashSet<MailboxId> targetMailboxes = Sets.newHashSet(mailboxIds);
            List<MailboxId> mailboxesToRemove = ImmutableList.copyOf(Sets.difference(currentMailboxes, targetMailboxes));
            SetView<MailboxId> mailboxesToAdd = Sets.difference(targetMailboxes, currentMailboxes);

            MessageResult referenceMessage = Iterables.getLast(messages);
            for (MailboxId mailboxId: mailboxesToAdd) {
                MessageRange messageRange = referenceMessage.getUid().toRange();
                mailboxManager.copyMessages(messageRange, referenceMessage.getMailboxId(), mailboxId, mailboxSession);
                mailboxManager.getMailbox(mailboxId, mailboxSession)
                    .setFlags(referenceMessage.getFlags(), FlagsUpdateMode.REPLACE, messageRange, mailboxSession);
            }

            for (MessageResult message: messages) {
                delete(message.getMessageId(), mailboxesToRemove, mailboxSession);
            }
        }
    }

    private List<MailboxId> getUsersMailboxIds(final MailboxSession mailboxSession) throws MailboxException {
        return FluentIterable.from(mailboxManager.search(userMailboxes(mailboxSession), mailboxSession))
            .transform(MailboxMetaData::getId)
            .toList();
    }

    private MailboxQuery userMailboxes(MailboxSession mailboxSession) {
        return MailboxQuery.builder()
                .matchesAll()
                .username(mailboxSession.getUser().getUserName())
                .build();
    }

    private List<MessageResult> retrieveMailboxMessages(MailboxId mailboxId, List<MessageId> messages, FetchGroup fetchGroup, MailboxSession mailboxSession) throws MailboxException {
        ImmutableList.Builder<MessageResult> builder = ImmutableList.builder();
        for (MessageId messageId: messages) {
            Optional<MessageResult> maybeMessage = findMessageWithId(mailboxId, messageId, fetchGroup, mailboxSession);
            if (maybeMessage.isPresent()) {
                builder.add(maybeMessage.get());
            }
        }
        return builder.build();
    }

    private void filterOnMailboxSession(List<MailboxId> mailboxIds, MailboxSession mailboxSession) throws MailboxNotFoundException {
        boolean isForbidden = FluentIterable.from(mailboxIds)
            .firstMatch(findMailboxBelongsToAnotherSession(mailboxSession))
            .isPresent();

        if (isForbidden) {
            throw new MailboxNotFoundException("Mailbox does not belong to session");
        }
    }

    private Predicate<MailboxId> findMailboxBelongsToAnotherSession(final MailboxSession mailboxSession) {
        return input -> {
            try {
                MailboxPath currentMailbox = mailboxManager.getMailbox(input, mailboxSession).getMailboxPath();
                return !mailboxSession.getUser().isSameUser(currentMailbox.getUser());
            } catch (MailboxException e) {
                return true;
            }
        };
    }

    private FluentIterable<MailboxId> currentMailboxes(List<MessageResult> messages) {
        return FluentIterable.from(messages)
            .transform(MessageResult::getMailboxId);
    }

    private Optional<MessageResult> findMessageWithId(MailboxId mailboxId, MessageId messageId, FetchGroup fetchGroup, MailboxSession mailboxSession) throws MailboxException {
        return FluentIterable.from(retrieveAllMessages(mailboxId, fetchGroup, mailboxSession))
            .filter(filterByMessageId(messageId))
            .first();
    }

    private Predicate<MessageResult> filterByMessageId(final MessageId messageId) {
        return messageResult -> messageResult.getMessageId().equals(messageId);
    }

    private ImmutableList<MessageResult> retrieveAllMessages(MailboxId mailboxId, FetchGroup fetchGroup, MailboxSession mailboxSession) throws MailboxException {
        MessageManager messageManager = mailboxManager.getMailbox(mailboxId, mailboxSession);
        return ImmutableList.copyOf(messageManager.getMessages(MessageRange.all(), fetchGroup, mailboxSession));
    }

}
