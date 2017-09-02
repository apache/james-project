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

import org.apache.james.mpt.api.ImapHostSystem;
import org.apache.james.mpt.imapmailbox.ImapTestConstants;
import org.apache.james.mpt.imapmailbox.suite.base.BasicImapCommands;
import org.apache.james.mpt.script.SimpleScriptedTestProtocol;
import org.junit.Before;
import org.junit.Test;

public abstract class SelectedInbox implements ImapTestConstants {

    protected abstract ImapHostSystem createImapHostSystem();
    
    private ImapHostSystem system;
    private SimpleScriptedTestProtocol simpleScriptedTestProtocol;

    @Before
    public void setUp() throws Exception {
        system = createImapHostSystem();
        simpleScriptedTestProtocol = new SimpleScriptedTestProtocol("/org/apache/james/imap/scripts/", system)
                .withUser(USER, PASSWORD)
                .withLocale(Locale.US);
        BasicImapCommands.welcome(simpleScriptedTestProtocol);
        BasicImapCommands.authenticate(simpleScriptedTestProtocol);
        BasicImapCommands.selectInbox(simpleScriptedTestProtocol);
    }
    
    @Test
    public void testValidNonAuthenticatedUS() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.US)
            .run("ValidNonAuthenticated");
    }

    @Test
    public void testCapabilityUS() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.US)
            .run("Capability");
    }

    @Test
    public void testNoopUS() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.US)
            .run("Noop");
    }

    @Test
    public void testLogoutUS() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.US)
            .run("Logout");
    }

    @Test
    public void testCreateUS() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.US)
            .run("Create");
    }

    @Test
    public void testExamineEmptyUS() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.US)
            .run("ExamineEmpty");
    }

    @Test
    public void testSelectEmptyUS() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.US)
            .run("SelectEmpty");
    }

    @Test
    public void testListNamespaceUS() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.US)
            .run("ListNamespace");
    }

    @Test
    public void testListMailboxesUS() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.US)
            .run("ListMailboxes");
    }

    @Test
    public void testStatusUS() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.US)
            .run("Status");
    }

    @Test
    public void testStringArgsUS() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.US)
            .run("StringArgs");
    }

    @Test
    public void testSubscribeUS() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.US)
            .run("Subscribe");
    }

    @Test
    public void testAppendUS() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.US)
            .run("Append");
    }

    @Test
    public void testDeleteUS() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.US)
            .run("Delete");
    }

    @Test
    public void testValidNonAuthenticatedITALY() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.ITALY)
            .run("ValidNonAuthenticated");
    }

    @Test
    public void testCapabilityITALY() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.ITALY)
            .run("Capability");
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
            .withLocale(Locale.ITALY)
            .run("Logout");
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
    public void testStringArgsITALY() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.ITALY)
            .run("StringArgs");
    }

    @Test
    public void testSubscribeITALY() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.ITALY)
            .run("Subscribe");
    }

    @Test
    public void testAppendITALY() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.ITALY)
            .run("Append");
    }

    @Test
    public void testDeleteITALY() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.ITALY)
            .run("Delete");
    }

    @Test
    public void testValidNonAuthenticatedKOREA() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.KOREA)
            .run("ValidNonAuthenticated");
    }

    @Test
    public void testCapabilityKOREA() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.KOREA)
            .run("Capability");
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
    public void testStringArgsKOREA() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.KOREA)
            .run("StringArgs");
    }

    @Test
    public void testSubscribeKOREA() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.KOREA)
            .run("Subscribe");
    }

    @Test
    public void testAppendKOREA() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.KOREA)
            .run("Append");
    }

    @Test
    public void testDeleteKOREA() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.KOREA)
            .run("Delete");
    }

}
