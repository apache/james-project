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

package org.apache.james.modules;

import static org.apache.james.mailbox.MessageManager.MailboxMetaData.RecentMode.IGNORE;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxACL.ACLCommand;
import org.apache.james.mailbox.model.MailboxACL.Rfc4314Rights;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.probe.ACLProbe;
import org.apache.james.utils.GuiceProbe;

public class ACLProbeImpl implements GuiceProbe, ACLProbe {
    private static final boolean RESET_RECENT = false;
    private final MailboxManager mailboxManager;

    @Inject
    private ACLProbeImpl(MailboxManager mailboxManager) {
        this.mailboxManager = mailboxManager;
    }

    @Override
    public void replaceRights(MailboxPath mailboxPath, String targetUser, Rfc4314Rights rights) throws MailboxException {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(mailboxPath.getUser());

        ACLCommand command = MailboxACL.command().forUser(Username.of(targetUser)).rights(rights).asReplacement();
        mailboxManager.applyRightsCommand(mailboxPath, command, mailboxSession);
    }

    @Override
    public void addRights(MailboxPath mailboxPath, String targetUser, Rfc4314Rights rights) throws MailboxException {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(mailboxPath.getUser());
        ACLCommand command = MailboxACL.command().forUser(Username.of(targetUser)).rights(rights).asAddition();

        mailboxManager.applyRightsCommand(mailboxPath, command, mailboxSession);
    }

    @Override
    public MailboxACL retrieveRights(MailboxPath mailboxPath) throws MailboxException {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(mailboxPath.getUser());

        return mailboxManager.getMailbox(mailboxPath, mailboxSession)
            .getMetaData(IGNORE, mailboxSession, MessageManager.MailboxMetaData.FetchGroup.NO_COUNT)
            .getACL();
    }
}