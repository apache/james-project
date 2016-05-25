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

import org.apache.james.mailbox.exception.AttachmentNotFoundException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.store.mail.AttachmentMapper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public abstract class AbstractAttachmentMapperTest {

    private MapperProvider mapperProvider;
    private AttachmentMapper attachmentMapper;

    public AbstractAttachmentMapperTest(MapperProvider mapperProvider) {
        this.mapperProvider = mapperProvider;
    }

    @Before
    public void setUp() throws MailboxException {
        mapperProvider.ensureMapperPrepared();
        attachmentMapper = mapperProvider.createAttachmentMapper();
    }

    @After
    public void tearDown() throws MailboxException {
        mapperProvider.clearMapper();
    }

    @Test (expected = IllegalArgumentException.class)
    public void getAttachmentShouldThrowWhenNullAttachmentId() throws Exception {
        attachmentMapper.getAttachment(null);
    }

    @Test (expected = AttachmentNotFoundException.class)
    public void getAttachmentShouldThrowWhenNonReferencedAttachmentId() throws Exception {
        attachmentMapper.getAttachment(AttachmentId.forPayload("unknown".getBytes()));
    }

    @Test
    public void getAttachmentShouldReturnTheAttachmentWhenReferenced() throws Exception {
        //Given
        Attachment expected = Attachment.from("payload".getBytes(), "content");
        AttachmentId attachmentId = expected.getAttachmentId();
        attachmentMapper.storeAttachment(expected);
        //When
        Attachment attachment = attachmentMapper.getAttachment(attachmentId);
        //Then
        assertThat(attachment).isEqualTo(expected);
    }
}
