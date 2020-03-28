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

package org.apache.james.mailbox.inmemory.mail;

import static org.apache.james.mailbox.store.mail.AbstractMessageMapper.UNLIMITED;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.mail.Flags;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.mailbox.MessageManager.FlagsUpdateMode;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.FlagsUpdateCalculator;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.MessageIdMapper;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;

import com.github.fge.lambdas.Throwing;
import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class InMemoryMessageIdMapper implements MessageIdMapper {
    private static final BinaryOperator<UpdatedFlags> KEEP_FIRST = (p, q) -> p;

    private final MailboxMapper mailboxMapper;
    private final InMemoryMessageMapper messageMapper;

    public InMemoryMessageIdMapper(MailboxMapper mailboxMapper, InMemoryMessageMapper messageMapper) {
        this.mailboxMapper = mailboxMapper;
        this.messageMapper = messageMapper;
    }

    @Override
    public List<MailboxMessage> find(Collection<MessageId> messageIds, MessageMapper.FetchType fetchType) {
        try {
            return mailboxMapper.list()
                .stream()
                .flatMap(Throwing.function(mailbox ->
                    ImmutableList.copyOf(
                        messageMapper.findInMailbox(mailbox, MessageRange.all(), fetchType, UNLIMITED))
                        .stream()))
                .filter(message -> messageIds.contains(message.getMessageId()))
                .collect(Guavate.toImmutableList());
        } catch (MailboxException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public List<MailboxId> findMailboxes(MessageId messageId) {
        return find(ImmutableList.of(messageId), MessageMapper.FetchType.Metadata)
            .stream()
            .map(MailboxMessage::getMailboxId)
            .collect(Guavate.toImmutableList());
    }

    @Override
    public void save(MailboxMessage mailboxMessage) throws MailboxException {
        Mailbox mailbox = mailboxMapper.findMailboxById(mailboxMessage.getMailboxId());
        messageMapper.save(mailbox, mailboxMessage);
    }

    @Override
    public void copyInMailbox(MailboxMessage mailboxMessage) throws MailboxException {
        boolean isAlreadyInMailbox = findMailboxes(mailboxMessage.getMessageId()).contains(mailboxMessage.getMailboxId());
        if (!isAlreadyInMailbox) {
            save(mailboxMessage);
        }
    }

    @Override
    public void delete(MessageId messageId) {
        find(ImmutableList.of(messageId), MessageMapper.FetchType.Metadata)
            .forEach(Throwing.consumer(
                message -> messageMapper.delete(
                    mailboxMapper.findMailboxById(message.getMailboxId()),
                    message)));
    }

    @Override
    public void delete(MessageId messageId, Collection<MailboxId> mailboxIds) {
        find(ImmutableList.of(messageId), MessageMapper.FetchType.Metadata)
            .stream()
            .filter(message -> mailboxIds.contains(message.getMailboxId()))
            .forEach(Throwing.consumer(
                message -> messageMapper.delete(
                    mailboxMapper.findMailboxById(message.getMailboxId()),
                    message)));
    }

    @Override
    public Map<MailboxId, UpdatedFlags> setFlags(MessageId messageId, List<MailboxId> mailboxIds,
                                                 Flags newState, FlagsUpdateMode updateMode) throws MailboxException {
        return find(ImmutableList.of(messageId), MessageMapper.FetchType.Metadata)
            .stream()
            .filter(message -> mailboxIds.contains(message.getMailboxId()))
            .map(updateMessage(newState, updateMode))
            .distinct()
            .collect(Guavate.toImmutableMap(
                Pair::getKey,
                Pair::getValue,
                KEEP_FIRST));
    }

    private Function<MailboxMessage, Pair<MailboxId, UpdatedFlags>> updateMessage(Flags newState, FlagsUpdateMode updateMode) {
        return Throwing.function((MailboxMessage message) -> {
            FlagsUpdateCalculator flagsUpdateCalculator = new FlagsUpdateCalculator(newState, updateMode);
            if (flagsUpdateCalculator.buildNewFlags(message.createFlags()).equals(message.createFlags())) {
                UpdatedFlags updatedFlags = UpdatedFlags.builder()
                    .modSeq(message.getModSeq())
                    .uid(message.getUid())
                    .oldFlags(message.createFlags())
                    .newFlags(newState)
                    .build();
                return Pair.of(message.getMailboxId(), updatedFlags);
            }
            return Pair.of(message.getMailboxId(),
                messageMapper.updateFlags(
                    mailboxMapper.findMailboxById(message.getMailboxId()),
                    flagsUpdateCalculator,
                    message.getUid().toRange())
                    .next());
        });
    }
}
