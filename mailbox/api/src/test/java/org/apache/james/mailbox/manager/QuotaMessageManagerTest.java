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

import java.nio.charset.StandardCharsets;

import javax.mail.Flags;

import org.apache.james.core.quota.QuotaCount;
import org.apache.james.core.quota.QuotaSize;
import org.apache.james.mailbox.FlagsBuilder;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.OverQuotaException;
import org.apache.james.mailbox.mock.MockMail;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mime4j.dom.Message;
import org.assertj.core.api.SoftAssertions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Test for quota support upon basic Message manager operation.
 *
 * Tests are performed with sufficient rights to ensure all underlying functions behave well.
 * Quota are adjusted and we check that exceptions are well thrown.
 */
public abstract class QuotaMessageManagerTest<T extends MailboxManager> {

    private ManagerTestResources<T> resources;

    private MessageManager messageManager;
    private MailboxManager mailboxManager;
    private MaxQuotaManager maxQuotaManager;
    private QuotaRootResolver quotaRootResolver;

    private MailboxSession session;
    private MailboxPath inbox;
    private MailboxPath subFolder;

    protected abstract ManagerTestResources<T> createResources() throws Exception;

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
        QuotaCount maxMessageCount = QuotaCount.count(8);
        maxQuotaManager.setMaxMessage(quotaRootResolver.getQuotaRoot(inbox), maxMessageCount);
        resources.fillMailbox();
    }

    @Test(expected = OverQuotaException.class)
    public void testAppendOverQuotaSize() throws Exception {
        QuotaSize maxQuotaSize = QuotaSize.size(3 * MockMail.MAIL_TEXT_PLAIN.length() + 1);
        maxQuotaManager.setMaxStorage(quotaRootResolver.getQuotaRoot(inbox), maxQuotaSize);
        resources.fillMailbox();
    }

    @Test(expected = OverQuotaException.class)
    public void testCopyOverQuotaMessages() throws Exception {
        try {
            resources.fillMailbox();
        } catch (OverQuotaException overQuotaException) {
            // Silent these exception as we don't want it to disturb the test
        }
        QuotaCount maxMessageCount = QuotaCount.count(15L);
        maxQuotaManager.setMaxMessage(quotaRootResolver.getQuotaRoot(inbox), maxMessageCount);
        mailboxManager.copyMessages(MessageRange.all(), inbox, subFolder, session);
    }

    @Test(expected = OverQuotaException.class)
    public void testCopyOverQuotaSize() throws Exception {
        QuotaSize maxQuotaSize = QuotaSize.size(15L * MockMail.MAIL_TEXT_PLAIN.length());
        maxQuotaManager.setMaxStorage(quotaRootResolver.getQuotaRoot(inbox), maxQuotaSize);
        try {
            resources.fillMailbox();
        } catch (OverQuotaException overQuotaException) {
            // Silent these exception as we don't want it to disturb the test
        }
        mailboxManager.copyMessages(MessageRange.all(), inbox, subFolder, session);
    }

    @Test
    public void testRetrievalOverMaxMessage() throws Exception {
        QuotaCount maxMessageCount = QuotaCount.count(15L);
        maxQuotaManager.setMaxMessage(quotaRootResolver.getQuotaRoot(inbox), maxMessageCount);
        try {
            resources.fillMailbox();
        } catch (OverQuotaException overQuotaException) {
            // We are here over quota
        }
        messageManager.expunge(MessageRange.all(), session);
        // We have suppressed at list one message. Ensure we can add an other message. If is impossible, an exception will be thrown.
        resources.appendMessage(messageManager, session, new FlagsBuilder().add(Flags.Flag.SEEN).build());
    }

    @Test
    public void testRetrievalOverMaxStorage() throws Exception {
        QuotaSize maxQuotaSize = QuotaSize.size(15 * MockMail.MAIL_TEXT_PLAIN.getBytes(StandardCharsets.UTF_8).length + 1);
        maxQuotaManager.setMaxStorage(quotaRootResolver.getQuotaRoot(inbox), maxQuotaSize);
        try {
            resources.fillMailbox();
        } catch (OverQuotaException overQuotaException) {
            // We are here over quota
        }
        messageManager.expunge(MessageRange.all(), session);
        // We have suppressed at list one message. Ensure we can add an other message. If is impossible, an exception will be thrown.
        resources.appendMessage(messageManager, session, new FlagsBuilder().add(Flags.Flag.SEEN).build());
    }

    @Test
    public void deletingAMailboxShouldDecreaseCurrentQuota() throws Exception {
        resources.fillMailbox();

        mailboxManager.deleteMailbox(inbox, session);

        QuotaRoot quotaRoot = quotaRootResolver.getQuotaRoot(inbox);
        Quota<QuotaCount> messageQuota = resources.getQuotaManager().getMessageQuota(quotaRoot);
        Quota<QuotaSize> storageQuota = resources.getQuotaManager().getStorageQuota(quotaRoot);
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(messageQuota.getUsed()).isEqualTo(QuotaCount.count(0));
            softly.assertThat(storageQuota.getUsed()).isEqualTo(QuotaSize.size(0));
        });
    }

    @Test
    public void deletingAMailboxShouldPreserveQuotaOfOtherMailboxes() throws Exception {
        resources.fillMailbox();

        mailboxManager.getMailbox(subFolder, session)
            .appendMessage(MessageManager.AppendCommand.from(
                Message.Builder.of()
                    .setSubject("test")
                    .setBody("testmail", StandardCharsets.UTF_8)
                    .build()), session);

        mailboxManager.deleteMailbox(subFolder, session);

        QuotaRoot quotaRoot = quotaRootResolver.getQuotaRoot(inbox);
        Quota<QuotaCount> messageQuota = resources.getQuotaManager().getMessageQuota(quotaRoot);
        Quota<QuotaSize> storageQuota = resources.getQuotaManager().getStorageQuota(quotaRoot);
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(messageQuota.getUsed()).isEqualTo(QuotaCount.count(16));
            softly.assertThat(storageQuota.getUsed()).isEqualTo(QuotaSize.size(16 * 247));
        });
    }
}