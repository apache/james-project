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

package org.apache.james.mpt.imapmailbox.suite.base;

import org.apache.james.mpt.api.ProtocolInteractor;
import org.apache.james.mpt.imapmailbox.ImapTestConstants;
import org.apache.james.mpt.script.SimpleScriptedTestProtocol;


public class BasicImapCommands implements ImapTestConstants {
    
    public static void welcome(SimpleScriptedTestProtocol scriptedTestProtocol) {
        scriptedTestProtocol.preElements().SL("\\* OK IMAP4rev1 Server ready", "BaseAuthenticatedState.java:32");
    }
    
    public static void authenticate(SimpleScriptedTestProtocol scriptedTestProtocol) {
        addLogin(scriptedTestProtocol.preElements(), USER, PASSWORD);
    }


    private static void addLogin(ProtocolInteractor preElements, String username, String password) {
        preElements.CL("a001 LOGIN " + username + " " + password);
        preElements.SL("a001 OK .*", "BaseAuthenticatedState.java:53");
    }
    
    public static void selectInbox(SimpleScriptedTestProtocol scriptedTestProtocol) {
        ProtocolInteractor preElements = scriptedTestProtocol.preElements();
        
        preElements.CL("abcd SELECT inbox");
        preElements.SL("\\* FLAGS \\(\\\\Answered \\\\Deleted \\\\Draft \\\\Flagged \\\\Seen\\)", "BasicImapCommands");
        preElements.SL("\\* \\d+ EXISTS", "BasicImapCommands");
        preElements.SL("\\* \\d+ RECENT", "BasicImapCommands");
        preElements.SL("\\* OK \\[UIDVALIDITY \\d+\\].*", "BasicImapCommands");
        preElements.SL("\\* OK \\[PERMANENTFLAGS \\(\\\\Answered \\\\Deleted \\\\Draft \\\\Flagged \\\\\\Seen( \\\\\\*)?\\)\\].*", "BasicImapCommands");
        preElements.SL("\\* OK \\[HIGHESTMODSEQ \\d+\\].*", "BasicImapCommands");
        preElements.SL("\\* OK \\[UIDNEXT 1\\].*", "BasicImapCommands");
        preElements.SL("abcd OK \\[READ-WRITE\\] SELECT completed.", "BasicImapCommands");
        
        addCloseInbox(scriptedTestProtocol.postElements());
    }
    
    private static void addCloseInbox(ProtocolInteractor postElements) {
        postElements.CL("a CLOSE");
        postElements.SL(".*", "AbstractBaseTestSelectedInbox.java:76");
    }
    
    public static void prepareMailbox(SimpleScriptedTestProtocol scriptedTestProtocol) throws Exception {
        scriptedTestProtocol.addTestFile("SelectedStateSetup.test", scriptedTestProtocol.preElements());
        scriptedTestProtocol.addTestFile("SelectedStateCleanup.test", scriptedTestProtocol.postElements());
    }
}
