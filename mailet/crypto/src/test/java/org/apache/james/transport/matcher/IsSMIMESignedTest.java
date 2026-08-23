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

package org.apache.james.transport.matcher;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.mailet.base.MailAddressFixture.ANY_AT_JAMES;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.util.Properties;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import org.apache.mailet.Mail;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMatcherConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IsSMIMESignedTest {
    private IsSMIMESigned testee;

    @BeforeEach
    void setUp() throws Exception {
        testee = new IsSMIMESigned();
        testee.init(FakeMatcherConfig.builder()
            .matcherName("IsSMIMESigned")
            .build());
    }

    @Test
    void shouldMatchMultipartSignedWithPkcs7Protocol() throws Exception {
        assertThat(testee.match(mailWithContentType(
                "multipart/signed; protocol=\"application/pkcs7-signature\"; micalg=sha-256; boundary=\"bound\"")))
            .containsOnly(ANY_AT_JAMES);
    }

    @Test
    void shouldMatchMultipartSignedWithLegacyPkcs7Protocol() throws Exception {
        assertThat(testee.match(mailWithContentType(
                "multipart/signed; protocol=\"application/x-pkcs7-signature\"; micalg=sha1; boundary=\"bound\"")))
            .containsOnly(ANY_AT_JAMES);
    }

    @Test
    void shouldMatchWhenProtocolCaseDiffers() throws Exception {
        assertThat(testee.match(mailWithContentType(
                "multipart/signed; protocol=\"Application/PKCS7-Signature\"; boundary=\"bound\"")))
            .containsOnly(ANY_AT_JAMES);
    }

    @Test
    void shouldNotMatchPgpMimeSignedMail() throws Exception {
        assertThat(testee.match(mailWithContentType(
                "multipart/signed; protocol=\"application/pgp-signature\"; micalg=pgp-sha256; boundary=\"bound\"")))
            .isNull();
    }

    @Test
    void shouldNotMatchMultipartSignedWithoutProtocol() throws Exception {
        assertThat(testee.match(mailWithContentType("multipart/signed; boundary=\"bound\"")))
            .isNull();
    }

    @Test
    void shouldNotMatchMultipartMixed() throws Exception {
        assertThat(testee.match(mailWithContentType("multipart/mixed; boundary=\"bound\"")))
            .isNull();
    }

    @Test
    void shouldMatchPkcs7SignatureMail() throws Exception {
        assertThat(testee.match(mailWithContentType("application/pkcs7-signature")))
            .containsOnly(ANY_AT_JAMES);
    }

    @Test
    void shouldMatchPkcs7MimeSignedData() throws Exception {
        assertThat(testee.match(mailWithContentType("application/pkcs7-mime; smime-type=signed-data; name=smime.p7m")))
            .containsOnly(ANY_AT_JAMES);
    }

    @Test
    void shouldNotMatchPkcs7MimeEnvelopedData() throws Exception {
        assertThat(testee.match(mailWithContentType("application/pkcs7-mime; smime-type=enveloped-data; name=smime.p7m")))
            .isNull();
    }

    @Test
    void shouldNotMatchTextPlain() throws Exception {
        assertThat(testee.match(mailWithContentType("text/plain; charset=UTF-8")))
            .isNull();
    }

    private Mail mailWithContentType(String contentType) throws Exception {
        String message = "Subject: any\r\n"
            + "Content-Type: " + contentType + "\r\n"
            + "\r\n"
            + "--bound\r\n"
            + "Content-Type: text/plain\r\n"
            + "\r\n"
            + "content\r\n"
            + "--bound--\r\n";

        return FakeMail.builder()
            .name("mail")
            .recipient(ANY_AT_JAMES)
            .mimeMessage(new MimeMessage(Session.getInstance(new Properties()),
                new ByteArrayInputStream(message.getBytes(UTF_8))))
            .build();
    }
}
