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

package org.apache.james.mailbox.cassandra;

import static org.apache.james.mailbox.cassandra.GhostMailbox.MAILBOX_ID;
import static org.apache.james.mailbox.cassandra.GhostMailbox.MAILBOX_NAME;
import static org.apache.james.mailbox.cassandra.GhostMailbox.TYPE;

import org.apache.james.events.Event;
import org.apache.james.events.EventListener;
import org.apache.james.events.Group;
import org.apache.james.mailbox.events.MailboxEvents.MailboxAdded;
import org.apache.james.mailbox.events.MailboxEvents.MailboxDeletion;
import org.apache.james.mailbox.events.MailboxEvents.MailboxRenamed;

/**
 * See https://issues.apache.org/jira/browse/MAILBOX-322 for reading about the Ghost mailbox bug.
 *
 * This class logs mailboxes writes in order to give context to analyse ghost mailbox bug.
 */
public class MailboxOperationLoggingListener implements EventListener.GroupEventListener {
    public static class MailboxOperationLoggingListenerGroup extends Group {

    }

    public static final String ADDED = "Added";
    public static final String REMOVED = "Removed";
    private static final Group GROUP = new MailboxOperationLoggingListenerGroup();

    @Override
    public Group getDefaultGroup() {
        return GROUP;
    }

    @Override
    public boolean isHandling(Event event) {
        return event instanceof MailboxRenamed || event instanceof MailboxDeletion || event instanceof MailboxAdded;
    }

    @Override
    public void event(Event event) {
        if (event instanceof MailboxRenamed) {
            MailboxRenamed mailboxRenamed = (MailboxRenamed) event;
            GhostMailbox.logger()
                .addField(MAILBOX_ID, mailboxRenamed.getMailboxId())
                .addField(MAILBOX_NAME, mailboxRenamed.getNewPath())
                .addField(TYPE, ADDED)
                .log(logger -> logger.info("Mailbox renamed event"));
        }
        if (event instanceof MailboxDeletion) {
            MailboxDeletion mailboxDeletion = (MailboxDeletion) event;
            GhostMailbox.logger()
                .addField(MAILBOX_ID, mailboxDeletion.getMailboxId())
                .addField(TYPE, REMOVED)
                .log(logger -> logger.info("Mailbox deleted event"));
        }
        if (event instanceof MailboxAdded) {
            MailboxAdded mailboxAdded = (MailboxAdded) event;
            GhostMailbox.logger()
                .addField(MAILBOX_ID, mailboxAdded.getMailboxId())
                .addField(TYPE, ADDED)
                .log(logger -> logger.info("Mailbox added event"));
        }
    }
}
