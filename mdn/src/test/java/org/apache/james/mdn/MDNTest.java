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

package org.apache.james.mdn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Pattern;

import jakarta.mail.internet.MimeMessage;

import org.apache.james.mdn.action.mode.DispositionActionMode;
import org.apache.james.mdn.fields.AddressType;
import org.apache.james.mdn.fields.Disposition;
import org.apache.james.mdn.fields.ExtensionField;
import org.apache.james.mdn.fields.FinalRecipient;
import org.apache.james.mdn.fields.Gateway;
import org.apache.james.mdn.fields.OriginalRecipient;
import org.apache.james.mdn.fields.ReportingUserAgent;
import org.apache.james.mdn.fields.Text;
import org.apache.james.mdn.modifier.DispositionModifier;
import org.apache.james.mdn.sending.mode.DispositionSendingMode;
import org.apache.james.mdn.type.DispositionType;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.message.BodyPart;
import org.apache.james.mime4j.message.BodyPartBuilder;
import org.apache.james.mime4j.message.DefaultMessageWriter;
import org.apache.james.mime4j.message.MultipartBuilder;
import org.apache.james.mime4j.message.SingleBodyBuilder;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class MDNTest {

    static final MDNReport MINIMAL_REPORT = MDNReport.builder()
        .finalRecipientField("final@domain.com")
        .dispositionField(Disposition.builder()
            .actionMode(DispositionActionMode.Automatic)
            .sendingMode(DispositionSendingMode.Automatic)
            .type(DispositionType.Deleted)
            .build())
        .build();

    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(MDN.class)
            .verify();
    }

    @Test
    void asMimeMessageShouldGenerateExpectedContentType() throws Exception {
        MimeMessage mimeMessage = MDN.builder()
            .humanReadableText("Explanation")
            .report(MINIMAL_REPORT)
            .build()
            .asMimeMessage();

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        mimeMessage.writeTo(byteArrayOutputStream);

        assertThat(new String(byteArrayOutputStream.toByteArray(), StandardCharsets.UTF_8))
            .containsPattern(
                Pattern.compile("Content-Type: multipart/report;.*(\r\n.+)*report-type=disposition-notification.*\r\n\r\n"));
    }

    @Test
    void asMimeMessageShouldComportExplanationPartAndReportPart() throws Exception {
        MimeMessage mimeMessage = MDN.builder()
            .humanReadableText("Explanation")
            .report(MINIMAL_REPORT)
            .build()
            .asMimeMessage();

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        mimeMessage.writeTo(byteArrayOutputStream);
        assertThat(new String(byteArrayOutputStream.toByteArray(), StandardCharsets.UTF_8))
            .contains(
                "Content-Type: text/plain; charset=UTF-8\r\n" +
                "Content-Transfer-Encoding: 7bit\r\n" +
                "Content-Disposition: inline\r\n" +
                "\r\n" +
                "Explanation")
            .contains(
                "Content-Type: message/disposition-notification\r\n" +
                    "Content-Transfer-Encoding: 7bit\r\n" +
                    "\r\n" +
                    "Final-Recipient: rfc822; final@domain.com\r\n" +
                    "Disposition: automatic-action/MDN-sent-automatically;deleted");
    }

    @Test
    void asMimeMessageShouldDisplayEmptyExplanation() throws Exception {
        MimeMessage mimeMessage = MDN.builder()
            .humanReadableText("Explanation")
            .report(MINIMAL_REPORT)
            .build()
            .asMimeMessage();

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        mimeMessage.writeTo(byteArrayOutputStream);
        assertThat(new String(byteArrayOutputStream.toByteArray(), StandardCharsets.UTF_8))
            .contains(
                "Content-Type: text/plain; charset=UTF-8\r\n" +
                    "Content-Transfer-Encoding: 7bit\r\n" +
                    "Content-Disposition: inline\r\n" +
                    "\r\n" +
                    "Explanation")
            .contains(
                "Content-Type: message/disposition-notification\r\n" +
                    "Content-Transfer-Encoding: 7bit\r\n" +
                    "\r\n" +
                    "Final-Recipient: rfc822; final@domain.com\r\n" +
                    "Disposition: automatic-action/MDN-sent-automatically;deleted");
    }

    @Test
    void reportShouldThrowOnNullValue() {
        assertThatThrownBy(() -> MDN.builder()
                .report(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void humanReadableTextShouldThrowOnNullValue() {
        assertThatThrownBy(() -> MDN.builder()
                .humanReadableText(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void buildShouldThrowOnEmptyHumanReadableText() {
        assertThatThrownBy(() -> MDN.builder()
                .humanReadableText("")
                .report(MINIMAL_REPORT)
                .build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void buildShouldThrowOnFoldingWhiteHumanReadableText() {
        assertThatThrownBy(() -> MDN.builder()
                .humanReadableText("  ")
                .report(MINIMAL_REPORT)
                .build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void humanReadableTextShouldNotBeTrimmed() throws Exception {
        MimeMessage mimeMessage = MDN.builder()
            .humanReadableText("Explanation:\n" +
                " - We should always write detailed unit tests\n" +
                " - We should think of all edge cases\n")
            .report(MINIMAL_REPORT)
            .build()
            .asMimeMessage();

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        mimeMessage.writeTo(byteArrayOutputStream);
        assertThat(new String(byteArrayOutputStream.toByteArray(), StandardCharsets.UTF_8))
            .contains(
                "Content-Type: text/plain; charset=UTF-8\r\n" +
                    "Content-Transfer-Encoding: 7bit\r\n" +
                    "Content-Disposition: inline\r\n" +
                    "\r\n" +
                    "Explanation:\n" +
                    " - We should always write detailed unit tests\n" +
                    " - We should think of all edge cases\n");
    }

    @Test
    void mdnShouldBeConvertibleToMime4JMessage() throws Exception {
        Message message = MDN.builder()
            .humanReadableText("Explanation:\n" +
                " - We should always write detailed unit tests\n" +
                " - We should think of all edge cases\n")
            .report(MINIMAL_REPORT)
            .build()
            .asMime4JMessageBuilder()
            .build();

        assertThat(asString(message))
            .contains("MIME-Version: 1.0\r\n" +
                "Content-Type: multipart/report;")
            .contains("Content-Type: text/plain; charset=UTF-8\r\n" +
                "\r\n" +
                "Explanation:\n" +
                " - We should always write detailed unit tests\n" +
                " - We should think of all edge cases")
        .contains("Content-Type: message/disposition-notification; charset=UTF-8\r\n" +
            "\r\n" +
            "Final-Recipient: rfc822; final@domain.com\r\n" +
            "Disposition: automatic-action/MDN-sent-automatically;deleted");
    }


    @Test
    void mime4JMessageExportShouldGenerateExpectedContentType() throws Exception {
        Message message = MDN.builder()
            .humanReadableText("RFCs are not funny")
            .report(MINIMAL_REPORT)
            .build()
            .asMime4JMessageBuilder()
            .build();

        assertThat(asString(message))
            .containsPattern(Pattern.compile("Content-Type: multipart/report;.*(\r\n.+)*report-type=disposition-notification.*(\r\n.+)*\r\n\r\n"));
    }

    @Test
    public void parseShouldThrowWhenNonMultipartMessage() throws Exception {
        Message message = Message.Builder.of()
            .setBody("content", StandardCharsets.UTF_8)
            .build();
        assertThatThrownBy(() -> MDN.parse(message))
            .isInstanceOf(MDN.MDNParseContentTypeException.class)
            .hasMessage("MDN Message must be multipart");
    }

    @Test
    public void parseShouldThrowWhenMultipartWithSinglePart() throws Exception {
        Message message = Message.Builder.of()
            .setBody(MultipartBuilder.create()
                .setSubType("report")
                .addTextPart("content", StandardCharsets.UTF_8)
                .build())
            .build();
        assertThatThrownBy(() -> MDN.parse(message))
            .isInstanceOf(MDN.MDNParseBodyPartInvalidException.class)
            .hasMessage("MDN Message must contain at least two parts");
    }

    @Test
    public void parseShouldThrowWhenSecondPartWithBadContentType() throws Exception {
        Message message = Message.Builder.of()
            .setBody(MultipartBuilder.create()
                .setSubType("report")
                .addTextPart("first", StandardCharsets.UTF_8)
                .addTextPart("second", StandardCharsets.UTF_8)
                .build())
            .build();
        assertThatThrownBy(() -> MDN.parse(message))
            .isInstanceOf(MDN.MDNParseException.class)
            .hasMessage("MDN can not extract. Body part is invalid");
    }

    @Test
    public void parseShouldFailWhenMDNMissingMustBeProperties() throws Exception {
        Message message = Message.Builder.of()
            .setBody(MultipartBuilder.create("report")
                .addTextPart("first", StandardCharsets.UTF_8)
                .addBodyPart(BodyPartBuilder
                    .create()
                    .setBody(SingleBodyBuilder.create()
                        .setText("Final-Recipient: rfc822; final_recipient")
                        .buildText())
                    .setContentType("message/disposition-notification")
                    .build())
                .build())
            .build();
        assertThatThrownBy(() -> MDN.parse(message))
            .isInstanceOf(MDN.MDNParseException.class)
            .hasMessage("MDN can not extract. Body part is invalid");
    }

    @Test
    public void parseShouldSuccessWithValidMDN() throws Exception {
        BodyPart mdnBodyPart = BodyPartBuilder
            .create()
            .setBody(SingleBodyBuilder.create()
                .setText("Reporting-UA: UA_name; UA_product\r\n" +
                        "MDN-Gateway: rfc822; apache.org\r\n" +
                        "Original-Recipient: rfc822; originalRecipient\r\n" +
                        "Final-Recipient: rfc822; final_recipient\r\n" +
                        "Original-Message-ID: <original@message.id>\r\n" +
                        "Disposition: automatic-action/MDN-sent-automatically;processed/error,failed\r\n" +
                        "Error: Message1\r\n" +
                        "Error: Message2\r\n" +
                        "X-OPENPAAS-IP: 177.177.177.77\r\n" +
                        "X-OPENPAAS-PORT: 8000\r\n" +
                        "".replace(System.lineSeparator(), "\r\n").strip())
                .buildText())
            .setContentType("message/disposition-notification")
            .build();

        Message message = Message.Builder.of()
            .setBody(MultipartBuilder.create("report")
                .addTextPart("first", StandardCharsets.UTF_8)
                .addBodyPart(mdnBodyPart)
                .build())
            .build();
        MDN mdnActual = MDN.parse(message);
        MDNReport mdnReportExpect = MDNReport.builder()
            .reportingUserAgentField(ReportingUserAgent.builder()
                .userAgentName("UA_name")
                .userAgentProduct("UA_product")
                .build())
            .gatewayField(Gateway.builder()
                .nameType(AddressType.RFC_822)
                .name(Text.fromRawText("apache.org"))
                .build())
            .originalRecipientField(OriginalRecipient.builder()
                .originalRecipient(Text.fromRawText("originalRecipient"))
                .addressType(AddressType.RFC_822)
                .build())
            .finalRecipientField(FinalRecipient.builder()
                .finalRecipient(Text.fromRawText("final_recipient"))
                .addressType(AddressType.RFC_822)
                .build())
            .originalMessageIdField("<original@message.id>")
            .dispositionField(Disposition.builder()
                .actionMode(DispositionActionMode.Automatic)
                .sendingMode(DispositionSendingMode.Automatic)
                .type(DispositionType.Processed)
                .addModifier(DispositionModifier.Error)
                .addModifier(DispositionModifier.Failed)
                .build())
            .addErrorField("Message1")
            .addErrorField("Message2")
            .withExtensionField(ExtensionField.builder()
                .fieldName("X-OPENPAAS-IP")
                .rawValue(" 177.177.177.77")
                .build())
            .withExtensionField(ExtensionField.builder()
                .fieldName("X-OPENPAAS-PORT")
                .rawValue(" 8000")
                .build())
            .build();

        MDN mdnExpect = MDN.builder()
            .report(mdnReportExpect)
            .humanReadableText("first")
            .build();
        assertThat(mdnActual).isEqualTo(mdnExpect);
    }

    @Test
    public void parseShouldSuccessWithMDNHasMinimalProperties() throws Exception {
        Message message = Message.Builder.of()
            .setBody(MultipartBuilder.create("report")
                .addTextPart("first", StandardCharsets.UTF_8)
                .addBodyPart(BodyPartBuilder
                    .create()
                    .setBody(SingleBodyBuilder.create()
                        .setText("Final-Recipient: rfc822; final_recipient\r\n" +
                            "Disposition: automatic-action/MDN-sent-automatically;processed/error,failed\r\n" +
                            "".replace(System.lineSeparator(), "\r\n").strip())
                        .buildText())
                    .setContentType("message/disposition-notification")
                    .build())
                .build())
            .build();
        MDN mdnActual = MDN.parse(message);
        MDNReport mdnReportExpect = MDNReport.builder()
            .finalRecipientField(FinalRecipient.builder()
                .finalRecipient(Text.fromRawText("final_recipient"))
                .addressType(AddressType.RFC_822)
                .build())
            .dispositionField(Disposition.builder()
                .actionMode(DispositionActionMode.Automatic)
                .sendingMode(DispositionSendingMode.Automatic)
                .type(DispositionType.Processed)
                .addModifier(DispositionModifier.Error)
                .addModifier(DispositionModifier.Failed)
                .build())
            .build();

        MDN mdnExpect = MDN.builder()
            .report(mdnReportExpect)
            .humanReadableText("first")
            .build();
        assertThat(mdnActual).isEqualTo(mdnExpect);
    }

    @Test
    public void includeOriginalMessageShouldReturnTrueWhenMDNHasContentOfOriginalMessage() throws Exception {
        Message message = Message.Builder.of()
            .setBody(MultipartBuilder.create("report")
                .addTextPart("first", StandardCharsets.UTF_8)
                .addBodyPart(BodyPartBuilder
                    .create()
                    .setBody(SingleBodyBuilder.create()
                        .setText(
                                "Final-Recipient: rfc822; final_recipient\r\n" +
                                "Disposition: automatic-action/MDN-sent-automatically;processed/error,failed\r\n" +
                                "".replace(System.lineSeparator(), "\r\n").strip())
                        .buildText())
                    .setContentType("message/disposition-notification")
                    .build())
                .addBodyPart(
                    BodyPartBuilder.create()
                        .setBody(Message.Builder.of()
                            .setSubject("Subject of the original message")
                            .setBody("Content of the original message", StandardCharsets.UTF_8)
                            .build()))
                .build())
            .build();
        MDN mdnActual = MDN.parse(message);
        assertThat(mdnActual.getOriginalMessage()).isPresent();
    }

    @Test
    public void originalMessageShouldBeContainInMimeMessage() throws Exception {
        MDN mdn = MDN.builder()
            .humanReadableText("humanReadableText")
            .report(MINIMAL_REPORT)
            .message(Optional.of(Message.Builder
                .of()
                .setSubject("Subject of original message$tag")
                .setBody("Body of message", StandardCharsets.UTF_8)
                .build()))
            .build();
        MimeMessage mimeMessage = mdn.asMimeMessage();

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        mimeMessage.writeTo(byteArrayOutputStream);

        assertThat(byteArrayOutputStream.toString(StandardCharsets.UTF_8))
            .contains("Content-Type: message/rfc822\r\n" +
                "\r\n" +
                "MIME-Version: 1.0\r\n" +
                "Subject: Subject of original message$tag\r\n" +
                "Content-Type: text/plain; charset=UTF-8\r\n" +
                "\r\n" +
                "Body of message");
    }

    private String asString(Message message) throws Exception {
        return new String(DefaultMessageWriter.asBytes(message), StandardCharsets.UTF_8);
    }
}
