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

import org.apache.james.imap.api.ImapConfiguration;
import org.apache.james.mpt.host.JamesImapHostSystem;
import org.apache.james.mpt.imapmailbox.ImapTestConstants;
import org.apache.james.mpt.imapmailbox.suite.base.BasicImapCommands;
import org.apache.james.mpt.script.SimpleScriptedTestProtocol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public abstract class Condstore implements ImapTestConstants {

    protected abstract JamesImapHostSystem createJamesImapHostSystem();
    
    private JamesImapHostSystem system;
    private SimpleScriptedTestProtocol simpleScriptedTestProtocol;

    @BeforeEach
    public void setUp() throws Exception {
        system = createJamesImapHostSystem();
        simpleScriptedTestProtocol = new SimpleScriptedTestProtocol("/org/apache/james/imap/scripts/", system)
                .withUser(USER, PASSWORD)
                .withLocale(Locale.US);
        BasicImapCommands.welcome(simpleScriptedTestProtocol);
        BasicImapCommands.authenticate(simpleScriptedTestProtocol);
    }
    

    @Test
    public void condstoreShouldBeDisableByDefault() throws Exception {
        system.configure(ImapConfiguration.builder().isProvisionDefaultMailboxes(false).build());
        simpleScriptedTestProtocol.run("CondstoreDisable");
    }

    @Test
    public void condstoreShouldBeDisableWhenGivenAndFalse() throws Exception {
        system.configure(
                ImapConfiguration.builder().isProvisionDefaultMailboxes(false).isCondstoreEnable(false).build());
        simpleScriptedTestProtocol.run("CondstoreDisable");
    }

    @Test
    public void condstoreShouldBeEnableWhenGivenAndTrue() throws Exception {
        system.configure(
                ImapConfiguration.builder().isProvisionDefaultMailboxes(false).isCondstoreEnable(true).build());
        simpleScriptedTestProtocol.run("CondstoreEnable");
    }
}
