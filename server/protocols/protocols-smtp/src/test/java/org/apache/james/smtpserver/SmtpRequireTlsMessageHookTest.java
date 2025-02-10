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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.james.smtpserver.SMTPServerTestSystem.BOB;
import static org.apache.james.smtpserver.SMTPServerTestSystem.PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Base64;

import org.apache.commons.net.smtp.SMTPSClient;
import org.apache.commons.net.smtp.SimpleSMTPHeader;
import org.apache.james.protocols.api.utils.BogusSslContextFactory;
import org.apache.james.protocols.api.utils.BogusTrustManagerFactory;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SmtpRequireTlsMessageHookTest {
    private final SMTPServerTestSystem testSystem = new SMTPServerTestSystem();
    private static final String REQUIRETLS = "REQUIRETLS";
    private static final AttributeName REQUIRETLS_ATTRIBUTE_NAME = AttributeName.of(REQUIRETLS);

    @BeforeEach
    void setUp() throws Exception {
        testSystem.setUp("smtpserver-requireTls.xml");
    }

    @AfterEach
    void tearDown() {
        testSystem.smtpServer.destroy();
    }

    private SMTPSClient initSMTPSClient() throws IOException {
        SMTPSClient client = new SMTPSClient(false, BogusSslContextFactory.getClientContext());
        client.setTrustManager(BogusTrustManagerFactory.getTrustManagers()[0]);
        InetSocketAddress bindedAddress = testSystem.getBindedAddress();
        client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        return client;
    }

    @Test
    void ehloShouldAdvertiseRequireTlsExtension() throws Exception {
        SMTPSClient client = initSMTPSClient();
        client.execTLS();
        client.sendCommand("EHLO localhost");

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(client.getReplyCode()).isEqualTo(250);
            softly.assertThat(client.getReplyString()).contains("250 REQUIRETLS");
        });
    }

    @Test
    void ehloShouldNotAdvertiseRequireTlsExtensionWithoutExecTLS() throws Exception {
        SMTPSClient client = initSMTPSClient();
        client.sendCommand("EHLO localhost");

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(client.getReplyCode()).isEqualTo(250);
            softly.assertThat(client.getReplyString()).doesNotContain("250 REQUIRETLS");
        });
    }

    @Test
    void mailShouldBeRejectedWhenInvalidTlsRequiredParameter() throws Exception {
        SMTPSClient client = initSMTPSClient();
        client.execTLS();
        authenticate(client);
        client.sendCommand("EHLO localhost");
        client.sendCommand("MAIL FROM: <bob@localhost> REQUIRETLS REQUIRETLS");

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(client.getReplyCode()).isEqualTo(501);
            softly.assertThat(client.getReplyString()).contains("501 The Mail parameter cannot contain more than one REQUIRETLS parameter at the same time");
        });
    }

    @Test
    void requireTlsParameterShouldBeSetOnTheFinalEmail() throws Exception {
        SMTPSClient client = initSMTPSClient();
        client.execTLS();
        authenticate(client);
        client.sendCommand("EHLO localhost");
        client.sendCommand("MAIL FROM:<bob@localhost> REQUIRETLS");
        client.sendCommand("RCPT TO:<rcpt@localhost>");
        client.sendShortMessageData("From: bob@localhost\r\n\r\nSubject: test mail\r\n\r\nTest body testSimpleMailSendWithMtPriority\r\n.\r\n");

        Mail lastMail = testSystem.queue.getLastMail();

        assertThat(lastMail.getAttribute(REQUIRETLS_ATTRIBUTE_NAME))
            .hasValue(new Attribute(REQUIRETLS_ATTRIBUTE_NAME, AttributeValue.of(true)));

    }

    @Test
    void requireTlsParameterShouldBeIgnoredInTheFinalEmail() throws Exception {
        SMTPSClient client = initSMTPSClient();
        authenticate(client);
        client.sendCommand("EHLO localhost");
        client.sendCommand("MAIL FROM:<bob@localhost> REQUIRETLS");
        client.sendCommand("RCPT TO:<rcpt@localhost>");
        client.sendShortMessageData("From: bob@localhost\r\n\r\nSubject: test mail\r\n\r\nTest body testSimpleMailSendWithMtPriority\r\n.\r\n");

        Mail lastMail = testSystem.queue.getLastMail();

        assertThat(lastMail.getAttribute(REQUIRETLS_ATTRIBUTE_NAME)).isEmpty();
    }

    @Test
    void tlsRequiredHeaderFieldShouldBeIgnoredWhenTheRequireTlsMailFromParameterIsSpecified() throws Exception {
        SMTPSClient client = initSMTPSClient();
        client.execTLS();
        authenticate(client);
        client.sendCommand("EHLO localhost");
        client.sendCommand("MAIL FROM:<bob@localhost> REQUIRETLS");
        client.sendCommand("RCPT TO:<rcpt@localhost>");
        SimpleSMTPHeader header = new SimpleSMTPHeader("bob@localhost", "rcpt@localhost", "Just testing");
        header.addHeaderField("TLS-Required", "No");
        client.sendShortMessageData(header + "Test body testRequireTlsEmail\r\n.\r\n");

        Mail lastMail = testSystem.queue.getLastMail();

        assertThat(lastMail.getAttribute(REQUIRETLS_ATTRIBUTE_NAME))
            .hasValue(new Attribute(REQUIRETLS_ATTRIBUTE_NAME, AttributeValue.of(true)));
    }

    @Test
    void tlsRequiredHeaderFieldShouldBeIncludedWhenTheRequireTlsMailFromParameterIsNotSpecified() throws Exception {
        SMTPSClient client = initSMTPSClient();
        client.execTLS();
        authenticate(client);
        client.sendCommand("EHLO localhost");
        client.sendCommand("MAIL FROM:<bob@localhost>");
        client.sendCommand("RCPT TO:<rcpt@localhost>");
        SimpleSMTPHeader header = new SimpleSMTPHeader("bob@localhost", "rcpt@localhost", "Just testing");
        header.addHeaderField("TLS-Required", "No");
        client.sendShortMessageData(header + "Test body testRequireTlsEmail\r\n.\r\n");

        Mail lastMail = testSystem.queue.getLastMail();

        assertThat(lastMail.getAttribute(REQUIRETLS_ATTRIBUTE_NAME))
                .hasValue(new Attribute(REQUIRETLS_ATTRIBUTE_NAME, AttributeValue.of(false)));
    }

    private void authenticate(SMTPSClient smtpProtocol) throws IOException {
        smtpProtocol.sendCommand("AUTH PLAIN");
        smtpProtocol.sendCommand(Base64.getEncoder().encodeToString(("\0" + BOB.asString() + "\0" + PASSWORD + "\0").getBytes(UTF_8)));
        assertThat(smtpProtocol.getReplyCode())
            .as("authenticated")
            .isEqualTo(235);
    }

}