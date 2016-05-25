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

package org.apache.james.mailbox.store.mail.model.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.apache.james.mailbox.store.mail.model.Attachment;
import org.junit.Before;
import org.junit.Test;

public class MessageParserTest {

    private MessageParser testee;

    @Before
    public void setup() {
        testee = new MessageParser();
    }

    @Test
    public void getAttachmentsShouldBeEmptyWhenNone() throws Exception {
        List<Attachment> attachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/noAttachment.eml"));

        assertThat(attachments).isEmpty();
    }

    @Test
    public void getAttachmentsShouldRetrieveAttachmentsWhenOne() throws Exception {
        List<Attachment> attachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/oneAttachmentAndSomeInlined.eml"));

        assertThat(attachments).hasSize(1);
    }

    @Test
    public void getAttachmentsShouldRetrieveTheAttachmentContentTypeWhenOne() throws Exception {
        List<Attachment> attachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/oneAttachmentAndSomeInlined.eml"));

        assertThat(attachments).hasSize(1);
        assertThat(attachments.get(0).getType()).isEqualTo("application/octet-stream");
    }

    @Test
    public void getAttachmentsShouldRetrieveTheAttachmentSizeWhenOne() throws Exception {
        List<Attachment> attachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/oneAttachmentAndSomeInlined.eml"));

        assertThat(attachments).hasSize(1);
        assertThat(attachments.get(0).getSize()).isEqualTo(3071);
    }

    @Test
    public void getAttachmentsShouldReturnTheExpectedAttachment() throws Exception {
        List<Attachment> attachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/oneAttachmentAndSomeInlined.eml"));

        Attachment attachment = attachments.get(0);
        assertThat(attachment.getStream()).hasContentEqualTo(ClassLoader.getSystemResourceAsStream("eml/gimp.png"));
    }

    @Test
    public void getAttachmentsShouldRetrieveAttachmentsWhenTwo() throws Exception {
        List<Attachment> attachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/twoAttachments.eml"));

        assertThat(attachments).hasSize(2);
    }

    @Test
    public void getAttachmentsShouldNotRetrieveEmbeddedAttachmentsWhenSome() throws Exception {
        List<Attachment> attachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/embeddedAttachmentWithInline.eml"));

        assertThat(attachments).hasSize(1);
    }

    @Test
    public void getAttachmentsShouldNotRetrieveInlineAttachmentsWhenSome() throws Exception {
        List<Attachment> attachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/embeddedAttachmentWithAttachment.eml"));

        assertThat(attachments).hasSize(1);
    }
}
