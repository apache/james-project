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

package org.apache.james.spamassassin;


import static org.apache.james.spamassassin.SpamAssassinResult.STATUS_MAIL;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Optional;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.MimeMessage;

import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.protocols.smtp.utils.BaseFakeSMTPSession;
import org.apache.james.spamassassin.mock.MockSpamd;
import org.apache.james.spamassassin.mock.MockSpamdExtension;
import org.apache.james.util.Host;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.base.Preconditions;

class SpamAssassinHandlerTest {

    private static final String SPAMD_HOST = "localhost";
    private static final Attribute FLAG_MAIL_ATTRIBUTE_NO = new Attribute(SpamAssassinResult.FLAG_MAIL, AttributeValue.of("NO"));
    private static final Attribute FLAG_MAIL_ATTRIBUTE_YES = new Attribute(SpamAssassinResult.FLAG_MAIL, AttributeValue.of("YES"));

    private Mail mockedMail;

    private SMTPSession setupMockedSMTPSession(Mail mail) {
        mockedMail = mail;

        return new BaseFakeSMTPSession() {

            private final HashMap<ProtocolSession.AttachmentKey<?>, Object> sessionState = new HashMap<>();
            private final HashMap<ProtocolSession.AttachmentKey<?>, Object> connectionState = new HashMap<>();
            private boolean relayingAllowed;

            @Override
            public <T> Optional<T> setAttachment(ProtocolSession.AttachmentKey<T> key, T value, ProtocolSession.State state) {
                Preconditions.checkNotNull(key, "key cannot be null");
                Preconditions.checkNotNull(value, "value cannot be null");

                if (state == ProtocolSession.State.Connection) {
                    return key.convert(connectionState.put(key, value));
                } else {
                    return key.convert(sessionState.put(key, value));
                }
            }

            @Override
            public <T> Optional<T> removeAttachment(ProtocolSession.AttachmentKey<T> key, ProtocolSession.State state) {
                Preconditions.checkNotNull(key, "key cannot be null");

                if (state == ProtocolSession.State.Connection) {
                    return key.convert(connectionState.remove(key));
                } else {
                    return key.convert(sessionState.remove(key));
                }
            }

            @Override
            public <T> Optional<T> getAttachment(ProtocolSession.AttachmentKey<T> key, ProtocolSession.State state) {
                try {
                    sessionState.put(SMTPSession.SENDER, MaybeSender.of(new MailAddress("sender@james.apache.org")));
                } catch (AddressException e) {
                    throw new RuntimeException(e);
                }
                if (state == ProtocolSession.State.Connection) {
                    return key.convert(connectionState.get(key));
                } else {
                    return key.convert(sessionState.get(key));
                }
            }

            @Override
            public boolean isRelayingAllowed() {
                return relayingAllowed;
            }

            @Override
            public void setRelayingAllowed(boolean relayingAllowed) {
                this.relayingAllowed = relayingAllowed;
            }
        };

    }

    @RegisterExtension
    MockSpamdExtension spamd = new MockSpamdExtension();

    private Mail setupMockedMail(MimeMessage message) throws MessagingException {
        return FakeMail.builder()
            .name("name")
            .mimeMessage(message)
            .build();
    }

    private MimeMessage setupMockedMimeMessage(String text) throws MessagingException {
        return MimeMessageBuilder.mimeMessageBuilder()
            .setText(text)
            .build();
    }

    @Test
    void testNonSpam() throws Exception {
        SMTPSession session = setupMockedSMTPSession(setupMockedMail(setupMockedMimeMessage("test")));

        SpamAssassinHandler handler = new SpamAssassinHandler(new RecordingMetricFactory(), new SpamAssassinConfiguration(Host.from(SPAMD_HOST, spamd.getPort())));

        handler.setSpamdRejectionHits(200.0);
        HookResult response = handler.onMessage(session, mockedMail);

        assertThat(HookReturnCode.declined()).describedAs("Email was not rejected").isEqualTo(response.getResult());
        assertThat(mockedMail.getAttribute(SpamAssassinResult.FLAG_MAIL)).describedAs("email was not spam").contains(FLAG_MAIL_ATTRIBUTE_NO);
        assertThat(mockedMail.getAttribute(STATUS_MAIL)).withFailMessage("spam hits").isPresent();

    }

    @Test
    void testSpam() throws Exception {
        SMTPSession session = setupMockedSMTPSession(setupMockedMail(setupMockedMimeMessage(MockSpamd.GTUBE)));

        SpamAssassinHandler handler = new SpamAssassinHandler(new RecordingMetricFactory(), new SpamAssassinConfiguration(Host.from(SPAMD_HOST, spamd.getPort())));

        handler.setSpamdRejectionHits(2000.0);
        HookResult response = handler.onMessage(session, mockedMail);

        assertThat(HookReturnCode.declined()).describedAs("Email was not rejected").isEqualTo(response.getResult());
        assertThat(mockedMail.getAttribute(SpamAssassinResult.FLAG_MAIL)).describedAs("email was spam").contains(FLAG_MAIL_ATTRIBUTE_YES);
        assertThat(mockedMail.getAttribute(STATUS_MAIL)).withFailMessage("spam hits").isPresent();
    }

    @Test
    void testSpamReject() throws Exception {
        SMTPSession session = setupMockedSMTPSession(setupMockedMail(setupMockedMimeMessage(MockSpamd.GTUBE)));

        SpamAssassinHandler handler = new SpamAssassinHandler(new RecordingMetricFactory(), new SpamAssassinConfiguration(Host.from(SPAMD_HOST, spamd.getPort())));

        handler.setSpamdRejectionHits(200.0);
        HookResult response = handler.onMessage(session, mockedMail);

        assertThat(HookReturnCode.deny()).describedAs("Email was rejected").isEqualTo(response.getResult());
        assertThat(mockedMail.getAttribute(SpamAssassinResult.FLAG_MAIL)).describedAs("email was spam").contains(FLAG_MAIL_ATTRIBUTE_YES);
        assertThat(mockedMail.getAttribute(STATUS_MAIL)).withFailMessage("spam hits").isPresent();
    }
}
