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
import org.apache.james.mpt.script.GenericSimpleScriptedTestProtocol;
import org.apache.james.mpt.script.SimpleScriptedTestProtocol;


public class BasicImapCommands implements ImapTestConstants {
    
    public static void welcome(GenericSimpleScriptedTestProtocol<?, ?> scriptedTestProtocol) {
        scriptedTestProtocol.preElements().sl("\\* OK IMAP4rev1 Server ready", "BaseAuthenticatedState.java:32");
    }
    
    public static void authenticate(GenericSimpleScriptedTestProtocol<?, ?> scriptedTestProtocol) {
        addLogin(scriptedTestProtocol.preElements(), USER.asString(), PASSWORD);
    }


    private static void addLogin(ProtocolInteractor preElements, String username, String password) {
        preElements.cl("a001 LOGIN " + username + " " + password);
        preElements.sl("a001 OK .*", "BaseAuthenticatedState.java:53");
    }
    
    public static void selectInbox(SimpleScriptedTestProtocol scriptedTestProtocol) {
        ProtocolInteractor preElements = scriptedTestProtocol.preElements();
        
        preElements.cl("abcd SELECT inbox");
        preElements.sl("\\* OK \\[MAILBOXID \\(.+\\)\\] Ok", "BasicImapCommands");
        preElements.sl("\\* FLAGS \\(\\\\Answered \\\\Deleted \\\\Draft \\\\Flagged \\\\Seen\\)", "BasicImapCommands");
        preElements.sl("\\* \\d+ EXISTS", "BasicImapCommands");
        preElements.sl("\\* \\d+ RECENT", "BasicImapCommands");
        preElements.sl("\\* OK \\[UIDVALIDITY \\d+\\].*", "BasicImapCommands");
        preElements.sl("\\* OK \\[PERMANENTFLAGS \\(\\\\Answered \\\\Deleted \\\\Draft \\\\Flagged \\\\\\Seen( \\\\\\*)?\\)\\].*", "BasicImapCommands");
        preElements.sl("\\* OK \\[HIGHESTMODSEQ \\d+\\].*", "BasicImapCommands");
        preElements.sl("\\* OK \\[UIDNEXT 1\\].*", "BasicImapCommands");
        preElements.sl("abcd OK \\[READ-WRITE\\] SELECT completed.", "BasicImapCommands");
        
        addCloseInbox(scriptedTestProtocol.postElements());
    }
    
    private static void addCloseInbox(ProtocolInteractor postElements) {
        postElements.cl("a CLOSE");
        postElements.sl(".*", "AbstractBaseTestSelectedInbox.java:76");
    }
    
    public static void prepareMailbox(SimpleScriptedTestProtocol scriptedTestProtocol) throws Exception {
        scriptedTestProtocol.addTestFile("SelectedStateSetup.test", scriptedTestProtocol.preElements());
        scriptedTestProtocol.addTestFile("SelectedStateCleanup.test", scriptedTestProtocol.postElements());
    }
}
