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
import java.util.Optional;

import org.apache.james.eventsourcing.Event;
import org.apache.james.eventsourcing.EventWithState;
import org.apache.james.eventsourcing.eventstore.History;
import org.apache.james.mailbox.acl.ACLDiff;
import org.apache.james.mailbox.exception.UnsupportedRightException;
import org.apache.james.mailbox.model.MailboxACL;

import com.google.common.collect.ImmutableList;

public class MailboxACLAggregate {

    private static class State {
        static State initial() {
            return new State(Optional.empty());
        }

        static State forAcl(MailboxACL acl) {
            return new State(Optional.of(acl));
        }

        private final Optional<MailboxACL> acl;

        private State(Optional<MailboxACL> acl) {
            this.acl = acl;
        }
    }

    public static MailboxACLAggregate load(MailboxAggregateId aggregateId, History history) {
        return new MailboxACLAggregate(aggregateId, history);
    }

    private final MailboxAggregateId aggregateId;
    private final History history;
    private State state;

    public MailboxACLAggregate(MailboxAggregateId aggregateId, History history) {
        this.aggregateId = aggregateId;
        this.history = history;

        this.state = State.initial();
        history.getEventsJava()
            .forEach(this::apply);
    }

    public List<EventWithState> deleteMailbox() {
        ACLUpdated event = new ACLUpdated(aggregateId, history.getNextEventId(),
            ACLDiff.computeDiff(state.acl.orElse(MailboxACL.EMPTY), MailboxACL.EMPTY));
        apply(event);
        return ImmutableList.of(EventWithState.noState(event));
    }

    public List<EventWithState> set(SetACLCommand setACLCommand) {
        ACLUpdated event = new ACLUpdated(aggregateId, history.getNextEventId(),
            ACLDiff.computeDiff(state.acl.orElse(MailboxACL.EMPTY), setACLCommand.getAcl()));
        apply(event);
        return ImmutableList.of(EventWithState.noState(event));
    }

    public List<EventWithState> update(UpdateACLCommand command) throws UnsupportedRightException {
        MailboxACL oldACL = state.acl.orElse(MailboxACL.EMPTY);
        ACLUpdated event = new ACLUpdated(command.getId(), history.getNextEventId(),
            ACLDiff.computeDiff(oldACL,
                oldACL.apply(command.getAclCommand())));
        apply(event);
        return ImmutableList.of(EventWithState.noState(event));
    }

    private void apply(Event event) {
        if (event instanceof ACLUpdated) {
            ACLUpdated aclUpdated = (ACLUpdated) event;
            state = State.forAcl(aclUpdated.getAclDiff().getNewACL());
        }
    }
}
