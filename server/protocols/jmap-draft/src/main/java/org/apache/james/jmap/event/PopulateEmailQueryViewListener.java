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

package org.apache.james.jmap.event;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.jmap.api.projections.EmailQueryView;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.events.Event;
import org.apache.james.mailbox.events.Group;
import org.apache.james.mailbox.events.MailboxListener.ReactiveGroupMailboxListener;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.stream.MimeConfig;
import org.reactivestreams.Publisher;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PopulateEmailQueryViewListener implements ReactiveGroupMailboxListener {
    public static class PopulateEmailQueryViewListenerGroup extends Group {

    }

    static final Group GROUP = new PopulateEmailQueryViewListenerGroup();
    private static final int CONCURRENCY = 5;

    private final MessageIdManager messageIdManager;
    private final EmailQueryView view;
    private final SessionProvider sessionProvider;

    @Inject
    public PopulateEmailQueryViewListener(MessageIdManager messageIdManager, EmailQueryView view, SessionProvider sessionProvider) {
        this.messageIdManager = messageIdManager;
        this.view = view;
        this.sessionProvider = sessionProvider;
    }

    @Override
    public Group getDefaultGroup() {
        return GROUP;
    }

    @Override
    public boolean isHandling(Event event) {
        return event instanceof Added
            || event instanceof Expunged
            || event instanceof MailboxDeletion;
    }

    @Override
    public Publisher<Void> reactiveEvent(Event event) {
        if (event instanceof Added) {
            return handleAdded((Added) event);
        }
        if (event instanceof Expunged) {
            return handleExpunged((Expunged) event);
        }
        if (event instanceof MailboxDeletion) {
            return handleMailboxDeletion((MailboxDeletion) event);
        }
        return Mono.empty();
    }

    public Publisher<Void> handleMailboxDeletion(MailboxDeletion mailboxDeletion) {
        return view.delete(mailboxDeletion.getMailboxId());
    }

    public Publisher<Void> handleExpunged(Expunged expunged) {
        return Flux.fromStream(expunged.getUids().stream()
            .map(uid -> expunged.getMetaData(uid).getMessageId()))
            .concatMap(messageId -> view.delete(expunged.getMailboxId(), messageId))
            .then();
    }

    public Mono<Void> handleAdded(Added added) {
        MailboxSession session = sessionProvider.createSystemSession(added.getUsername());
        return Flux.fromStream(added.getUids().stream()
            .map(added::getMetaData))
            .flatMap(messageMetaData -> handleAdded(added, messageMetaData, session), CONCURRENCY)
            .then();
    }

    public Mono<Void> handleAdded(Added added, MessageMetaData messageMetaData, MailboxSession session) {
        MessageId messageId = messageMetaData.getMessageId();
        ZonedDateTime receivedAt = ZonedDateTime.ofInstant(messageMetaData.getInternalDate().toInstant(), ZoneOffset.UTC);

        Mono<ZonedDateTime> sentAtMono = Flux.from(messageIdManager.getMessagesReactive(ImmutableList.of(messageId), FetchGroup.HEADERS, session))
            .next()
            .map(Throwing.function(messageResult -> Message.Builder
                .of()
                .use(MimeConfig.PERMISSIVE)
                .parse(messageResult.getFullContent().getInputStream())
                .build()))
            .map(message -> Optional.ofNullable(message.getDate()).orElse(messageMetaData.getInternalDate()))
            .map(date -> ZonedDateTime.ofInstant(date.toInstant(), ZoneOffset.UTC));

        return sentAtMono.flatMap(sentAt -> view.save(added.getMailboxId(), sentAt, receivedAt, messageId));
    }
}
