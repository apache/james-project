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
import static org.apache.mailet.DsnParameters.Notify.DELAY;
import static org.apache.mailet.DsnParameters.Notify.FAILURE;
import static org.apache.mailet.DsnParameters.Notify.SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Base64;
import java.util.EnumSet;

import org.apache.commons.net.smtp.SMTPClient;
import org.apache.james.core.MailAddress;
import org.apache.james.server.core.configuration.Configuration;
import org.apache.james.server.core.configuration.FileConfigurationProvider;
import org.apache.mailet.DsnParameters;
import org.apache.mailet.Mail;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DSNTest {
    protected Configuration configuration;

    private SMTPServerTestSystem testSystem = new SMTPServerTestSystem();

    @BeforeEach
    void setUp() throws Exception {
        testSystem.preSetUp();
    }

    @AfterEach
    void tearDown() {
        testSystem.smtpServer.destroy();
    }

    @Test
    void ehloShouldAdvertiseDsnExtension() throws Exception {
        testSystem.smtpServer.configure(FileConfigurationProvider.getConfig(
            ClassLoader.getSystemResourceAsStream("smtpserver-dsn.xml")));
        testSystem.smtpServer.init();

        SMTPClient smtpProtocol = new SMTPClient();
        InetSocketAddress bindedAddress = testSystem.getBindedAddress();
        smtpProtocol.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        authenticate(smtpProtocol);
        smtpProtocol.sendCommand("EHLO localhost");

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(smtpProtocol.getReplyCode()).isEqualTo(250);
            softly.assertThat(smtpProtocol.getReplyString()).contains("250 DSN");
        });
    }

    private void authenticate(SMTPClient smtpProtocol) throws IOException {
        smtpProtocol.sendCommand("AUTH PLAIN");
        smtpProtocol.sendCommand(Base64.getEncoder().encodeToString(("\0" + BOB.asString() + "\0" + PASSWORD + "\0").getBytes(UTF_8)));
        assertThat(smtpProtocol.getReplyCode())
            .as("authenticated")
            .isEqualTo(235);
    }

    @Test
    void dsnParametersShouldBeSetOnTheFinalEmail() throws Exception {
        testSystem.smtpServer.configure(FileConfigurationProvider.getConfig(
            ClassLoader.getSystemResourceAsStream("smtpserver-dsn.xml")));
        testSystem.smtpServer.init();

        SMTPClient smtpProtocol = new SMTPClient();
        InetSocketAddress bindedAddress = testSystem.getBindedAddress();
        smtpProtocol.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        authenticate(smtpProtocol);

        smtpProtocol.sendCommand("EHLO localhost");
        smtpProtocol.sendCommand("MAIL FROM: <bob@localhost> RET=HDRS ENVID=QQ314159");
        smtpProtocol.sendCommand("RCPT TO:<rcpt@localhost> NOTIFY=SUCCESS,FAILURE,DELAY ORCPT=rfc822;orcpt@localhost");
        smtpProtocol.sendShortMessageData("Subject: test mail\r\n\r\nTest body testSimpleMailSendWithDSN\r\n.\r\n");

        Mail lastMail = testSystem.queue.getLastMail();
        assertThat(lastMail.dsnParameters())
            .contains(DsnParameters.builder()
                .envId(DsnParameters.EnvId.of("QQ314159"))
                .ret(DsnParameters.Ret.HDRS)
                .addRcptParameter(new MailAddress("rcpt@localhost"), DsnParameters.RecipientDsnParameters.of(
                    EnumSet.of(SUCCESS, FAILURE, DELAY),
                    new MailAddress("orcpt@localhost")
                )).build().get());
    }

    @Test
    void multipleRecipientsShouldBeSupported() throws Exception {
        testSystem.smtpServer.configure(FileConfigurationProvider.getConfig(
            ClassLoader.getSystemResourceAsStream("smtpserver-dsn.xml")));
        testSystem.smtpServer.init();

        SMTPClient smtpProtocol = new SMTPClient();
        InetSocketAddress bindedAddress = testSystem.getBindedAddress();
        smtpProtocol.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        authenticate(smtpProtocol);

        smtpProtocol.sendCommand("EHLO localhost");
        smtpProtocol.sendCommand("MAIL FROM: <bob@localhost> RET=HDRS ENVID=QQ314159");
        smtpProtocol.sendCommand("RCPT TO:<rcpt1@localhost> NOTIFY=SUCCESS,FAILURE,DELAY ORCPT=rfc822;orcpt1@localhost");
        smtpProtocol.sendCommand("RCPT TO:<rcpt2@localhost> NOTIFY=SUCCESS,FAILURE,DELAY ORCPT=rfc822;orcpt2@localhost");
        smtpProtocol.sendCommand("RCPT TO:<rcpt@localhost>");
        smtpProtocol.sendShortMessageData("Subject: test mail\r\n\r\nTest body testSimpleMailSendWithDSN\r\n.\r\n");

        Mail lastMail = testSystem.queue.getLastMail();
        assertThat(lastMail.dsnParameters())
            .contains(DsnParameters.builder()
                .envId(DsnParameters.EnvId.of("QQ314159"))
                .ret(DsnParameters.Ret.HDRS)
                .addRcptParameter(new MailAddress("rcpt1@localhost"), DsnParameters.RecipientDsnParameters.of(
                    EnumSet.of(SUCCESS, FAILURE, DELAY),
                    new MailAddress("orcpt1@localhost")))
                .addRcptParameter(new MailAddress("rcpt2@localhost"), DsnParameters.RecipientDsnParameters.of(
                    EnumSet.of(SUCCESS, FAILURE, DELAY),
                    new MailAddress("orcpt2@localhost")
                )).build().get());
    }

    @Test
    void notifyCanBeOmitted() throws Exception {
        testSystem.smtpServer.configure(FileConfigurationProvider.getConfig(
            ClassLoader.getSystemResourceAsStream("smtpserver-dsn.xml")));
        testSystem.smtpServer.init();

        SMTPClient smtpProtocol = new SMTPClient();
        InetSocketAddress bindedAddress = testSystem.getBindedAddress();
        smtpProtocol.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        authenticate(smtpProtocol);

        smtpProtocol.sendCommand("EHLO localhost");
        smtpProtocol.sendCommand("MAIL FROM: <bob@localhost> RET=HDRS ENVID=QQ314159");
        smtpProtocol.sendCommand("RCPT TO:<rcpt@localhost> ORCPT=rfc822;orcpt@localhost");
        smtpProtocol.sendShortMessageData("Subject: test mail\r\n\r\nTest body testSimpleMailSendWithDSN\r\n.\r\n");

        Mail lastMail = testSystem.queue.getLastMail();
        assertThat(lastMail.dsnParameters())
            .contains(DsnParameters.builder()
                .envId(DsnParameters.EnvId.of("QQ314159"))
                .ret(DsnParameters.Ret.HDRS)
                .addRcptParameter(new MailAddress("rcpt@localhost"), DsnParameters.RecipientDsnParameters.of(
                    new MailAddress("orcpt@localhost")))
                .build().get());
    }

    @Test
    void orcptCanBeOmitted() throws Exception {
        testSystem.smtpServer.configure(FileConfigurationProvider.getConfig(
            ClassLoader.getSystemResourceAsStream("smtpserver-dsn.xml")));
        testSystem.smtpServer.init();

        SMTPClient smtpProtocol = new SMTPClient();
        InetSocketAddress bindedAddress = testSystem.getBindedAddress();
        smtpProtocol.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        authenticate(smtpProtocol);

        smtpProtocol.sendCommand("EHLO localhost");
        smtpProtocol.sendCommand("MAIL FROM: <bob@localhost> RET=HDRS ENVID=QQ314159");
        smtpProtocol.sendCommand("RCPT TO:<rcpt@localhost> NOTIFY=SUCCESS,FAILURE,DELAY");
        smtpProtocol.sendShortMessageData("Subject: test mail\r\n\r\nTest body testSimpleMailSendWithDSN\r\n.\r\n");

        Mail lastMail = testSystem.queue.getLastMail();
        assertThat(lastMail.dsnParameters())
            .contains(DsnParameters.builder()
                .envId(DsnParameters.EnvId.of("QQ314159"))
                .ret(DsnParameters.Ret.HDRS)
                .addRcptParameter(new MailAddress("rcpt@localhost"), DsnParameters.RecipientDsnParameters.of(
                    EnumSet.of(SUCCESS, FAILURE, DELAY)))
                .build().get());
    }

    @Test
    void retCanBeOmitted() throws Exception {
        testSystem.smtpServer.configure(FileConfigurationProvider.getConfig(
            ClassLoader.getSystemResourceAsStream("smtpserver-dsn.xml")));
        testSystem.smtpServer.init();

        SMTPClient smtpProtocol = new SMTPClient();
        InetSocketAddress bindedAddress = testSystem.getBindedAddress();
        smtpProtocol.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        authenticate(smtpProtocol);

        smtpProtocol.sendCommand("EHLO localhost");
        smtpProtocol.sendCommand("MAIL FROM: <bob@localhost> ENVID=QQ314159");
        smtpProtocol.sendCommand("RCPT TO:<rcpt@localhost> NOTIFY=SUCCESS,FAILURE,DELAY ORCPT=rfc822;orcpt@localhost");
        smtpProtocol.sendShortMessageData("Subject: test mail\r\n\r\nTest body testSimpleMailSendWithDSN\r\n.\r\n");

        Mail lastMail = testSystem.queue.getLastMail();
        assertThat(lastMail.dsnParameters())
            .contains(DsnParameters.builder()
                .envId(DsnParameters.EnvId.of("QQ314159"))
                .addRcptParameter(new MailAddress("rcpt@localhost"), DsnParameters.RecipientDsnParameters.of(
                    EnumSet.of(SUCCESS, FAILURE, DELAY),
                    new MailAddress("orcpt@localhost")))
                .build().get());
    }

    @Test
    void envIdCanBeOmitted() throws Exception {
        testSystem.smtpServer.configure(FileConfigurationProvider.getConfig(
            ClassLoader.getSystemResourceAsStream("smtpserver-dsn.xml")));
        testSystem.smtpServer.init();

        SMTPClient smtpProtocol = new SMTPClient();
        InetSocketAddress bindedAddress = testSystem.getBindedAddress();
        smtpProtocol.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        authenticate(smtpProtocol);

        smtpProtocol.sendCommand("EHLO localhost");
        smtpProtocol.sendCommand("MAIL FROM: <bob@localhost> RET=HDRS");
        smtpProtocol.sendCommand("RCPT TO:<rcpt@localhost> NOTIFY=SUCCESS,FAILURE,DELAY ORCPT=rfc822;orcpt@localhost");
        smtpProtocol.sendShortMessageData("Subject: test mail\r\n\r\nTest body testSimpleMailSendWithDSN\r\n.\r\n");

        Mail lastMail = testSystem.queue.getLastMail();
        assertThat(lastMail.dsnParameters())
            .contains(DsnParameters.builder()
                .ret(DsnParameters.Ret.HDRS)
                .addRcptParameter(new MailAddress("rcpt@localhost"), DsnParameters.RecipientDsnParameters.of(
                    EnumSet.of(SUCCESS, FAILURE, DELAY),
                    new MailAddress("orcpt@localhost")
                )).build().get());
    }
}
