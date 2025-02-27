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
import org.apache.james.mpt.api.ImapHostSystem;
import org.apache.james.mpt.imapmailbox.ImapTestConstants;
import org.apache.james.mpt.imapmailbox.suite.base.BasicImapCommands;
import org.apache.james.mpt.script.ImapScriptedTestProtocol;
import org.apache.james.mpt.script.SimpleScriptedTestProtocol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public abstract class Rename implements ImapTestConstants {
    public static final Username OTHER_USER_NAME = Username.of("Boby");
    public static final String OTHER_USER_PASSWORD = "password";

    protected abstract ImapHostSystem createImapHostSystem();
    
    private ImapHostSystem system;
    private SimpleScriptedTestProtocol simpleScriptedTestProtocol;

    @BeforeEach
    public void setUp() throws Exception {
        system = createImapHostSystem();
        simpleScriptedTestProtocol = new SimpleScriptedTestProtocol("/org/apache/james/imap/scripts/", system)
                .withUser(USER, PASSWORD)
                .withLocale(Locale.US);
        BasicImapCommands.welcome(simpleScriptedTestProtocol);
        BasicImapCommands.authenticate(simpleScriptedTestProtocol);
        BasicImapCommands.prepareMailbox(simpleScriptedTestProtocol);
    }
    
    @Test
    public void testRenameUS() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.US)
            .run("Rename");
    }
    
    @Test
    public void testRenameKOREA() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.KOREA)
            .run("Rename");
    }

    @Test
    public void testRenameITALY() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.ITALY)
            .run("Rename");
    }

    @Test
    public void testRenameHierarchyUS() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.US)
            .run("RenameHierarchy");
    }

    @Test
    public void testRenameHierarchyKO() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.KOREA)
            .run("RenameHierarchy");
    }

    @Test
    public void testRenameHierarchyIT() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.ITALY)
            .run("RenameHierarchy");
    }

    @Test
    public void testRenameSelectedUS() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.US)
            .run("RenameSelected");
    }

    @Test
    public void testRenameSelectedIT() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.ITALY)
            .run("RenameSelected");
    }

    @Test
    public void testRenameSelectedKO() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.KOREA)
            .run("RenameSelected");
    }

    @Test
    public void testRenameInbox() throws Exception {
        simpleScriptedTestProtocol
            .run("RenameInbox");
    }
    
    @Test
    public void testRenameSharedMailbox() throws Exception {
        ImapScriptedTestProtocol scriptedTestProtocol = new ImapScriptedTestProtocol("/org/apache/james/imap/scripts/", system)
            .withUser(USER, PASSWORD)
            .withUser(OTHER_USER_NAME, OTHER_USER_PASSWORD);
        BasicImapCommands.welcome(scriptedTestProtocol);
        BasicImapCommands.authenticate(scriptedTestProtocol);

        MailboxPath sharedMailbox = MailboxPath.forUser(OTHER_USER_NAME, "sharedMailbox");
        MailboxPath child1WithDeleteMailboxRight = sharedMailbox.child("child1-eiklprstwx", '.');
        MailboxPath child2WithoutDeleteMailboxRight = sharedMailbox.child("child2-eiklprstw", '.');
        MailboxPath child3 = sharedMailbox.child("child3", '.');
        MailboxPath child3Sub1 = child3.child("sub1", '.');

        MailboxPath sharedMailboxWithoutCreateMailboxRight = MailboxPath.forUser(OTHER_USER_NAME, "sharedMailbox-eilprstwx");
        MailboxPath otherSharedMailbox = MailboxPath.forUser(OTHER_USER_NAME, "otherSharedMailbox");

        scriptedTestProtocol
            .withFilledMailbox(MailboxPath.inbox(USER))
            .withFilledMailbox(sharedMailbox)
            .withRights(sharedMailbox, USER, MailboxACL.Rfc4314Rights.fromSerializedRfc4314Rights("eiklprstwx"))
            .withFilledMailbox(child1WithDeleteMailboxRight)
            .withRights(child1WithDeleteMailboxRight, USER, MailboxACL.Rfc4314Rights.fromSerializedRfc4314Rights("eiklprstwx"))
            .withFilledMailbox(child2WithoutDeleteMailboxRight)
            .withRights(child2WithoutDeleteMailboxRight, USER, MailboxACL.Rfc4314Rights.fromSerializedRfc4314Rights("eiklprstw"))
            .withFilledMailbox(sharedMailboxWithoutCreateMailboxRight)
            .withRights(sharedMailboxWithoutCreateMailboxRight, USER, MailboxACL.Rfc4314Rights.fromSerializedRfc4314Rights("eilprstwx"))
            .withFilledMailbox(otherSharedMailbox)
            .withRights(otherSharedMailbox, USER, MailboxACL.Rfc4314Rights.fromSerializedRfc4314Rights("eiklprstwx"))
            .withFilledMailbox(child3)
            .withRights(child3, USER, MailboxACL.Rfc4314Rights.fromSerializedRfc4314Rights("eiklprstwx"))
            .withFilledMailbox(child3Sub1)
            .withRights(child3Sub1, USER, MailboxACL.Rfc4314Rights.fromSerializedRfc4314Rights("eiklprstw"))
            .withLocale(Locale.US)
            .run("RenameSharedMailbox");
    }
}
