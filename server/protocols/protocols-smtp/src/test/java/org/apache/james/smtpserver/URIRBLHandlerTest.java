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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.MimeMessage;

import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.dnsservice.api.mock.MockDNSService;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.protocols.smtp.utils.BaseFakeSMTPSession;
import org.apache.james.smtpserver.fastfail.URIRBLHandler;
import org.apache.mailet.Mail;
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.Test;

import com.google.common.base.Preconditions;

class URIRBLHandlerTest {

    private static final String BAD_DOMAIN1 = "bad.domain.de";
    private static final String BAD_DOMAIN2 = "bad2.domain.de";
    private static final String GOOD_DOMAIN = "good.apache.org";
    private static final String URISERVER = "multi.surbl.org.";
    private Mail mockedMail;

    private SMTPSession setupMockedSMTPSession(Mail mail) {
        mockedMail = mail;

        return new BaseFakeSMTPSession() {

            private boolean relayingAllowed;

            private final HashMap<AttachmentKey<?>, Object> sessionState = new HashMap<>();
            private final HashMap<AttachmentKey<?>, Object> connectionState = new HashMap<>();

            @Override
            public <T> Optional<T> setAttachment(AttachmentKey<T> key, T value, State state) {
                Preconditions.checkNotNull(key, "key cannot be null");
                Preconditions.checkNotNull(value, "value cannot be null");

                if (state == State.Connection) {
                    return key.convert(connectionState.put(key, value));
                } else {
                    return key.convert(sessionState.put(key, value));
                }
            }

            @Override
            public <T> Optional<T> removeAttachment(AttachmentKey<T> key, State state) {
                Preconditions.checkNotNull(key, "key cannot be null");

                if (state == State.Connection) {
                    return key.convert(connectionState.remove(key));
                } else {
                    return key.convert(sessionState.remove(key));
                }
            }

            @Override
            public <T> Optional<T> getAttachment(AttachmentKey<T> key, State state) {
                try {
                    sessionState.put(SMTPSession.SENDER, MaybeSender.of(new MailAddress("sender@james.apache.org")));
                } catch (AddressException e) {
                    throw new RuntimeException(e);
                }

                if (state == State.Connection) {
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

    private MimeMessage setupMockedMimeMessageMP(String text) throws MessagingException {
        return MimeMessageBuilder.mimeMessageBuilder()
            .setMultipartWithBodyParts(
                MimeMessageBuilder.bodyPartBuilder()
                    .data(text))
            .build();
    }

    /**
     * Setup the mocked dnsserver
     * 
     */
    private DNSService setupMockedDnsServer() {

        return new MockDNSService() {

            @Override
            public Collection<String> findTXTRecords(String hostname) {
                List<String> res = new ArrayList<>();
                if (hostname == null) {
                    return res;
                }

                if ((BAD_DOMAIN1.substring(4)).equals(hostname)) {
                    res.add("Blocked - see http://www.surbl.org");
                }
                return res;
            }

            @Override
            public InetAddress getByName(String host) throws UnknownHostException {
                if ((BAD_DOMAIN1.substring(4) + "." + URISERVER).equals(host)) {
                    return InetAddress.getByName("127.0.0.1");
                } else if ((BAD_DOMAIN2.substring(4) + "." + URISERVER).equals(host)) {
                    return InetAddress.getByName("127.0.0.1");
                } else if ((GOOD_DOMAIN.substring(5) + "." + URISERVER).equals(host)) {
                    throw new UnknownHostException();
                }
                throw new UnsupportedOperationException("getByName(" + host + ") not implemented by this mock");
            }
        };
    }

    @Test
    void testNotBlocked() throws IOException, MessagingException {

        ArrayList<String> servers = new ArrayList<>();
        servers.add(URISERVER);

        SMTPSession session = setupMockedSMTPSession(setupMockedMail(setupMockedMimeMessage(
                "http://" + GOOD_DOMAIN + "/")));

        URIRBLHandler handler = new URIRBLHandler();

        handler.setDNSService(setupMockedDnsServer());
        handler.setUriRblServer(servers);
        HookResult response = handler.onMessage(session, mockedMail);

        assertThat(HookReturnCode.declined()).describedAs("Email was not rejected").isEqualTo(response.getResult());
    }

    @Test
    void testBlocked() throws IOException, MessagingException {

        ArrayList<String> servers = new ArrayList<>();
        servers.add(URISERVER);

        SMTPSession session = setupMockedSMTPSession(setupMockedMail(setupMockedMimeMessage(
                "http://" + BAD_DOMAIN1 + "/")));

        URIRBLHandler handler = new URIRBLHandler();

        handler.setDNSService(setupMockedDnsServer());
        handler.setUriRblServer(servers);
        HookResult response = handler.onMessage(session, mockedMail);

        assertThat(HookReturnCode.deny()).describedAs("Email was rejected").isEqualTo(response.getResult());
    }

    @Test
    void testBlockedMultiPart() throws IOException, MessagingException {

        ArrayList<String> servers = new ArrayList<>();
        servers.add(URISERVER);

        SMTPSession session = setupMockedSMTPSession(setupMockedMail(setupMockedMimeMessageMP(
                "http://" + BAD_DOMAIN1 + "/" + " " + "http://" + GOOD_DOMAIN + "/")));

        URIRBLHandler handler = new URIRBLHandler();

        handler.setDNSService(setupMockedDnsServer());
        handler.setUriRblServer(servers);
        HookResult response = handler.onMessage(session, mockedMail);

        assertThat(HookReturnCode.deny()).describedAs("Email was rejected").isEqualTo(response.getResult());
    }

    /*
     * public void testAddJunkScore() throws IOException, MessagingException {
     * 
     * ArrayList servers = new ArrayList(); servers.add(URISERVER);
     * 
     * SMTPSession session =
     * setupMockedSMTPSession(setupMockedMail(setupMockedMimeMessage("http://" +
     * BAD_DOMAIN1 + "/"))); session.getState().put(JunkScore.JUNK_SCORE, new
     * JunkScoreImpl());
     * 
     * URIRBLHandler handler = new URIRBLHandler();
     * 
     * ContainerUtil.enableLogging(handler, new MockLogger());
     * handler.setDnsServer(setupMockedDnsServer());
     * handler.setUriRblServer(servers); handler.setAction("junkScore");
     * handler.setScore(20); HookResult response = handler.onMessage(session,
     * mockedMail);
     * 
     * assertNull("Email was not rejected", response);
     * assertEquals("JunkScore added", ((JunkScore)
     * session.getState().get(JunkScore
     * .JUNK_SCORE)).getStoredScore("UriRBLCheck"), 20.0, 0d); }
     */
}
