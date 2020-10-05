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

package org.apache.james.jmap.draft.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.apache.james.core.Username;
import org.apache.james.jmap.draft.exceptions.InvalidOriginMessageForMDNException;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.model.TestMessageId;
import org.apache.james.mdn.action.mode.DispositionActionMode;
import org.apache.james.mdn.sending.mode.DispositionSendingMode;
import org.apache.james.mdn.type.DispositionType;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.address.Mailbox;
import org.apache.james.mime4j.stream.RawField;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class JmapMDNTest {

    public static final String TEXT_BODY = "text body";
    public static final String SUBJECT = "subject";
    public static final String REPORTING_UA = "reportingUA";
    public static final TestMessageId MESSAGE_ID = TestMessageId.of(45);
    public static final MDNDisposition DISPOSITION = MDNDisposition.builder()
        .actionMode(DispositionActionMode.Automatic)
        .sendingMode(DispositionSendingMode.Automatic)
        .type(DispositionType.Processed)
        .build();
    public static final JmapMDN MDN = JmapMDN.builder()
        .disposition(DISPOSITION)
        .messageId(MESSAGE_ID)
        .reportingUA(REPORTING_UA)
        .subject(SUBJECT)
        .textBody(TEXT_BODY)
        .build();
    public static final MailboxSession MAILBOX_SESSION = MailboxSessionUtil.create(Username.of("user@localhost.com"));

    @Test
    public void shouldMatchBeanContract() {
        EqualsVerifier.forClass(JmapMDN.class)
            .verify();
    }

    @Test
    public void builderShouldReturnObjectWhenAllFieldsAreValid() {
        assertThat(MDN)
            .isEqualTo(new JmapMDN(MESSAGE_ID, SUBJECT, TEXT_BODY, REPORTING_UA, DISPOSITION));
    }

    @Test
    public void dispositionIsCompulsory() {
        assertThatThrownBy(() ->
            JmapMDN.builder()
                .messageId(MESSAGE_ID)
                .reportingUA(REPORTING_UA)
                .subject(SUBJECT)
                .textBody(TEXT_BODY)
                .build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void messageIdIsCompulsory() {
        assertThatThrownBy(() ->
            JmapMDN.builder()
                .disposition(DISPOSITION)
                .reportingUA(REPORTING_UA)
                .subject(SUBJECT)
                .textBody(TEXT_BODY)
                .build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void reportingUAIsCompulsory() {
        assertThatThrownBy(() ->
            JmapMDN.builder()
                .disposition(DISPOSITION)
                .messageId(MESSAGE_ID)
                .subject(SUBJECT)
                .textBody(TEXT_BODY)
                .build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void subjectIsCompulsory() {
        assertThatThrownBy(() ->
            JmapMDN.builder()
                .disposition(DISPOSITION)
                .messageId(MESSAGE_ID)
                .reportingUA(REPORTING_UA)
                .textBody(TEXT_BODY)
                .build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void textBodyIsCompulsory() {
        assertThatThrownBy(() ->
            JmapMDN.builder()
                .disposition(DISPOSITION)
                .messageId(MESSAGE_ID)
                .reportingUA(REPORTING_UA)
                .subject(SUBJECT)
                .build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void generateMDNMessageShouldUseDispositionHeaders() throws Exception {
        String senderAddress = "sender@local";
        Message originMessage = Message.Builder.of()
            .setMessageId("45554@local.com")
            .setFrom(senderAddress)
            .setBody("body", StandardCharsets.UTF_8)
            .addField(new RawField(JmapMDN.RETURN_PATH, "<" + senderAddress + ">"))
            .addField(new RawField(JmapMDN.DISPOSITION_NOTIFICATION_TO, "<" + senderAddress + ">"))
            .build();

        assertThat(
            MDN.generateMDNMessage(originMessage, MAILBOX_SESSION)
                .getTo())
            .extracting(address -> (Mailbox) address)
            .extracting(Mailbox::getAddress)
            .containsExactly(senderAddress);
    }

    @Test
    public void generateMDNMessageShouldPositionDateHeader() throws Exception {
        String senderAddress = "sender@local";
        Message originMessage = Message.Builder.of()
            .setMessageId("45554@local.com")
            .setFrom(senderAddress)
            .setBody("body", StandardCharsets.UTF_8)
            .addField(new RawField(JmapMDN.RETURN_PATH, "<" + senderAddress + ">"))
            .addField(new RawField(JmapMDN.DISPOSITION_NOTIFICATION_TO, "<" + senderAddress + ">"))
            .build();

        assertThat(
            MDN.generateMDNMessage(originMessage, MAILBOX_SESSION)
                .getDate())
            .isNotNull();
    }

    @Test
    public void generateMDNMessageShouldFailOnMissingDisposition() throws Exception {
        String senderAddress = "sender@local";
        Message originMessage = Message.Builder.of()
            .setMessageId("45554@local.com")
            .setFrom(senderAddress)
            .setBody("body", StandardCharsets.UTF_8)
            .addField(new RawField(JmapMDN.RETURN_PATH, "<" + senderAddress + ">"))
            .build();

        assertThatThrownBy(() ->
            MDN.generateMDNMessage(originMessage, MAILBOX_SESSION))
            .isInstanceOf(InvalidOriginMessageForMDNException.class);
    }

}