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

package org.apache.james.mailbox.store;

import static org.apache.james.mailbox.fixture.MailboxFixture.ALICE;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.mail.Flags;

import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.OverQuotaException;
import org.apache.james.mailbox.fixture.MailboxFixture;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.quota.CurrentQuotaManager;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.mailbox.quota.QuotaManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

public abstract class AbstractMessageIdManagerQuotaTest {
    private static final MessageUid messageUid1 = MessageUid.of(111);

    public static final Flags FLAGS = new Flags();

    private MessageIdManager messageIdManager;
    private MailboxSession session;
    private Mailbox mailbox1;
    private Mailbox mailbox2;
    private Mailbox mailbox3;
    private MessageIdManagerTestSystem testingData;
    private MaxQuotaManager maxQuotaManager;

    protected abstract MessageIdManagerTestSystem createTestSystem(QuotaManager quotaManager, CurrentQuotaManager currentQuotaManager) throws Exception;

    protected abstract MaxQuotaManager createMaxQuotaManager();
    
    protected abstract CurrentQuotaManager createCurrentQuotaManager();
    
    protected abstract QuotaManager createQuotaManager(MaxQuotaManager maxQuotaManager, CurrentQuotaManager currentQuotaManager);

    @BeforeEach
    void setUp() throws Exception {
        maxQuotaManager = createMaxQuotaManager();
        CurrentQuotaManager currentQuotaManager = createCurrentQuotaManager();
        QuotaManager quotaManager = createQuotaManager(maxQuotaManager, currentQuotaManager);

        session = MailboxSessionUtil.create(ALICE);
        testingData = createTestSystem(quotaManager, currentQuotaManager);
        messageIdManager = testingData.getMessageIdManager();

        mailbox1 = testingData.createMailbox(MailboxFixture.INBOX_ALICE, session);
        mailbox2 = testingData.createMailbox(MailboxFixture.OUTBOX_ALICE, session);
        mailbox3 = testingData.createMailbox(MailboxFixture.SENT_ALICE, session);
    }

    @Test
    void setInMailboxesShouldNotThrowWhenMessageQuotaNotExceeded() throws Exception {
        maxQuotaManager.setGlobalMaxMessage(QuotaCountLimit.count(1));

        MessageId messageId = testingData.persist(mailbox2.getMailboxId(), messageUid1, FLAGS, session);

        messageIdManager.setInMailboxes(messageId, ImmutableList.of(mailbox1.getMailboxId()), session);
    }

    @Test
    void setInMailboxesShouldNotThrowWhenStorageQuotaNotExceeded() throws Exception {
        maxQuotaManager.setGlobalMaxStorage(QuotaSizeLimit.size(testingData.getConstantMessageSize()));

        MessageId messageId = testingData.persist(mailbox2.getMailboxId(), messageUid1, FLAGS, session);

        messageIdManager.setInMailboxes(messageId, ImmutableList.of(mailbox1.getMailboxId()), session);
    }

    @Test
    void setInMailboxesShouldThrowWhenStorageQuotaExceeded() throws Exception {
        maxQuotaManager.setGlobalMaxStorage(QuotaSizeLimit.size(2 * testingData.getConstantMessageSize()));

        testingData.persist(mailbox1.getMailboxId(), messageUid1, FLAGS, session);
        MessageId messageId = testingData.persist(mailbox2.getMailboxId(), messageUid1, FLAGS, session);

        assertThatThrownBy(() -> messageIdManager.setInMailboxes(messageId,
                ImmutableList.of(mailbox1.getMailboxId(), mailbox2.getMailboxId()),
                session))
            .isInstanceOf(OverQuotaException.class);
    }

    @Test
    void setInMailboxesShouldThrowWhenStorageQuotaExceededWhenCopiedToMultipleMailboxes() throws Exception {
        maxQuotaManager.setGlobalMaxStorage(QuotaSizeLimit.size(2 * testingData.getConstantMessageSize()));

        MessageId messageId = testingData.persist(mailbox1.getMailboxId(), messageUid1, FLAGS, session);

        assertThatThrownBy(() -> messageIdManager.setInMailboxes(messageId,
                ImmutableList.of(mailbox1.getMailboxId(), mailbox2.getMailboxId(), mailbox3.getMailboxId()),
                session))
            .isInstanceOf(OverQuotaException.class);
    }

    @Test
    void setInMailboxesShouldThrowWhenStorageMessageExceeded() throws Exception {
        maxQuotaManager.setGlobalMaxMessage(QuotaCountLimit.count(2));

        testingData.persist(mailbox1.getMailboxId(), messageUid1, FLAGS, session);
        MessageId messageId = testingData.persist(mailbox2.getMailboxId(), messageUid1, FLAGS, session);

        assertThatThrownBy(() -> messageIdManager.setInMailboxes(messageId,
                ImmutableList.of(mailbox1.getMailboxId(), mailbox2.getMailboxId(), mailbox3.getMailboxId()),
                session))
            .isInstanceOf(OverQuotaException.class);
    }
}
