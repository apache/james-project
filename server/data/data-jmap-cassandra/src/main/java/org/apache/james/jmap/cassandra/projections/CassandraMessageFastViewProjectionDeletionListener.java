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

package org.apache.james.jmap.cassandra.projections;

import jakarta.inject.Inject;

import org.apache.james.events.Event;
import org.apache.james.events.EventListener;
import org.apache.james.events.Group;
import org.apache.james.jmap.api.projections.MessageFastViewProjection;
import org.apache.james.mailbox.events.MailboxEvents;
import org.reactivestreams.Publisher;

import reactor.core.publisher.Mono;

public class CassandraMessageFastViewProjectionDeletionListener implements EventListener.ReactiveGroupEventListener {
    public static class CassandraMessageFastViewProjectionDeletionListenerGroup extends Group {

    }

    private static final Group GROUP = new CassandraMessageFastViewProjectionDeletionListenerGroup();

    private final MessageFastViewProjection messageFastViewProjection;

    @Inject
    public CassandraMessageFastViewProjectionDeletionListener(MessageFastViewProjection messageFastViewProjection) {
        this.messageFastViewProjection = messageFastViewProjection;
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
            return Mono.from(messageFastViewProjection.delete(contentDeletionEvent.messageId()));
        }

        return Mono.empty();
    }

}
