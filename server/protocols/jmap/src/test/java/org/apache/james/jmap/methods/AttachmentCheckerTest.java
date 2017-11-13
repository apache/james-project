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

package org.apache.james.jmap.methods;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.james.jmap.exceptions.AttachmentsNotFoundException;
import org.apache.james.jmap.model.Attachment;
import org.apache.james.jmap.model.BlobId;
import org.apache.james.jmap.model.CreationMessage;
import org.apache.james.jmap.model.CreationMessageId;
import org.apache.james.mailbox.AttachmentManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.mock.MockMailboxSession;
import org.apache.james.mailbox.model.AttachmentId;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class AttachmentCheckerTest {

    private final CreationMessageId creationMessageId = CreationMessageId.of("dlkja");
    private final CreationMessage.Builder creationMessageBuilder = CreationMessage.builder()
        .from(CreationMessage.DraftEmailer.builder().name("alice").email("alice@example.com").build())
        .to(ImmutableList.of(CreationMessage.DraftEmailer.builder().name("bob").email("bob@example.com").build()))
        .mailboxId("id")
        .subject("Hey! ");

    private AttachmentManager attachmentManager;
    private MailboxSession session;

    private AttachmentChecker sut;

    @Before
    public void setUp() {
        session = new MockMailboxSession("Jonhy");
        attachmentManager = mock(AttachmentManager.class);

        sut = new AttachmentChecker(attachmentManager);
    }

    @Test
    public void assertAttachmentsExistShouldThrowWhenUnknownBlobId() throws MailboxException {
        BlobId unknownBlobId = BlobId.of("unknownBlobId");
        AttachmentId unknownAttachmentId = AttachmentId.from(unknownBlobId.getRawValue());
        when(attachmentManager.exists(unknownAttachmentId, session)).thenReturn(false);

        assertThatThrownBy(() -> sut.assertAttachmentsExist(
            new ValueWithId.CreationMessageEntry(
                creationMessageId,
                creationMessageBuilder.attachments(
                    Attachment.builder().size(12L).type("image/jpeg").blobId(unknownBlobId).build())
                    .build()
            ),
            session))
            .isInstanceOf(AttachmentsNotFoundException.class);
    }

    @Test
    public void assertAttachmentsExistShouldThrowWhenUnknownBlobIds() throws MailboxException {
        BlobId unknownBlobId1 = BlobId.of("unknownBlobId1");
        BlobId unknownBlobId2 = BlobId.of("unknownBlobId2");
        AttachmentId unknownAttachmentId1 = AttachmentId.from(unknownBlobId1.getRawValue());
        AttachmentId unknownAttachmentId2 = AttachmentId.from(unknownBlobId2.getRawValue());

        when(attachmentManager.exists(unknownAttachmentId1, session)).thenReturn(false);
        when(attachmentManager.exists(unknownAttachmentId2, session)).thenReturn(false);

        assertThatThrownBy(() -> sut.assertAttachmentsExist(
            new ValueWithId.CreationMessageEntry(
                creationMessageId,
                creationMessageBuilder.attachments(
                    Attachment.builder().size(12L).type("image/jpeg").blobId(unknownBlobId1).build(),
                    Attachment.builder().size(23L).type("image/git").blobId(unknownBlobId2).build())
                    .build()
            ),
            session))
            .isInstanceOf(AttachmentsNotFoundException.class)
            .matches(e -> ((AttachmentsNotFoundException)e).getAttachmentIds().containsAll(ImmutableSet.of(unknownBlobId1, unknownBlobId2)));
    }

}