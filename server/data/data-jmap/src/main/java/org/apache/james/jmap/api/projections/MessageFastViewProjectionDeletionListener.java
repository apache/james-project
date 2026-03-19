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

package org.apache.james.jmap.api.projections;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.events.Event;
import org.apache.james.events.EventListener;
import org.apache.james.events.Group;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.events.MailboxEvents;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.util.FunctionalUtils;
import org.reactivestreams.Publisher;

import reactor.core.publisher.Mono;

public class MessageFastViewProjectionDeletionListener implements EventListener.ReactiveGroupEventListener {
    public static class MessageFastViewProjectionDeletionListenerGroup extends Group {

    }

    private static final Group GROUP = new MessageFastViewProjectionDeletionListenerGroup();

    private final MailboxSession session;
    private final MessageFastViewProjection messageFastViewProjection;
    private final MailboxSessionMapperFactory mapperFactory;

    @Inject
    public MessageFastViewProjectionDeletionListener(MessageFastViewProjection messageFastViewProjection,
                                                     SessionProvider sessionProvider,
                                                     MailboxSessionMapperFactory mapperFactory) {
        this.session = sessionProvider.createSystemSession(Username.of(getClass().getName()));
        this.messageFastViewProjection = messageFastViewProjection;
        this.mapperFactory = mapperFactory;
    }

    @Override
    public Group getDefaultGroup() {
        return GROUP;
    }

    @Override
    public boolean isHandling(Event event) {
        return event instanceof MailboxEvents.MessageContentDeletionEvent;
    }

    @Override
    public Publisher<Void> reactiveEvent(Event event) {
        if (event instanceof MailboxEvents.MessageContentDeletionEvent contentDeletionEvent) {
            return isUnreferenced(contentDeletionEvent.messageId(), contentDeletionEvent.mailboxId())
                .filter(Boolean::booleanValue)
                .flatMap(any -> Mono.from(messageFastViewProjection.delete(contentDeletionEvent.messageId())));
        }

        return Mono.empty();
    }

    private Mono<Boolean> isUnreferenced(MessageId messageId,
                                         MailboxId excludedMailboxId) {
        return mapperFactory.getMessageIdMapper(session)
            .findMailboxesReactive(messageId)
            .filter(mailboxId -> !mailboxId.equals(excludedMailboxId))
            .hasElements()
            .map(FunctionalUtils.negate());
    }

}
