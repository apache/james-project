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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public abstract class SelectedState implements ImapTestConstants {

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
    public void testCheckUS() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.US)
            .run("Check");
    }

    @Test
    public void testExpungeUS() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.US)
            .run("Expunge");
    }

    @Test
    public void testSearchUS() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.US)
            .run("Search");
    }

    @Test
    public void testFetchSingleMessageUS() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.US)
            .run("FetchSingleMessage");
    }

    @Test
    public void testFetchMultipleMessagesUS() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.US)
            .run("FetchMultipleMessages");
    }

    @Test
    public void testFetchPeekUS() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.US)
            .run("FetchPeek");
    }

    @Test
    public void testStoreUS() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.US)
            .run("Store");
    }

    @Test
    public void testCopyUS() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.US)
            .run("Copy");
    }

    @Test
    public void testUidUS() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.US)
            .run("Uid");
    }

    @Test
    public void testCheckITALY() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.ITALY)
            .run("Check");
    }

    @Test
    public void testExpungeITALY() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.ITALY)
            .run("Expunge");
    }

    @Test
    public void testSearchITALY() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.ITALY)
            .run("Search");
    }

    @Test
    public void testFetchSingleMessageITALY() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.ITALY)
            .run("FetchSingleMessage");
    }

    @Test
    public void testFetchMultipleMessagesITALY() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.ITALY)
            .run("FetchMultipleMessages");
    }

    @Test
    public void testFetchPeekITALY() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.ITALY)
            .run("FetchPeek");
    }

    @Test
    public void testStoreITALY() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.ITALY)
            .run("Store");
    }

    @Test
    public void testCopyITALY() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.ITALY)
            .run("Copy");
    }

    @Test
    public void testUidITALY() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.ITALY)
            .run("Uid");
    }

    @Test
    public void testCheckKOREA() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.KOREA)
            .run("Check");
    }

    @Test
    public void testExpungeKOREA() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.KOREA)
            .run("Expunge");
    }

    @Test
    public void testSearchKOREA() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.KOREA)
            .run("Search");
    }

    @Test
    public void testFetchSingleMessageKOREA() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.KOREA)
            .run("FetchSingleMessage");
    }

    @Test
    public void testFetchMultipleMessagesKOREA() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.KOREA)
            .run("FetchMultipleMessages");
    }

    @Test
    public void testFetchPeekKOREA() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.KOREA)
            .run("FetchPeek");
    }

    @Test
    public void testStoreKOREA() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.KOREA)
            .run("Store");
    }

    @Test
    public void testCopyKOREA() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.KOREA)
            .run("Copy");
    }

    @Test
    public void testUidKOREA() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.KOREA)
            .run("Uid");
    }
    
    @Test
    public void testNamespaceUS() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.US)
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
}
