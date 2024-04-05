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

import static org.apache.james.jmap.methods.BlobManagerImpl.MESSAGE_RFC822_CONTENT_TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;

import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.memory.MemoryBlobStoreDAO;
import org.apache.james.core.Username;
import org.apache.james.jmap.exceptions.BlobNotFoundException;
import org.apache.james.jmap.model.Blob;
import org.apache.james.jmap.model.BlobId;
import org.apache.james.jmap.memory.upload.InMemoryUploadRepository;
import org.apache.james.mailbox.AttachmentManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.StringBackedAttachmentIdFactory;
import org.apache.james.mailbox.exception.AttachmentNotFoundException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.mailbox.model.StringBackedAttachmentId;
import org.apache.james.mailbox.model.AttachmentMetadata;
import org.apache.james.mailbox.model.ByteContent;
import org.apache.james.mailbox.model.Content;
import org.apache.james.mailbox.model.ContentType;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.TestMessageId;
import org.apache.james.server.blob.deduplication.DeDuplicationBlobStore;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class BlobManagerImplTest {
    static final String ID = "abc";
    static final StringBackedAttachmentId ATTACHMENT_ID = StringBackedAttachmentId.from(ID);
    static final ContentType CONTENT_TYPE = ContentType.of("text/plain");
    static final byte[] BYTES = "abc".getBytes(StandardCharsets.UTF_8);
    static final TestMessageId MESSAGE_ID = TestMessageId.of(125);
    static final BlobId BLOB_ID_ATTACHMENT = BlobId.of(ID);
    static final BlobId BLOB_ID_MESSAGE = BlobId.of(MESSAGE_ID.serialize());
    BlobManagerImpl blobManager;

    AttachmentManager attachmentManager;
    MessageIdManager messageIdManager;
    MailboxSession session;

    @BeforeEach
    void setUp() {
        attachmentManager = mock(AttachmentManager.class);
        messageIdManager = mock(MessageIdManager.class);
        session = MailboxSessionUtil.create(Username.of("user"));

        blobManager = new BlobManagerImpl(attachmentManager, messageIdManager, new TestMessageId.Factory(),
            new InMemoryUploadRepository(new DeDuplicationBlobStore(new MemoryBlobStoreDAO(), BucketName.of("default"), new HashBlobId.Factory()),
                Clock.systemUTC()), new StringBackedAttachmentIdFactory());
    }

    @Test
    void retrieveShouldReturnBlobWhenAttachment() throws Exception {
        when(attachmentManager.getAttachment(ATTACHMENT_ID, session))
            .thenReturn(AttachmentMetadata.builder()
                .messageId(InMemoryMessageId.of(45))
                .attachmentId(ATTACHMENT_ID)
                .size(BYTES.length)
                .type(CONTENT_TYPE)
                .build());
        when(attachmentManager.loadAttachmentContentReactive(ATTACHMENT_ID, session))
            .thenReturn(Mono.just(new ByteArrayInputStream(BYTES)));

        Blob blob = blobManager.retrieve(BLOB_ID_ATTACHMENT, session);

        SoftAssertions.assertSoftly(Throwing.consumer(
            softly -> {
                assertThat(blob.getBlobId()).isEqualTo(BlobId.of(ATTACHMENT_ID.getId()));
                assertThat(blob.getContentType()).isEqualTo(CONTENT_TYPE);
                assertThat(blob.getSize()).isEqualTo(BYTES.length);
                assertThat(blob.getStream()).hasSameContentAs(new ByteArrayInputStream(BYTES));
            }));
    }

    @Test
    void retrieveShouldThrowWhenNotFound() throws Exception {
        when(attachmentManager.getAttachment(ATTACHMENT_ID, session))
            .thenThrow(new AttachmentNotFoundException(ID));
        when(messageIdManager.getMessage(MESSAGE_ID, FetchGroup.FULL_CONTENT, session))
            .thenReturn(ImmutableList.of());

        assertThatThrownBy(() -> blobManager.retrieve(BLOB_ID_ATTACHMENT, session))
            .isInstanceOf(BlobNotFoundException.class);
    }

    @Test
    void retrieveShouldReturnBlobWhenMessage() throws Exception {
        when(attachmentManager.getAttachment(any(), any()))
            .thenThrow(new AttachmentNotFoundException(ID));

        MessageResult messageResult = mock(MessageResult.class);
        Content content = new ByteContent(BYTES);
        when(messageResult.getFullContent()).thenReturn(content);
        when(messageIdManager.getMessagesReactive(any(), eq(FetchGroup.FULL_CONTENT), eq(session)))
            .thenReturn(Flux.just(messageResult));

        Blob blob = blobManager.retrieve(BLOB_ID_MESSAGE, session);

        SoftAssertions.assertSoftly(Throwing.consumer(
            softly -> {
                assertThat(blob.getBlobId()).isEqualTo(BLOB_ID_MESSAGE);
                assertThat(blob.getContentType()).isEqualTo(MESSAGE_RFC822_CONTENT_TYPE);
                assertThat(blob.getSize()).isEqualTo(BYTES.length);
                assertThat(blob.getStream()).hasSameContentAs(new ByteArrayInputStream(BYTES));
            }));
    }

    @Test
    void retrieveShouldThrowOnMailboxExceptionWhenRetrievingAttachment() throws Exception {
        when(attachmentManager.getAttachment(any(), any()))
            .thenThrow(new MailboxException());

        assertThatThrownBy(() -> blobManager.retrieve(BLOB_ID_MESSAGE, session))
            .isInstanceOf(MailboxException.class);
    }

    @Test
    void retrieveShouldThrowOnRuntimeExceptionWhenRetrievingAttachment() throws Exception {
        when(attachmentManager.getAttachment(any(), any()))
            .thenThrow(new RuntimeException());

        assertThatThrownBy(() -> blobManager.retrieve(BLOB_ID_MESSAGE, session))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void retrieveShouldThrowOnRuntimeExceptionWhenRetrievingMessage() throws Exception {
        when(attachmentManager.getAttachment(any(), any()))
            .thenThrow(new AttachmentNotFoundException(ID));

        when(messageIdManager.getMessage(MESSAGE_ID, FetchGroup.FULL_CONTENT, session))
            .thenThrow(new RuntimeException());

        assertThatThrownBy(() -> blobManager.retrieve(BLOB_ID_MESSAGE, session))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void retrieveShouldThrowOnMailboxExceptionWhenRetrievingMessage() throws Exception {
        when(attachmentManager.getAttachment(any(), any()))
            .thenThrow(new AttachmentNotFoundException(ID));

        when(messageIdManager.getMessage(MESSAGE_ID, FetchGroup.FULL_CONTENT, session))
            .thenThrow(new MailboxException());

        assertThatThrownBy(() -> blobManager.retrieve(BLOB_ID_MESSAGE, session))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void retrieveShouldThrowOnMailboxExceptionWhenRetrievingMessageContent() throws Exception {
        when(attachmentManager.getAttachment(any(), any()))
            .thenThrow(new AttachmentNotFoundException(ID));

        MessageResult messageResult = mock(MessageResult.class);
        when(messageResult.getFullContent()).thenThrow(new MailboxException());
        when(messageIdManager.getMessage(MESSAGE_ID, FetchGroup.FULL_CONTENT, session))
            .thenReturn(ImmutableList.of(messageResult));

        assertThatThrownBy(() -> blobManager.retrieve(BLOB_ID_MESSAGE, session))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void retrieveShouldThrowOnRuntimeExceptionWhenRetrievingMessageContent() throws Exception {
        when(attachmentManager.getAttachment(any(), any()))
            .thenThrow(new AttachmentNotFoundException(ID));

        MessageResult messageResult = mock(MessageResult.class);
        when(messageResult.getFullContent()).thenThrow(new RuntimeException());
        when(messageIdManager.getMessage(MESSAGE_ID, FetchGroup.FULL_CONTENT, session))
            .thenReturn(ImmutableList.of(messageResult));

        assertThatThrownBy(() -> blobManager.retrieve(BLOB_ID_MESSAGE, session))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void retrieveShouldThrowOnIOExceptionWhenRetrievingMessageContentInputStream() throws Exception {
        when(attachmentManager.getAttachment(any(), any()))
            .thenThrow(new AttachmentNotFoundException(ID));

        MessageResult messageResult = mock(MessageResult.class);
        Content content = mock(Content.class);
        when(content.getInputStream()).thenThrow(new IOException());
        when(messageResult.getFullContent()).thenReturn(content);
        when(messageIdManager.getMessagesReactive(any(), eq(FetchGroup.FULL_CONTENT), eq(session)))
            .thenReturn(Flux.just(messageResult));

        Blob blob = blobManager.retrieve(BLOB_ID_MESSAGE, session);
        assertThatThrownBy(blob::getStream)
            .isInstanceOf(IOException.class);
    }

    @Test
    void retrieveShouldThrowOnRuntimeExceptionWhenRetrievingMessageContentInputStream() throws Exception {
        when(attachmentManager.getAttachment(any(), any()))
            .thenThrow(new AttachmentNotFoundException(ID));

        MessageResult messageResult = mock(MessageResult.class);
        Content content = mock(Content.class);
        when(content.getInputStream()).thenThrow(new RuntimeException());
        when(messageResult.getFullContent()).thenReturn(content);
        when(messageIdManager.getMessagesReactive(any(), eq(FetchGroup.FULL_CONTENT), eq(session)))
            .thenReturn(Flux.just(messageResult));

        Blob blob = blobManager.retrieve(BLOB_ID_MESSAGE, session);
        assertThatThrownBy(blob::getStream)
            .isInstanceOf(RuntimeException.class);
    }

}
