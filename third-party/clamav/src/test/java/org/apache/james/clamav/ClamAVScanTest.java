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

package org.apache.james.clamav;

import static org.apache.james.clamav.ClamAVScan.INFECTED_HEADER_NAME;
import static org.apache.james.clamav.ClamAVScan.INFECTED_MAIL_ATTRIBUTE_NAME;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.apache.james.server.core.MimeMessageInputStream;
import org.apache.james.util.MimeMessageUtil;
import org.apache.mailet.Mail;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClamAVScanTest {
    private static final DockerClamAV dockerClamAV = new DockerClamAV();

    private ClamAVScan clamAVScan;

    @BeforeEach
    public void setUp() throws MessagingException {
        clamAVScan = new ClamAVScan();
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .mailetName("Test")
            .setProperty("host", "localhost")
            .setProperty("port", dockerClamAV.getPort().toString())
            .build();
        clamAVScan.init(mailetConfig);
    }

    @BeforeAll
    static void setup() {
        dockerClamAV.start();
    }

    @AfterAll
    static void teardown() {
        dockerClamAV.stop();
    }

    @Test
    void testHasVirusFunctionForVirusMail() throws Exception {
        MimeMessage mimeMessage = MimeMessageUtil.mimeMessageFromStream(
            ClassLoader.getSystemResourceAsStream("attachment/inlineVirusTextAttachment.eml"));
        InputStream mimeMessageInputStream = new MimeMessageInputStream(mimeMessage);

        assertThat(clamAVScan.hasVirus(mimeMessageInputStream)).isTrue();
    }

    @Test
    void testHasVirusFunctionForNonVirusMail() throws Exception {
        MimeMessage mimeMessage = MimeMessageUtil.mimeMessageFromStream(
            ClassLoader.getSystemResourceAsStream("attachment/inlineNonVirusTextAttachment.eml"));
        InputStream mimeMessageInputStream = new MimeMessageInputStream(mimeMessage);

        assertThat(clamAVScan.hasVirus(mimeMessageInputStream)).isFalse();
    }

    @Test
    void mailetShouldMarkInfectedHeaderAndMailAttributeAsTrueWhenMailHasVirus() throws Exception {
        MimeMessage mimeMessage = MimeMessageUtil.mimeMessageFromStream(
            ClassLoader.getSystemResourceAsStream("attachment/inlineVirusTextAttachment.eml"));
        Mail mail = FakeMail.builder()
            .name("name")
            .recipient("user1@example.com")
            .mimeMessage(mimeMessage)
            .build();

        clamAVScan.service(mail);

        assertThat(mail.getAttribute(INFECTED_MAIL_ATTRIBUTE_NAME).get().getValue().value()).isEqualTo(true);
        assertThat(mail.getMessage().getHeader(INFECTED_HEADER_NAME)[0]).isEqualTo("true");
    }

    @Test
    void mailetShouldMarkInfectedHeaderAndMailAttributeAsFalseWhenMailHasNoVirus() throws Exception {
        MimeMessage mimeMessage = MimeMessageUtil.mimeMessageFromStream(
            ClassLoader.getSystemResourceAsStream("attachment/inlineNonVirusTextAttachment.eml"));
        Mail mail = FakeMail.builder()
            .name("name")
            .recipient("user1@example.com")
            .mimeMessage(mimeMessage)
            .build();

        clamAVScan.service(mail);

        assertThat(mail.getAttribute(INFECTED_MAIL_ATTRIBUTE_NAME).get().getValue().value()).isEqualTo(false);
        assertThat(mail.getMessage().getHeader(INFECTED_HEADER_NAME)[0]).isEqualTo("false");
    }

    @Test
    void mailedShouldWorkWellWhenScanningContinuousMails() throws Exception {
        MimeMessage virusMimeMessage = MimeMessageUtil.mimeMessageFromStream(
            ClassLoader.getSystemResourceAsStream("attachment/inlineVirusTextAttachment.eml"));
        Mail virusMail = FakeMail.builder()
            .name("name")
            .recipient("user1@example.com")
            .mimeMessage(virusMimeMessage)
            .build();

        MimeMessage nonVirusMimeMessage = MimeMessageUtil.mimeMessageFromStream(
            ClassLoader.getSystemResourceAsStream("attachment/inlineNonVirusTextAttachment.eml"));
        Mail nonVirusMail = FakeMail.builder()
            .name("name")
            .recipient("user1@example.com")
            .mimeMessage(nonVirusMimeMessage)
            .build();

        clamAVScan.service(virusMail);
        clamAVScan.service(nonVirusMail);

        assertThat(virusMail.getMessage().getHeader(INFECTED_HEADER_NAME)[0]).isEqualTo("true");
        assertThat(nonVirusMail.getMessage().getHeader(INFECTED_HEADER_NAME)[0]).isEqualTo("false");
    }

    @Test
    void mailetShouldNotMarkHeaderAndMailAttributeAndJustLogErrorWhenCanNotConnectToClamAV() throws Exception {
        MimeMessage mimeMessage = MimeMessageUtil.mimeMessageFromStream(
            ClassLoader.getSystemResourceAsStream("attachment/inlineVirusTextAttachment.eml"));
        Mail mail = FakeMail.builder()
            .name("name")
            .recipient("user1@example.com")
            .mimeMessage(mimeMessage)
            .build();

        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .mailetName("Test")
            .setProperty("host", "localhost")
            .setProperty("port", "0")
            .setProperty("maxPings", "0")
            .build();
        clamAVScan.init(mailetConfig);
        clamAVScan.service(mail);

        assertThat(mail.getAttribute(INFECTED_MAIL_ATTRIBUTE_NAME)).isEmpty();
        assertThat(mail.getMessage().getHeader(INFECTED_HEADER_NAME)).isNull();
    }
}
