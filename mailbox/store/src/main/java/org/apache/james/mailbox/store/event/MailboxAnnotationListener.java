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
package org.apache.james.mailbox.store.event;

import jakarta.inject.Inject;

import org.apache.james.events.Event;
import org.apache.james.events.EventListener;
import org.apache.james.events.Group;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.events.MailboxEvents.MailboxDeletion;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.mail.AnnotationMapper;
import org.reactivestreams.Publisher;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class MailboxAnnotationListener implements EventListener.ReactiveGroupEventListener {
    public static final class MailboxAnnotationListenerGroup extends Group {

    }

    public static final Group GROUP = new MailboxAnnotationListenerGroup();

    private final MailboxSessionMapperFactory mailboxSessionMapperFactory;
    private final SessionProvider sessionProvider;

    @Inject
    public MailboxAnnotationListener(MailboxSessionMapperFactory mailboxSessionMapperFactory, SessionProvider sessionProvider) {
        this.mailboxSessionMapperFactory = mailboxSessionMapperFactory;
        this.sessionProvider = sessionProvider;
    }

    @Override
    public boolean isHandling(Event event) {
        return event instanceof MailboxDeletion;
    }

    @Override
    public Group getDefaultGroup() {
        return GROUP;
    }

    @Override
    public Publisher<Void> reactiveEvent(Event event) {
        if (event instanceof MailboxDeletion) {
            MailboxSession mailboxSession = sessionProvider.createSystemSession(event.getUsername());
            AnnotationMapper annotationMapper = mailboxSessionMapperFactory.getAnnotationMapper(mailboxSession);
            MailboxId mailboxId = ((MailboxDeletion) event).getMailboxId();

            return Flux.from(annotationMapper.getAllAnnotationsReactive(mailboxId))
                .flatMap(annotation -> Mono.from(annotationMapper.deleteAnnotationReactive(mailboxId, annotation.getKey())))
                .then()
                .doFinally(any -> mailboxSessionMapperFactory.endProcessingRequest(mailboxSession));
        }
        return Mono.empty();
    }
}
