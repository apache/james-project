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

public abstract class NonAuthenticatedState implements ImapTestConstants {

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
    public void testCapabilityUS() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.US)
            .run("Capability");
    }

    @Test
    public void testLoginUS() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.US)
            .run("Login");
    }

    @Test
    public void testValidAuthenticatedUS() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.US)
            .run("ValidAuthenticated");
    }

    @Test
    public void testValidSelectedUS() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.US)
            .run("ValidSelected");
    }

    @Test
    public void testAuthenticateUS() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.US)
            .run("Authenticate");
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
    public void testCapabilityITALY() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.ITALY)
            .run("Capability");
    }

    @Test
    public void testLoginITALY() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.ITALY)
            .run("Login");
    }

    @Test
    public void testValidAuthenticatedITALY() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.ITALY)
            .run("ValidAuthenticated");
    }

    @Test
    public void testValidSelectedITALY() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.ITALY)
            .run("ValidSelected");
    }

    @Test
    public void testAuthenticateITALY() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.ITALY)
            .run("Authenticate");
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
    public void testLoginKOREA() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.KOREA)
            .run("Login");
    }

    @Test
    public void testValidAuthenticatedKOREA() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.KOREA)
            .run("ValidAuthenticated");
    }

    @Test
    public void testValidSelectedKOREA() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.KOREA)
            .run("ValidSelected");
    }

    @Test
    public void testAuthenticateKOREA() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.KOREA)
            .run("Authenticate");
    }
}
