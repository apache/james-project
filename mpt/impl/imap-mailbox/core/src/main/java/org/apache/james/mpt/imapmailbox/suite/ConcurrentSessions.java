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

public abstract class ConcurrentSessions implements ImapTestConstants {

    protected abstract ImapHostSystem createImapHostSystem();
    
    private ImapHostSystem system;
    private SimpleScriptedTestProtocol simpleScriptedTestProtocol;

    @BeforeEach
    public void setUp() throws Exception {
        system = createImapHostSystem();
        simpleScriptedTestProtocol = new SimpleScriptedTestProtocol("/org/apache/james/imap/scripts/", system)
                .withUser(USER, PASSWORD);
        BasicImapCommands.welcome(simpleScriptedTestProtocol);
        BasicImapCommands.authenticate(simpleScriptedTestProtocol);
    }

    @Test
    public void testConcurrentExpungeResponseUS() throws Exception {
          simpleScriptedTestProtocol
              .withLocale(Locale.US)
              .run("ConcurrentExpungeResponse");
    }

    @Test
    public void testConcurrentExpungeResponseITALY() throws Exception {
        simpleScriptedTestProtocol
              .withLocale(Locale.ITALY)
              .run("ConcurrentExpungeResponse");
    }

    @Test
    public void testConcurrentExpungeResponseKOREA() throws Exception {
        simpleScriptedTestProtocol
              .withLocale(Locale.KOREA)
              .run("ConcurrentExpungeResponse");
    }

    @Test
    public void testConcurrentCrossExpungeUS() throws Exception {
          simpleScriptedTestProtocol
              .withLocale(Locale.US)
              .run("ConcurrentCrossExpunge");
    }
    
    @Test
    public void testConcurrentCrossExpungeITALY() throws Exception {
          simpleScriptedTestProtocol
              .withLocale(Locale.ITALY)
              .run("ConcurrentCrossExpunge");
    }
    
    @Test
    public void testConcurrentCrossExpungeKOREA() throws Exception {
          simpleScriptedTestProtocol
              .withLocale(Locale.KOREA)
              .run("ConcurrentCrossExpunge");
    }
    
    @Test
    public void testConcurrentRenameSelectedSubUS() throws Exception {
        simpleScriptedTestProtocol
              .withLocale(Locale.US)
              .run("ConcurrentRenameSelectedSub");
    }

    @Test
    public void testConcurrentExistsResponseUS() throws Exception {
        simpleScriptedTestProtocol
              .withLocale(Locale.US)
              .run("ConcurrentExistsResponse");
    }

    @Test
    public void testConcurrentDeleteSelectedUS() throws Exception {
        simpleScriptedTestProtocol
              .withLocale(Locale.US)
              .run("ConcurrentDeleteSelected");
    }

    @Test
    public void testConcurrentFetchResponseUS() throws Exception {
        simpleScriptedTestProtocol
              .withLocale(Locale.US)
              .run("ConcurrentFetchResponse");
    }

    @Test
    public void testConcurrentRenameSelectedUS() throws Exception {
        simpleScriptedTestProtocol
              .withLocale(Locale.US)
              .run("ConcurrentRenameSelected");
    }

    @Test
    public void testConcurrentRenameSelectedSubKOREA() throws Exception {
        simpleScriptedTestProtocol
              .withLocale(Locale.KOREA)
              .run("ConcurrentRenameSelectedSub");
    }
    
    @Test
    public void testConcurrentExistsResponseKOREA() throws Exception {
        simpleScriptedTestProtocol
              .withLocale(Locale.KOREA)
              .run("ConcurrentExistsResponse");
    }

    @Test
    public void testConcurrentDeleteSelectedKOREA() throws Exception {
        simpleScriptedTestProtocol
              .withLocale(Locale.KOREA)
              .run("ConcurrentDeleteSelected");
    }

    @Test
    public void testConcurrentFetchResponseKOREA() throws Exception {
        simpleScriptedTestProtocol
              .withLocale(Locale.KOREA)
              .run("ConcurrentFetchResponse");
    }

    @Test
    public void testConcurrentRenameSelectedKOREA() throws Exception {
        simpleScriptedTestProtocol
              .withLocale(Locale.KOREA)
              .run("ConcurrentRenameSelected");
    }

    @Test
    public void testConcurrentRenameSelectedSubITALY() throws Exception {
        simpleScriptedTestProtocol
              .withLocale(Locale.ITALY)
              .run("ConcurrentRenameSelectedSub");
    }
    
    @Test
    public void testConcurrentExistsResponseITALY() throws Exception {
        simpleScriptedTestProtocol
              .withLocale(Locale.ITALY)
              .run("ConcurrentExistsResponse");
    }

    @Test
    public void testConcurrentDeleteSelectedITALY() throws Exception {
        simpleScriptedTestProtocol
              .withLocale(Locale.ITALY)
              .run("ConcurrentDeleteSelected");
    }

    @Test
    public void testConcurrentFetchResponseITALY() throws Exception {
        simpleScriptedTestProtocol
              .withLocale(Locale.ITALY)
              .run("ConcurrentFetchResponse");
    }

    @Test
    public void testConcurrentRenameSelectedITALY() throws Exception {
        simpleScriptedTestProtocol
              .withLocale(Locale.ITALY)
              .run("ConcurrentRenameSelected");
    }

    @Test
    public void expungeShouldNotBreakUIDToMSNMapping() throws Exception {
        simpleScriptedTestProtocol
              .withLocale(Locale.US)
              .run("ConcurrentExpungeUIDToMSNMapping");
    }

    @Test
    public void appendShouldNotBreakUIDToMSNMapping() throws Exception {
        simpleScriptedTestProtocol
              .withLocale(Locale.US)
              .run("ConcurrentAppendUIDToMSNMapping");
    }
}
