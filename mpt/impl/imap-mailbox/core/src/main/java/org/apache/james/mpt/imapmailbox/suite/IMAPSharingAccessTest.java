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
import org.apache.james.mpt.imapmailbox.ImapTestConstants;
import org.apache.james.mpt.imapmailbox.suite.base.BasicImapCommands;
import org.apache.james.mpt.script.ImapScriptedTestProtocol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public abstract class IMAPSharingAccessTest implements ImapTestConstants {
    public static final Username OTHER_USER_NAME = Username.of("Boby");
    public static final String OTHER_USER_PASSWORD = "password";

    protected abstract ImapHostSystem createImapHostSystem();

    private ImapHostSystem system;
    private ImapScriptedTestProtocol scriptedTestProtocol;

    @BeforeEach
    public void setUp() throws Exception {
        system = createImapHostSystem();
        scriptedTestProtocol = new ImapScriptedTestProtocol("/org/apache/james/imap/scripts/", system)
            .withUser(USER, PASSWORD)
            .withUser(OTHER_USER_NAME, OTHER_USER_PASSWORD)
            .withFilledMailbox(MailboxPath.inbox(USER))
            .withFilledMailbox(MailboxPath.forUser(OTHER_USER_NAME, "mailbox-l"))
            .withRights(MailboxPath.forUser(OTHER_USER_NAME, "mailbox-l"), USER, MailboxACL.Rfc4314Rights.fromSerializedRfc4314Rights("l"))
            .withFilledMailbox(MailboxPath.forUser(OTHER_USER_NAME, "mailbox-lr"))
            .withRights(MailboxPath.forUser(OTHER_USER_NAME, "mailbox-lr"), USER, MailboxACL.Rfc4314Rights.fromSerializedRfc4314Rights("lr"))
            .withFilledMailbox(MailboxPath.forUser(OTHER_USER_NAME, "mailbox-lrs"))
            .withRights(MailboxPath.forUser(OTHER_USER_NAME, "mailbox-lrs"), USER, MailboxACL.Rfc4314Rights.fromSerializedRfc4314Rights("lrs"))
            .withFilledMailbox(MailboxPath.forUser(OTHER_USER_NAME, "mailbox-lrw"))
            .withRights(MailboxPath.forUser(OTHER_USER_NAME, "mailbox-lrw"), USER, MailboxACL.Rfc4314Rights.fromSerializedRfc4314Rights("lrw"))
            .withFilledMailbox(MailboxPath.forUser(OTHER_USER_NAME, "mailbox-lri"))
            .withRights(MailboxPath.forUser(OTHER_USER_NAME, "mailbox-lri"), USER, MailboxACL.Rfc4314Rights.fromSerializedRfc4314Rights("lri"))
            .withFilledMailbox(MailboxPath.forUser(OTHER_USER_NAME, "mailbox-lrk"))
            .withRights(MailboxPath.forUser(OTHER_USER_NAME, "mailbox-lrk"), USER, MailboxACL.Rfc4314Rights.fromSerializedRfc4314Rights("lrk"))
            .withFilledMailbox(MailboxPath.forUser(OTHER_USER_NAME, "mailbox-lrx"))
            .withRights(MailboxPath.forUser(OTHER_USER_NAME, "mailbox-lrx"), USER, MailboxACL.Rfc4314Rights.fromSerializedRfc4314Rights("lrx"))
            .withFilledMailbox(MailboxPath.forUser(OTHER_USER_NAME, "mailbox-lrt")) // todo
            .withRights(MailboxPath.forUser(OTHER_USER_NAME, "mailbox-lrt"), USER, MailboxACL.Rfc4314Rights.fromSerializedRfc4314Rights("lrt"))
            .withFilledMailbox(MailboxPath.forUser(OTHER_USER_NAME, "mailbox-lrte")) // todo
            .withRights(MailboxPath.forUser(OTHER_USER_NAME, "mailbox-lrte"), USER, MailboxACL.Rfc4314Rights.fromSerializedRfc4314Rights("lrte"))
            .withFilledMailbox(MailboxPath.forUser(OTHER_USER_NAME, "mailbox-lra"))
            .withRights(MailboxPath.forUser(OTHER_USER_NAME, "mailbox-lra"), USER, MailboxACL.Rfc4314Rights.fromSerializedRfc4314Rights("lra"));
        BasicImapCommands.welcome(scriptedTestProtocol);
        BasicImapCommands.authenticate(scriptedTestProtocol);
    }

    @Test
    public void testMailboxL() throws Exception {
        scriptedTestProtocol
            .withLocale(Locale.US)
            .run("SharingAccessL");
    }

    @Test
    public void testMailboxLR() throws Exception {
        scriptedTestProtocol
            .withLocale(Locale.US)
            .run("SharingAccessLR");
    }

    @Test
    public void testMailboxLRS() throws Exception {
        scriptedTestProtocol
            .withLocale(Locale.US)
            .run("SharingAccessLRS");
    }

    @Test
    public void testMailboxLRK() throws Exception {
        scriptedTestProtocol
            .withLocale(Locale.US)
            .run("SharingAccessLRK");
    }

    @Test
    public void testMailboxLRX() throws Exception {
        scriptedTestProtocol
            .withLocale(Locale.US)
            .run("SharingAccessLRX");
    }

    @Test
    public void testMailboxLRA() throws Exception {
        scriptedTestProtocol
            .withLocale(Locale.US)
            .run("SharingAccessLRA");
    }

    @Test
    public void testMailboxLRI() throws Exception {
        scriptedTestProtocol
            .withLocale(Locale.US)
            .run("SharingAccessLRI");
    }

    @Test
    public void testMailboxLRW() throws Exception {
        scriptedTestProtocol
            .withLocale(Locale.US)
            .run("SharingAccessLRW");
    }

    @Test
    public void testMailboxLRT() throws Exception {
        scriptedTestProtocol
            .withLocale(Locale.US)
            .run("SharingAccessLRT");
    }

    @Test
    public void testMailboxLRTE() throws Exception {
        scriptedTestProtocol
            .withLocale(Locale.US)
            .run("SharingAccessLRTE");
    }
}
