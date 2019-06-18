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

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Optional;

import org.apache.james.mailbox.model.Attachment;
import org.apache.james.mailbox.model.Cid;
import org.apache.james.mailbox.model.MessageAttachment;
import org.apache.james.mdn.MDN;
import org.apache.james.mdn.MDNReport;
import org.apache.james.mdn.action.mode.DispositionActionMode;
import org.apache.james.mdn.fields.Disposition;
import org.apache.james.mdn.fields.ReportingUserAgent;
import org.apache.james.mdn.sending.mode.DispositionSendingMode;
import org.apache.james.mdn.type.DispositionType;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.message.DefaultMessageWriter;
import org.junit.Before;
import org.junit.Test;

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
    public void getAttachmentsShouldRetrieveAttachmentNameWhenOneContainingNonASCIICharacters() throws Exception {
        List<MessageAttachment> attachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/messageWithNonASCIIFilenameAttachment.eml"));
        assertThat(attachments).hasSize(1);
        assertThat(attachments.get(0).getName()).contains("ديناصور.odt");
    }

    @Test
    public void getAttachmentsShouldRetrieveEmptyNameWhenNone() throws Exception {
        List<MessageAttachment> attachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/oneAttachmentWithoutName.eml"));

        assertThat(attachments).hasSize(1);
        assertThat(attachments.get(0).getName()).isEqualTo(Optional.empty());
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
    public void retrieveAttachmentsShouldNotFailOnMessagesWithManyHeaders() throws Exception {
        List<MessageAttachment> messageAttachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/mailWithManyHeaders.eml"));

        assertThat(messageAttachments).hasSize(1);
    }

    @Test
    public void retrieveAttachmentsShouldNotFailOnMessagesWithLongHeaders() throws Exception {
        List<MessageAttachment> messageAttachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/mailWithLongHeaders.eml"));

        assertThat(messageAttachments).hasSize(1);
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
        assertThat(attachment.getStream()).hasSameContentAs(ClassLoader.getSystemResourceAsStream("eml/gimp.png"));
    }

    @Test
    public void getAttachmentsShouldRetrieveAttachmentsWhenTwo() throws Exception {
        List<MessageAttachment> attachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/twoAttachments.eml"));

        assertThat(attachments).hasSize(2);
    }

    @Test
    public void retrieveAttachmentShouldUseFilenameAsNameWhenNoName() throws Exception {
        List<MessageAttachment> attachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/filenameOnly.eml"));

        assertThat(attachments).hasSize(1)
            .extracting(MessageAttachment::getName)
            .allMatch(Optional::isPresent)
            .extracting(Optional::get)
            .containsExactly("inventory.csv");
    }

    @Test
    public void retrieveAttachmentShouldUseNameWhenBothNameAndFilename() throws Exception {
        List<MessageAttachment> attachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/filenameAndName.eml"));

        assertThat(attachments).hasSize(1)
            .extracting(MessageAttachment::getName)
            .allMatch(Optional::isPresent)
            .extracting(Optional::get)
            .containsExactly("good.csv");
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
        
        assertThat(attachments).hasSize(7);
    }

    @Test
    public void getAttachmentsShouldNotConsiderUnknownContentDispositionAsAttachments() throws Exception {
        List<MessageAttachment> attachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/unknownDisposition.eml"));

        assertThat(attachments).hasSize(0);
    }

    @Test
    public void getAttachmentsShouldConsiderNoContentDispositionAsAttachmentsWhenCID() throws Exception {
        List<MessageAttachment> attachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/noContentDispositionWithCID.eml"));

        assertThat(attachments).hasSize(1);
    }

    @Test
    public void getAttachmentsShouldRetrieveAttachmentsWhenNoCidForInlined() throws Exception {
        List<MessageAttachment> attachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/oneInlinedAttachmentWithNoCid.eml"));

        assertThat(attachments).hasSize(1);
    }

    @Test
    public void getAttachmentsShouldRetrieveAttachmentsWhenEmptyCidForInlined() throws Exception {
        List<MessageAttachment> attachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/oneInlinedAttachmentWithEmptyCid.eml"));

        assertThat(attachments).hasSize(1);
    }

    @Test
    public void getAttachmentsShouldRetrieveAttachmentsWhenBlankCidForInlined() throws Exception {
        List<MessageAttachment> attachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/oneInlinedAttachmentWithBlankCid.eml"));

        assertThat(attachments).hasSize(1);
    }

    @Test
    public void getAttachmentsShouldRetrieveAttachmentsWhenOneFailOnWrongContentDisposition() throws Exception {
        List<MessageAttachment> attachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/multiAttachmentsWithOneWrongContentDisposition.eml"));

        assertThat(attachments).hasSize(2);
    }

    @Test
    public void getAttachmentsShouldRetrieveOneAttachmentWhenMessageWithAttachmentContentDisposition() throws Exception {
        List<MessageAttachment> attachments = testee.retrieveAttachments(
            ClassLoader.getSystemResourceAsStream("eml/emailWithOnlyAttachment.eml"));

        assertThat(attachments).hasSize(1);
    }

    @Test
    public void getAttachmentsShouldConsiderICSAsAttachments() throws Exception {
        List<MessageAttachment> attachments = testee.retrieveAttachments(
            ClassLoader.getSystemResourceAsStream("eml/calendar.eml"));

        assertThat(attachments)
            .hasSize(1)
            .allMatch(messageAttachment -> messageAttachment.getAttachment().getType().equals("text/calendar"));
    }

    @Test
    public void gpgSignatureShouldBeConsideredAsAnAttachment() throws Exception {
        List<MessageAttachment> attachments = testee.retrieveAttachments(
            ClassLoader.getSystemResourceAsStream("eml/signedMessage.eml"));

        assertThat(attachments).hasSize(2)
            .extracting(MessageAttachment::getName)
            .allMatch(Optional::isPresent)
            .extracting(Optional::get)
            .containsOnly("message suivi", "signature.asc");
    }

    @Test
    public void mdnReportShouldBeConsideredAsAttachmentWhenDispositionContentType() throws Exception {
        Message message = MDN.builder()
            .humanReadableText("A little test")
            .report(MDNReport.builder()
                .dispositionField(Disposition.builder()
                    .actionMode(DispositionActionMode.Automatic)
                    .sendingMode(DispositionSendingMode.Automatic)
                    .type(DispositionType.Processed)
                    .build())
                .originalMessageIdField("zeugzev@domain.tld")
                .reportingUserAgentField(ReportingUserAgent.builder().userAgentName("Thunderbird").build())
                .finalRecipientField("user@domain.tld")
                .originalRecipientField("user@domain.tld")
                .build())
            .build()
            .asMime4JMessageBuilder()
            .build();

        List<MessageAttachment> result = testee.retrieveAttachments(new ByteArrayInputStream(DefaultMessageWriter.asBytes(message)));
        assertThat(result).hasSize(1)
            .allMatch(attachment -> attachment.getAttachment().getType().equals(MDN.DISPOSITION_CONTENT_TYPE));
    }
}
