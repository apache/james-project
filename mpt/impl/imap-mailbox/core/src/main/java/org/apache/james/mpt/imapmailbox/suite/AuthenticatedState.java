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
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mpt.api.ImapFeatures.Feature;
import org.apache.james.mpt.api.ImapHostSystem;
import org.apache.james.mpt.imapmailbox.suite.base.BasicImapCommands;
import org.apache.james.mpt.script.SimpleScriptedTestProtocol;
import org.junit.Assume;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public abstract class AuthenticatedState extends BasicImapCommands {
    private static final Username USER_2 = Username.of("imapuser2");

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
    }
    
    @Test
    public void testId() throws Exception {
        simpleScriptedTestProtocol.run("Id");
    }

    @Test
    public void testNoopUS() throws Exception {
        simpleScriptedTestProtocol.run("Noop");
    }

    @Test
    public void testLogoutUS() throws Exception {
        simpleScriptedTestProtocol.run("Logout");
    }

    @Test
    public void testCapabilityUS() throws Exception {
        simpleScriptedTestProtocol.run("Capability");
    }

    @Test
    public void testAppendExamineInboxUS() throws Exception {
        simpleScriptedTestProtocol.run("AppendExamineInbox");
    }

    @Test
    public void testAppendSelectInboxUS() throws Exception {
        simpleScriptedTestProtocol.run("AppendSelectInbox");
    }

    @Test
    public void testCreateUS() throws Exception {
        simpleScriptedTestProtocol.run("Create");
    }

    @Test
    public void testExamineEmptyUS() throws Exception {
        simpleScriptedTestProtocol.run("ExamineEmpty");
    }

    @Test
    public void testSelectEmptyUS() throws Exception {
        simpleScriptedTestProtocol.run("SelectEmpty");
    }

    @Test
    public void testListNamespaceUS() throws Exception {
        simpleScriptedTestProtocol.run("ListNamespace");
    }

    @Test
    public void testListMailboxesUS() throws Exception {
        simpleScriptedTestProtocol.run("ListMailboxes");
    }

    @Test
    public void testListSubscribed() throws Exception {
        simpleScriptedTestProtocol.run("ListSubscribed");
    }

    @Test
    public void testStatusUS() throws Exception {
        simpleScriptedTestProtocol.run("Status");
    }

    @Test
    public void testSubscribeUS() throws Exception {
        simpleScriptedTestProtocol.run("Subscribe");
    }

    @Test
    public void testDeleteUS() throws Exception {
        simpleScriptedTestProtocol.run("Delete");
    }

    @Test
    public void testAppendUS() throws Exception {
        simpleScriptedTestProtocol.run("Append");
    }

    @Test
    public void testAppendExpungeUS() throws Exception {
        simpleScriptedTestProtocol.run("AppendExpunge");
    }

    @Test
    public void testSelectAppendUS() throws Exception {
        simpleScriptedTestProtocol.run("SelectAppend");
    }
    
    @Test
    public void testStringArgsUS() throws Exception {
        simpleScriptedTestProtocol.run("StringArgs");
    }

    @Test
    public void testValidNonAuthenticatedUS() throws Exception {
        simpleScriptedTestProtocol.run("ValidNonAuthenticated");
    }

    @Test
    public void testNoopITALY() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.ITALY)
            .run("Noop");
    }

    @Test
    public void testLogoutITALY() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.ITALY).run("Logout");
    }

    @Test
    public void testCapabilityITALY() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.ITALY)
            .run("Capability");
    }

    @Test
    public void testAppendExamineInboxITALY() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.ITALY)
            .run("AppendExamineInbox");
    }

    @Test
    public void testAppendSelectInboxITALY() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.ITALY)
            .run("AppendSelectInbox");
    }

    @Test
    public void testCreateITALY() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.ITALY)
            .run("Create");
    }

    @Test
    public void testExamineEmptyITALY() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.ITALY)
            .run("ExamineEmpty");
    }

    @Test
    public void testSelectEmptyITALY() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.ITALY)
            .run("SelectEmpty");
    }

    @Test
    public void testListNamespaceITALY() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.ITALY)
            .run("ListNamespace");
    }

    @Test
    public void testListMailboxesITALY() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.ITALY)
            .run("ListMailboxes");
    }

    @Test
    public void testStatusITALY() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.ITALY)
            .run("Status");
    }

    @Test
    public void testSubscribeITALY() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.ITALY)
            .run("Subscribe");
    }

    @Test
    public void testDeleteITALY() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.ITALY)
            .run("Delete");
    }

    @Test
    public void testAppendITALY() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.ITALY)
            .run("Append");
    }

    @Test
    public void testAppendExpungeITALY() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.ITALY)
            .run("AppendExpunge");
    }

    @Test
    public void testSelectAppendITALY() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.ITALY)
            .run("SelectAppend");
    }
    
    @Test
    public void testStringArgsITALY() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.ITALY)
            .run("StringArgs");
    }

    @Test
    public void testValidNonAuthenticatedITALY() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.ITALY)
            .run("ValidNonAuthenticated");
    }

    @Test
    public void testNoopKOREA() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.KOREA)
            .run("Noop");
    }

    @Test
    public void testLogoutKOREA() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.KOREA)
            .run("Logout");
    }

    @Test
    public void testCapabilityKOREA() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.KOREA)
            .run("Capability");
    }

    @Test
    public void testAppendExamineInboxKOREA() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.KOREA)
            .run("AppendExamineInbox");
    }

    @Test
    public void testAppendSelectInboxKOREA() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.KOREA)
            .run("AppendSelectInbox");
    }

    @Test
    public void testCreateKOREA() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.KOREA)
            .run("Create");
    }

    @Test
    public void testExamineEmptyKOREA() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.KOREA)
            .run("ExamineEmpty");
    }

    @Test
    public void testSelectEmptyKOREA() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.KOREA)
            .run("SelectEmpty");
    }

    @Test
    public void testListNamespaceKOREA() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.KOREA)
            .run("ListNamespace");
    }

    @Test
    public void testListMailboxesKOREA() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.KOREA)
            .run("ListMailboxes");
    }

    @Test
    public void testStatusKOREA() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.KOREA)
            .run("Status");
    }

    @Test
    public void testSubscribeKOREA() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.KOREA)
            .run("Subscribe");
    }

    @Test
    public void testDeleteKOREA() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.KOREA)
            .run("Delete");
    }

    @Test
    public void testAppendKOREA() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.KOREA)
            .run("Append");
    }

    @Test
    public void testAppendExpungeKOREA() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.KOREA)
            .run("AppendExpunge");
    }

    @Test
    public void testSelectAppendKOREA() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.KOREA)
            .run("SelectAppend");
    }

    @Test
    public void testStringArgsKOREA() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.KOREA)
            .run("StringArgs");
    }

    @Test
    public void testValidNonAuthenticatedKOREA() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.KOREA)
            .run("ValidNonAuthenticated");
    }

    @Test
    public void testNamespaceUS() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.KOREA)
            .run("Namespace");
    }

    @Test
    public void testNamespaceITALY() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.ITALY)
            .run("Namespace");
    }

    @Test
    public void testNamespaceKOREA() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.KOREA)
            .run("Namespace");
    }

    @Test
    public void listShouldNotListMailboxWithOtherNamespaceUS() throws Exception {
        Assume.assumeTrue(system.supports(Feature.NAMESPACE_SUPPORT));
        system.createMailbox(new MailboxPath("#namespace", USER, "Other"));
        simpleScriptedTestProtocol.run("ListMailboxes");
    }

    @Test
    public void listShouldNotListMailboxWithOtherNamespaceITALY() throws Exception {
        Assume.assumeTrue(system.supports(Feature.NAMESPACE_SUPPORT));
        system.createMailbox(new MailboxPath("#namespace", USER, "Other"));
        simpleScriptedTestProtocol
            .withLocale(Locale.ITALY)
            .run("ListMailboxes");
    }

    @Test
    public void listShouldNotListMailboxWithOtherNamespaceKOREA() throws Exception {
        Assume.assumeTrue(system.supports(Feature.NAMESPACE_SUPPORT));
        system.createMailbox(new MailboxPath("#namespace", USER, "Other"));
        simpleScriptedTestProtocol
            .withLocale(Locale.KOREA)
            .run("ListMailboxes");
    }

    @Test
    public void listShouldNotListMailboxWithOtherUserUS() throws Exception {
        system.createMailbox(MailboxPath.forUser(USER_2, "Other"));
        simpleScriptedTestProtocol.run("ListMailboxes");
    }

    @Test
    public void listShouldNotListMailboxWithOtherUserITALY() throws Exception {
        system.createMailbox(MailboxPath.forUser(USER_2, "Other"));
        simpleScriptedTestProtocol
            .withLocale(Locale.ITALY)
            .run("ListMailboxes");
    }

    @Test
    public void listShouldNotListMailboxWithOtherUserKOREA() throws Exception {
        system.createMailbox(MailboxPath.forUser(USER_2, "Other"));
        simpleScriptedTestProtocol
            .withLocale(Locale.KOREA)
            .run("ListMailboxes");
    }

    @Test
    public void rightsCommandsShouldBeSupported() throws Exception {
        system.createMailbox(MailboxPath.forUser(USER_2, "Other"));
        simpleScriptedTestProtocol
            .withLocale(Locale.KOREA)
            .run("Rights");
    }
}
