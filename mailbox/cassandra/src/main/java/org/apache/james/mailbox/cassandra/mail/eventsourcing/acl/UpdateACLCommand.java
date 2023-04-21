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

import org.apache.james.eventsourcing.Command;
import org.apache.james.eventsourcing.EventWithState;
import org.apache.james.eventsourcing.eventstore.EventStore;
import org.apache.james.mailbox.model.MailboxACL;
import org.reactivestreams.Publisher;

import com.github.fge.lambdas.Throwing;

import reactor.core.publisher.Mono;

public class UpdateACLCommand implements Command {
    public static class CommandHandler implements org.apache.james.eventsourcing.CommandHandler<UpdateACLCommand> {
        private final EventStore eventStore;

        public CommandHandler(EventStore eventStore) {
            this.eventStore = eventStore;
        }

        @Override
        public Class<UpdateACLCommand> handledClass() {
            return UpdateACLCommand.class;
        }

        @Override
        public Publisher<List<EventWithState>> handle(UpdateACLCommand command) {
            return Mono.from(eventStore.getEventsOfAggregate(command.getId()))
                .map(history -> MailboxACLAggregate.load(command.getId(), history))
                .map(Throwing.function(aggregate -> aggregate.update(command)));
        }
    }

    private final MailboxAggregateId id;
    private final MailboxACL.ACLCommand aclCommand;

    public UpdateACLCommand(MailboxAggregateId id, MailboxACL.ACLCommand aclCommand) {
        this.id = id;
        this.aclCommand = aclCommand;
    }

    public MailboxAggregateId getId() {
        return id;
    }

    public MailboxACL.ACLCommand getAclCommand() {
        return aclCommand;
    }
}
