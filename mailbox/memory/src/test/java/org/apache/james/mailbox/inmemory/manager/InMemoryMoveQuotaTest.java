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

package org.apache.james.mailbox.inmemory.manager;

import static org.apache.james.mailbox.fixture.MailboxFixture.ALICE;
import static org.apache.james.mailbox.fixture.MailboxFixture.BOB;
import static org.apache.james.mailbox.fixture.MailboxFixture.BOB_2;
import static org.apache.james.mailbox.fixture.MailboxFixture.INBOX_ALICE;
import static org.apache.james.mailbox.fixture.MailboxFixture.INBOX_BOB;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.OverQuotaException;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mime4j.dom.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryMoveQuotaTest {
    private InMemoryIntegrationResources resources;
    private MailboxManager mailboxManager;
    private MailboxSession aliceSession;
    private MailboxSession bobSession;

    @BeforeEach
    void setUp() throws Exception {
        resources = InMemoryIntegrationResources.defaultResources();
        mailboxManager = resources.getMailboxManager();
        aliceSession = mailboxManager.createSystemSession(ALICE);
        bobSession = mailboxManager.createSystemSession(BOB);
        mailboxManager.createMailbox(INBOX_ALICE, aliceSession);
        mailboxManager.createMailbox(INBOX_BOB, bobSession);
        mailboxManager.createMailbox(BOB_2, bobSession);
        mailboxManager.applyRightsCommand(INBOX_ALICE, MailboxACL.command().forUser(BOB)
            .rights(MailboxACL.Right.Lookup, MailboxACL.Right.Read, MailboxACL.Right.Insert)
            .asAddition(), aliceSession);
        mailboxManager.getMailbox(INBOX_BOB, bobSession)
            .appendMessage(MessageManager.AppendCommand.from(Message.Builder.of()
                .setSubject("test")
                .setBody("a body longer than the quota", StandardCharsets.UTF_8)), bobSession);
    }

    @Test
    void moveToAnotherQuotaRootShouldRespectItsQuota() throws Exception {
        resources.getMaxQuotaManager().setMaxStorage(resources.getQuotaRootResolver().getQuotaRoot(INBOX_ALICE), QuotaSizeLimit.size(10));

        assertThatThrownBy(() -> mailboxManager.moveMessages(MessageRange.all(), INBOX_BOB, INBOX_ALICE, bobSession))
            .isInstanceOf(OverQuotaException.class);
        assertThat(mailboxManager.getMailbox(INBOX_BOB, bobSession).getMessageCount(bobSession)).isEqualTo(1);
    }

    @Test
    void moveWithinAQuotaRootShouldBeAllowedWhenOverQuota() throws Exception {
        resources.getMaxQuotaManager().setMaxStorage(resources.getQuotaRootResolver().getQuotaRoot(INBOX_BOB), QuotaSizeLimit.size(10));

        mailboxManager.moveMessages(MessageRange.all(), INBOX_BOB, BOB_2, bobSession);

        assertThat(mailboxManager.getMailbox(BOB_2, bobSession).getMessageCount(bobSession)).isEqualTo(1);
    }
}
