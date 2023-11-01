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

public abstract class Fetch implements ImapTestConstants {

    protected abstract ImapHostSystem createImapHostSystem();
    
    private ImapHostSystem system;
    protected SimpleScriptedTestProtocol simpleScriptedTestProtocol;

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
    public void testFetchEnvelopeUS() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.US)
            .run("FetchEnvelope");
    }

    @Test
    public void testFetchPartial() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.US)
            .run("FetchPartial");
    }

    @Test
    public void testFetchEnvelopeIT() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.ITALY)
            .run("FetchEnvelope");
    }

    @Test
    public void testFetchEnvelopeKOREA() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.KOREA)
            .run("FetchEnvelope");
    }

    @Test
    public void testFetchTextUS() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.US)
            .run("FetchText");
    }

    @Test
    public void testFetchBodyNoSectionUS() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.US)
            .run("FetchBodyNoSection");
    }

    @Test
    public void testFetchTextIT() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.ITALY)
            .run("FetchText");
    }

    @Test
    public void testFetchBodyNoSectionIT() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.ITALY)
            .run("FetchBodyNoSection");
    }

    @Test
    public void testFetchTextKOREA() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.KOREA)
            .run("FetchText");
    }

    @Test
    public void testFetchBodyNoSectionKOREA() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.KOREA)
            .run("FetchBodyNoSection");
    }

    @Test
    public void testFetchRFC822US() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.US)
            .run("FetchRFC822");
    }

    @Test
    public void testFetchRFC822TextUS() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.US)
            .run("FetchRFC822Text");
    }

    @Test
    public void testFetchRFC822HeaderUS() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.US)
            .run("FetchRFC822Header");
    }

    @Test
    public void testFetchRFC822KOREA() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.KOREA)
            .run("FetchRFC822");
    }

    @Test
    public void testFetchRFC822TextKOREA() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.KOREA)
            .run("FetchRFC822Text");
    }

    @Test
    public void testFetchRFC822HeaderKOREA() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.KOREA)
            .run("FetchRFC822Header");
    }

    @Test
    public void testFetchRFC822ITALY() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.ITALY)
            .run("FetchRFC822");
    }

    @Test
    public void testFetchRFC822TextITALY() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.ITALY)
            .run("FetchRFC822Text");
    }

    @Test
    public void testFetchRFC822HeaderITALY() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.ITALY)
            .run("FetchRFC822Header");
    }

    @Test
    public void testFetchInternalDateUS() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.US)
            .run("FetchInternalDate");
    }

    @Test
    public void testFetchInternalDateITALY() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.ITALY)
            .run("FetchInternalDate");
    }

    @Test
    public void testFetchInternalDateKOREA() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.KOREA)
            .run("FetchInternalDate");
    }

    @Test
    public void testFetchFetchRfcMixedUS() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.US)
            .run("FetchRFC822Mixed");
    }

    @Test
    public void testFetchFetchRfcMixedKOREA() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.KOREA)
            .run("FetchRFC822Mixed");
    }

    @Test
    public void testFetchFetchRfcMixedITALY() throws Exception {
        simpleScriptedTestProtocol
            .withLocale(Locale.ITALY)
            .run("FetchRFC822Mixed");
    }

    @Test
    public void testFetchSaveDate() throws Exception {
        simpleScriptedTestProtocol
            .run("FetchSaveDate");
    }
}
