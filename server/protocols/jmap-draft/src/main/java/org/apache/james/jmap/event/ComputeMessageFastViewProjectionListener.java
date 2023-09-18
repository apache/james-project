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

import static org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY;

import java.io.IOException;

import javax.inject.Inject;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.events.Event;
import org.apache.james.events.EventListener;
import org.apache.james.events.Group;
import org.apache.james.jmap.api.projections.MessageFastViewPrecomputedProperties;
import org.apache.james.jmap.api.projections.MessageFastViewProjection;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.events.MailboxEvents.Added;
import org.apache.james.mailbox.events.MailboxEvents.Expunged;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageResult;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ComputeMessageFastViewProjectionListener implements EventListener.ReactiveGroupEventListener {
    public static class ComputeMessageFastViewProjectionListenerGroup extends Group {

    }

    static final Group GROUP = new ComputeMessageFastViewProjectionListenerGroup();

    private final MessageIdManager messageIdManager;
    private final MessageFastViewProjection messageFastViewProjection;
    private final SessionProvider sessionProvider;
    private final MessageFastViewPrecomputedProperties.Factory messageFastViewPrecomputedPropertiesFactory;

    @Inject
    public ComputeMessageFastViewProjectionListener(SessionProvider sessionProvider, MessageIdManager messageIdManager,
                                                    MessageFastViewProjection messageFastViewProjection,
                                                    MessageFastViewPrecomputedProperties.Factory messageFastViewPrecomputedPropertiesFactory) {
        this.sessionProvider = sessionProvider;
        this.messageIdManager = messageIdManager;
        this.messageFastViewProjection = messageFastViewProjection;
        this.messageFastViewPrecomputedPropertiesFactory = messageFastViewPrecomputedPropertiesFactory;
    }

    @Override
    public Group getDefaultGroup() {
        return GROUP;
    }

    @Override
    public Mono<Void> reactiveEvent(Event event) {
        if (event instanceof Added) {
            MailboxSession session = sessionProvider.createSystemSession(event.getUsername());
            return handleAddedEvent((Added) event, session);
        }
        if (event instanceof Expunged) {
            MailboxSession session = sessionProvider.createSystemSession(event.getUsername());
            return handedExpungedEvent((Expunged) event, session);
        }
        return Mono.empty();
    }


    @Override
    public boolean isHandling(Event event) {
        return event instanceof Added
            || event instanceof Expunged;
    }

    private Mono<Void> handleAddedEvent(Added addedEvent, MailboxSession session) {
        return Flux.from(messageIdManager.getMessagesReactive(addedEvent.getMessageIds(), FetchGroup.FULL_CONTENT, session))
            .flatMap(Throwing.function(messageResult -> Mono.fromCallable(
                () -> Pair.of(messageResult.getMessageId(),
                    computeFastViewPrecomputedProperties(messageResult)))), DEFAULT_CONCURRENCY)
            .flatMap(message -> messageFastViewProjection.store(message.getKey(), message.getValue()), DEFAULT_CONCURRENCY)
            .then();
    }

    @VisibleForTesting
    MessageFastViewPrecomputedProperties computeFastViewPrecomputedProperties(MessageResult messageResult) throws MailboxException, IOException {
        return messageFastViewPrecomputedPropertiesFactory.from(messageResult);
    }

    private Mono<Void> handedExpungedEvent(Expunged expunged, MailboxSession session) {
        ImmutableSet<MessageId> expungedMessageIds = expunged.getMessageIds();
        return Mono.from(messageIdManager.accessibleMessagesReactive(expungedMessageIds, session))
            .flatMapIterable(accessibleMessageIds -> CollectionUtils.subtract(expungedMessageIds, accessibleMessageIds))
            .flatMap(messageFastViewProjection::delete, DEFAULT_CONCURRENCY)
            .then();
    }

}
