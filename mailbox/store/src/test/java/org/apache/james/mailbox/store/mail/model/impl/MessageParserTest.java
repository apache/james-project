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

import org.apache.james.mailbox.model.Cid;
import org.apache.james.mailbox.model.ContentType;
import org.apache.james.mailbox.model.ParsedAttachment;
import org.apache.james.mdn.MDN;
import org.apache.james.mdn.MDNReport;
import org.apache.james.mdn.action.mode.DispositionActionMode;
import org.apache.james.mdn.fields.Disposition;
import org.apache.james.mdn.fields.ReportingUserAgent;
import org.apache.james.mdn.sending.mode.DispositionSendingMode;
import org.apache.james.mdn.type.DispositionType;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.message.DefaultMessageWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MessageParserTest {
    MessageParser testee;

    @BeforeEach
    void setup() {
        testee = new MessageParser();
    }

    @Test
    void getAttachmentsShouldBeEmptyWhenNoAttachment() throws Exception {
        MessageParser.ParsingResult attachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/noAttachment.eml"));

        assertThat(attachments.getAttachments()).isEmpty();
    }

    @Test
    void getAttachmentsShouldIgnoreInlineWhenMixedMultipart() throws Exception {
        MessageParser.ParsingResult attachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/inlined-mixed.eml"));

        assertThat(attachments.getAttachments()).hasSize(2);
    }

    @Test
    void getAttachmentsShouldRetrieveAttachmentsWhenOneAttachment() throws Exception {
        MessageParser.ParsingResult attachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/oneAttachmentAndSomeTextInlined.eml"));

        assertThat(attachments.getAttachments()).hasSize(1);
    }

    @Test
    void getAttachmentsShouldRetrieveAttachmentNameWhenOne() throws Exception {
        MessageParser.ParsingResult attachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/oneAttachmentAndSomeTextInlined.eml"));

        assertThat(attachments.getAttachments()).hasSize(1);
        Optional<String> expectedName = Optional.of("exploits_of_a_mom.png");
        assertThat(attachments.getAttachments().get(0).getName()).isEqualTo(expectedName);
    }

    @Test
    void getAttachmentsShouldRetrieveAttachmentNameWhenOneContainingNonASCIICharacters() throws Exception {
        MessageParser.ParsingResult attachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/messageWithNonASCIIFilenameAttachment.eml"));
        assertThat(attachments.getAttachments()).hasSize(1);
        assertThat(attachments.getAttachments().get(0).getName()).contains("ديناصور.odt");
    }

    @Test
    void getAttachmentsShouldRetrieveEmptyNameWhenNone() throws Exception {
        MessageParser.ParsingResult attachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/oneAttachmentWithoutName.eml"));

        assertThat(attachments.getAttachments()).hasSize(1);
        assertThat(attachments.getAttachments().get(0).getName()).isEqualTo(Optional.empty());
    }

    @Test
    void getAttachmentsShouldNotFailWhenContentTypeIsNotHere() throws Exception {
        MessageParser.ParsingResult attachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/oneAttachmentWithoutContentType.eml"));

        assertThat(attachments.getAttachments()).hasSize(1);
        assertThat(attachments.getAttachments().get(0).getContentType())
            .isEqualTo(ContentType.of("application/octet-stream"));
    }

    @Test
    void getAttachmentsShouldNotFailWhenContentTypeIsEmpty() throws Exception {
        MessageParser.ParsingResult attachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/oneAttachmentWithEmptyContentType.eml"));

        assertThat(attachments.getAttachments()).hasSize(1);
        assertThat(attachments.getAttachments().get(0).getContentType())
            .isEqualTo(ContentType.of("application/octet-stream"));
    }

    @Test
    void getAttachmentsShouldRetrieveTheAttachmentContentTypeWhenOneAttachment() throws Exception {
        MessageParser.ParsingResult attachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/oneAttachmentAndSomeTextInlined.eml"));

        assertThat(attachments.getAttachments()).hasSize(1);
        assertThat(attachments.getAttachments().get(0).getContentType())
            .isEqualTo(ContentType.of("application/octet-stream;\tname=\"exploits_of_a_mom.png\""));
    }

    @Test
    void retrieveAttachmentsShouldNotFailOnMessagesWithManyHeaders() throws Exception {
        MessageParser.ParsingResult attachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/mailWithManyHeaders.eml"));

        assertThat(attachments.getAttachments()).hasSize(1);
    }

    @Test
    void retrieveAttachmentsShouldNotFailOnMessagesWithLongHeaders() throws Exception {
        MessageParser.ParsingResult attachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/mailWithLongHeaders.eml"));

        assertThat(attachments.getAttachments()).hasSize(1);
    }

    @Test
    void getAttachmentsShouldRetrieveTheAttachmentContentTypeWhenOneAttachmentWithSimpleContentType() throws Exception {
        MessageParser.ParsingResult attachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/oneAttachmentWithSimpleContentType.eml"));

        assertThat(attachments.getAttachments()).hasSize(1);
        assertThat(attachments.getAttachments().get(0).getContentType())
            .isEqualTo(ContentType.of("application/octet-stream"));
    }

    @Test
    void getAttachmentsShouldReturnTheExpectedAttachment() throws Exception {
        MessageParser.ParsingResult attachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/oneAttachmentAndSomeTextInlined.eml"));

        ParsedAttachment attachment = attachments.getAttachments().get(0);
        assertThat(attachment.getContent().openStream())
            .hasSameContentAs(ClassLoader.getSystemResourceAsStream("eml/gimp.png"));
    }

    @Test
    void getAttachmentsShouldRetrieveAttachmentsWhenTwo() throws Exception {
        MessageParser.ParsingResult attachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/twoAttachments.eml"));

        assertThat(attachments.getAttachments()).hasSize(2);
    }

    @Test
    void retrieveAttachmentShouldUseFilenameAsNameWhenNoName() throws Exception {
        MessageParser.ParsingResult attachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/filenameOnly.eml"));

        assertThat(attachments.getAttachments()).hasSize(1)
            .extracting(ParsedAttachment::getName)
            .allMatch(Optional::isPresent)
            .extracting(Optional::get)
            .containsExactly("inventory.csv");
    }

    @Test
    void retrieveAttachmentShouldUseNameWhenBothNameAndFilename() throws Exception {
        MessageParser.ParsingResult attachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/filenameAndName.eml"));

        assertThat(attachments.getAttachments()).hasSize(1)
            .extracting(ParsedAttachment::getName)
            .allMatch(Optional::isPresent)
            .extracting(Optional::get)
            .containsExactly("good.csv");
    }

    @Test
    void getAttachmentsShouldRetrieveEmbeddedAttachmentsWhenSome() throws Exception {
        MessageParser.ParsingResult attachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/embeddedAttachmentWithInline.eml"));

        assertThat(attachments.getAttachments()).hasSize(1);
    }

    @Test
    void getAttachmentsShouldRetrieveInlineAttachmentsWhenSome() throws Exception {
        MessageParser.ParsingResult attachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/embeddedAttachmentWithAttachment.eml"));

        assertThat(attachments.getAttachments()).hasSize(1);
    }

    @Test
    void getAttachmentsShouldRetrieveTheAttachmentCIDWhenOne() throws Exception {
        MessageParser.ParsingResult attachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/oneInlinedAttachment.eml"));

        assertThat(attachments.getAttachments()).hasSize(1);
        assertThat(attachments.getAttachments().get(0).getCid()).isEqualTo(Optional.of(Cid.from("part1.37A15C92.A7C3488D@linagora.com")));
    }

    @Test
    void getAttachmentsShouldSetInlineWhenOneInlinedAttachment() throws Exception {
        MessageParser.ParsingResult attachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/oneInlinedAttachment.eml"));

        assertThat(attachments.getAttachments()).hasSize(1);
        assertThat(attachments.getAttachments().get(0).isInline()).isTrue();
    }

    @Test
    void getAttachementsShouldRetrieveHtmlAttachementsWhenSome() throws Exception {
        MessageParser.ParsingResult attachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/oneHtmlAttachmentAndSomeTextInlined.eml"));

        assertThat(attachments.getAttachments()).hasSize(1);
    }

    @Test
    void getAttachementsShouldRetrieveAttachmentsWhenSomeAreInTheMultipartAlternative() throws Exception {
        MessageParser.ParsingResult attachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/invitationEmailFromOP.eml"));
        
        assertThat(attachments.getAttachments()).hasSize(6);
    }

    @Test
    void getAttachmentsShouldNotConsiderUnknownContentDispositionAsAttachments() throws Exception {
        MessageParser.ParsingResult attachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/unknownDisposition.eml"));

        assertThat(attachments.getAttachments()).hasSize(0);
    }

    @Test
    void getAttachmentsShouldConsiderNoContentDispositionAsAttachmentsWhenCID() throws Exception {
        MessageParser.ParsingResult attachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/noContentDispositionWithCID.eml"));

        assertThat(attachments.getAttachments()).hasSize(1);
    }

    @Test
    void getAttachmentsShouldRetrieveAttachmentsWhenNoCidForInlined() throws Exception {
        MessageParser.ParsingResult attachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/oneInlinedAttachmentWithNoCid.eml"));

        assertThat(attachments.getAttachments()).hasSize(1);
    }

    @Test
    void getAttachmentsShouldRetrieveAttachmentsWhenEmptyCidForInlined() throws Exception {
        MessageParser.ParsingResult attachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/oneInlinedAttachmentWithEmptyCid.eml"));

        assertThat(attachments.getAttachments()).hasSize(1);
    }

    @Test
    void getAttachmentsShouldRetrieveAttachmentsWhenBlankCidForInlined() throws Exception {
        MessageParser.ParsingResult attachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/oneInlinedAttachmentWithBlankCid.eml"));

        assertThat(attachments.getAttachments()).hasSize(1);
    }

    @Test
    void getAttachmentsShouldRetrieveAttachmentsWhenOneFailOnWrongContentDisposition() throws Exception {
        MessageParser.ParsingResult attachments = testee.retrieveAttachments(ClassLoader.getSystemResourceAsStream("eml/multiAttachmentsWithOneWrongContentDisposition.eml"));

        assertThat(attachments.getAttachments()).hasSize(2);
    }

    @Test
    void getAttachmentsShouldRetrieveOneAttachmentWhenMessageWithAttachmentContentDisposition() throws Exception {
        MessageParser.ParsingResult attachments = testee.retrieveAttachments(
            ClassLoader.getSystemResourceAsStream("eml/emailWithOnlyAttachment.eml"));

        assertThat(attachments.getAttachments()).hasSize(1);
    }

    @Test
    void getAttachmentsShouldRetrieveCharset() throws Exception {
        MessageParser.ParsingResult attachments = testee.retrieveAttachments(
            ClassLoader.getSystemResourceAsStream("eml/charset.eml"));

        assertThat(attachments.getAttachments()).hasSize(1)
            .first()
            .satisfies(attachment -> assertThat(attachment.getContentType())
                .isEqualTo(ContentType.of("text/calendar; charset=\"iso-8859-1\"; method=COUNTER")));
    }

    @Test
    void getAttachmentsShouldRetrieveAllPartsCharset() throws Exception {
        MessageParser.ParsingResult attachments = testee.retrieveAttachments(
            ClassLoader.getSystemResourceAsStream("eml/charset2.eml"));

        assertThat(attachments.getAttachments()).hasSize(2)
            .extracting(ParsedAttachment::getContentType)
            .containsOnly(ContentType.of("text/calendar; charset=\"iso-8859-1\"; method=COUNTER"),
                ContentType.of("text/calendar; charset=\"iso-4444-5\"; method=COUNTER"));
    }

    @Test
    void getAttachmentsShouldNotConsiderTextCalendarAsAttachmentsByDefault() throws Exception {
        MessageParser.ParsingResult attachments = testee.retrieveAttachments(
            ClassLoader.getSystemResourceAsStream("eml/calendar.eml"));

        assertThat(attachments.getAttachments())
            .isEmpty();
    }

    @Test
    void getAttachmentsShouldConsiderTextCalendarAsAttachments() throws Exception {
        MessageParser.ParsingResult attachments = testee.retrieveAttachments(
            ClassLoader.getSystemResourceAsStream("eml/calendar2.eml"));

        assertThat(attachments.getAttachments())
            .hasSize(1)
            .extracting(ParsedAttachment::getContentType)
            .containsExactly(ContentType.of("text/calendar; charset=\"utf-8\"; method=COUNTER"));
    }

    @Test
    void gpgSignatureShouldBeConsideredAsAnAttachment() throws Exception {
        MessageParser.ParsingResult attachments = testee.retrieveAttachments(
            ClassLoader.getSystemResourceAsStream("eml/signedMessage.eml"));

        assertThat(attachments.getAttachments()).hasSize(2)
            .extracting(ParsedAttachment::getName)
            .allMatch(Optional::isPresent)
            .extracting(Optional::get)
            .containsOnly("message suivi", "signature.asc");
    }

    @Test
    void mdnReportShouldBeConsideredAsAttachmentWhenDispositionContentType() throws Exception {
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

        List<ParsedAttachment> result = testee.retrieveAttachments(new ByteArrayInputStream(DefaultMessageWriter.asBytes(message)))
            .getAttachments();
        assertThat(result).hasSize(1)
            .allMatch(attachment -> attachment.getContentType().equals(ContentType.of("message/disposition-notification; charset=UTF-8")));
    }
}
