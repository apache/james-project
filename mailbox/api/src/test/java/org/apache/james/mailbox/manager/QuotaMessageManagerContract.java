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

import static org.apache.james.mailbox.MessageManager.MailboxMetaData.RecentMode.RESET;
import static org.apache.james.mailbox.manager.ManagerTestProvisionner.INBOX;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;

import jakarta.mail.Flags;

import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.mailbox.FlagsBuilder;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.OverQuotaException;
import org.apache.james.mailbox.mock.MockMail;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mime4j.dom.Message;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

/**
 * Test for quota support upon basic Message manager operation.
 *
 * Tests are performed with sufficient rights to ensure all underlying functions behave well.
 * Quota are adjusted and we check that exceptions are well thrown.
 */
public interface QuotaMessageManagerContract<T extends MailboxManager> {

    IntegrationResources<T> getResources();

    ManagerTestProvisionner getProvisionner();

    @Test
    default void testAppendOverQuotaMessages() throws Exception {
        ManagerTestProvisionner provisionner = getProvisionner();
        IntegrationResources<T> resources = getResources();

        QuotaCountLimit maxMessageCount = QuotaCountLimit.count(8);
        resources.getMaxQuotaManager().setMaxMessage(resources.getQuotaRootResolver().getQuotaRoot(INBOX), maxMessageCount);

        assertThatThrownBy(() -> provisionner.fillMailbox())
            .isInstanceOf(OverQuotaException.class);
    }

    @Test
    default void testAppendOverQuotaSize() throws Exception {
        ManagerTestProvisionner provisionner = getProvisionner();
        IntegrationResources<T> resources = getResources();

        QuotaSizeLimit maxQuotaSize = QuotaSizeLimit.size(3 * MockMail.MAIL_TEXT_PLAIN.length() + 1);
        resources.getMaxQuotaManager().setMaxStorage(resources.getQuotaRootResolver().getQuotaRoot(INBOX), maxQuotaSize);

        assertThatThrownBy(() -> provisionner.fillMailbox())
            .isInstanceOf(OverQuotaException.class);
    }

    @Test
    default void testCopyOverQuotaMessages() throws Exception {
        ManagerTestProvisionner provisionner = getProvisionner();
        IntegrationResources<T> resources = getResources();

        try {
            provisionner.fillMailbox();
        } catch (OverQuotaException overQuotaException) {
            // Silent these exception as we don't want it to disturb the test
        }
        QuotaCountLimit maxMessageCount = QuotaCountLimit.count(15L);
        resources.getMaxQuotaManager().setMaxMessage(resources.getQuotaRootResolver().getQuotaRoot(INBOX), maxMessageCount);

        assertThatThrownBy(() -> resources.getMailboxManager().copyMessages(
                MessageRange.all(), INBOX, provisionner.getSubFolder(), provisionner.getSession()))
            .isInstanceOf(OverQuotaException.class);
    }

    @Test
    default void testCopyOverQuotaSize() throws Exception {
        ManagerTestProvisionner provisionner = getProvisionner();
        IntegrationResources<T> resources = getResources();

        QuotaSizeLimit maxQuotaSize = QuotaSizeLimit.size(15L * MockMail.MAIL_TEXT_PLAIN.length());
        resources.getMaxQuotaManager().setMaxStorage(resources.getQuotaRootResolver().getQuotaRoot(INBOX), maxQuotaSize);
        try {
            provisionner.fillMailbox();
        } catch (OverQuotaException overQuotaException) {
            // Silent these exception as we don't want it to disturb the test
        }
        assertThatThrownBy(() -> resources.getMailboxManager().copyMessages(
                MessageRange.all(), INBOX, provisionner.getSubFolder(), provisionner.getSession()))
            .isInstanceOf(OverQuotaException.class);
    }

    @Test
    default void testRetrievalOverMaxMessageAfterExpunge() throws Exception {
        ManagerTestProvisionner provisionner = getProvisionner();
        IntegrationResources<T> resources = getResources();

        QuotaCountLimit maxMessageCount = QuotaCountLimit.count(15L);
        resources.getMaxQuotaManager().setMaxMessage(resources.getQuotaRootResolver().getQuotaRoot(INBOX), maxMessageCount);
        try {
            provisionner.fillMailbox();
        } catch (OverQuotaException overQuotaException) {
            // We are here over quota
        }
        provisionner.getMessageManager().expunge(MessageRange.all(), provisionner.getSession());
        // We have suppressed at list one message. Ensure we can add an other message. If is impossible, an exception will be thrown.
        assertThatCode(() -> provisionner.appendMessage(
                provisionner.getMessageManager(), provisionner.getSession(), new FlagsBuilder().add(Flags.Flag.SEEN).build()))
            .doesNotThrowAnyException();
    }

    @Test
    default void testRetrievalOverMaxStorageAfterExpunge() throws Exception {
        ManagerTestProvisionner provisionner = getProvisionner();
        IntegrationResources<T> resources = getResources();

        QuotaSizeLimit maxQuotaSize = QuotaSizeLimit.size(15 * MockMail.MAIL_TEXT_PLAIN.getBytes(StandardCharsets.UTF_8).length + 1);
        resources.getMaxQuotaManager().setMaxStorage(resources.getQuotaRootResolver().getQuotaRoot(INBOX), maxQuotaSize);
        try {
            provisionner.fillMailbox();
        } catch (OverQuotaException overQuotaException) {
            // We are here over quota
        }
        provisionner.getMessageManager().expunge(MessageRange.all(), provisionner.getSession());
        // We have suppressed at list one message. Ensure we can add an other message. If is impossible, an exception will be thrown.
        assertThatCode(() -> provisionner.appendMessage(
                provisionner.getMessageManager(), provisionner.getSession(), new FlagsBuilder().add(Flags.Flag.SEEN).build()))
            .doesNotThrowAnyException();
    }

    @Test
    default void testRetrievalOverMaxMessageAfterDelete() throws Exception {
        ManagerTestProvisionner provisionner = getProvisionner();
        IntegrationResources<T> resources = getResources();

        QuotaCountLimit maxMessageCount = QuotaCountLimit.count(15L);
        resources.getMaxQuotaManager().setMaxMessage(resources.getQuotaRootResolver().getQuotaRoot(INBOX), maxMessageCount);
        try {
            provisionner.fillMailbox();
        } catch (OverQuotaException overQuotaException) {
            // We are here over quota
        }

        List<MessageUid> uids = provisionner.getMessageManager()
            .getMetaData(RESET, provisionner.getSession(), MessageManager.MailboxMetaData.FetchGroup.UNSEEN_COUNT)
            .getRecent();
        provisionner.getMessageManager().delete(uids, provisionner.getSession());
        // We have suppressed at list one message. Ensure we can add an other message. If is impossible, an exception will be thrown.
        assertThatCode(() -> provisionner.appendMessage(
                provisionner.getMessageManager(), provisionner.getSession(), new FlagsBuilder().add(Flags.Flag.SEEN).build()))
            .doesNotThrowAnyException();
    }

    @Test
    default void testRetrievalOverMaxStorageAfterDelete() throws Exception {
        ManagerTestProvisionner provisionner = getProvisionner();
        IntegrationResources<T> resources = getResources();

        QuotaSizeLimit maxQuotaSize = QuotaSizeLimit.size(15 * MockMail.MAIL_TEXT_PLAIN.getBytes(StandardCharsets.UTF_8).length + 1);
        resources.getMaxQuotaManager().setMaxStorage(resources.getQuotaRootResolver().getQuotaRoot(INBOX), maxQuotaSize);
        try {
            provisionner.fillMailbox();
        } catch (OverQuotaException overQuotaException) {
            // We are here over quota
        }

        List<MessageUid> uids = provisionner.getMessageManager()
            .getMetaData(RESET, provisionner.getSession(), MessageManager.MailboxMetaData.FetchGroup.UNSEEN_COUNT)
            .getRecent();
        provisionner.getMessageManager().delete(uids, provisionner.getSession());
        // We have suppressed at list one message. Ensure we can add an other message. If is impossible, an exception will be thrown.
        assertThatCode(() -> provisionner.appendMessage(
                provisionner.getMessageManager(), provisionner.getSession(), new FlagsBuilder().add(Flags.Flag.SEEN).build()))
            .doesNotThrowAnyException();
    }

    @Test
    default void deletingAMailboxShouldDecreaseCurrentQuota() throws Exception {
        ManagerTestProvisionner provisionner = getProvisionner();
        IntegrationResources<T> resources = getResources();

        provisionner.fillMailbox();

        resources.getMailboxManager().deleteMailbox(INBOX, provisionner.getSession());

        QuotaRoot quotaRoot = resources.getQuotaRootResolver().getQuotaRoot(INBOX);
        Quota<QuotaCountLimit, QuotaCountUsage> messageQuota = resources.getQuotaManager().getMessageQuota(quotaRoot);
        Quota<QuotaSizeLimit, QuotaSizeUsage> storageQuota = resources.getQuotaManager().getStorageQuota(quotaRoot);
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(messageQuota.getUsed()).isEqualTo(QuotaCountUsage.count(0));
            softly.assertThat(storageQuota.getUsed()).isEqualTo(QuotaSizeUsage.size(0));
        });
    }

    @Test
    default void deletingAMailboxShouldPreserveQuotaOfOtherMailboxes() throws Exception {
        ManagerTestProvisionner provisionner = getProvisionner();
        IntegrationResources<T> resources = getResources();

        provisionner.fillMailbox();

        resources.getMailboxManager().getMailbox(provisionner.getSubFolder(), provisionner.getSession())
            .appendMessage(MessageManager.AppendCommand.from(
                Message.Builder.of()
                    .setSubject("test")
                    .setBody("testmail", StandardCharsets.UTF_8)
                    .build()), provisionner.getSession());

        resources.getMailboxManager().deleteMailbox(provisionner.getSubFolder(), provisionner.getSession());

        QuotaRoot quotaRoot = resources.getQuotaRootResolver().getQuotaRoot(INBOX);
        Quota<QuotaCountLimit, QuotaCountUsage> messageQuota = resources.getQuotaManager().getMessageQuota(quotaRoot);
        Quota<QuotaSizeLimit, QuotaSizeUsage> storageQuota = resources.getQuotaManager().getStorageQuota(quotaRoot);
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(messageQuota.getUsed()).isEqualTo(QuotaCountUsage.count(16));
            softly.assertThat(storageQuota.getUsed()).isEqualTo(QuotaSizeUsage.size(16 * 247));
        });
    }
}