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
import org.apache.james.mailbox.store.mail.model.MessageAttachment;
import org.junit.Before;
import org.junit.Test;

import com.google.common.base.Optional;

public class MessageParserTest {

    private MessageParser testee;

    @Before
    public void setup() {
        testee = new MessageParser();
    }

    @Test
    public void getAttachmentsShouldBeEmptyWhenNoAttachment() throws Exception {
        List<MessageAttachment> attachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/noAttachment.eml"));

        assertThat(attachments).isEmpty();
    }

    @Test
    public void getAttachmentsShouldRetrieveAttachmentsWhenOneAttachment() throws Exception {
        List<MessageAttachment> attachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/oneAttachmentAndSomeTextInlined.eml"));

        assertThat(attachments).hasSize(1);
    }

    @Test
    public void getAttachmentsShouldRetrieveAttachmentNameWhenOne() throws Exception {
        List<MessageAttachment> attachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/oneAttachmentAndSomeTextInlined.eml"));

        assertThat(attachments).hasSize(1);
        Optional<String> expectedName = Optional.of("exploits_of_a_mom.png");
        assertThat(attachments.get(0).getName()).isEqualTo(expectedName);
    }

    @Test
    public void getAttachmentsShouldRetrieveEmptyNameWhenNone() throws Exception {
        List<MessageAttachment> attachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/oneAttachmentWithoutName.eml"));

        assertThat(attachments).hasSize(1);
        assertThat(attachments.get(0).getName()).isEqualTo(Optional.absent());
    }

    @Test
    public void getAttachmentsShouldNotFailWhenContentTypeIsNotHere() throws Exception {
        List<MessageAttachment> attachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/oneAttachmentWithoutContentType.eml"));

        assertThat(attachments).hasSize(1);
        assertThat(attachments.get(0).getAttachment().getType()).isEqualTo("application/octet-stream");
    }

    @Test
    public void getAttachmentsShouldNotFailWhenContentTypeIsEmpty() throws Exception {
        List<MessageAttachment> attachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/oneAttachmentWithEmptyContentType.eml"));

        assertThat(attachments).hasSize(1);
        assertThat(attachments.get(0).getAttachment().getType()).isEqualTo("application/octet-stream");
    }

    @Test
    public void getAttachmentsShouldRetrieveTheAttachmentContentTypeWhenOneAttachment() throws Exception {
        List<MessageAttachment> attachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/oneAttachmentAndSomeTextInlined.eml"));

        assertThat(attachments).hasSize(1);
        assertThat(attachments.get(0).getAttachment().getType()).isEqualTo("application/octet-stream");
    }

    @Test
    public void getAttachmentsShouldRetrieveTheAttachmentContentTypeWhenOneAttachmentWithSimpleContentType() throws Exception {
        List<MessageAttachment> attachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/oneAttachmentWithSimpleContentType.eml"));

        assertThat(attachments).hasSize(1);
        assertThat(attachments.get(0).getAttachment().getType()).isEqualTo("application/octet-stream");
    }

    @Test
    public void getAttachmentsShouldRetrieveTheAttachmentSizeWhenOneAttachment() throws Exception {
        List<MessageAttachment> attachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/oneAttachmentAndSomeTextInlined.eml"));

        assertThat(attachments).hasSize(1);
        assertThat(attachments.get(0).getAttachment().getSize()).isEqualTo(3071);
    }

    @Test
    public void getAttachmentsShouldReturnTheExpectedAttachment() throws Exception {
        List<MessageAttachment> attachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/oneAttachmentAndSomeTextInlined.eml"));

        Attachment attachment = attachments.get(0).getAttachment();
        assertThat(attachment.getStream()).hasContentEqualTo(ClassLoader.getSystemResourceAsStream("eml/gimp.png"));
    }

    @Test
    public void getAttachmentsShouldRetrieveAttachmentsWhenTwo() throws Exception {
        List<MessageAttachment> attachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/twoAttachments.eml"));

        assertThat(attachments).hasSize(2);
    }

    @Test
    public void getAttachmentsShouldRetrieveEmbeddedAttachmentsWhenSome() throws Exception {
        List<MessageAttachment> attachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/embeddedAttachmentWithInline.eml"));

        assertThat(attachments).hasSize(1);
    }

    @Test
    public void getAttachmentsShouldRetrieveInlineAttachmentsWhenSome() throws Exception {
        List<MessageAttachment> attachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/embeddedAttachmentWithAttachment.eml"));

        assertThat(attachments).hasSize(1);
    }

    @Test
    public void getAttachmentsShouldRetrieveTheAttachmentCIDWhenOne() throws Exception {
        List<MessageAttachment> attachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/oneInlinedAttachment.eml"));

        assertThat(attachments).hasSize(1);
        assertThat(attachments.get(0).getCid()).isEqualTo(Optional.of(Cid.from("part1.37A15C92.A7C3488D@linagora.com")));
    }

    @Test
    public void getAttachmentsShouldSetInlineWhenOneInlinedAttachment() throws Exception {
        List<MessageAttachment> attachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/oneInlinedAttachment.eml"));

        assertThat(attachments).hasSize(1);
        assertThat(attachments.get(0).isInline()).isTrue();
    }

    @Test
    public void getAttachementsShouldRetrieveHtmlAttachementsWhenSome() throws Exception {
        List<MessageAttachment> attachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/oneHtmlAttachmentAndSomeTextInlined.eml"));

        assertThat(attachments).hasSize(1);
    }

    @Test
    public void getAttachementsShouldRetrieveAttachmentsWhenSomeAreInTheMultipartAlternative() throws Exception {
        List<MessageAttachment> attachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/invitationEmailFromOP.eml"));
        
        assertThat(attachments).hasSize(6);
    }
}
