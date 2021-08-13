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

package org.apache.james.jmap.draft.methods;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;

import org.apache.james.core.Username;
import org.apache.james.jmap.draft.exceptions.AttachmentsNotFoundException;
import org.apache.james.jmap.draft.exceptions.BlobNotFoundException;
import org.apache.james.jmap.draft.model.Attachment;
import org.apache.james.jmap.draft.model.Blob;
import org.apache.james.jmap.draft.model.BlobId;
import org.apache.james.jmap.draft.model.CreationMessage;
import org.apache.james.jmap.draft.model.CreationMessageId;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.exception.MailboxException;
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

    private BlobManager blobManager;
    private MailboxSession session;

    private AttachmentChecker sut;

    @Before
    public void setUp() {
        session = MailboxSessionUtil.create(Username.of("Jonhy"));
        blobManager = mock(BlobManager.class);

        sut = new AttachmentChecker(blobManager);
    }

    @Test
    public void assertAttachmentsExistShouldThrowWhenUnknownBlobId() throws MailboxException {
        BlobId unknownBlobId = BlobId.of("unknownBlobId");
        when(blobManager.retrieve(unknownBlobId, session)).thenThrow(new BlobNotFoundException(unknownBlobId));

        assertThatThrownBy(() -> sut.assertAttachmentsExist(
            new ValueWithId.CreationMessageEntry(
                creationMessageId,
                creationMessageBuilder.attachments(
                    Attachment.builder().size(12L).type("image/jpeg").blobId(unknownBlobId).build())
                    .build()
            ),
            session).block())
            .isInstanceOf(AttachmentsNotFoundException.class);
    }

    @Test
    public void assertAttachmentsExistShouldNotThrowWhenAttachmentExists() throws Exception {
        BlobId blobId = BlobId.of("unknownBlobId");
        when(blobManager.retrieve(blobId, session))
            .thenReturn(Blob.builder()
                .contentType("text/plain")
                .size(38)
                .payload(() -> new ByteArrayInputStream("".getBytes()))
                .id(blobId)
                .build());

        sut.assertAttachmentsExist(
            new ValueWithId.CreationMessageEntry(
                creationMessageId,
                creationMessageBuilder.attachments(
                    Attachment.builder().size(12L).type("image/jpeg").blobId(blobId).build())
                    .build()
            ),
            session).block();
    }

    @Test
    public void assertAttachmentsExistShouldThrowWhenUnknownBlobIds() throws MailboxException {
        BlobId unknownBlobId1 = BlobId.of("unknownBlobId1");
        BlobId unknownBlobId2 = BlobId.of("unknownBlobId2");

        when(blobManager.retrieve(unknownBlobId1, session)).thenThrow(new BlobNotFoundException(unknownBlobId1));
        when(blobManager.retrieve(unknownBlobId2, session)).thenThrow(new BlobNotFoundException(unknownBlobId2));

        assertThatThrownBy(() -> sut.assertAttachmentsExist(
            new ValueWithId.CreationMessageEntry(
                creationMessageId,
                creationMessageBuilder.attachments(
                    Attachment.builder().size(12L).type("image/jpeg").blobId(unknownBlobId1).build(),
                    Attachment.builder().size(23L).type("image/git").blobId(unknownBlobId2).build())
                    .build()
            ),
            session).block())
            .isInstanceOf(AttachmentsNotFoundException.class)
            .matches(e -> ((AttachmentsNotFoundException)e).getAttachmentIds().containsAll(ImmutableSet.of(unknownBlobId1, unknownBlobId2)));
    }

    @Test
    public void assertAttachmentsExistShouldNotThrowWhenKnownBlobIds() throws Exception {
        BlobId blobId1 = BlobId.of("unknownBlobId1");
        BlobId blobId2 = BlobId.of("unknownBlobId2");

        when(blobManager.retrieve(blobId1, session))
            .thenReturn(Blob.builder()
                .contentType("text/plain")
                .size(38)
                .payload(() -> new ByteArrayInputStream("".getBytes()))
                .id(blobId1)
                .build());
        when(blobManager.retrieve(blobId2, session))
            .thenReturn(Blob.builder()
                .contentType("text/plain")
                .size(38)
                .payload(() -> new ByteArrayInputStream("".getBytes()))
                .id(blobId2)
                .build());

        sut.assertAttachmentsExist(
            new ValueWithId.CreationMessageEntry(
                creationMessageId,
                creationMessageBuilder.attachments(
                    Attachment.builder().size(12L).type("image/jpeg").blobId(blobId1).build(),
                    Attachment.builder().size(23L).type("image/git").blobId(blobId2).build())
                    .build()
            ),
            session).block();
    }

    @Test
    public void assertAttachmentsExistShouldThrowWhenAtLeastOneUnknownBlobId() throws MailboxException {
        BlobId blobId1 = BlobId.of("unknownBlobId1");
        BlobId unknownBlobId2 = BlobId.of("unknownBlobId2");


        when(blobManager.retrieve(blobId1, session))
            .thenReturn(Blob.builder()
                .contentType("text/plain")
                .size(38)
                .payload(() -> new ByteArrayInputStream("".getBytes()))
                .id(blobId1)
                .build());
        when(blobManager.retrieve(unknownBlobId2, session)).thenThrow(new BlobNotFoundException(unknownBlobId2));

        assertThatThrownBy(() -> sut.assertAttachmentsExist(
            new ValueWithId.CreationMessageEntry(
                creationMessageId,
                creationMessageBuilder.attachments(
                    Attachment.builder().size(12L).type("image/jpeg").blobId(blobId1).build(),
                    Attachment.builder().size(23L).type("image/git").blobId(unknownBlobId2).build())
                    .build()
            ),
            session).block())
            .isInstanceOf(AttachmentsNotFoundException.class)
            .matches(e -> ((AttachmentsNotFoundException)e).getAttachmentIds()
                .containsAll(ImmutableSet.of(unknownBlobId2)));
    }

}