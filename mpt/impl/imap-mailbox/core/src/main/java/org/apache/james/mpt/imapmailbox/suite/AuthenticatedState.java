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

import javax.inject.Inject;

import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mpt.api.ImapHostSystem;
import org.apache.james.mpt.imapmailbox.suite.base.BaseAuthenticatedState;
import org.junit.Test;

public class AuthenticatedState extends BaseAuthenticatedState {
    
    @Inject
    private static ImapHostSystem system;
    
    public AuthenticatedState() throws Exception {
        super(system);
    }

    @Test
    public void testNoopUS() throws Exception {
        scriptTest("Noop", Locale.US);
    }

    @Test
    public void testLogoutUS() throws Exception {
        scriptTest("Logout", Locale.US);
    }

    @Test
    public void testCapabilityUS() throws Exception {
        scriptTest("Capability", Locale.US);
    }

    @Test
    public void testAppendExamineInboxUS() throws Exception {
        scriptTest("AppendExamineInbox", Locale.US);
    }

    @Test
    public void testAppendSelectInboxUS() throws Exception {
        scriptTest("AppendSelectInbox", Locale.US);
    }

    @Test
    public void testCreateUS() throws Exception {
        scriptTest("Create", Locale.US);
    }

    @Test
    public void testExamineEmptyUS() throws Exception {
        scriptTest("ExamineEmpty", Locale.US);
    }

    @Test
    public void testSelectEmptyUS() throws Exception {
        scriptTest("SelectEmpty", Locale.US);
    }

    @Test
    public void testListNamespaceUS() throws Exception {
        scriptTest("ListNamespace", Locale.US);
    }

    @Test
    public void testListMailboxesUS() throws Exception {
        scriptTest("ListMailboxes", Locale.US);
    }

    @Test
    public void testStatusUS() throws Exception {
        scriptTest("Status", Locale.US);
    }

    @Test
    public void testSubscribeUS() throws Exception {
        scriptTest("Subscribe", Locale.US);
    }

    @Test
    public void testDeleteUS() throws Exception {
        scriptTest("Delete", Locale.US);
    }

    @Test
    public void testAppendUS() throws Exception {
        scriptTest("Append", Locale.US);
    }

    @Test
    public void testAppendExpungeUS() throws Exception {
        scriptTest("AppendExpunge", Locale.US);
    }

    @Test
    public void testSelectAppendUS() throws Exception {
        scriptTest("SelectAppend", Locale.US);
    }
    
    @Test
    public void testStringArgsUS() throws Exception {
        scriptTest("StringArgs", Locale.US);
    }

    @Test
    public void testValidNonAuthenticatedUS() throws Exception {
        scriptTest("ValidNonAuthenticated", Locale.US);
    }

    @Test
    public void testNoopITALY() throws Exception {
        scriptTest("Noop", Locale.ITALY);
    }

    @Test
    public void testLogoutITALY() throws Exception {
        scriptTest("Logout", Locale.ITALY);
    }

    @Test
    public void testCapabilityITALY() throws Exception {
        scriptTest("Capability", Locale.ITALY);
    }

    @Test
    public void testAppendExamineInboxITALY() throws Exception {
        scriptTest("AppendExamineInbox", Locale.ITALY);
    }

    @Test
    public void testAppendSelectInboxITALY() throws Exception {
        scriptTest("AppendSelectInbox", Locale.ITALY);
    }

    @Test
    public void testCreateITALY() throws Exception {
        scriptTest("Create", Locale.ITALY);
    }

    @Test
    public void testExamineEmptyITALY() throws Exception {
        scriptTest("ExamineEmpty", Locale.ITALY);
    }

    @Test
    public void testSelectEmptyITALY() throws Exception {
        scriptTest("SelectEmpty", Locale.ITALY);
    }

    @Test
    public void testListNamespaceITALY() throws Exception {
        scriptTest("ListNamespace", Locale.ITALY);
    }

    @Test
    public void testListMailboxesITALY() throws Exception {
        scriptTest("ListMailboxes", Locale.ITALY);
    }

    @Test
    public void testStatusITALY() throws Exception {
        scriptTest("Status", Locale.ITALY);
    }

    @Test
    public void testSubscribeITALY() throws Exception {
        scriptTest("Subscribe", Locale.ITALY);
    }

    @Test
    public void testDeleteITALY() throws Exception {
        scriptTest("Delete", Locale.ITALY);
    }

    @Test
    public void testAppendITALY() throws Exception {
        scriptTest("Append", Locale.ITALY);
    }

    @Test
    public void testAppendExpungeITALY() throws Exception {
        scriptTest("AppendExpunge", Locale.ITALY);
    }

    @Test
    public void testSelectAppendITALY() throws Exception {
        scriptTest("SelectAppend", Locale.ITALY);
    }
    
    @Test
    public void testStringArgsITALY() throws Exception {
        scriptTest("StringArgs", Locale.ITALY);
    }

    @Test
    public void testValidNonAuthenticatedITALY() throws Exception {
        scriptTest("ValidNonAuthenticated", Locale.ITALY);
    }

    @Test
    public void testNoopKOREA() throws Exception {
        scriptTest("Noop", Locale.KOREA);
    }

    @Test
    public void testLogoutKOREA() throws Exception {
        scriptTest("Logout", Locale.KOREA);
    }

    @Test
    public void testCapabilityKOREA() throws Exception {
        scriptTest("Capability", Locale.KOREA);
    }

    @Test
    public void testAppendExamineInboxKOREA() throws Exception {
        scriptTest("AppendExamineInbox", Locale.KOREA);
    }

    @Test
    public void testAppendSelectInboxKOREA() throws Exception {
        scriptTest("AppendSelectInbox", Locale.KOREA);
    }

    @Test
    public void testCreateKOREA() throws Exception {
        scriptTest("Create", Locale.KOREA);
    }

    @Test
    public void testExamineEmptyKOREA() throws Exception {
        scriptTest("ExamineEmpty", Locale.KOREA);
    }

    @Test
    public void testSelectEmptyKOREA() throws Exception {
        scriptTest("SelectEmpty", Locale.KOREA);
    }

    @Test
    public void testListNamespaceKOREA() throws Exception {
        scriptTest("ListNamespace", Locale.KOREA);
    }

    @Test
    public void testListMailboxesKOREA() throws Exception {
        scriptTest("ListMailboxes", Locale.KOREA);
    }

    @Test
    public void testStatusKOREA() throws Exception {
        scriptTest("Status", Locale.KOREA);
    }

    @Test
    public void testSubscribeKOREA() throws Exception {
        scriptTest("Subscribe", Locale.KOREA);
    }

    @Test
    public void testDeleteKOREA() throws Exception {
        scriptTest("Delete", Locale.KOREA);
    }

    @Test
    public void testAppendKOREA() throws Exception {
        scriptTest("Append", Locale.KOREA);
    }

    @Test
    public void testAppendExpungeKOREA() throws Exception {
        scriptTest("AppendExpunge", Locale.KOREA);
    }

    @Test
    public void testSelectAppendKOREA() throws Exception {
        scriptTest("SelectAppend", Locale.KOREA);
    }

    @Test
    public void testStringArgsKOREA() throws Exception {
        scriptTest("StringArgs", Locale.KOREA);
    }

    @Test
    public void testValidNonAuthenticatedKOREA() throws Exception {
        scriptTest("ValidNonAuthenticated", Locale.KOREA);
    }

    @Test
    public void testNamespaceUS() throws Exception {
        scriptTest("Namespace", Locale.US);
    }

    @Test
    public void testNamespaceITALY() throws Exception {
        scriptTest("Namespace", Locale.ITALY);
    }

    @Test
    public void testNamespaceKOREA() throws Exception {
        scriptTest("Namespace", Locale.KOREA);
    }

    @Test
    public void listShouldNotListMailboxWithOtherNamspaceUS() throws Exception {
        system.createMailbox(new MailboxPath("#namespace", USER, "Other"));
        scriptTest("ListMailboxes", Locale.US);
    }

    @Test
    public void listShouldNotListMailboxWithOtherNamspaceITALY() throws Exception {
        system.createMailbox(new MailboxPath("#namespace", USER, "Other"));
        scriptTest("ListMailboxes", Locale.ITALY);
    }

    @Test
    public void listShouldNotListMailboxWithOtherNamspaceKOREA() throws Exception {
        system.createMailbox(new MailboxPath("#namespace", USER, "Other"));
        scriptTest("ListMailboxes", Locale.KOREA);
    }

    @Test
    public void listShouldNotListMailboxWithOtherUserUS() throws Exception {
        system.createMailbox(new MailboxPath("#namespace", USER + "2", "Other"));
        scriptTest("ListMailboxes", Locale.US);
    }

    @Test
    public void listShouldNotListMailboxWithOtherUserITALY() throws Exception {
        system.createMailbox(new MailboxPath("#namespace", USER + "2", "Other"));
        scriptTest("ListMailboxes", Locale.ITALY);
    }

    @Test
    public void listShouldNotListMailboxWithOtherUserKOREA() throws Exception {
        system.createMailbox(new MailboxPath("#namespace", USER + "2", "Other"));
        scriptTest("ListMailboxes", Locale.KOREA);
    }
}
