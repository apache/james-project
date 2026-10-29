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

package org.apache.james.imap.api.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.MailboxPath;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class DefaultMailboxTyperTest {
    private final Username username = Username.of("bob@example.com");
    private final DefaultMailboxTyper typer = new DefaultMailboxTyper();

    private ImapSession session() {
        MailboxSession mailboxSession = mock(MailboxSession.class);
        when(mailboxSession.getUser()).thenReturn(username);
        ImapSession session = mock(ImapSession.class);
        when(session.getMailboxSession()).thenReturn(mailboxSession);
        return session;
    }

    @ParameterizedTest
    @ValueSource(strings = {"x-custom", "x-", "X-custom", "ordinary"})
    void customMailboxShouldHaveNoSpecialUseOrXListAttribute(String name) {
        MailboxType type = typer.getMailboxType(session(), MailboxPath.forUser(username, name));

        assertThat(type).isEqualTo(MailboxType.OTHER);
        assertThat(type.getAttributeName()).isNull();
        assertThat(type.getRfc6154attributeName()).isNull();
    }

    @ParameterizedTest
    @CsvSource({"INBOX,INBOX", "InBoX,INBOX", "Drafts,DRAFTS", "Sent,SENT", "Trash,TRASH", "Spam,SPAM", "Archive,ARCHIVE"})
    void predefinedMailboxShouldKeepItsType(String name, MailboxType expected) {
        assertThat(typer.getMailboxType(session(), MailboxPath.forUser(username, name))).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"INBOX", "Drafts", "Spam", "x-custom"})
    void otherUsersMailboxShouldHaveNoSpecialUse(String name) {
        assertThat(typer.getMailboxType(session(), MailboxPath.forUser(Username.of("alice@example.com"), name)))
            .isEqualTo(MailboxType.OTHER);
    }
}
