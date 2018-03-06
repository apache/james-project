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

package org.apache.james.mailbox.indexer.registrations;

import java.util.List;

import org.apache.james.mailbox.Event;
import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.indexer.events.FlagsMessageEvent;
import org.apache.james.mailbox.indexer.events.ImpactingMessageEvent;
import org.apache.james.mailbox.indexer.events.MessageDeletedEvent;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.UpdatedFlags;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

public class MailboxRegistration implements MailboxListener {

    private final Multimap<MessageUid, ImpactingMessageEvent> impactingMessageEvents;
    private final MailboxPath mailboxPath;

    public MailboxRegistration(MailboxPath mailboxPath) {
        this.impactingMessageEvents = Multimaps.synchronizedMultimap(ArrayListMultimap.<MessageUid, ImpactingMessageEvent>create());
        this.mailboxPath = mailboxPath;
    }

    @Override
    public ListenerType getType() {
        return ListenerType.MAILBOX;
    }

    @Override
    public ExecutionMode getExecutionMode() {
        return ExecutionMode.SYNCHRONOUS;
    }

    public List<ImpactingMessageEvent> getImpactingEvents(MessageUid uid) {
        return ImmutableList.copyOf(impactingMessageEvents.get(uid));
    }

    @Override
    public void event(Event event) {
        if (event instanceof FlagsUpdated) {
            for (UpdatedFlags updatedFlags : ((FlagsUpdated) event).getUpdatedFlags()) {
                impactingMessageEvents.put(updatedFlags.getUid(), new FlagsMessageEvent(mailboxPath, updatedFlags.getUid(), updatedFlags.getNewFlags()));
            }
        } else if (event instanceof Expunged) {
            for (MessageUid uid: ((Expunged) event).getUids()) {
                impactingMessageEvents.put(uid, new MessageDeletedEvent(mailboxPath, uid));
            }
        }
    }

}
