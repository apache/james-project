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

package org.apache.james.mailbox.store.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;

import javax.mail.Flags;
import javax.mail.util.SharedByteArrayInputStream;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.mail.model.DefaultMessageId;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class MessageUtilsTest {
    private static final MessageUid MESSAGE_UID = MessageUid.of(1);
    private static final MessageId MESSAGE_ID = new DefaultMessageId();
    private static final int BODY_START = 16;
    private static final String CONTENT = "anycontent";
    
    @Mock private ModSeqProvider modSeqProvider;
    @Mock private UidProvider uidProvider;
    @Mock private Mailbox mailbox;
    private MailboxSession mailboxSession;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private MessageUtils messageUtils;
    private MailboxMessage message;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mailboxSession = MailboxSessionUtil.create(Username.of("user"));
        messageUtils = new MessageUtils(mailboxSession, uidProvider, modSeqProvider);
        message = new SimpleMailboxMessage(MESSAGE_ID, new Date(), CONTENT.length(), BODY_START, new SharedByteArrayInputStream(CONTENT.getBytes()), new Flags(), new PropertyBuilder(), mailbox.getMailboxId());
    }
    
    @Test
    public void newInstanceShouldFailWhenNullUidProvider() {
        expectedException.expect(NullPointerException.class);
        new MessageUtils(mailboxSession, null, modSeqProvider);
    }
    
    @Test
    public void newInstanceShouldFailWhenNullModSeqProvider() {
        expectedException.expect(NullPointerException.class);
        new MessageUtils(mailboxSession, uidProvider, null);
    }
    
    @Test
    public void getHighestModSeqShouldCallModSeqProvider() throws Exception {
        messageUtils.getHighestModSeq(mailbox);
        verify(modSeqProvider).highestModSeq(eq(mailbox));
    }
    
    @Test
    public void nextModSeqShouldCallModSeqProvider() throws Exception {
        messageUtils.nextModSeq(mailbox);
        verify(modSeqProvider).nextModSeq(eq(mailbox));
    }
    
    @Test
    public void getLastUidShouldCallUidProvider() throws Exception {
        messageUtils.getLastUid(mailbox);
        verify(uidProvider).lastUid(eq(mailboxSession), eq(mailbox));
    }
    
    @Test
    public void nextUidShouldCallUidProvider() throws Exception {
        messageUtils.nextUid(mailbox);
        verify(uidProvider).nextUid(eq(mailboxSession), eq(mailbox));
    }
    
    @Test
    public void enrichMesageShouldEnrichUidAndModSeq() throws Exception {
        when(uidProvider.nextUid(eq(mailboxSession), eq(mailbox))).thenReturn(MESSAGE_UID);
        when(modSeqProvider.nextModSeq(eq(mailbox))).thenReturn(ModSeq.of(11));

        messageUtils.enrichMessage(mailbox, message);
        
        assertThat(message.getUid()).isEqualTo(MESSAGE_UID);
        assertThat(message.getModSeq()).isEqualTo(ModSeq.of(11));
    }
}
