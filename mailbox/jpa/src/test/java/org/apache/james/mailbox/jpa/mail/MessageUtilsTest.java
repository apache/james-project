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

package org.apache.james.mailbox.jpa.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;

import javax.mail.Flags;
import javax.mail.util.SharedByteArrayInputStream;

import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.mail.ModSeqProvider;
import org.apache.james.mailbox.store.mail.UidProvider;
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

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private MessageUtils messageUtils;
    private MailboxMessage message;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        messageUtils = new MessageUtils(uidProvider, modSeqProvider);
        message = new SimpleMailboxMessage(MESSAGE_ID, new Date(), CONTENT.length(), BODY_START, new SharedByteArrayInputStream(CONTENT.getBytes()), new Flags(), new PropertyBuilder(), mailbox.getMailboxId());
    }
    
    @Test
    public void newInstanceShouldFailWhenNullUidProvider() {
        expectedException.expect(NullPointerException.class);
        new MessageUtils(null, modSeqProvider);
    }
    
    @Test
    public void newInstanceShouldFailWhenNullModSeqProvider() {
        expectedException.expect(NullPointerException.class);
        new MessageUtils(uidProvider, null);
    }
    
    @Test
    public void nextModSeqShouldCallModSeqProvider() throws Exception {
        messageUtils.nextModSeq(mailbox);
        verify(modSeqProvider).nextModSeq(eq(mailbox));
    }
    
    @Test
    public void nextUidShouldCallUidProvider() throws Exception {
        messageUtils.nextUid(mailbox);
        verify(uidProvider).nextUid(eq(mailbox));
    }
    
    @Test
    public void enrichMesageShouldEnrichUidAndModSeq() throws Exception {
        when(uidProvider.nextUid(eq(mailbox))).thenReturn(MESSAGE_UID);
        when(modSeqProvider.nextModSeq(eq(mailbox))).thenReturn(ModSeq.of(11));

        messageUtils.enrichMessage(mailbox, message);
        
        assertThat(message.getUid()).isEqualTo(MESSAGE_UID);
        assertThat(message.getModSeq()).isEqualTo(ModSeq.of(11));
    }
}
