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

package org.apache.james.mailbox.store.mail.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.apache.james.mailbox.exception.AttachmentNotFoundException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Attachment;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.store.mail.AttachmentMapper;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;

public abstract class AttachmentMapperTest {

    private AttachmentMapper attachmentMapper;
    private MapperProvider mapperProvider;

    @Rule
    public ExpectedException expected = ExpectedException.none();

    protected abstract MapperProvider createMapperProvider();

    @Before
    public final void setUp() throws MailboxException {
        mapperProvider = createMapperProvider();
        Assume.assumeTrue(mapperProvider.getSupportedCapabilities().contains(MapperProvider.Capabilities.ATTACHMENT));
        this.attachmentMapper = mapperProvider.createAttachmentMapper();
    }

    @After
    public void tearDown() throws Exception {
        mapperProvider.clearMapper();
        mapperProvider.close();
    }

    @Test
    public void getAttachmentShouldThrowWhenNullAttachmentId() throws Exception {
        expected.expect(IllegalArgumentException.class);
        attachmentMapper.getAttachment(null);
    }

    @Test
    public void getAttachmentShouldThrowWhenNonReferencedAttachmentId() throws Exception {
        expected.expect(AttachmentNotFoundException.class);
        attachmentMapper.getAttachment(AttachmentId.forPayload("unknown".getBytes(Charsets.UTF_8)));
    }

    @Test
    public void getAttachmentShouldReturnTheAttachmentWhenReferenced() throws Exception {
        //Given
        Attachment expected = Attachment.builder()
                .bytes("payload".getBytes(Charsets.UTF_8))
                .type("content")
                .build();
        AttachmentId attachmentId = expected.getAttachmentId();
        attachmentMapper.storeAttachment(expected);
        //When
        Attachment attachment = attachmentMapper.getAttachment(attachmentId);
        //Then
        assertThat(attachment).isEqualTo(expected);
    }

    @Test
    public void getAttachmentShouldReturnTheAttachmentsWhenMultipleStored() throws Exception {
        //Given
        Attachment expected1 = Attachment.builder()
                .bytes("payload1".getBytes(Charsets.UTF_8))
                .type("content1")
                .build();
        Attachment expected2 = Attachment.builder()
                .bytes("payload2".getBytes(Charsets.UTF_8))
                .type("content2")
                .build();
        AttachmentId attachmentId1 = expected1.getAttachmentId();
        AttachmentId attachmentId2 = expected2.getAttachmentId();
        //When
        attachmentMapper.storeAttachments(ImmutableList.of(expected1, expected2));
        //Then
        Attachment attachment1 = attachmentMapper.getAttachment(attachmentId1);
        Attachment attachment2 = attachmentMapper.getAttachment(attachmentId2);
        assertThat(attachment1).isEqualTo(expected1);
        assertThat(attachment2).isEqualTo(expected2);
    }

    @Test
    public void getAttachmentsShouldThrowWhenNullAttachmentId() throws Exception {
        expected.expect(IllegalArgumentException.class);
        attachmentMapper.getAttachments(null);
    }

    @Test
    public void getAttachmentsShouldReturnEmptyListWhenNonReferencedAttachmentId() throws Exception {
        List<Attachment> attachments = attachmentMapper.getAttachments(ImmutableList.of(AttachmentId.forPayload("unknown".getBytes(Charsets.UTF_8))));

        assertThat(attachments).isEmpty();
    }

    @Test
    public void getAttachmentsShouldReturnTheAttachmentsWhenSome() throws Exception {
        //Given
        Attachment expected = Attachment.builder()
                .bytes("payload".getBytes(Charsets.UTF_8))
                .type("content")
                .build();
        AttachmentId attachmentId = expected.getAttachmentId();
        attachmentMapper.storeAttachment(expected);

        Attachment expected2 = Attachment.builder()
                .bytes("payload2".getBytes(Charsets.UTF_8))
                .type("content")
                .build();
        AttachmentId attachmentId2 = expected2.getAttachmentId();
        attachmentMapper.storeAttachment(expected2);

        //When
        List<Attachment> attachments = attachmentMapper.getAttachments(ImmutableList.of(attachmentId, attachmentId2));
        //Then
        assertThat(attachments).contains(expected, expected2);
    }
}
