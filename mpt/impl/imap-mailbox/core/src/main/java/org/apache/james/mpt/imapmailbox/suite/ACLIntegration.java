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

import javax.inject.Inject;

import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.SimpleMailboxACL;
import org.apache.james.mpt.api.ImapHostSystem;
import org.apache.james.mpt.imapmailbox.GrantRightsOnHost;
import org.apache.james.mpt.imapmailbox.ImapTestConstants;
import org.apache.james.mpt.imapmailbox.MailboxMessageAppender;
import org.apache.james.mpt.script.SimpleScriptedTestProtocol;
import org.junit.Before;
import org.junit.Test;

public class ACLIntegration implements ImapTestConstants {
    public static final String OTHER_USER_NAME = "Boby";
    public static final String OTHER_USER_PASSWORD = "password";
    public static final MailboxPath OTHER_USER_MAILBOX = new MailboxPath("#private", OTHER_USER_NAME, "");
    public static final MailboxPath MY_INBOX = new MailboxPath("#private", USER, "");

    @Inject
    private static ImapHostSystem system;
    @Inject
    private GrantRightsOnHost grantRightsOnHost;
    @Inject
    private MailboxMessageAppender mailboxMessageAppender;

    private SimpleScriptedTestProtocol simpleScriptedTestProtocol;

    @Before
    public void setUp() throws Exception {
        simpleScriptedTestProtocol = new SimpleScriptedTestProtocol("/org/apache/james/imap/scripts/", system)
                .withUser(TO_ADDRESS, PASSWORD)
                .withUser(OTHER_USER_NAME, OTHER_USER_PASSWORD)
                .withLocale(Locale.US);
    }
    
    @Test
    public void rightRShouldBeSufficientToPerformStatusSelectCloseExamineUS() throws Exception {
        grantRightsOnHost.grantRights(OTHER_USER_MAILBOX, USER, new SimpleMailboxACL.Rfc4314Rights("r"));
        simpleScriptedTestProtocol.run("aclIntegration/ACLIntegrationRightR");
    }

    @Test
    public void rightRShouldBeNeededToPerformStatusSelectCloseExamineUS() throws Exception {
        grantRightsOnHost.grantRights(OTHER_USER_MAILBOX, USER, new SimpleMailboxACL.Rfc4314Rights("lswipkxtecda"));
        simpleScriptedTestProtocol.run("aclIntegration/ACLIntegrationWithoutRightR");
    }

    @Test
    public void rightLShouldBeSufficientToPerformListUS() throws Exception {
        grantRightsOnHost.grantRights(OTHER_USER_MAILBOX, USER, new SimpleMailboxACL.Rfc4314Rights("l"));
        simpleScriptedTestProtocol.run("aclIntegration/ACLIntegrationRightL");
    }

    @Test
    public void rightLShouldBeNeededToPerformListLsubSubscribeUS() throws Exception {
        grantRightsOnHost.grantRights(OTHER_USER_MAILBOX, USER, new SimpleMailboxACL.Rfc4314Rights("rswipkxtecda"));
        simpleScriptedTestProtocol.run("aclIntegration/ACLIntegrationWithoutRightL");
    }

    @Test
    public void rightAShouldBeSufficientToManageACLUS() throws Exception {
        grantRightsOnHost.grantRights(OTHER_USER_MAILBOX, USER, new SimpleMailboxACL.Rfc4314Rights("a"));
        simpleScriptedTestProtocol.run("aclIntegration/ACLIntegrationRightA");
    }

    @Test
    public void rightAShouldBeNeededToManageACLUS() throws Exception {
        grantRightsOnHost.grantRights(OTHER_USER_MAILBOX, USER, new SimpleMailboxACL.Rfc4314Rights("rswipkxtecdl"));
        simpleScriptedTestProtocol.run("aclIntegration/ACLIntegrationWithoutRightA");
    }

    @Test
    public void rightXOnOriginShouldBeSufficientToRenameAMailboxUS() throws Exception {
        system.createMailbox(new MailboxPath("#private","Boby","test"));
        grantRightsOnHost.grantRights(new MailboxPath("#private", OTHER_USER_NAME, "test"), USER, new SimpleMailboxACL.Rfc4314Rights("x"));
        simpleScriptedTestProtocol.run("aclIntegration/ACLIntegrationRightX");
    }

    @Test
    public void rightXOnOriginShouldBeNeededToRenameAMailboxUS() throws Exception {
        system.createMailbox(new MailboxPath("#private","Boby","test"));
        grantRightsOnHost.grantRights(new MailboxPath("#private", OTHER_USER_NAME, "test"), USER, new SimpleMailboxACL.Rfc4314Rights("rswipktela"));
        simpleScriptedTestProtocol.run("aclIntegration/ACLIntegrationWithoutRightX");
    }

    @Test
    public void rightKOnDestinationShouldBeSufficientToRenameAMailboxUS() throws Exception {
        MailboxPath newMailbox = new MailboxPath("#private", USER, "test");
        system.createMailbox(newMailbox);
        grantRightsOnHost.grantRights(newMailbox, USER, new SimpleMailboxACL.Rfc4314Rights("x"));
        grantRightsOnHost.grantRights(OTHER_USER_MAILBOX, USER, new SimpleMailboxACL.Rfc4314Rights("k"));
        simpleScriptedTestProtocol.run("aclIntegration/ACLIntegrationRightK");
    }

    @Test
    public void rightKOnDestinationShouldBeNeededToRenameAMailboxUS() throws Exception {
        MailboxPath newMailbox = new MailboxPath("#private", USER, "test");
        system.createMailbox(newMailbox);
        grantRightsOnHost.grantRights(newMailbox, USER, new SimpleMailboxACL.Rfc4314Rights("x"));
        grantRightsOnHost.grantRights(OTHER_USER_MAILBOX, USER, new SimpleMailboxACL.Rfc4314Rights("rswipxtela"));
        simpleScriptedTestProtocol.run("aclIntegration/ACLIntegrationWithoutRightK");
    }

    @Test
    public void rightREShouldBeSufficientToPerformExpungeUS() throws Exception {
        grantRightsOnHost.grantRights(OTHER_USER_MAILBOX, USER, new SimpleMailboxACL.Rfc4314Rights("re"));
        simpleScriptedTestProtocol.run("aclIntegration/ACLIntegrationRightRE");
    }

    @Test
    public void rightEShouldBeNeededToPerformExpungeUS() throws Exception {
        grantRightsOnHost.grantRights(OTHER_USER_MAILBOX, USER, new SimpleMailboxACL.Rfc4314Rights("rswipxtclak"));
        simpleScriptedTestProtocol.run("aclIntegration/ACLIntegrationWithoutRightE");
    }

    @Test
    public void rightIShouldBeSufficientToPerformAppendUS() throws Exception {
        grantRightsOnHost.grantRights(OTHER_USER_MAILBOX, USER, new SimpleMailboxACL.Rfc4314Rights("ri"));
        simpleScriptedTestProtocol.run("aclIntegration/ACLIntegrationRightI");
    }

    @Test
    public void rightIShouldBeNeededToPerformAppendUS() throws Exception {
        grantRightsOnHost.grantRights(OTHER_USER_MAILBOX, USER, new SimpleMailboxACL.Rfc4314Rights("rswepxtcdlak"));
        simpleScriptedTestProtocol.run("aclIntegration/ACLIntegrationWithoutRightI");
    }

    @Test
    public void rightISShouldBeSufficientToPerformAppendOfSeenMessageUS() throws Exception {
        grantRightsOnHost.grantRights(OTHER_USER_MAILBOX, USER, new SimpleMailboxACL.Rfc4314Rights("ris"));
        simpleScriptedTestProtocol.run("aclIntegration/ACLIntegrationRightIS");
    }

    @Test
    public void rightITShouldBeSufficientToPerformAppendOfDeletedMessageUS() throws Exception {
        grantRightsOnHost.grantRights(OTHER_USER_MAILBOX, USER, new SimpleMailboxACL.Rfc4314Rights("rit"));
        simpleScriptedTestProtocol.run("aclIntegration/ACLIntegrationRightIT");
    }

    @Test
    public void rightIWShouldBeSufficientToPerformAppendOfDeletedMessageUS() throws Exception {
        grantRightsOnHost.grantRights(OTHER_USER_MAILBOX, USER, new SimpleMailboxACL.Rfc4314Rights("riw"));
        simpleScriptedTestProtocol.run("aclIntegration/ACLIntegrationRightIW");
    }

    @Test
    public void rightRSShouldBeSufficientToPerformStoreAndFetchOnSeenMessageUS() throws Exception {
        grantRightsOnHost.grantRights(OTHER_USER_MAILBOX, USER, new SimpleMailboxACL.Rfc4314Rights("rs"));
        mailboxMessageAppender.fillMailbox(OTHER_USER_MAILBOX);
        simpleScriptedTestProtocol.run("aclIntegration/ACLIntegrationRightRS");
    }

    @Test
    public void rightSShouldBeNeededToPerformStoreAndFetchOnSeenMessageUS() throws Exception {
        grantRightsOnHost.grantRights(OTHER_USER_MAILBOX, USER, new SimpleMailboxACL.Rfc4314Rights("rwipxtcdlake"));
        mailboxMessageAppender.fillMailbox(OTHER_USER_MAILBOX);
        simpleScriptedTestProtocol.run("aclIntegration/ACLIntegrationWithoutRightS");
    }

    @Test
    public void rightRWShouldBeSufficientToPerformStoreOnFlaggedMessageUS() throws Exception {
        grantRightsOnHost.grantRights(OTHER_USER_MAILBOX, USER, new SimpleMailboxACL.Rfc4314Rights("rw"));
        mailboxMessageAppender.fillMailbox(OTHER_USER_MAILBOX);
        simpleScriptedTestProtocol.run("aclIntegration/ACLIntegrationRightRW");
    }

    @Test
    public void rightWShouldBeNeededToPerformStoreOnFlaggedMessageUS() throws Exception {
        grantRightsOnHost.grantRights(OTHER_USER_MAILBOX, USER, new SimpleMailboxACL.Rfc4314Rights("rsipxtcdlake"));
        mailboxMessageAppender.fillMailbox(OTHER_USER_MAILBOX);
        simpleScriptedTestProtocol.run("aclIntegration/ACLIntegrationWithoutRightW");
    }

    @Test
    public void rightRTShouldBeSufficientToPerformStoreOnDeletedMessageUS() throws Exception {
        grantRightsOnHost.grantRights(OTHER_USER_MAILBOX, USER, new SimpleMailboxACL.Rfc4314Rights("rt"));
        mailboxMessageAppender.fillMailbox(OTHER_USER_MAILBOX);
        simpleScriptedTestProtocol.run("aclIntegration/ACLIntegrationRightRT");
    }

    @Test
    public void rightTShouldBeNeededToPerformStoreOnFlaggedMessageUS() throws Exception {
        grantRightsOnHost.grantRights(OTHER_USER_MAILBOX, USER, new SimpleMailboxACL.Rfc4314Rights("rwipxslake"));
        mailboxMessageAppender.fillMailbox(OTHER_USER_MAILBOX);
        simpleScriptedTestProtocol.run("aclIntegration/ACLIntegrationWithoutRightT");
    }

    @Test
    public void rightIShouldBeSufficientToPerformCopyUS() throws Exception {
        grantRightsOnHost.grantRights(OTHER_USER_MAILBOX, USER, new SimpleMailboxACL.Rfc4314Rights("i"));
        mailboxMessageAppender.fillMailbox(MY_INBOX);
        simpleScriptedTestProtocol.run("aclIntegration/ACLIntegrationCopyI");
    }

    @Test
    public void rightIShouldBeNeededToPerformCopyUS() throws Exception {
        grantRightsOnHost.grantRights(OTHER_USER_MAILBOX, USER, new SimpleMailboxACL.Rfc4314Rights("rswpxtcdlake"));
        mailboxMessageAppender.fillMailbox(MY_INBOX);
        simpleScriptedTestProtocol.run("aclIntegration/ACLIntegrationCopyWithoutI");
    }

    @Test
    public void rightIShouldBeSufficientToPerformOfSeenMessagesCopyUS() throws Exception {
        grantRightsOnHost.grantRights(OTHER_USER_MAILBOX, USER, new SimpleMailboxACL.Rfc4314Rights("ris"));
        mailboxMessageAppender.fillMailbox(MY_INBOX);
        simpleScriptedTestProtocol.run("aclIntegration/ACLIntegrationCopyIS");
    }

    @Test
    public void rightSShouldBeNeededToPerformCopyOfSeenMessageUS() throws Exception {
        grantRightsOnHost.grantRights(OTHER_USER_MAILBOX, USER, new SimpleMailboxACL.Rfc4314Rights("riwpxtcdlake"));
        mailboxMessageAppender.fillMailbox(MY_INBOX);
        simpleScriptedTestProtocol.run("aclIntegration/ACLIntegrationCopyWithoutS");
    }

    @Test
    public void rightIWShouldBeSufficientToPerformOfFlaggedMessagesCopyUS() throws Exception {
        grantRightsOnHost.grantRights(OTHER_USER_MAILBOX, USER, new SimpleMailboxACL.Rfc4314Rights("riw"));
        mailboxMessageAppender.fillMailbox(MY_INBOX);
        simpleScriptedTestProtocol.run("aclIntegration/ACLIntegrationCopyIW");
    }

    @Test
    public void rightWShouldBeNeededToPerformCopyOfFlaggedMessageUS() throws Exception {
        grantRightsOnHost.grantRights(OTHER_USER_MAILBOX, USER, new SimpleMailboxACL.Rfc4314Rights("rispxtcdlake"));
        mailboxMessageAppender.fillMailbox(MY_INBOX);
        simpleScriptedTestProtocol.run("aclIntegration/ACLIntegrationCopyWithoutW");
    }

    @Test
    public void rightITShouldBeSufficientToPerformOfDeletedMessagesCopyUS() throws Exception {
        grantRightsOnHost.grantRights(OTHER_USER_MAILBOX, USER, new SimpleMailboxACL.Rfc4314Rights("rit"));
        mailboxMessageAppender.fillMailbox(MY_INBOX);
        simpleScriptedTestProtocol.run("aclIntegration/ACLIntegrationCopyIT");
    }

    @Test
    public void rightTShouldBeNeededToPerformCopyOfDeletedMessageUS() throws Exception {
        grantRightsOnHost.grantRights(OTHER_USER_MAILBOX, USER, new SimpleMailboxACL.Rfc4314Rights("rispxwlake"));
        mailboxMessageAppender.fillMailbox(MY_INBOX);
        simpleScriptedTestProtocol.run("aclIntegration/ACLIntegrationCopyWithoutT");
    }
}
