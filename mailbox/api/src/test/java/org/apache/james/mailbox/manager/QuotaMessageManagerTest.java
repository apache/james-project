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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.mail.Flags;

import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.mailbox.FlagsBuilder;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.OverQuotaException;
import org.apache.james.mailbox.mock.MockMail;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mime4j.dom.Message;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test for quota support upon basic Message manager operation.
 *
 * Tests are performed with sufficient rights to ensure all underlying functions behave well.
 * Quota are adjusted and we check that exceptions are well thrown.
 */
public abstract class QuotaMessageManagerTest<T extends MailboxManager> {

    private ManagerTestProvisionner provisionner;

    private MessageManager messageManager;
    private MailboxManager mailboxManager;
    private MaxQuotaManager maxQuotaManager;
    private QuotaManager quotaManager;
    private QuotaRootResolver quotaRootResolver;

    private MailboxSession session;
    private MailboxPath inbox;
    private MailboxPath subFolder;

    protected abstract IntegrationResources<T> createResources() throws Exception;

    @BeforeEach
    void setUp() throws Exception {
        IntegrationResources<T> resources = createResources();
        this.provisionner = new ManagerTestProvisionner(resources);
        this.provisionner.createMailboxes();
        messageManager = this.provisionner.getMessageManager();
        mailboxManager = resources.getMailboxManager();
        session = this.provisionner.getSession();
        inbox = this.provisionner.getInbox();
        subFolder = this.provisionner.getSubFolder();
        maxQuotaManager = resources.getMaxQuotaManager();
        quotaRootResolver = resources.getQuotaRootResolver();
        quotaManager = resources.getQuotaManager();
    }

    @Test
    void testAppendOverQuotaMessages() throws Exception {
        QuotaCountLimit maxMessageCount = QuotaCountLimit.count(8);
        maxQuotaManager.setMaxMessage(quotaRootResolver.getQuotaRoot(inbox), maxMessageCount);

        assertThatThrownBy(() -> provisionner.fillMailbox())
            .isInstanceOf(OverQuotaException.class);
    }

    @Test
    void testAppendOverQuotaSize() throws Exception {
        QuotaSizeLimit maxQuotaSize = QuotaSizeLimit.size(3 * MockMail.MAIL_TEXT_PLAIN.length() + 1);
        maxQuotaManager.setMaxStorage(quotaRootResolver.getQuotaRoot(inbox), maxQuotaSize);

        assertThatThrownBy(() -> provisionner.fillMailbox())
            .isInstanceOf(OverQuotaException.class);
    }

    @Test
    void testCopyOverQuotaMessages() throws Exception {
        try {
            provisionner.fillMailbox();
        } catch (OverQuotaException overQuotaException) {
            // Silent these exception as we don't want it to disturb the test
        }
        QuotaCountLimit maxMessageCount = QuotaCountLimit.count(15L);
        maxQuotaManager.setMaxMessage(quotaRootResolver.getQuotaRoot(inbox), maxMessageCount);

        assertThatThrownBy(() -> mailboxManager.copyMessages(MessageRange.all(), inbox, subFolder, session))
            .isInstanceOf(OverQuotaException.class);
    }

    @Test
    void testCopyOverQuotaSize() throws Exception {
        QuotaSizeLimit maxQuotaSize = QuotaSizeLimit.size(15L * MockMail.MAIL_TEXT_PLAIN.length());
        maxQuotaManager.setMaxStorage(quotaRootResolver.getQuotaRoot(inbox), maxQuotaSize);
        try {
            provisionner.fillMailbox();
        } catch (OverQuotaException overQuotaException) {
            // Silent these exception as we don't want it to disturb the test
        }
        assertThatThrownBy(() -> mailboxManager.copyMessages(MessageRange.all(), inbox, subFolder, session))
            .isInstanceOf(OverQuotaException.class);
    }

    @Test
    void testRetrievalOverMaxMessageAfterExpunge() throws Exception {
        QuotaCountLimit maxMessageCount = QuotaCountLimit.count(15L);
        maxQuotaManager.setMaxMessage(quotaRootResolver.getQuotaRoot(inbox), maxMessageCount);
        try {
            provisionner.fillMailbox();
        } catch (OverQuotaException overQuotaException) {
            // We are here over quota
        }
        messageManager.expunge(MessageRange.all(), session);
        // We have suppressed at list one message. Ensure we can add an other message. If is impossible, an exception will be thrown.
        assertThatCode(() -> provisionner.appendMessage(messageManager, session, new FlagsBuilder().add(Flags.Flag.SEEN).build()))
            .doesNotThrowAnyException();
    }

    @Test
    void testRetrievalOverMaxStorageAfterExpunge() throws Exception {
        QuotaSizeLimit maxQuotaSize = QuotaSizeLimit.size(15 * MockMail.MAIL_TEXT_PLAIN.getBytes(StandardCharsets.UTF_8).length + 1);
        maxQuotaManager.setMaxStorage(quotaRootResolver.getQuotaRoot(inbox), maxQuotaSize);
        try {
            provisionner.fillMailbox();
        } catch (OverQuotaException overQuotaException) {
            // We are here over quota
        }
        messageManager.expunge(MessageRange.all(), session);
        // We have suppressed at list one message. Ensure we can add an other message. If is impossible, an exception will be thrown.
        assertThatCode(() -> provisionner.appendMessage(messageManager, session, new FlagsBuilder().add(Flags.Flag.SEEN).build()))
            .doesNotThrowAnyException();
    }

    @Test
    void testRetrievalOverMaxMessageAfterDelete() throws Exception {
        QuotaCountLimit maxMessageCount = QuotaCountLimit.count(15L);
        maxQuotaManager.setMaxMessage(quotaRootResolver.getQuotaRoot(inbox), maxMessageCount);
        try {
            provisionner.fillMailbox();
        } catch (OverQuotaException overQuotaException) {
            // We are here over quota
        }

        List<MessageUid> uids = messageManager.getMetaData(true, session, MessageManager.MetaData.FetchGroup.UNSEEN_COUNT).getRecent();
        messageManager.delete(uids, session);
        // We have suppressed at list one message. Ensure we can add an other message. If is impossible, an exception will be thrown.
        assertThatCode(() -> provisionner.appendMessage(messageManager, session, new FlagsBuilder().add(Flags.Flag.SEEN).build()))
            .doesNotThrowAnyException();
    }

    @Test
    void testRetrievalOverMaxStorageAfterDelete() throws Exception {
        QuotaSizeLimit maxQuotaSize = QuotaSizeLimit.size(15 * MockMail.MAIL_TEXT_PLAIN.getBytes(StandardCharsets.UTF_8).length + 1);
        maxQuotaManager.setMaxStorage(quotaRootResolver.getQuotaRoot(inbox), maxQuotaSize);
        try {
            provisionner.fillMailbox();
        } catch (OverQuotaException overQuotaException) {
            // We are here over quota
        }

        List<MessageUid> uids = messageManager.getMetaData(true, session, MessageManager.MetaData.FetchGroup.UNSEEN_COUNT).getRecent();
        messageManager.delete(uids, session);
        // We have suppressed at list one message. Ensure we can add an other message. If is impossible, an exception will be thrown.
        assertThatCode(() -> provisionner.appendMessage(messageManager, session, new FlagsBuilder().add(Flags.Flag.SEEN).build()))
            .doesNotThrowAnyException();
    }

    @Test
    void deletingAMailboxShouldDecreaseCurrentQuota() throws Exception {
        provisionner.fillMailbox();

        mailboxManager.deleteMailbox(inbox, session);

        QuotaRoot quotaRoot = quotaRootResolver.getQuotaRoot(inbox);
        Quota<QuotaCountLimit, QuotaCountUsage> messageQuota = quotaManager.getMessageQuota(quotaRoot);
        Quota<QuotaSizeLimit, QuotaSizeUsage> storageQuota = quotaManager.getStorageQuota(quotaRoot);
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(messageQuota.getUsed()).isEqualTo(QuotaCountUsage.count(0));
            softly.assertThat(storageQuota.getUsed()).isEqualTo(QuotaSizeUsage.size(0));
        });
    }

    @Test
    void deletingAMailboxShouldPreserveQuotaOfOtherMailboxes() throws Exception {
        provisionner.fillMailbox();

        mailboxManager.getMailbox(subFolder, session)
            .appendMessage(MessageManager.AppendCommand.from(
                Message.Builder.of()
                    .setSubject("test")
                    .setBody("testmail", StandardCharsets.UTF_8)
                    .build()), session);

        mailboxManager.deleteMailbox(subFolder, session);

        QuotaRoot quotaRoot = quotaRootResolver.getQuotaRoot(inbox);
        Quota<QuotaCountLimit, QuotaCountUsage> messageQuota = quotaManager.getMessageQuota(quotaRoot);
        Quota<QuotaSizeLimit, QuotaSizeUsage> storageQuota = quotaManager.getStorageQuota(quotaRoot);
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(messageQuota.getUsed()).isEqualTo(QuotaCountUsage.count(16));
            softly.assertThat(storageQuota.getUsed()).isEqualTo(QuotaSizeUsage.size(16 * 247));
        });
    }
}