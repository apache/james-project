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
package org.apache.james.mailbox.spamassassin;

import java.io.InputStream;

import javax.inject.Inject;

import org.apache.james.mailbox.Role;
import org.apache.james.mailbox.store.event.EventFactory;
import org.apache.james.mailbox.store.event.SpamEventListener;
import org.apache.james.mailbox.store.mail.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.github.steveash.guavate.Guavate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

public class SpamAssassinListener implements SpamEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(SpamAssassinListener.class);

    private final SpamAssassin spamAssassin;

    @Inject
    public SpamAssassinListener(SpamAssassin spamAssassin) {
        this.spamAssassin = spamAssassin;
    }

    @Override
    public ListenerType getType() {
        return ListenerType.ONCE;
    }

    @Override
    public ExecutionMode getExecutionMode() {
        return ExecutionMode.ASYNCHRONOUS;
    }

    @Override
    public void event(Event event) {
        LOGGER.debug("Event {} received in listener.", event);
        if (event instanceof EventFactory.AddedImpl) {
            EventFactory.AddedImpl addedToMailboxEvent = (EventFactory.AddedImpl) event;
            if (isEventOnSpamMailbox(addedToMailboxEvent)) {
                LOGGER.debug("Spam event detected");
                ImmutableList<InputStream> messages = addedToMailboxEvent.getAvailableMessages()
                    .values()
                    .stream()
                    .map(Throwing.function(Message::getFullContent))
                    .collect(Guavate.toImmutableList());
                spamAssassin.learnSpam(messages);
            }
        }
    }

    @VisibleForTesting
    boolean isEventOnSpamMailbox(Event event) {
        return Role.from(event.getMailboxPath().getName())
            .filter(role -> role.equals(Role.SPAM))
            .isPresent();
    }
}
