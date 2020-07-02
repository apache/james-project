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

package org.apache.james.jmap.event;

import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.RightManager;
import org.apache.james.mailbox.acl.ACLDiff;
import org.apache.james.mailbox.events.Event;
import org.apache.james.mailbox.events.Group;
import org.apache.james.mailbox.events.MailboxListener;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxACL.Entry;
import org.apache.james.mailbox.model.MailboxACL.Right;
import org.apache.james.mailbox.model.MailboxPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PropagateLookupRightListener implements MailboxListener.GroupMailboxListener {
    public static class PropagateLookupRightListenerGroup extends Group {

    }

    private static final Logger LOGGER = LoggerFactory.getLogger(PropagateLookupRightListener.class);
    private static final Group GROUP = new PropagateLookupRightListenerGroup();

    private final RightManager rightManager;
    private final MailboxManager mailboxManager;

    @Inject
    public PropagateLookupRightListener(RightManager rightManager, MailboxManager mailboxManager) {
        this.rightManager = rightManager;
        this.mailboxManager = mailboxManager;
    }

    @Override
    public Group getDefaultGroup() {
        return GROUP;
    }

    @Override
    public void event(Event event) throws MailboxException {
        MailboxSession mailboxSession = createMailboxSession(event);

        if (event instanceof MailboxACLUpdated) {
            MailboxACLUpdated aclUpdateEvent = (MailboxACLUpdated) event;
            MailboxPath mailboxPath = mailboxManager.getMailbox(aclUpdateEvent.getMailboxId(), mailboxSession).getMailboxPath();

            updateLookupRightOnParent(mailboxSession, mailboxPath, aclUpdateEvent.getAclDiff());
        } else if (event instanceof MailboxRenamed) {
            MailboxRenamed renamedEvent = (MailboxRenamed) event;
            updateLookupRightOnParent(mailboxSession, renamedEvent.getNewPath());
        }
    }

    private MailboxSession createMailboxSession(Event event) throws MailboxException {
        return mailboxManager.createSystemSession(event.getUsername());
    }

    private void updateLookupRightOnParent(MailboxSession session, MailboxPath path) throws MailboxException {
        MailboxACL acl = rightManager.listRights(path, session);
        listAncestors(session, path)
            .forEach(parentMailboxPath ->
                updateLookupRight(
                    session,
                    parentMailboxPath,
                    acl.getEntries()
                        .entrySet()
                        .stream()
                        .map(entry -> new Entry(entry.getKey(), entry.getValue()))));
    }

    private void updateLookupRightOnParent(MailboxSession mailboxSession, MailboxPath mailboxPath, ACLDiff aclDiff) {
        listAncestors(mailboxSession, mailboxPath)
            .forEach(path ->
                updateLookupRight(
                    mailboxSession, path,
                    Stream.concat(aclDiff.addedEntries(), aclDiff.changedEntries())
                ));
    }

    private void updateLookupRight(MailboxSession session, MailboxPath mailboxPath, Stream<Entry> entries) {
        entries
            .filter(entry -> !entry.getKey().isNegative())
            .filter(entry -> entry.getValue().contains(Right.Lookup))
            .forEach(entry -> applyLookupRight(session, mailboxPath, entry));
    }

    private Stream<MailboxPath> listAncestors(MailboxSession mailboxSession, MailboxPath mailboxPath) {
        return mailboxPath.getHierarchyLevels(mailboxSession.getPathDelimiter())
            .stream()
            .filter(Predicate.not(Predicate.isEqual(mailboxPath)));
    }

    private void applyLookupRight(MailboxSession session, MailboxPath mailboxPath, Entry entry) {
        try {
            rightManager.applyRightsCommand(mailboxPath,
                MailboxACL.command()
                    .rights(Right.Lookup)
                    .key(entry.getKey())
                    .asAddition(),
                session);
        } catch (MailboxException e) {
            LOGGER.error(String.format("Mailbox '%s' does not exist, user '%s' cannot share mailbox",
                mailboxPath,
                session.getUser().asString()), e);
        }
    }
}
