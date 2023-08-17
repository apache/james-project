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
package org.apache.james.mpt.imapmailbox.suite;

import java.util.Locale;

import org.apache.james.core.Username;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mpt.api.HostSystem;
import org.apache.james.mpt.api.ImapHostSystem;
import org.apache.james.mpt.imapmailbox.GrantRightsOnHost;
import org.apache.james.mpt.imapmailbox.MailboxMessageAppender;
import org.apache.james.mpt.script.ImapScriptedTestProtocol;
import org.apache.james.mpt.script.SimpleScriptedTestProtocol;

public class ACLScriptedTestProtocol extends ImapScriptedTestProtocol {

    private static class GrantRightsCommand implements SimpleScriptedTestProtocol.PrepareCommand<HostSystem> {
        GrantRightsOnHost grantRightsOnHost;
        MailboxPath mailboxPath;
        Username username;
        MailboxACL.Rfc4314Rights rights;

        GrantRightsCommand(GrantRightsOnHost grantRightsOnHost, MailboxPath mailboxPath, Username username, MailboxACL.Rfc4314Rights rights) {
            this.grantRightsOnHost = grantRightsOnHost;
            this.mailboxPath = mailboxPath;
            this.username = username;
            this.rights = rights;
        }

        @Override
        public void prepare(HostSystem system) throws Exception {
            grantRightsOnHost.grantRights(mailboxPath, username, rights);
        }
    }
    
    private static class FillMailboxCommand implements SimpleScriptedTestProtocol.PrepareCommand<HostSystem> {
        MailboxMessageAppender mailboxMessageAppender;
        MailboxPath mailboxPath;

        FillMailboxCommand(MailboxMessageAppender mailboxMessageAppender, MailboxPath mailboxPath) {
            this.mailboxMessageAppender = mailboxMessageAppender;
            this.mailboxPath = mailboxPath;
        }

        @Override
        public void prepare(HostSystem system) throws Exception {
            mailboxMessageAppender.fillMailbox(mailboxPath);
        }
    }
    
    private final GrantRightsOnHost grantRightsOnHost;
    private final MailboxMessageAppender mailboxMessageAppender;

    public ACLScriptedTestProtocol(GrantRightsOnHost grantRightsOnHost, MailboxMessageAppender mailboxMessageAppender, String scriptDirectory, ImapHostSystem hostSystem) throws Exception {
        super(scriptDirectory, hostSystem);
        this.grantRightsOnHost = grantRightsOnHost;
        this.mailboxMessageAppender = mailboxMessageAppender;
    }

    public ACLScriptedTestProtocol withGrantRights(MailboxPath mailboxPath, Username username, MailboxACL.Rfc4314Rights rights) {
        return (ACLScriptedTestProtocol) withPreparedCommand(new GrantRightsCommand(grantRightsOnHost, mailboxPath, username, rights));
    }
    
    public ACLScriptedTestProtocol withFilledMailbox(MailboxPath otherUserMailbox) {
        return (ACLScriptedTestProtocol) withPreparedCommand(new FillMailboxCommand(mailboxMessageAppender, otherUserMailbox));
    }
    
    @Override
    public ACLScriptedTestProtocol withUser(String user, String password) {
        return (ACLScriptedTestProtocol) super.withUser(user, password);
    }

    @Override
    public ACLScriptedTestProtocol withUser(Username user, String password) {
        return (ACLScriptedTestProtocol) super.withUser(user, password);
    }
    
    @Override
    public ACLScriptedTestProtocol withLocale(Locale locale) {
        return (ACLScriptedTestProtocol) super.withLocale(locale);
    }
    
    @Override
    public ACLScriptedTestProtocol withMailbox(MailboxPath mailboxPath) {
        return (ACLScriptedTestProtocol) super.withMailbox(mailboxPath);
    }
}
