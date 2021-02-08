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
import org.apache.james.mpt.script.SimpleScriptedTestProtocol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public abstract class Security implements ImapTestConstants {
    protected abstract ImapHostSystem createImapHostSystem();
    
    private ImapHostSystem system;
    private SimpleScriptedTestProtocol simpleScriptedTestProtocol;

    @BeforeEach
    public void setUp() throws Exception {
        system = createImapHostSystem();
        simpleScriptedTestProtocol = new SimpleScriptedTestProtocol("/org/apache/james/imap/scripts/", system)
                .withUser(USER, PASSWORD)
                .withLocale(Locale.US);
    }

    @Test
    public void accessingOtherPeopleNamespaceShouldBeDenied() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.US)
            .run("SharedMailbox");
    }

    @Test
    public void testLoginThreeStrikesUS() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.US)
            .run("LoginThreeStrikes");
    }

    @Test
    public void testLoginThreeStrikesKOREA() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.KOREA)
            .run("LoginThreeStrikes");
    }

    @Test
    public void testLoginThreeStrikesITALY() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.ITALY)
            .run("LoginThreeStrikes");
    }

    @Test
    public void testBadTagUS() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.US)
            .run("BadTag");
    }

    @Test
    public void testBadTagKOREA() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.KOREA)
            .run("BadTag");
    }

    @Test
    public void testBadTagITALY() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.ITALY)
            .run("BadTag");
    }

    @Test
    public void testNoTagUS() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.US)
            .run("NoTag");
    }

    @Test
    public void testNoTagKOREA() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.KOREA)
            .run("NoTag");
    }

    @Test
    public void testNoTagITALY() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.ITALY)
            .run("NoTag");
    }

    @Test
    public void testIllegalTagUS() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.US)
            .run("IllegalTag");
    }

    @Test
    public void testIllegalTagKOREA() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.KOREA)
            .run("IllegalTag");
    }

    @Test
    public void testIllegalTagITALY() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.ITALY)
            .run("IllegalTag");
    }

    @Test
    public void testJustTagUS() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.US)
            .run("JustTag");
    }

    @Test
    public void testJustTagKOREA() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.KOREA)
            .run("JustTag");
    }

    @Test
    public void testJustTagITALY() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.ITALY)
            .run("JustTag");
    }

    @Test
    public void testNoCommandUS() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.US)
            .run("NoCommand");
    }

    @Test
    public void testNoCommandKOREA() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.KOREA)
            .run("NoCommand");
    }

    @Test
    public void testNoCommandITALY() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.ITALY)
            .run("NoCommand");
    }

    @Test
    public void testBogusCommandUS() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.US)
            .run("BogusCommand");
    }

    @Test
    public void testBogusCommandKOREA() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.KOREA)
            .run("BogusCommand");
    }

    @Test
    public void testNoBogusITALY() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.US)
            .run("BogusCommand");
    }
}
