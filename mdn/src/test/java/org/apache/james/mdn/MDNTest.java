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
import java.util.regex.Pattern;

import javax.mail.internet.MimeMessage;

import org.apache.james.mdn.action.mode.DispositionActionMode;
import org.apache.james.mdn.fields.Disposition;
import org.apache.james.mdn.sending.mode.DispositionSendingMode;
import org.apache.james.mdn.type.DispositionType;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.message.DefaultMessageWriter;
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

    private String asString(Message message) throws Exception {
        return new String(DefaultMessageWriter.asBytes(message), StandardCharsets.UTF_8);
    }
}
