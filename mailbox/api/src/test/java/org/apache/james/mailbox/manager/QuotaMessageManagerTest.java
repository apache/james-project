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

package org.apache.james.mailbox.manager;

import org.apache.james.mailbox.FlagsBuilder;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.exception.OverQuotaException;
import org.apache.james.mailbox.mock.MockMail;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.mail.Flags;

/**
 * Test for quota support upon basic Message manager operation.
 *
 * Tests are performed with sufficient rights to ensure all underlying functions behave well.
 * Quota are adjusted and we check that exceptions are well thrown.
 */
public abstract class QuotaMessageManagerTest {

    private ManagerTestResources resources;

    private MessageManager messageManager;
    private MailboxManager mailboxManager;
    private MaxQuotaManager maxQuotaManager;
    private QuotaRootResolver quotaRootResolver;

    private MailboxSession session;
    private MailboxPath inbox;
    private MailboxPath subFolder;

    protected abstract ManagerTestResources createResources() throws Exception;

    @Before
    public void setUp() throws Exception {
        resources = createResources();
        resources.createMailboxes();
        messageManager = resources.getMessageManager();
        mailboxManager = resources.getMailboxManager();
        session = resources.getSession();
        inbox = resources.getInbox();
        subFolder = resources.getSubFolder();
        maxQuotaManager = resources.getMaxQuotaManager();
        quotaRootResolver = resources.getQuotaRootResolver();
    }

    @After
    public void cleanUp() throws Exception {
        resources.getIntegrationResources().clean();
    }

    @Test(expected = OverQuotaException.class)
    public void testAppendOverQuotaMessages() throws Exception {
        maxQuotaManager.setMaxMessage(quotaRootResolver.getQuotaRoot(inbox), 8l);
        resources.fillMailbox();
    }

    @Test(expected = OverQuotaException.class)
    public void testAppendOverQuotaSize() throws Exception {
        maxQuotaManager.setMaxStorage(quotaRootResolver.getQuotaRoot(inbox), 3 * MockMail.MAIL_TEXT_PLAIN.length() + 1);
        resources.fillMailbox();
    }

    @Test(expected = OverQuotaException.class)
    public void testCopyOverQuotaMessages() throws Exception {
        try {
            resources.fillMailbox();
        } catch(OverQuotaException overQuotaException) {
            // Silent these exception as we don't want it to disturb the test
        }
        maxQuotaManager.setMaxMessage(quotaRootResolver.getQuotaRoot(inbox), 20l);
        mailboxManager.copyMessages(MessageRange.all(), inbox, subFolder, session);
    }

    @Test(expected = OverQuotaException.class)
    public void testCopyOverQuotaSize() throws Exception {
        maxQuotaManager.setMaxStorage(quotaRootResolver.getQuotaRoot(inbox), 20l * MockMail.MAIL_TEXT_PLAIN.length());
        try {
            resources.fillMailbox();
        } catch(OverQuotaException overQuotaException) {
            // Silent these exception as we don't want it to disturb the test
        }
        mailboxManager.copyMessages(MessageRange.all(), inbox, subFolder, session);
    }

    @Test
    public void testRetrievalOverMaxMessage() throws Exception {
        maxQuotaManager.setMaxMessage(quotaRootResolver.getQuotaRoot(inbox), 8l);
        try {
            resources.fillMailbox();
        } catch(OverQuotaException overQuotaException) {
            // We are here over quota
        }
        messageManager.expunge(MessageRange.all(), session);
        // We have suppressed at list one message. Ensure we can add an other message. If is impossible, an exception will be thrown.
        resources.appendMessage(messageManager, session, new FlagsBuilder().add(Flags.Flag.SEEN).build());
    }

    @Test
    public void testRetrievalOverMaxStorage() throws Exception {
        maxQuotaManager.setMaxStorage(quotaRootResolver.getQuotaRoot(inbox), 8 * MockMail.MAIL_TEXT_PLAIN.length() + 1);
        try {
            resources.fillMailbox();
        } catch(OverQuotaException overQuotaException) {
            // We are here over quota
        }
        messageManager.expunge(MessageRange.all(), session);
        // We have suppressed at list one message. Ensure we can add an other message. If is impossible, an exception will be thrown.
        resources.appendMessage(messageManager, session, new FlagsBuilder().add(Flags.Flag.SEEN).build());
    }


}