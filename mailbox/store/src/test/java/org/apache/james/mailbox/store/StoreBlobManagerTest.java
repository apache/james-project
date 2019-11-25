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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.james.core.Username;
import org.apache.james.mailbox.AttachmentManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.exception.AttachmentNotFoundException;
import org.apache.james.mailbox.exception.BlobNotFoundException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Attachment;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.Blob;
import org.apache.james.mailbox.model.BlobId;
import org.apache.james.mailbox.model.Content;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.TestMessageId;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;

public class StoreBlobManagerTest {
    public static final String ID = "abc";
    public static final AttachmentId ATTACHMENT_ID = AttachmentId.from(ID);
    public static final String CONTENT_TYPE = "text/plain";
    public static final byte[] BYTES = "abc".getBytes(StandardCharsets.UTF_8);
    public static final TestMessageId MESSAGE_ID = TestMessageId.of(125);
    public static final BlobId BLOB_ID_ATTACHMENT = BlobId.fromString(ID);
    public static final BlobId BLOB_ID_MESSAGE = BlobId.fromString(MESSAGE_ID.serialize());
    private StoreBlobManager blobManager;

    private AttachmentManager attachmentManager;
    private MessageIdManager messageIdManager;
    private MailboxSession session;

    @Before
    public void setUp() {
        attachmentManager = mock(AttachmentManager.class);
        messageIdManager = mock(MessageIdManager.class);
        session = MailboxSessionUtil.create(Username.of("user"));

        blobManager = new StoreBlobManager(attachmentManager, messageIdManager, new TestMessageId.Factory());
    }

    @Test
    public void retrieveShouldReturnBlobWhenAttachment() throws Exception {
        when(attachmentManager.getAttachment(ATTACHMENT_ID, session))
            .thenReturn(Attachment.builder()
                .attachmentId(ATTACHMENT_ID)
                .bytes(BYTES)
                .type(CONTENT_TYPE)
                .build());

        assertThat(blobManager.retrieve(BLOB_ID_ATTACHMENT, session))
            .isEqualTo(Blob.builder()
                .id(BlobId.fromString("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"))
                .contentType(CONTENT_TYPE)
                .payload(BYTES)
                .build());
    }

    @Test
    public void retrieveShouldThrowWhenNotFound() throws Exception {
        when(attachmentManager.getAttachment(ATTACHMENT_ID, session))
            .thenThrow(new AttachmentNotFoundException(ID));
        when(messageIdManager.getMessages(ImmutableList.of(MESSAGE_ID), FetchGroup.FULL_CONTENT, session))
            .thenReturn(ImmutableList.of());

        assertThatThrownBy(() -> blobManager.retrieve(BLOB_ID_ATTACHMENT, session))
            .isInstanceOf(BlobNotFoundException.class);
    }

    @Test
    public void retrieveShouldReturnBlobWhenMessage() throws Exception {
        when(attachmentManager.getAttachment(any(), any()))
            .thenThrow(new AttachmentNotFoundException(ID));

        MessageResult messageResult = mock(MessageResult.class);
        Content content = mock(Content.class);
        when(content.getInputStream()).thenReturn(new ByteArrayInputStream(BYTES));
        when(messageResult.getFullContent()).thenReturn(content);
        when(messageIdManager.getMessages(ImmutableList.of(MESSAGE_ID), FetchGroup.FULL_CONTENT, session))
            .thenReturn(ImmutableList.of(messageResult));

        assertThat(blobManager.retrieve(BLOB_ID_MESSAGE, session))
            .isEqualTo(Blob.builder()
                .id(BLOB_ID_MESSAGE)
                .contentType(StoreBlobManager.MESSAGE_RFC822_CONTENT_TYPE)
                .payload(BYTES)
                .build());
    }

    @Test
    public void retrieveShouldThrowOnMailboxExceptionWhenRetrievingAttachment() throws Exception {
        when(attachmentManager.getAttachment(any(), any()))
            .thenThrow(new MailboxException());

        assertThatThrownBy(() -> blobManager.retrieve(BLOB_ID_MESSAGE, session))
            .isInstanceOf(MailboxException.class);
    }

    @Test
    public void retrieveShouldThrowOnRuntimeExceptionWhenRetrievingAttachment() throws Exception {
        when(attachmentManager.getAttachment(any(), any()))
            .thenThrow(new RuntimeException());

        assertThatThrownBy(() -> blobManager.retrieve(BLOB_ID_MESSAGE, session))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    public void retrieveShouldThrowOnRuntimeExceptionWhenRetrievingMessage() throws Exception {
        when(attachmentManager.getAttachment(any(), any()))
            .thenThrow(new AttachmentNotFoundException(ID));

        when(messageIdManager.getMessages(ImmutableList.of(MESSAGE_ID), FetchGroup.FULL_CONTENT, session))
            .thenThrow(new RuntimeException());

        assertThatThrownBy(() -> blobManager.retrieve(BLOB_ID_MESSAGE, session))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    public void retrieveShouldThrowOnMailboxExceptionWhenRetrievingMessage() throws Exception {
        when(attachmentManager.getAttachment(any(), any()))
            .thenThrow(new AttachmentNotFoundException(ID));

        when(messageIdManager.getMessages(ImmutableList.of(MESSAGE_ID), FetchGroup.FULL_CONTENT, session))
            .thenThrow(new MailboxException());

        assertThatThrownBy(() -> blobManager.retrieve(BLOB_ID_MESSAGE, session))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    public void retrieveShouldThrowOnMailboxExceptionWhenRetrievingMessageContent() throws Exception {
        when(attachmentManager.getAttachment(any(), any()))
            .thenThrow(new AttachmentNotFoundException(ID));

        MessageResult messageResult = mock(MessageResult.class);
        when(messageResult.getFullContent()).thenThrow(new MailboxException());
        when(messageIdManager.getMessages(ImmutableList.of(MESSAGE_ID), FetchGroup.FULL_CONTENT, session))
            .thenReturn(ImmutableList.of(messageResult));

        assertThatThrownBy(() -> blobManager.retrieve(BLOB_ID_MESSAGE, session))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    public void retrieveShouldThrowOnRuntimeExceptionWhenRetrievingMessageContent() throws Exception {
        when(attachmentManager.getAttachment(any(), any()))
            .thenThrow(new AttachmentNotFoundException(ID));

        MessageResult messageResult = mock(MessageResult.class);
        when(messageResult.getFullContent()).thenThrow(new RuntimeException());
        when(messageIdManager.getMessages(ImmutableList.of(MESSAGE_ID), FetchGroup.FULL_CONTENT, session))
            .thenReturn(ImmutableList.of(messageResult));

        assertThatThrownBy(() -> blobManager.retrieve(BLOB_ID_MESSAGE, session))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    public void retrieveShouldThrowOnIOExceptionWhenRetrievingMessageContentInputStream() throws Exception {
        when(attachmentManager.getAttachment(any(), any()))
            .thenThrow(new AttachmentNotFoundException(ID));

        MessageResult messageResult = mock(MessageResult.class);
        Content content = mock(Content.class);
        when(content.getInputStream()).thenThrow(new IOException());
        when(messageResult.getFullContent()).thenReturn(content);
        when(messageIdManager.getMessages(ImmutableList.of(MESSAGE_ID), FetchGroup.FULL_CONTENT, session))
            .thenReturn(ImmutableList.of(messageResult));

        assertThatThrownBy(() -> blobManager.retrieve(BLOB_ID_MESSAGE, session))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    public void retrieveShouldThrowOnRuntimeExceptionWhenRetrievingMessageContentInputStream() throws Exception {
        when(attachmentManager.getAttachment(any(), any()))
            .thenThrow(new AttachmentNotFoundException(ID));

        MessageResult messageResult = mock(MessageResult.class);
        Content content = mock(Content.class);
        when(content.getInputStream()).thenThrow(new RuntimeException());
        when(messageResult.getFullContent()).thenReturn(content);
        when(messageIdManager.getMessages(ImmutableList.of(MESSAGE_ID), FetchGroup.FULL_CONTENT, session))
            .thenReturn(ImmutableList.of(messageResult));

        assertThatThrownBy(() -> blobManager.retrieve(BLOB_ID_MESSAGE, session))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    public void toBlobIdShouldReturnBlobIdCorrespondingToAMessageId() {
        assertThat(blobManager.toBlobId(MESSAGE_ID))
            .isEqualTo(BlobId.fromString("125"));
    }

}
