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

package org.apache.james.mailbox.cassandra.mail.eventsourcing.acl;

import java.util.List;

import org.apache.james.event.MailboxAggregateId;
import org.apache.james.eventsourcing.Command;
import org.apache.james.eventsourcing.EventWithState;
import org.apache.james.eventsourcing.eventstore.EventStore;
import org.reactivestreams.Publisher;

import reactor.core.publisher.Mono;

public class DeleteMailboxCommand implements Command {
    public static class CommandHandler implements org.apache.james.eventsourcing.CommandHandler<DeleteMailboxCommand> {
        private final EventStore eventStore;

        public CommandHandler(EventStore eventStore) {
            this.eventStore = eventStore;
        }

        @Override
        public Class<DeleteMailboxCommand> handledClass() {
            return DeleteMailboxCommand.class;
        }

        @Override
        public Publisher<List<EventWithState>> handle(DeleteMailboxCommand command) {
            return Mono.from(eventStore.getEventsOfAggregate(command.getId()))
                .map(history -> MailboxACLAggregate.load(command.getId(), history))
                .map(MailboxACLAggregate::deleteMailbox);
        }
    }

    private final MailboxAggregateId id;

    public DeleteMailboxCommand(MailboxAggregateId id) {
        this.id = id;
    }

    public MailboxAggregateId getId() {
        return id;
    }
}
