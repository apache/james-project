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

package org.apache.james.jdkim.mailets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import javax.mail.Address;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMessage.RecipientType;

import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.jdkim.api.SignatureRecord;
import org.apache.james.jdkim.exceptions.FailException;
import org.apache.james.jdkim.exceptions.PermFailException;
import org.apache.james.server.core.JamesServerResourceLoader;
import org.apache.james.server.core.filesystem.FileSystemImpl;
import org.apache.james.util.MimeMessageUtil;
import org.apache.mailet.Mail;
import org.apache.mailet.Mailet;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DKIMSignTest {
    private static final String PKCS1_PEM_FILE = "classpath://test-dkim-pkcs1.pem";
    private static final String PKCS8_PEM_FILE = "classpath://test-dkim-pkcs8.pem";
    private static final FakeMailContext FAKE_MAIL_CONTEXT = FakeMailContext.defaultContext();

    FileSystem fileSystem;

    @BeforeEach
    void setUp() {
        fileSystem = new FileSystemImpl(new JamesServerResourceLoader("../testsFileSystemExtension/" + UUID.randomUUID()));
    }

    @ParameterizedTest
    @ValueSource(strings = {PKCS1_PEM_FILE, PKCS8_PEM_FILE})
    void testDKIMSign(String pemFile) throws MessagingException, IOException,
            FailException {
        String message = "Received: by 10.XX.XX.12 with SMTP id dfgskldjfhgkljsdfhgkljdhfg;\r\n\tTue, 06 Oct 2009 07:37:34 -0700 (PDT)\r\nReturn-Path: <bounce@example.com>\r\nReceived: from example.co.uk (example.co.uk [XX.XXX.125.19])\r\n\tby mx.example.com with ESMTP id dgdfgsdfgsd.97.2009.10.06.07.37.32;\r\n\tTue, 06 Oct 2009 07:37:32 -0700 (PDT)\r\nFrom: apache@bago.org\r\nTo: apache@bago.org\r\n\r\nbody\r\nprova\r\n";

        Mailet mailet = new DKIMSign(fileSystem);

        FakeMailetConfig mci = FakeMailetConfig.builder()
                .mailetName("Test")
                .mailetContext(FAKE_MAIL_CONTEXT)
                .setProperty(
                        "signatureTemplate",
                        "v=1; s=selector; d=example.com; h=from:to:received:received; a=rsa-sha256; bh=; b=;")
                .setProperty("privateKeyFilepath", pemFile)
                .build();

        mailet.init(mci);

        Mail mail = FakeMail.builder()
            .name("test")
            .mimeMessage(new MimeMessage(Session
                .getDefaultInstance(new Properties()),
                new ByteArrayInputStream(message.getBytes())))
            .build();

        mailet.service(mail);

        Mailet m7bit = new ConvertTo7Bit();
        m7bit.init(mci);
        m7bit.service(mail);

        ByteArrayOutputStream rawMessage = new ByteArrayOutputStream();
        mail.getMessage().writeTo(rawMessage);

        MockPublicKeyRecordRetriever mockPublicKeyRecordRetriever = new MockPublicKeyRecordRetriever(
                "v=DKIM1; k=rsa; p=MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDYDaYKXzwVYwqWbLhmuJ66aTAN8wmDR+rfHE8HfnkSOax0oIoTM5zquZrTLo30870YMfYzxwfB6j/Nz3QdwrUD/t0YMYJiUKyWJnCKfZXHJBJ+yfRHr7oW+UW3cVo9CG2bBfIxsInwYe175g9UjyntJpWueqdEIo1c2bhv9Mp66QIDAQAB;",
                "selector", "example.com");
        verify(rawMessage, mockPublicKeyRecordRetriever);
    }

    private List<SignatureRecord> verify(ByteArrayOutputStream rawMessage,
                                         MockPublicKeyRecordRetriever mockPublicKeyRecordRetriever)
            throws MessagingException, FailException {
        List<SignatureRecord> signs = new DKIMVerifier(mockPublicKeyRecordRetriever)
            .verifyUsingCRLF(MimeMessageUtil.mimeMessageFromStream(
                new ByteArrayInputStream(rawMessage.toByteArray())));
        assertThat(signs).hasSize(1);

        return signs;
    }

    @ParameterizedTest
    @ValueSource(strings = {PKCS1_PEM_FILE, PKCS8_PEM_FILE})
    void testDKIMSignFuture(String pemFile) throws MessagingException, IOException,
            FailException {
        String message = "Received: by 10.XX.XX.12 with SMTP id dfgskldjfhgkljsdfhgkljdhfg;\r\n\tTue, 06 Oct 2009 07:37:34 -0700 (PDT)\r\nReturn-Path: <bounce@example.com>\r\nReceived: from example.co.uk (example.co.uk [XX.XXX.125.19])\r\n\tby mx.example.com with ESMTP id dgdfgsdfgsd.97.2009.10.06.07.37.32;\r\n\tTue, 06 Oct 2009 07:37:32 -0700 (PDT)\r\nFrom: apache@bago.org\r\nTo: apache@bago.org\r\n\r\nbody\r\nprova\r\n";

        Mailet mailet = new DKIMSign(fileSystem);

        FakeMailetConfig mci = FakeMailetConfig.builder()
                .mailetName("Test")
                .mailetContext(FAKE_MAIL_CONTEXT)
                .setProperty(
                        "signatureTemplate",
                        "v=1; t=" + ((System.currentTimeMillis() / 1000) + 1000) + "; s=selector; d=example.com; h=from:to:received:received; a=rsa-sha256; bh=; b=;")
                .setProperty("privateKeyFilepath", pemFile)
                .build();

        mailet.init(mci);

        Mail mail = FakeMail.builder()
            .name("test")
            .mimeMessage(new MimeMessage(Session
                .getDefaultInstance(new Properties()),
                new ByteArrayInputStream(message.getBytes())))
            .build();

        mailet.service(mail);

        Mailet m7bit = new ConvertTo7Bit();
        m7bit.init(mci);
        m7bit.service(mail);

        ByteArrayOutputStream rawMessage = new ByteArrayOutputStream();
        mail.getMessage().writeTo(rawMessage);

        MockPublicKeyRecordRetriever mockPublicKeyRecordRetriever = new MockPublicKeyRecordRetriever(
                "v=DKIM1; k=rsa; p=MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDYDaYKXzwVYwqWbLhmuJ66aTAN8wmDR+rfHE8HfnkSOax0oIoTM5zquZrTLo30870YMfYzxwfB6j/Nz3QdwrUD/t0YMYJiUKyWJnCKfZXHJBJ+yfRHr7oW+UW3cVo9CG2bBfIxsInwYe175g9UjyntJpWueqdEIo1c2bhv9Mp66QIDAQAB;",
                "selector", "example.com");
        try {
            verify(rawMessage, mockPublicKeyRecordRetriever);
            fail("Expecting signature to be ignored");
        } catch (PermFailException e) {
            // signature ignored, so fail for missing signatures.
        }
    }


    @ParameterizedTest
    @ValueSource(strings = {PKCS1_PEM_FILE, PKCS8_PEM_FILE})
    void testDKIMSignTime(String pemFile) throws MessagingException, IOException,
            FailException {
        String message = "Received: by 10.XX.XX.12 with SMTP id dfgskldjfhgkljsdfhgkljdhfg;\r\n\tTue, 06 Oct 2009 07:37:34 -0700 (PDT)\r\nReturn-Path: <bounce@example.com>\r\nReceived: from example.co.uk (example.co.uk [XX.XXX.125.19])\r\n\tby mx.example.com with ESMTP id dgdfgsdfgsd.97.2009.10.06.07.37.32;\r\n\tTue, 06 Oct 2009 07:37:32 -0700 (PDT)\r\nFrom: apache@bago.org\r\nTo: apache@bago.org\r\n\r\nbody\r\nprova\r\n";

        Mailet mailet = new DKIMSign(fileSystem);

        FakeMailetConfig mci = FakeMailetConfig.builder()
                .mailetName("Test")
                .mailetContext(FAKE_MAIL_CONTEXT)
                .setProperty(
                        "signatureTemplate",
                        "v=1; t=; s=selector; d=example.com; h=from:to:received:received; a=rsa-sha256; bh=; b=;")
                .setProperty("privateKeyFilepath", pemFile)
                .build();

        mailet.init(mci);

        Mail mail = FakeMail.builder()
            .name("test")
            .mimeMessage(new MimeMessage(Session
                .getDefaultInstance(new Properties()),
                new ByteArrayInputStream(message.getBytes())))
            .build();

        mailet.service(mail);

        Mailet m7bit = new ConvertTo7Bit();
        m7bit.init(mci);
        m7bit.service(mail);

        ByteArrayOutputStream rawMessage = new ByteArrayOutputStream();
        mail.getMessage().writeTo(rawMessage);

        MockPublicKeyRecordRetriever mockPublicKeyRecordRetriever = new MockPublicKeyRecordRetriever(
                "v=DKIM1; k=rsa; p=MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDYDaYKXzwVYwqWbLhmuJ66aTAN8wmDR+rfHE8HfnkSOax0oIoTM5zquZrTLo30870YMfYzxwfB6j/Nz3QdwrUD/t0YMYJiUKyWJnCKfZXHJBJ+yfRHr7oW+UW3cVo9CG2bBfIxsInwYe175g9UjyntJpWueqdEIo1c2bhv9Mp66QIDAQAB;",
                "selector", "example.com");
        verify(rawMessage, mockPublicKeyRecordRetriever);

        List<SignatureRecord> rs = verify(rawMessage, mockPublicKeyRecordRetriever);

        // check we have a valued signatureTimestamp
        assertThat(rs.get(0).getSignatureTimestamp()).isNotNull();
        long ref = System.currentTimeMillis() / 1000;
        // Chech that the signature timestamp is in the past 60 seconds.
        assertThat(rs.get(0).getSignatureTimestamp() <= ref).isTrue();
        assertThat(rs.get(0).getSignatureTimestamp() >= ref - 60).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {PKCS1_PEM_FILE, PKCS8_PEM_FILE})
    void testDKIMSignMessageAsText(String pemFile) throws MessagingException,
            IOException, FailException {
        MimeMessage mm = new MimeMessage(Session
                .getDefaultInstance(new Properties()));
        mm.addFrom(new Address[]{new InternetAddress("io@bago.org")});
        mm.addRecipient(RecipientType.TO, new InternetAddress("io@bago.org"));
        mm.setText("An 8bit encoded body with €uro symbol.", "ISO-8859-15");

        Mailet mailet = new DKIMSign(fileSystem);

        FakeMailetConfig mci = FakeMailetConfig.builder()
                .mailetName("Test")
                .mailetContext(FAKE_MAIL_CONTEXT)
                .setProperty(
                        "signatureTemplate",
                        "v=1; s=selector; d=example.com; h=from:to:received:received; a=rsa-sha256; bh=; b=;")
                .setProperty("privateKeyFilepath", pemFile)
                .build();

        mailet.init(mci);

        Mail mail = FakeMail.builder()
            .name("test")
            .mimeMessage(mm)
            .build();

        Mailet m7bit = new ConvertTo7Bit();
        m7bit.init(mci);

        mailet.service(mail);

        m7bit.service(mail);

        ByteArrayOutputStream rawMessage = new ByteArrayOutputStream();
        mail.getMessage().writeTo(rawMessage);

        MockPublicKeyRecordRetriever mockPublicKeyRecordRetriever = new MockPublicKeyRecordRetriever(
                "v=DKIM1; k=rsa; p=MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDYDaYKXzwVYwqWbLhmuJ66aTAN8wmDR+rfHE8HfnkSOax0oIoTM5zquZrTLo30870YMfYzxwfB6j/Nz3QdwrUD/t0YMYJiUKyWJnCKfZXHJBJ+yfRHr7oW+UW3cVo9CG2bBfIxsInwYe175g9UjyntJpWueqdEIo1c2bhv9Mp66QIDAQAB;",
                "selector", "example.com");

        verify(rawMessage, mockPublicKeyRecordRetriever);
    }

    @ParameterizedTest
    @ValueSource(strings = {PKCS1_PEM_FILE, PKCS8_PEM_FILE})
    void testDKIMSignMessageAsObjectConvertedTo7Bit(String pemFile)
            throws MessagingException, IOException, FailException {
        MimeMessage mm = new MimeMessage(Session
                .getDefaultInstance(new Properties()));
        mm.addFrom(new Address[]{new InternetAddress("io@bago.org")});
        mm.addRecipient(RecipientType.TO, new InternetAddress("io@bago.org"));
        mm.setContent("An 8bit encoded body with €uro symbol.",
                "text/plain; charset=iso-8859-15");
        mm.setHeader("Content-Transfer-Encoding", "8bit");
        mm.saveChanges();

        FAKE_MAIL_CONTEXT.getServerInfo();

        FakeMailetConfig mci = FakeMailetConfig.builder()
                .mailetName("Test")
                .mailetContext(FAKE_MAIL_CONTEXT)
                .setProperty(
                        "signatureTemplate",
                        "v=1; s=selector; d=example.com; h=from:to:received:received; a=rsa-sha256; bh=; b=;")
                .setProperty("privateKeyFilepath", pemFile)
                .build();

        Mail mail = FakeMail.builder()
            .name("test")
            .mimeMessage(mm)
            .build();

        Mailet mailet = new DKIMSign(fileSystem);
        mailet.init(mci);

        Mailet m7bit = new ConvertTo7Bit();
        m7bit.init(mci);
        m7bit.service(mail);

        mailet.service(mail);

        m7bit.service(mail);

        ByteArrayOutputStream rawMessage = new ByteArrayOutputStream();
        mail.getMessage().writeTo(rawMessage);

        MockPublicKeyRecordRetriever mockPublicKeyRecordRetriever = new MockPublicKeyRecordRetriever(
                "v=DKIM1; k=rsa; p=MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDYDaYKXzwVYwqWbLhmuJ66aTAN8wmDR+rfHE8HfnkSOax0oIoTM5zquZrTLo30870YMfYzxwfB6j/Nz3QdwrUD/t0YMYJiUKyWJnCKfZXHJBJ+yfRHr7oW+UW3cVo9CG2bBfIxsInwYe175g9UjyntJpWueqdEIo1c2bhv9Mp66QIDAQAB;",
                "selector", "example.com");
        verify(rawMessage, mockPublicKeyRecordRetriever);
    }

    @ParameterizedTest
    @ValueSource(strings = {PKCS1_PEM_FILE, PKCS8_PEM_FILE})
    void testDKIMSignMessageAsObjectNotConverted(String pemFile)
            throws MessagingException, IOException, FailException {
        MimeMessage mm = new MimeMessage(Session
                .getDefaultInstance(new Properties()));
        mm.addFrom(new Address[]{new InternetAddress("io@bago.org")});
        mm.addRecipient(RecipientType.TO, new InternetAddress("io@bago.org"));
        mm.setContent("An 8bit encoded body with €uro symbol.",
                "text/plain; charset=iso-8859-15");
        mm.setHeader("Content-Transfer-Encoding", "8bit");
        mm.saveChanges();

        FAKE_MAIL_CONTEXT.getServerInfo();

        FakeMailetConfig mci = FakeMailetConfig.builder()
                .mailetName("Test")
                .mailetContext(FAKE_MAIL_CONTEXT)
                .setProperty(
                        "signatureTemplate",
                        "v=1; s=selector; d=example.com; h=from:to:received:received; a=rsa-sha256; bh=; b=;")
                .setProperty("privateKeyFilepath", pemFile)
                .build();

        Mail mail = FakeMail.builder()
            .name("test")
            .mimeMessage(mm)
            .build();

        Mailet mailet = new DKIMSign(fileSystem);
        mailet.init(mci);

        Mailet m7bit = new ConvertTo7Bit();
        m7bit.init(mci);
        // m7bit.service(mail);

        mailet.service(mail);

        m7bit.service(mail);

        ByteArrayOutputStream rawMessage = new ByteArrayOutputStream();
        mail.getMessage().writeTo(rawMessage);

        MockPublicKeyRecordRetriever mockPublicKeyRecordRetriever = new MockPublicKeyRecordRetriever(
                "v=DKIM1; k=rsa; p=MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDYDaYKXzwVYwqWbLhmuJ66aTAN8wmDR+rfHE8HfnkSOax0oIoTM5zquZrTLo30870YMfYzxwfB6j/Nz3QdwrUD/t0YMYJiUKyWJnCKfZXHJBJ+yfRHr7oW+UW3cVo9CG2bBfIxsInwYe175g9UjyntJpWueqdEIo1c2bhv9Mp66QIDAQAB;",
                "selector", "example.com");
        try {
            verify(rawMessage, mockPublicKeyRecordRetriever);
            fail("Expected PermFail");
        } catch (PermFailException e) {
            // do nothing
        }
    }

}
