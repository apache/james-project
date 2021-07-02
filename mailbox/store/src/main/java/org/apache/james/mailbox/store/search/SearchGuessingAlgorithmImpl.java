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

package org.apache.james.mailbox.store.search;

import java.util.Collections;

import javax.inject.Inject;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MultimailboxesSearchQuery;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.StoreRightManager;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.MessageIdMapper;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;

import com.github.fge.lambdas.Throwing;
import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Flux;

public class SearchGuessingAlgorithmImpl implements SearchGuessingAlgorithm {
    private final MessageSearchIndex index;
    private final MailboxSessionMapperFactory mailboxSessionMapperFactory;
    private final StoreRightManager storeRightManager;

    @Inject
    public SearchGuessingAlgorithmImpl(MessageSearchIndex index, MailboxSessionMapperFactory mailboxSessionMapperFactory, StoreRightManager storeRightManager) {
        this.index = index;
        this.mailboxSessionMapperFactory = mailboxSessionMapperFactory;
        this.storeRightManager = storeRightManager;
    }

    @Override
    public Flux<MailboxMessage> searchMailboxMessages(MultimailboxesSearchQuery expression, MailboxSession session, long limit, MessageMapper.FetchType fetchType) throws MailboxException {
        MessageIdMapper messageIdMapper = mailboxSessionMapperFactory.getMessageIdMapper(session);

        return searchMessageIds(expression, session, limit)
            .flatMap(messageId -> messageIdMapper.findReactive(Collections.singletonList(messageId), fetchType));
    }

    @Override
    public Flux<MessageId> searchMessageIds(MultimailboxesSearchQuery expression, MailboxSession session, long limit) throws MailboxException {
        return getInMailboxIds(expression, session)
            .filter(id -> !expression.getNotInMailboxes().contains(id))
            .collect(Guavate.toImmutableSet())
            .flatMapMany(Throwing.function(ids -> index.search(session, ids, expression.getSearchQuery(), limit)));
    }

    private Flux<MailboxId> getInMailboxIds(MultimailboxesSearchQuery expression, MailboxSession session) {
        if (expression.getInMailboxes().isEmpty()) {
            return accessibleMailboxIds(expression.getNamespace(), MailboxACL.Right.Read, session);
        } else {
            return filterReadable(expression.getInMailboxes(), session)
                .filter(mailbox -> expression.getNamespace().keepAccessible(mailbox))
                .map(Mailbox::getMailboxId);
        }
    }

    private Flux<MailboxId> accessibleMailboxIds(MultimailboxesSearchQuery.Namespace namespace, MailboxACL.Right right, MailboxSession session) {
        MailboxMapper mailboxMapper = mailboxSessionMapperFactory.getMailboxMapper(session);
        Flux<MailboxId> baseMailboxes = mailboxMapper
            .userMailboxes(session.getUser());
        Flux<MailboxId> delegatedMailboxes = getDelegatedMailboxes(mailboxMapper, namespace, right, session);
        return Flux.concat(baseMailboxes, delegatedMailboxes)
            .distinct();
    }

    private Flux<MailboxId> getDelegatedMailboxes(MailboxMapper mailboxMapper, MultimailboxesSearchQuery.Namespace namespace,
                                                  MailboxACL.Right right, MailboxSession session) {
        if (!namespace.accessDelegatedMailboxes()) {
            return Flux.empty();
        }
        return mailboxMapper.findNonPersonalMailboxes(session.getUser(), right)
            .map(Mailbox::getMailboxId);
    }

    private Flux<Mailbox> filterReadable(ImmutableSet<MailboxId> inMailboxes, MailboxSession session) {
        MailboxMapper mailboxMapper = mailboxSessionMapperFactory.getMailboxMapper(session);
        return Flux.fromIterable(inMailboxes)
            .concatMap(mailboxMapper::findMailboxById)
            .filter(Throwing.<Mailbox>predicate(mailbox -> storeRightManager.hasRight(mailbox, MailboxACL.Right.Read, session)).sneakyThrow());
    }
}
