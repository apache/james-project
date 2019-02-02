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

import java.nio.charset.StandardCharsets;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.exception.AttachmentNotFoundException;
import org.apache.james.mailbox.model.Attachment;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.TestMessageId;
import org.apache.james.mailbox.store.mail.AttachmentMapper;
import org.apache.james.mailbox.store.mail.AttachmentMapperFactory;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class StoreAttachmentManagerTest {
    private static final TestMessageId MESSAGE_ID = TestMessageId.of(1L);
    private static final ImmutableList<MessageId> MESSAGE_IDS = ImmutableList.of(MESSAGE_ID);
    private static final AttachmentId ATTACHMENT_ID = AttachmentId.from("1");
    private static final Attachment ATTACHMENT = Attachment.builder()
        .attachmentId(ATTACHMENT_ID)
        .type("type")
        .bytes("Any".getBytes(StandardCharsets.UTF_8))
        .build();

    private StoreAttachmentManager testee;
    private AttachmentMapper attachmentMapper;
    private MessageIdManager messageIdManager;

    @Before
    public void setup() throws Exception {
        attachmentMapper = mock(AttachmentMapper.class);
        AttachmentMapperFactory attachmentMapperFactory = mock(AttachmentMapperFactory.class);
        when(attachmentMapperFactory.getAttachmentMapper(any(MailboxSession.class)))
            .thenReturn(attachmentMapper);
        messageIdManager = mock(MessageIdManager.class);

        testee = new StoreAttachmentManager(attachmentMapperFactory, messageIdManager);
    }

    @Test
    public void getAttachmentShouldThrowWhenAttachmentDoesNotBelongToUser() throws Exception {
        MailboxSession mailboxSession = mock(MailboxSession.class);
        when(attachmentMapper.getAttachment(ATTACHMENT_ID)).thenReturn(ATTACHMENT);
        when(attachmentMapper.getRelatedMessageIds(ATTACHMENT_ID)).thenReturn(MESSAGE_IDS);
        when(attachmentMapper.getOwners(ATTACHMENT_ID)).thenReturn(ImmutableList.of());
        when(messageIdManager.accessibleMessages(MESSAGE_IDS, mailboxSession)).thenReturn(ImmutableSet.of());

        assertThatThrownBy(() -> testee.getAttachment(ATTACHMENT_ID, mailboxSession))
            .isInstanceOf(AttachmentNotFoundException.class);
    }

}