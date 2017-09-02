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

public abstract class Rename implements ImapTestConstants {

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
}
