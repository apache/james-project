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

import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.SimpleMailboxACL;
import org.apache.james.mpt.api.HostSystem;
import org.apache.james.mpt.imapmailbox.GrantRightsOnHost;
import org.apache.james.mpt.imapmailbox.suite.base.BaseImapProtocol;
import org.junit.Test;

import javax.inject.Inject;
import java.util.Locale;

public class ACLCommands extends BaseImapProtocol {
    public static final String OTHER_USER_NAME = "Boby";
    public static final String OTHER_USER_PASSWORD = "password";
    public static final MailboxPath OTHER_USER_MAILBOX = new MailboxPath("#private", OTHER_USER_NAME, "") ;
    private final MailboxACL.MailboxACLRights readWriteSeenRight;

    @Inject
    private static HostSystem system;
    @Inject
    private GrantRightsOnHost grantRightsOnHost;

    public ACLCommands() throws Exception {
        super(system);
        readWriteSeenRight = new SimpleMailboxACL.Rfc4314Rights("rsw");
    }

    @Test
    public void testACLCommandsOwnerUS() throws Exception {
        scriptTest("ACLCommandsOnOwner", Locale.US);
    }

    @Test
    public void testACLCommandsOtherUserUS() throws Exception {
        system.addUser(OTHER_USER_NAME, OTHER_USER_PASSWORD);
        grantRightsOnHost.grantRights(OTHER_USER_MAILBOX, USER, readWriteSeenRight);
        scriptTest("ACLCommandsOnOtherUser", Locale.US);
    }

    @Test
    public void testACLCommandsOwnerKorea() throws Exception {
        scriptTest("ACLCommandsOnOwner", Locale.KOREA);
    }

    @Test
    public void testACLCommandsOtherUserKorea() throws Exception {
        system.addUser(OTHER_USER_NAME, OTHER_USER_PASSWORD);
        grantRightsOnHost.grantRights(OTHER_USER_MAILBOX, USER, readWriteSeenRight);
        scriptTest("ACLCommandsOnOtherUser", Locale.KOREA);
    }


    @Test
    public void testACLCommandsOwnerItaly() throws Exception {
        scriptTest("ACLCommandsOnOwner", Locale.ITALY);
    }

    @Test
    public void testACLCommandsOtherUserItaly() throws Exception {
        system.addUser(OTHER_USER_NAME, OTHER_USER_PASSWORD);
        grantRightsOnHost.grantRights(OTHER_USER_MAILBOX, USER, readWriteSeenRight);
        scriptTest("ACLCommandsOnOtherUser", Locale.ITALY);
    }

}
