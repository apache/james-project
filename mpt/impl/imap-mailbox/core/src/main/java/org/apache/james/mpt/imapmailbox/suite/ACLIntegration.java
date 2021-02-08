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

public abstract class ACLIntegration implements ImapTestConstants {
    public static final Username OTHER_USER_NAME = Username.of("Boby");
    public static final String OTHER_USER_PASSWORD = "password";
    public static final MailboxPath OTHER_USER_MAILBOX = MailboxPath.forUser(OTHER_USER_NAME, "");
    public static final MailboxPath MY_INBOX = MailboxPath.forUser(USER, "");

    protected abstract ImapHostSystem createImapHostSystem();
    
    protected abstract GrantRightsOnHost createGrantRightsOnHost();
    
    protected abstract MailboxMessageAppender createMailboxMessageAppender();
    
    private ImapHostSystem system;
    private GrantRightsOnHost grantRightsOnHost;
    private MailboxMessageAppender mailboxMessageAppender;

    private ACLScriptedTestProtocol scriptedTestProtocol;

    @BeforeEach
    public void setUp() throws Exception {
        system = createImapHostSystem();
        grantRightsOnHost = createGrantRightsOnHost();
        mailboxMessageAppender = createMailboxMessageAppender();
        scriptedTestProtocol = new ACLScriptedTestProtocol(grantRightsOnHost, mailboxMessageAppender, "/org/apache/james/imap/scripts/", system)
                .withUser(USER, PASSWORD)
                .withUser(OTHER_USER_NAME, OTHER_USER_PASSWORD)
                .withLocale(Locale.US);
    }
    
    @Test
    public void rightRShouldBeSufficientToPerformStatusSelectCloseExamineUS() throws Exception {
        scriptedTestProtocol
            .withGrantRights(OTHER_USER_MAILBOX, USER, MailboxACL.Rfc4314Rights.fromSerializedRfc4314Rights("r"))
            .run("aclIntegration/ACLIntegrationRightR");
    }

    @Test
    public void rightRShouldBeNeededToPerformStatusSelectCloseExamineUS() throws Exception {
        scriptedTestProtocol
            .withGrantRights(OTHER_USER_MAILBOX, USER, MailboxACL.Rfc4314Rights.fromSerializedRfc4314Rights("lswipkxtecda"))
            .run("aclIntegration/ACLIntegrationWithoutRightR");
    }

    @Test
    public void rightLShouldBeSufficientToPerformListUS() throws Exception {
        scriptedTestProtocol
            .withGrantRights(OTHER_USER_MAILBOX, USER, MailboxACL.Rfc4314Rights.fromSerializedRfc4314Rights("l"))
            .run("aclIntegration/ACLIntegrationRightL");
    }

    @Test
    public void rightLShouldBeNeededToPerformListLsubSubscribeUS() throws Exception {
        scriptedTestProtocol
            .withGrantRights(OTHER_USER_MAILBOX, USER, MailboxACL.Rfc4314Rights.fromSerializedRfc4314Rights("rswipkxtecda"))
            .run("aclIntegration/ACLIntegrationWithoutRightL");
    }

    @Test
    public void rightAShouldBeSufficientToManageACLUS() throws Exception {
        scriptedTestProtocol
            .withGrantRights(OTHER_USER_MAILBOX, USER, MailboxACL.Rfc4314Rights.fromSerializedRfc4314Rights("a"))
            .run("aclIntegration/ACLIntegrationRightA");
    }

    @Test
    public void rightAShouldBeNeededToManageACLUS() throws Exception {
        scriptedTestProtocol
            .withGrantRights(OTHER_USER_MAILBOX, USER, MailboxACL.Rfc4314Rights.fromSerializedRfc4314Rights("rswipkxtecdl"))
            .run("aclIntegration/ACLIntegrationWithoutRightA");
    }

    @Test
    public void rightXOnOriginShouldBeSufficientToRenameAMailboxUS() throws Exception {
        scriptedTestProtocol
            .withMailbox(MailboxPath.forUser(OTHER_USER_NAME,"test"))
            .withGrantRights(MailboxPath.forUser(OTHER_USER_NAME, "test"), USER, MailboxACL.Rfc4314Rights.fromSerializedRfc4314Rights("x"))
            .run("aclIntegration/ACLIntegrationRightX");
    }

    @Test
    public void rightXOnOriginShouldBeNeededToRenameAMailboxUS() throws Exception {
        scriptedTestProtocol
            .withMailbox(MailboxPath.forUser(OTHER_USER_NAME,"test"))
            .withGrantRights(MailboxPath.forUser(OTHER_USER_NAME, "test"), USER, MailboxACL.Rfc4314Rights.fromSerializedRfc4314Rights("rswipktela"))
            .run("aclIntegration/ACLIntegrationWithoutRightX");
    }

    @Test
    public void rightKOnDestinationShouldBeSufficientToRenameAMailboxUS() throws Exception {
        MailboxPath newMailbox = MailboxPath.forUser(USER, "test");
        scriptedTestProtocol
            .withMailbox(newMailbox)
            .withGrantRights(newMailbox, USER, MailboxACL.Rfc4314Rights.fromSerializedRfc4314Rights("x"))
            .withGrantRights(OTHER_USER_MAILBOX, USER, MailboxACL.Rfc4314Rights.fromSerializedRfc4314Rights("k"))
            .run("aclIntegration/ACLIntegrationRightK");
    }

    @Test
    public void rightKOnDestinationShouldBeNeededToRenameAMailboxUS() throws Exception {
        MailboxPath newMailbox = MailboxPath.forUser(USER, "test");
        scriptedTestProtocol
            .withMailbox(newMailbox)
            .withGrantRights(newMailbox, USER, MailboxACL.Rfc4314Rights.fromSerializedRfc4314Rights("x"))
            .withGrantRights(OTHER_USER_MAILBOX, USER, MailboxACL.Rfc4314Rights.fromSerializedRfc4314Rights("rswipxtela"))
            .run("aclIntegration/ACLIntegrationWithoutRightK");
    }

    @Test
    public void rightREShouldBeSufficientToPerformExpungeUS() throws Exception {
        scriptedTestProtocol
            .withGrantRights(OTHER_USER_MAILBOX, USER, MailboxACL.Rfc4314Rights.fromSerializedRfc4314Rights("re"))
            .run("aclIntegration/ACLIntegrationRightRE");
    }

    @Test
    public void rightEShouldBeNeededToPerformExpungeUS() throws Exception {
        scriptedTestProtocol
            .withGrantRights(OTHER_USER_MAILBOX, USER, MailboxACL.Rfc4314Rights.fromSerializedRfc4314Rights("rswipxtclak"))
            .run("aclIntegration/ACLIntegrationWithoutRightE");
    }

    @Test
    public void rightIShouldBeSufficientToPerformAppendUS() throws Exception {
        scriptedTestProtocol
            .withGrantRights(OTHER_USER_MAILBOX, USER, MailboxACL.Rfc4314Rights.fromSerializedRfc4314Rights("ri"))
            .run("aclIntegration/ACLIntegrationRightI");
    }

    @Test
    public void rightIShouldBeNeededToPerformAppendUS() throws Exception {
        scriptedTestProtocol
            .withGrantRights(OTHER_USER_MAILBOX, USER, MailboxACL.Rfc4314Rights.fromSerializedRfc4314Rights("rswepxtcdlak"))
            .run("aclIntegration/ACLIntegrationWithoutRightI");
    }

    @Test
    public void rightISShouldBeSufficientToPerformAppendOfSeenMessageUS() throws Exception {
        scriptedTestProtocol
            .withGrantRights(OTHER_USER_MAILBOX, USER, MailboxACL.Rfc4314Rights.fromSerializedRfc4314Rights("ris"))
            .run("aclIntegration/ACLIntegrationRightIS");
    }

    @Test
    public void rightITShouldBeSufficientToPerformAppendOfDeletedMessageUS() throws Exception {
        scriptedTestProtocol
            .withGrantRights(OTHER_USER_MAILBOX, USER, MailboxACL.Rfc4314Rights.fromSerializedRfc4314Rights("rit"))
            .run("aclIntegration/ACLIntegrationRightIT");
    }

    @Test
    public void rightIWShouldBeSufficientToPerformAppendOfDeletedMessageUS() throws Exception {
        scriptedTestProtocol
            .withGrantRights(OTHER_USER_MAILBOX, USER, MailboxACL.Rfc4314Rights.fromSerializedRfc4314Rights("riw"))
            .run("aclIntegration/ACLIntegrationRightIW");
    }

    @Test
    public void rightRSShouldBeSufficientToPerformStoreAndFetchOnSeenMessageUS() throws Exception {
        scriptedTestProtocol
            .withGrantRights(OTHER_USER_MAILBOX, USER, MailboxACL.Rfc4314Rights.fromSerializedRfc4314Rights("rs"))
            .withFilledMailbox(OTHER_USER_MAILBOX)
            .run("aclIntegration/ACLIntegrationRightRS");
    }

    @Test
    public void rightSShouldBeNeededToPerformStoreAndFetchOnSeenMessageUS() throws Exception {
        scriptedTestProtocol
            .withGrantRights(OTHER_USER_MAILBOX, USER, MailboxACL.Rfc4314Rights.fromSerializedRfc4314Rights("rwipxtcdlake"))
            .withFilledMailbox(OTHER_USER_MAILBOX)
            .run("aclIntegration/ACLIntegrationWithoutRightS");
    }

    @Test
    public void rightRWShouldBeSufficientToPerformStoreOnFlaggedMessageUS() throws Exception {
        scriptedTestProtocol
            .withGrantRights(OTHER_USER_MAILBOX, USER, MailboxACL.Rfc4314Rights.fromSerializedRfc4314Rights("rw"))
            .withFilledMailbox(OTHER_USER_MAILBOX)
            .run("aclIntegration/ACLIntegrationRightRW");
    }

    @Test
    public void rightWShouldBeNeededToPerformStoreOnFlaggedMessageUS() throws Exception {
        scriptedTestProtocol
            .withGrantRights(OTHER_USER_MAILBOX, USER, MailboxACL.Rfc4314Rights.fromSerializedRfc4314Rights("rsipxtcdlake"))
            .withFilledMailbox(OTHER_USER_MAILBOX)
            .run("aclIntegration/ACLIntegrationWithoutRightW");
    }

    @Test
    public void rightRTShouldBeSufficientToPerformStoreOnDeletedMessageUS() throws Exception {
        scriptedTestProtocol
            .withGrantRights(OTHER_USER_MAILBOX, USER, MailboxACL.Rfc4314Rights.fromSerializedRfc4314Rights("rt"))
            .withFilledMailbox(OTHER_USER_MAILBOX)
            .run("aclIntegration/ACLIntegrationRightRT");
    }

    @Test
    public void rightTShouldBeNeededToPerformStoreOnFlaggedMessageUS() throws Exception {
        scriptedTestProtocol
            .withGrantRights(OTHER_USER_MAILBOX, USER, MailboxACL.Rfc4314Rights.fromSerializedRfc4314Rights("rwipxslake"))
            .withFilledMailbox(OTHER_USER_MAILBOX)
            .run("aclIntegration/ACLIntegrationWithoutRightT");
    }

    @Test
    public void rightIShouldBeSufficientToPerformCopyUS() throws Exception {
        scriptedTestProtocol
            .withGrantRights(OTHER_USER_MAILBOX, USER, MailboxACL.Rfc4314Rights.fromSerializedRfc4314Rights("i"))
            .withFilledMailbox(MY_INBOX)
            .run("aclIntegration/ACLIntegrationCopyI");
    }

    @Test
    public void rightIShouldBeNeededToPerformCopyUS() throws Exception {
        scriptedTestProtocol
            .withGrantRights(OTHER_USER_MAILBOX, USER, MailboxACL.Rfc4314Rights.fromSerializedRfc4314Rights("rswpxtcdlake"))
            .withFilledMailbox(MY_INBOX)
            .run("aclIntegration/ACLIntegrationCopyWithoutI");
    }

    @Test
    public void rightIShouldBeSufficientToPerformOfSeenMessagesCopyUS() throws Exception {
        scriptedTestProtocol
            .withGrantRights(OTHER_USER_MAILBOX, USER, MailboxACL.Rfc4314Rights.fromSerializedRfc4314Rights("ris"))
            .withFilledMailbox(MY_INBOX)
            .run("aclIntegration/ACLIntegrationCopyIS");
    }

    @Test
    public void rightSShouldBeNeededToPerformCopyOfSeenMessageUS() throws Exception {
        scriptedTestProtocol
            .withGrantRights(OTHER_USER_MAILBOX, USER, MailboxACL.Rfc4314Rights.fromSerializedRfc4314Rights("riwpxtcdlake"))
            .withFilledMailbox(MY_INBOX)
            .run("aclIntegration/ACLIntegrationCopyWithoutS");
    }

    @Test
    public void rightIWShouldBeSufficientToPerformOfFlaggedMessagesCopyUS() throws Exception {
        scriptedTestProtocol
            .withGrantRights(OTHER_USER_MAILBOX, USER, MailboxACL.Rfc4314Rights.fromSerializedRfc4314Rights("riw"))
            .withFilledMailbox(MY_INBOX)
            .run("aclIntegration/ACLIntegrationCopyIW");
    }

    @Test
    public void rightWShouldBeNeededToPerformCopyOfFlaggedMessageUS() throws Exception {
        scriptedTestProtocol
            .withGrantRights(OTHER_USER_MAILBOX, USER, MailboxACL.Rfc4314Rights.fromSerializedRfc4314Rights("rispxtcdlake"))
            .withFilledMailbox(MY_INBOX)
            .run("aclIntegration/ACLIntegrationCopyWithoutW");
    }

    @Test
    public void rightITShouldBeSufficientToPerformOfDeletedMessagesCopyUS() throws Exception {
        scriptedTestProtocol
            .withGrantRights(OTHER_USER_MAILBOX, USER, MailboxACL.Rfc4314Rights.fromSerializedRfc4314Rights("rit"))
            .withFilledMailbox(MY_INBOX)
            .run("aclIntegration/ACLIntegrationCopyIT");
    }

    @Test
    public void rightTShouldBeNeededToPerformCopyOfDeletedMessageUS() throws Exception {
        scriptedTestProtocol
            .withGrantRights(OTHER_USER_MAILBOX, USER, MailboxACL.Rfc4314Rights.fromSerializedRfc4314Rights("rispxwlake"))
            .withFilledMailbox(MY_INBOX)
            .run("aclIntegration/ACLIntegrationCopyWithoutT");
    }
}
