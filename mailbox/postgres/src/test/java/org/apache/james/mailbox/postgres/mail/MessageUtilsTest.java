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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;

import javax.mail.Flags;

import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.model.ByteContent;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.postgres.mail.MessageUtils;
import org.apache.james.mailbox.store.mail.ModSeqProvider;
import org.apache.james.mailbox.store.mail.UidProvider;
import org.apache.james.mailbox.store.mail.model.DefaultMessageId;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class MessageUtilsTest {
    static final MessageUid MESSAGE_UID = MessageUid.of(1);
    static final MessageId MESSAGE_ID = new DefaultMessageId();
    static final ThreadId THREAD_ID = ThreadId.fromBaseMessageId(MESSAGE_ID);
    static final int BODY_START = 16;
    static final String CONTENT = "anycontent";
    
    @Mock ModSeqProvider modSeqProvider;
    @Mock UidProvider uidProvider;
    @Mock Mailbox mailbox;

    MessageUtils messageUtils;
    MailboxMessage message;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        messageUtils = new MessageUtils(uidProvider, modSeqProvider);
        message = new SimpleMailboxMessage(MESSAGE_ID, THREAD_ID, new Date(), CONTENT.length(), BODY_START,
            new ByteContent(CONTENT.getBytes()), new Flags(), new PropertyBuilder().build(), mailbox.getMailboxId());
    }
    
    @Test
    void newInstanceShouldFailWhenNullUidProvider() {
        assertThatThrownBy(() -> new MessageUtils(null, modSeqProvider))
            .isInstanceOf(NullPointerException.class);
    }
    
    @Test
    void newInstanceShouldFailWhenNullModSeqProvider() {
        assertThatThrownBy(() -> new MessageUtils(uidProvider, null))
            .isInstanceOf(NullPointerException.class);
    }
    
    @Test
    void nextModSeqShouldCallModSeqProvider() throws Exception {
        messageUtils.nextModSeq(mailbox);
        verify(modSeqProvider).nextModSeq(eq(mailbox));
    }
    
    @Test
    void nextUidShouldCallUidProvider() throws Exception {
        messageUtils.nextUid(mailbox);
        verify(uidProvider).nextUid(eq(mailbox));
    }
    
    @Test
    void enrichMesageShouldEnrichUidAndModSeq() throws Exception {
        when(uidProvider.nextUid(eq(mailbox))).thenReturn(MESSAGE_UID);
        when(modSeqProvider.nextModSeq(eq(mailbox))).thenReturn(ModSeq.of(11));

        messageUtils.enrichMessage(mailbox, message);
        
        assertThat(message.getUid()).isEqualTo(MESSAGE_UID);
        assertThat(message.getModSeq()).isEqualTo(ModSeq.of(11));
    }
}
