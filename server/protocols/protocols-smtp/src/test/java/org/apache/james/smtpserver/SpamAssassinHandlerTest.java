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
package org.apache.james.smtpserver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.HashMap;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.protocols.smtp.utils.BaseFakeSMTPSession;
import org.apache.james.smtpserver.fastfail.SpamAssassinHandler;
import org.apache.james.smtpserver.mock.mailet.MockMail;
import org.apache.james.util.scanner.SpamAssassinResult;
import org.apache.james.utils.MockSpamd;
import org.apache.james.utils.MockSpamdTestRule;
import org.apache.mailet.Mail;
import org.junit.Rule;
import org.junit.Test;

public class SpamAssassinHandlerTest {

    private Mail mockedMail;
    public static final String SPAMD_HOST = "localhost";

    private SMTPSession setupMockedSMTPSession(Mail mail) {
        mockedMail = mail;

        return new BaseFakeSMTPSession() {

            private final HashMap<String, Object> sstate = new HashMap<>();
            private final HashMap<String, Object> connectionState = new HashMap<>();
            private boolean relayingAllowed;

            @Override
            public Object setAttachment(String key, Object value, State state) {
                if (state == State.Connection) {
                    if (value == null) {
                        return connectionState.remove(key);
                    }
                    return connectionState.put(key, value);
                } else {
                    if (value == null) {
                        return sstate.remove(key);
                    }
                    return sstate.put(key, value);
                }
            }

            @Override
            public Object getAttachment(String key, State state) {
                sstate.put(SMTPSession.SENDER, "sender@james.apache.org");
                if (state == State.Connection) {
                    return connectionState.get(key);
                } else {
                    return sstate.get(key);
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

    @Rule
    public MockSpamdTestRule spamd = new MockSpamdTestRule();

    private Mail setupMockedMail(MimeMessage message) {
        MockMail mail = new MockMail();
        mail.setMessage(message);
        return mail;
    }

    public MimeMessage setupMockedMimeMessage(String text) throws MessagingException {
        return MimeMessageBuilder.mimeMessageBuilder()
            .setText(text)
            .build();
    }

    @Test
    public void testNonSpam() throws Exception {
        SMTPSession session = setupMockedSMTPSession(setupMockedMail(setupMockedMimeMessage("test")));

        SpamAssassinHandler handler = new SpamAssassinHandler();

        handler.setSpamdHost(SPAMD_HOST);
        handler.setSpamdPort(spamd.getPort());
        handler.setSpamdRejectionHits(200.0);
        HookResult response = handler.onMessage(session, mockedMail);

        assertEquals("Email was not rejected", response.getResult(), HookReturnCode.DECLINED);
        assertEquals("email was not spam", mockedMail.getAttribute(SpamAssassinResult.FLAG_MAIL_ATTRIBUTE_NAME), "NO");
        assertNotNull("spam hits", mockedMail.getAttribute(SpamAssassinResult.STATUS_MAIL_ATTRIBUTE_NAME));

    }

    @Test
    public void testSpam() throws Exception {
        SMTPSession session = setupMockedSMTPSession(setupMockedMail(setupMockedMimeMessage(MockSpamd.GTUBE)));

        SpamAssassinHandler handler = new SpamAssassinHandler();

        handler.setSpamdHost(SPAMD_HOST);
        handler.setSpamdPort(spamd.getPort());
        handler.setSpamdRejectionHits(2000.0);
        HookResult response = handler.onMessage(session, mockedMail);

        assertEquals("Email was not rejected", response.getResult(), HookReturnCode.DECLINED);
        assertEquals("email was spam", mockedMail.getAttribute(SpamAssassinResult.FLAG_MAIL_ATTRIBUTE_NAME), "YES");
        assertNotNull("spam hits", mockedMail.getAttribute(SpamAssassinResult.STATUS_MAIL_ATTRIBUTE_NAME));
    }

    @Test
    public void testSpamReject() throws Exception {
        SMTPSession session = setupMockedSMTPSession(setupMockedMail(setupMockedMimeMessage(MockSpamd.GTUBE)));

        SpamAssassinHandler handler = new SpamAssassinHandler();

        handler.setSpamdHost(SPAMD_HOST);
        handler.setSpamdPort(spamd.getPort());
        handler.setSpamdRejectionHits(200.0);
        HookResult response = handler.onMessage(session, mockedMail);

        assertEquals("Email was rejected", response.getResult(), HookReturnCode.DENY);
        assertEquals("email was spam", mockedMail.getAttribute(SpamAssassinResult.FLAG_MAIL_ATTRIBUTE_NAME), "YES");
        assertNotNull("spam hits", mockedMail.getAttribute(SpamAssassinResult.STATUS_MAIL_ATTRIBUTE_NAME));
    }
}
