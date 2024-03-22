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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.exception.AttachmentNotFoundException;
import org.apache.james.mailbox.model.AttachmentMetadata;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.StringBackedAttachmentId;
import org.apache.james.mailbox.model.TestMessageId;
import org.apache.james.mailbox.store.mail.AttachmentMapper;
import org.apache.james.mailbox.store.mail.AttachmentMapperFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

class StoreAttachmentManagerTest {
    static final TestMessageId MESSAGE_ID = TestMessageId.of(1L);
    static final ImmutableList<MessageId> MESSAGE_IDS = ImmutableList.of(MESSAGE_ID);
    static final StringBackedAttachmentId ATTACHMENT_ID = StringBackedAttachmentId.from("1");
    static final AttachmentMetadata ATTACHMENT = AttachmentMetadata.builder()
        .attachmentId(ATTACHMENT_ID)
        .messageId(MESSAGE_ID)
        .size(48)
        .type("type")
        .build();

    StoreAttachmentManager testee;
    AttachmentMapper attachmentMapper;
    MessageIdManager messageIdManager;

    @BeforeEach
    void setup() {
        attachmentMapper = mock(AttachmentMapper.class);
        AttachmentMapperFactory attachmentMapperFactory = mock(AttachmentMapperFactory.class);
        when(attachmentMapperFactory.getAttachmentMapper(any(MailboxSession.class)))
            .thenReturn(attachmentMapper);
        messageIdManager = mock(MessageIdManager.class);

        testee = new StoreAttachmentManager(attachmentMapperFactory, messageIdManager);
    }

    @Test
    void getAttachmentShouldThrowWhenAttachmentDoesNotBelongToUser() throws Exception {
        MailboxSession mailboxSession = mock(MailboxSession.class);
        when(attachmentMapper.getAttachment(ATTACHMENT_ID)).thenReturn(ATTACHMENT);
        when(messageIdManager.accessibleMessages(MESSAGE_IDS, mailboxSession)).thenReturn(ImmutableSet.of());

        assertThatThrownBy(() -> testee.getAttachment(ATTACHMENT_ID, mailboxSession))
            .isInstanceOf(AttachmentNotFoundException.class);
    }

}