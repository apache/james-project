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
package org.apache.james.mailbox.spamassassin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Map;
import java.util.SortedMap;

import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.mailbox.MailboxListener.Added;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.store.event.EventFactory;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailbox;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;

public class SpamAssassinListenerTest {

    private SpamAssassin spamAssassin;
    private SpamAssassinListener listener;

    @Before
    public void setup() {
        spamAssassin = mock(SpamAssassin.class);
        listener = new SpamAssassinListener(spamAssassin);
    }

    @Test
    public void isEventOnSpamMailboxShouldReturnFalseWhenMailboxIsNotSpam() {
        MailboxSession mailboxSession = null;
        int uidValidity = 1;
        Mailbox mailbox = new SimpleMailbox(MailboxPath.forUser("user", "mbx"), uidValidity);
        SortedMap<MessageUid, MessageMetaData> uids = ImmutableSortedMap.of();
        Map<MessageUid, MailboxMessage> availableMessages = ImmutableMap.of();
        Added added = new EventFactory().added(mailboxSession, uids, mailbox, availableMessages);

        assertThat(listener.isEventOnSpamMailbox(added)).isFalse();
    }

    @Test
    public void isEventOnSpamMailboxShouldReturnTrueWhenMailboxIsSpam() {
        MailboxSession mailboxSession = null;
        int uidValidity = 1;
        Mailbox mailbox = new SimpleMailbox(MailboxPath.forUser("user", DefaultMailboxes.SPAM), uidValidity);
        SortedMap<MessageUid, MessageMetaData> uids = ImmutableSortedMap.of();
        Map<MessageUid, MailboxMessage> availableMessages = ImmutableMap.of();
        Added added = new EventFactory().added(mailboxSession, uids, mailbox, availableMessages);

        assertThat(listener.isEventOnSpamMailbox(added)).isTrue();
    }

    @Test
    public void isEventOnSpamMailboxShouldReturnFalseWhenMailboxIsSpamOtherCase() {
        MailboxSession mailboxSession = null;
        int uidValidity = 1;
        Mailbox mailbox = new SimpleMailbox(MailboxPath.forUser("user", "SPAM"), uidValidity);
        SortedMap<MessageUid, MessageMetaData> uids = ImmutableSortedMap.of();
        Map<MessageUid, MailboxMessage> availableMessages = ImmutableMap.of();
        Added added = new EventFactory().added(mailboxSession, uids, mailbox, availableMessages);

        assertThat(listener.isEventOnSpamMailbox(added)).isFalse();
    }

    @Test
    public void eventShouldCallSpamAssassinWhenTheEventMatches() {
        MailboxSession mailboxSession = null;
        int uidValidity = 1;
        Mailbox mailbox = new SimpleMailbox(MailboxPath.forUser("user", "Spam"), uidValidity);
        SortedMap<MessageUid, MessageMetaData> uids = ImmutableSortedMap.of();
        Map<MessageUid, MailboxMessage> availableMessages = ImmutableMap.of();
        Added added = new EventFactory().added(mailboxSession, uids, mailbox, availableMessages);

        listener.event(added);

        verify(spamAssassin).learnSpam(any(), any());
    }
}
