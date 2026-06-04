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

package org.apache.james.imapserver.netty;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


@SuppressWarnings("checkstyle:membername")
class IMAPServerRenameMailboxTest extends AbstractIMAPServerTest {
    IMAPServer imapServer;
    private int port;

    @BeforeEach
    void beforeEach() throws Exception {
        imapServer = createImapServer("imapServer.xml");
        port = imapServer.getListenAddresses().get(0).getPort();
        MailboxSession mailboxSession = memoryIntegrationResources.getMailboxManager().createSystemSession(USER);
        memoryIntegrationResources.getMailboxManager()
            .createMailbox(MailboxPath.forUser(USER, "mailbox1"), mailboxSession);
        memoryIntegrationResources.getMailboxManager()
            .createMailbox(MailboxPath.forUser(USER, "mailbox2"), mailboxSession);
    }

    @AfterEach
    void tearDown() {
        imapServer.destroy();
    }

    @Test
    void renameShouldFailWhenTargetMailboxAlreadyExists() throws Exception {
        testIMAPClient.connect("127.0.0.1", port)
            .login(USER.asString(), USER_PASS);

        String response = testIMAPClient.sendCommand("RENAME mailbox1 mailbox2");

        assertThat(response).contains("NO RENAME failed. Mailbox already exists.");
    }

    @Test
    void renameShouldFailWhenRequestedMailboxDoesNotExist() throws Exception {
        testIMAPClient.connect("127.0.0.1", port)
            .login(USER.asString(), USER_PASS);

        String response = testIMAPClient.sendCommand("RENAME nonExistingMailbox newMailboxName");

        assertThat(response).contains("NO RENAME failed. Mailbox not found.");
    }

    @Test
    void renameShouldFailWhenInsufficientRightsOnSharedMailbox() throws Exception {
        // Create a mailbox for another user
        memoryIntegrationResources.getMailboxManager()
            .createMailbox(MailboxPath.forUser(USER2, "sharedMailbox.child1"),
                memoryIntegrationResources.getMailboxManager().createSystemSession(USER2));

        // Ensure the current user does not have the "delete mailbox" right on the shared mailbox
        memoryIntegrationResources.getMailboxManager()
            .applyRightsCommand(MailboxPath.forUser(USER2, "sharedMailbox"),
                MailboxACL.command()
                    .forUser(USER)
                    .rights(MailboxACL.Right.Lookup, MailboxACL.Right.Read, MailboxACL.Right.Insert,
                        MailboxACL.Right.CreateMailbox,
                        MailboxACL.Right.Administer, MailboxACL.Right.Write)
                    .asAddition(),
                memoryIntegrationResources.getMailboxManager().createSystemSession(USER2));
        memoryIntegrationResources.getMailboxManager()
            .applyRightsCommand(MailboxPath.forUser(USER2, "sharedMailbox.child1"),
                MailboxACL.command()
                    .forUser(USER)
                    .rights(MailboxACL.Right.Lookup, MailboxACL.Right.Read, MailboxACL.Right.Insert,
                        MailboxACL.Right.CreateMailbox,
                        MailboxACL.Right.Administer, MailboxACL.Right.Write)
                    .asAddition(),
                memoryIntegrationResources.getMailboxManager().createSystemSession(USER2));

        // Connect and attempt to rename the shared mailbox
        testIMAPClient.connect("127.0.0.1", port)
            .login(USER.asString(), USER_PASS);
        String response = testIMAPClient.sendCommand("RENAME #user.bobo.sharedMailbox.child1 #user.bobo.sharedMailbox.newChild");

        // Assert that the operation fails due to insufficient rights
        assertThat(response).contains("NO RENAME failed. Insufficient rights.");
    }
}
