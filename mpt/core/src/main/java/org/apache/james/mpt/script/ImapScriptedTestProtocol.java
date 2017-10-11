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

package org.apache.james.mpt.script;

import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mpt.api.ImapHostSystem;

public class ImapScriptedTestProtocol extends GenericSimpleScriptedTestProtocol<ImapHostSystem, ImapScriptedTestProtocol> {

    private static class CreateMailbox implements PrepareCommand<ImapHostSystem> {

        final MailboxPath mailboxPath;

        CreateMailbox(MailboxPath mailboxPath) {
            this.mailboxPath = mailboxPath;
        }

        @Override
        public void prepare(ImapHostSystem system) throws Exception {
            system.createMailbox(mailboxPath);
        }
    }

    private static class CreateRights implements PrepareCommand<ImapHostSystem> {

        final MailboxPath mailboxPath;
        final String userName;
        final MailboxACL.Rfc4314Rights rights;

        public CreateRights(MailboxPath mailboxPath, String userName, MailboxACL.Rfc4314Rights rights) {
            this.mailboxPath = mailboxPath;
            this.userName = userName;
            this.rights = rights;
        }

        public void prepare(ImapHostSystem system) throws Exception {
            system.grantRights(mailboxPath, userName, rights);
        }
    }


    public ImapScriptedTestProtocol(String scriptDirectory, ImapHostSystem hostSystem) throws Exception {
        super(scriptDirectory, hostSystem);
    }

    public ImapScriptedTestProtocol withMailbox(MailboxPath mailboxPath) {
        return withPreparedCommand(new CreateMailbox(mailboxPath));
    }

    public ImapScriptedTestProtocol withRights(MailboxPath mailboxPath, String userName, MailboxACL.Rfc4314Rights rights) {
        return withPreparedCommand(new CreateRights(mailboxPath, userName, rights));
    }
}