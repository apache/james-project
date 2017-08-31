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

import java.io.ByteArrayOutputStream;

import javax.mail.internet.MimeMessage;

import org.apache.james.mdn.action.mode.DispositionActionMode;
import org.apache.james.mdn.fields.Disposition;
import org.apache.james.mdn.sending.mode.DispositionSendingMode;
import org.apache.james.mdn.type.DispositionType;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.google.common.base.Charsets;

import nl.jqno.equalsverifier.EqualsVerifier;

public class MDNTest {

    public static final MDNReport MINIMAL_REPORT = MDNReport.builder()
        .finalRecipientField("final@domain.com")
        .dispositionField(Disposition.builder()
            .actionMode(DispositionActionMode.Automatic)
            .sendingMode(DispositionSendingMode.Automatic)
            .type(DispositionType.Deleted)
            .build())
        .build();
    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void shouldMatchBeanContract() {
        EqualsVerifier.forClass(MDN.class)
            .allFieldsShouldBeUsed()
            .verify();
    }

    @Test
    public void asMimeMessageShouldComportExplanationPartAndReportPart() throws Exception {
        MimeMessage mimeMessage = MDN.builder()
            .humanReadableText("Explanation")
            .report(MINIMAL_REPORT)
            .build()
            .asMimeMessage();

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        mimeMessage.writeTo(byteArrayOutputStream);
        assertThat(new String(byteArrayOutputStream.toByteArray(), Charsets.UTF_8))
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
    public void asMimeMessageShouldDisplayEmptyExplanation() throws Exception {
        MimeMessage mimeMessage = MDN.builder()
            .humanReadableText("Explanation")
            .report(MINIMAL_REPORT)
            .build()
            .asMimeMessage();

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        mimeMessage.writeTo(byteArrayOutputStream);
        assertThat(new String(byteArrayOutputStream.toByteArray(), Charsets.UTF_8))
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
    public void reportShouldThrowOnNullValue() {
        expectedException.expect(NullPointerException.class);

        MDN.builder()
            .report(null);
    }

    @Test
    public void humanReadableTextShouldThrowOnNullValue() {
        expectedException.expect(NullPointerException.class);

        MDN.builder()
            .humanReadableText(null);
    }

    @Test
    public void buildShouldThrowOnEmptyHumanReadableText() {
        expectedException.expect(IllegalStateException.class);

        MDN.builder()
            .humanReadableText("")
            .report(MINIMAL_REPORT)
            .build();
    }

    @Test
    public void buildShouldThrowOnFoldingWhiteHumanReadableText() {
        expectedException.expect(IllegalStateException.class);

        MDN.builder()
            .humanReadableText("  ")
            .report(MINIMAL_REPORT)
            .build();
    }

    @Test
    public void humanReadableTextShouldNotBeTrimmed() throws Exception {
        MimeMessage mimeMessage = MDN.builder()
            .humanReadableText("Explanation:\n" +
                " - We should always write detailed unit tests\n" +
                " - We should think of all edge cases\n")
            .report(MINIMAL_REPORT)
            .build()
            .asMimeMessage();

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        mimeMessage.writeTo(byteArrayOutputStream);
        assertThat(new String(byteArrayOutputStream.toByteArray(), Charsets.UTF_8))
            .contains(
                "Content-Type: text/plain; charset=UTF-8\r\n" +
                    "Content-Transfer-Encoding: 7bit\r\n" +
                    "Content-Disposition: inline\r\n" +
                    "\r\n" +
                    "Explanation:\n" +
                    " - We should always write detailed unit tests\n" +
                    " - We should think of all edge cases\n");
    }
}
