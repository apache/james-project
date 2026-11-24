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

public abstract class SharedMailboxRightsTest implements ImapTestConstants {
    private static final Username OTHER_USER_NAME = Username.of("Boby");
    private static final String OTHER_USER_PASSWORD = "password";
    private static final MailboxPath SHARED_MAILBOX = MailboxPath.forUser(OTHER_USER_NAME, "sharedMailbox");

    protected abstract ImapHostSystem createImapHostSystem();

    private ImapScriptedTestProtocol scriptedTestProtocol;

    @BeforeEach
    public void setUp() throws Exception {
        scriptedTestProtocol = new ImapScriptedTestProtocol("/org/apache/james/imap/scripts/", createImapHostSystem())
            .withUser(USER, PASSWORD)
            .withUser(OTHER_USER_NAME, OTHER_USER_PASSWORD)
            .withLocale(Locale.US);
        BasicImapCommands.welcome(scriptedTestProtocol);
        BasicImapCommands.authenticate(scriptedTestProtocol);
    }

    private ImapScriptedTestProtocol emptySharedMailbox(String rights) throws Exception {
        return scriptedTestProtocol
            .withMailbox(SHARED_MAILBOX)
            .withRights(SHARED_MAILBOX, USER, MailboxACL.Rfc4314Rights.fromSerializedRfc4314Rights(rights));
    }

    private ImapScriptedTestProtocol filledSharedMailbox(String rights) throws Exception {
        return scriptedTestProtocol
            .withFilledMailbox(SHARED_MAILBOX)
            .withRights(SHARED_MAILBOX, USER, MailboxACL.Rfc4314Rights.fromSerializedRfc4314Rights(rights));
    }

    @Test
    public void appendShouldDropTheFlagsTheDelegateIsNotAllowedToSet() throws Exception {
        emptySharedMailbox("lri")
            .run("SharedMailboxAppendWithoutFlagRights");
    }

    @Test
    public void appendShouldKeepTheFlagsTheDelegateIsAllowedToSet() throws Exception {
        emptySharedMailbox("lristw")
            .run("SharedMailboxAppendWithFlagRights");
    }

    @Test
    public void copyShouldDropTheFlagsTheDelegateIsNotAllowedToSet() throws Exception {
        emptySharedMailbox("lri")
            .run("SharedMailboxCopyWithoutFlagRights");
    }

    @Test
    public void moveShouldDropTheFlagsTheDelegateIsNotAllowedToSet() throws Exception {
        emptySharedMailbox("lri")
            .run("SharedMailboxMoveWithoutFlagRights");
    }

    @Test
    public void removingMessagesShouldBeRejectedWithoutPerformExpungeRight() throws Exception {
        filledSharedMailbox("lrt")
            .run("SharedMailboxRemoveWithoutPerformExpungeRight");
    }

    @Test
    public void removingMessagesShouldSucceedWithPerformExpungeRight() throws Exception {
        filledSharedMailbox("lrte")
            .run("SharedMailboxRemoveWithPerformExpungeRight");
    }

    @Test
    public void aNegativeReadEntryShouldHideTheMailboxContent() throws Exception {
        filledSharedMailbox("lra")
            .run("SharedMailboxNegativeReadRight");
    }
}
