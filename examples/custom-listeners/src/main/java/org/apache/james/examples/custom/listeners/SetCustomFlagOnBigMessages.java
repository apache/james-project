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

package org.apache.james.examples.custom.listeners;

import javax.inject.Inject;

import jakarta.mail.Flags;

import org.apache.james.events.Event;
import org.apache.james.events.EventListener;
import org.apache.james.events.Group;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageManager.FlagsUpdateMode;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.events.MailboxEvents.Added;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MessageRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Listener to determine the size of added messages.
 *
 * If the size is greater or equals than the BIG_MESSAGE size threshold ({@value ONE_MB}).
 * Then it will be considered as a big message and added BIG_MESSAGE {@value BIG_MESSAGE} flag
 *
 */
class SetCustomFlagOnBigMessages implements EventListener.GroupEventListener {
    public static class PositionCustomFlagOnBigMessagesGroup extends Group {

    }

    private static final PositionCustomFlagOnBigMessagesGroup GROUP = new PositionCustomFlagOnBigMessagesGroup();
    private static final Logger LOGGER = LoggerFactory.getLogger(SetCustomFlagOnBigMessages.class);

    static final long ONE_MB = 1000L * 1000L;

    static String BIG_MESSAGE = "BIG_MESSAGE";

    private final MailboxManager mailboxManager;

    @Inject
    SetCustomFlagOnBigMessages(MailboxManager mailboxManager) {
        this.mailboxManager = mailboxManager;
    }

    @Override
    public void event(Event event) {
        if (event instanceof Added) {
            Added addedEvent = (Added) event;
            addedEvent.getUids().stream()
                .filter(messageUid -> isBig(addedEvent, messageUid))
                .forEach(messageUid -> setBigMessageFlag(addedEvent, messageUid));
        }
    }

    private boolean isBig(Added addedEvent, MessageUid messageUid) {
        return addedEvent.getMetaData(messageUid).getSize() >= ONE_MB;
    }

    private void setBigMessageFlag(Added addedEvent, MessageUid messageUid) {
        try {
            MailboxSession session = mailboxManager.createSystemSession(addedEvent.getUsername());
            MessageManager messageManager = mailboxManager.getMailbox(addedEvent.getMailboxId(), session);

            messageManager.setFlags(
                new Flags(BIG_MESSAGE),
                FlagsUpdateMode.ADD,
                MessageRange.one(messageUid),
                session);
            mailboxManager.endProcessingRequest(session);
        } catch (MailboxException e) {
            LOGGER.error("error happens when adding '{}' flag to the message with uid {} in mailbox {} of user {}",
                BIG_MESSAGE, messageUid.asLong(), addedEvent.getMailboxId(), addedEvent.getUsername().asString(), e);
        }
    }

    @Override
    public Group getDefaultGroup() {
        return GROUP;
    }
}
