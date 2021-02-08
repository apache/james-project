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

import org.apache.james.core.Username;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mpt.api.ImapHostSystem;
import org.apache.james.mpt.imapmailbox.GrantRightsOnHost;
import org.apache.james.mpt.imapmailbox.ImapTestConstants;
import org.apache.james.mpt.imapmailbox.MailboxMessageAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public abstract class ACLCommands implements ImapTestConstants {
    public static final Username OTHER_USER_NAME = Username.of("Boby");
    public static final String OTHER_USER_PASSWORD = "password";
    public static final MailboxPath OTHER_USER_MAILBOX = MailboxPath.forUser(OTHER_USER_NAME, "");

    protected abstract ImapHostSystem createImapHostSystem();
    
    protected abstract GrantRightsOnHost createGrantRightsOnHost();
    
    private ImapHostSystem system;
    private GrantRightsOnHost grantRightsOnHost;

    private MailboxACL.Rfc4314Rights readWriteSeenRight;
    private ACLScriptedTestProtocol scriptedTestProtocol;

    @BeforeEach
    public void setUp() throws Exception {
        system = createImapHostSystem();
        grantRightsOnHost = createGrantRightsOnHost();
        MailboxMessageAppender appender = null;
        scriptedTestProtocol = new ACLScriptedTestProtocol(grantRightsOnHost, appender, "/org/apache/james/imap/scripts/", system)
                .withUser(USER, PASSWORD)
                .withLocale(Locale.US);
        readWriteSeenRight = MailboxACL.Rfc4314Rights.fromSerializedRfc4314Rights("rsw");
    }

    @Test
    public void testACLCommandsOwnerUS() throws Exception {
        scriptedTestProtocol.run("ACLCommandsOnOwner");
    }

    @Test
    public void testACLCommandsOtherUserUS() throws Exception {
        scriptedTestProtocol
            .withUser(OTHER_USER_NAME, OTHER_USER_PASSWORD)
            .withGrantRights(OTHER_USER_MAILBOX, USER, readWriteSeenRight)
            .run("ACLCommandsOnOtherUser");
    }

    @Test
    public void testACLCommandsOwnerKorea() throws Exception {
        scriptedTestProtocol.withLocale(Locale.KOREA)
            .run("ACLCommandsOnOwner");
    }

    @Test
    public void testACLCommandsOtherUserKorea() throws Exception {
        scriptedTestProtocol
            .withUser(OTHER_USER_NAME, OTHER_USER_PASSWORD)
            .withLocale(Locale.KOREA)
            .withGrantRights(OTHER_USER_MAILBOX, USER, readWriteSeenRight)
            .run("ACLCommandsOnOtherUser");
    }


    @Test
    public void testACLCommandsOwnerItaly() throws Exception {
        scriptedTestProtocol
            .withLocale(Locale.ITALY)
            .run("ACLCommandsOnOwner");
    }

    @Test
    public void testACLCommandsOtherUserItaly() throws Exception {
        scriptedTestProtocol
            .withUser(OTHER_USER_NAME, OTHER_USER_PASSWORD)
            .withLocale(Locale.ITALY)
            .withGrantRights(OTHER_USER_MAILBOX, USER, readWriteSeenRight)
            .run("ACLCommandsOnOtherUser");
    }

}
