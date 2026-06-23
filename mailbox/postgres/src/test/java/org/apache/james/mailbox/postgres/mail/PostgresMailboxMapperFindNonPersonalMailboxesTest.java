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

package org.apache.james.mailbox.postgres.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.UidValidity;
import org.apache.james.mailbox.postgres.PostgresMailboxId;
import org.apache.james.mailbox.postgres.mail.dao.PostgresMailboxDAO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;

class PostgresMailboxMapperFindNonPersonalMailboxesTest {
    private static final Username USER = Username.of("bob@domain.tld");
    private static final MailboxACL.EntryKey USER_ENTRY_KEY = MailboxACL.EntryKey.createUserEntryKey(USER);
    private static final MailboxACL.Rfc4314Rights LOOKUP = new MailboxACL.Rfc4314Rights(MailboxACL.Right.Lookup);

    private PostgresMailboxDAO dao;
    private PostgresMailboxMapper testee;

    @BeforeEach
    void setUp() {
        dao = mock(PostgresMailboxDAO.class);
        testee = new PostgresMailboxMapper(dao);
    }

    private PostgresMailbox mailboxWithAcl(MailboxACL acl) {
        Mailbox mailbox = new Mailbox(MailboxPath.forUser(USER, "any"), UidValidity.of(1L), PostgresMailboxId.generate());
        mailbox.setACL(acl);
        return new PostgresMailbox(mailbox, ModSeq.first(), MessageUid.MIN_VALUE);
    }

    @Test
    void findNonPersonalMailboxesShouldNotThrowWhenAclEntryForUserIsMissing() {
        // Reproduces ISSUE-2445: the sliced ACL coming back from Postgres occasionally
        // does not contain the requested user's entry. The mailbox must then be filtered
        // out instead of triggering a NullPointerException that would poison the connection.
        PostgresMailbox mailboxWithoutUserEntry = mailboxWithAcl(new MailboxACL());
        when(dao.findMailboxesByUsername(USER)).thenReturn(Flux.just(mailboxWithoutUserEntry));

        assertThatCode(() -> testee.findNonPersonalMailboxes(USER, MailboxACL.Right.Lookup).collectList().block())
            .doesNotThrowAnyException();
    }

    @Test
    void findNonPersonalMailboxesShouldFilterOutMailboxesWithMissingUserAclEntry() {
        PostgresMailbox mailboxWithoutUserEntry = mailboxWithAcl(new MailboxACL());
        when(dao.findMailboxesByUsername(USER)).thenReturn(Flux.just(mailboxWithoutUserEntry));

        assertThat(testee.findNonPersonalMailboxes(USER, MailboxACL.Right.Lookup).collectList().block())
            .isEmpty();
    }

    @Test
    void findNonPersonalMailboxesShouldStillReturnMailboxesGrantingTheRight() {
        PostgresMailbox grantedMailbox = mailboxWithAcl(new MailboxACL(new MailboxACL.Entry(USER_ENTRY_KEY, LOOKUP)));
        PostgresMailbox mailboxWithoutUserEntry = mailboxWithAcl(new MailboxACL());
        when(dao.findMailboxesByUsername(USER)).thenReturn(Flux.just(grantedMailbox, mailboxWithoutUserEntry));

        assertThat(testee.findNonPersonalMailboxes(USER, MailboxACL.Right.Lookup).collectList().block())
            .containsExactly(grantedMailbox);
    }
}
