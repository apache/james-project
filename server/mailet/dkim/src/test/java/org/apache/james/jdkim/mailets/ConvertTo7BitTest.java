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

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.mail.BodyPart;
import javax.mail.internet.MimeMultipart;

import org.apache.commons.io.IOUtils;
import org.apache.james.util.MimeMessageUtil;
import org.apache.mailet.Mail;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.github.fge.lambdas.Throwing;

class ConvertTo7BitTest {

    private static final String CONTENT_TRANSFER_ENCODING = "Content-Transfer-Encoding";
    private static final String X_MIME_AUTOCONVERTED = "X-MIME-Autoconverted";
    private static final String QUOTED_PRINTABLE = "quoted-printable";
    private static final String MESSAGE_BODY_8BIT = "A 8bit encoded body with €uro symbol.";
    private static final String MESSAGE_BODY_QUOTED_PRINTABLE = "A 8bit encoded body with =E2=82=ACuro symbol.";
    private static final String BASE64 = "base64";

    private ConvertTo7Bit testee;

    @BeforeEach
    void setUp() throws Exception {
        testee = new ConvertTo7Bit();
        testee.init(FakeMailetConfig.builder()
            .build());
    }

    @Nested
    class When7BitMail {
        @Nested
        class WhenTextContent {
            @Test
            void serviceShouldKeepMessageContentUnTouch() throws Exception {
                Mail mail = FakeMail.builder()
                    .name("a-mail-with-quoted-printable-encoding")
                    .mimeMessage(MimeMessageUtil.mimeMessageFromString(
                        fileContent("eml/text-only-7bit.eml")))
                    .build();

                testee.service(mail);

                SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                    softly.assertThat(mail.getMessage().getEncoding())
                        .isEqualTo(QUOTED_PRINTABLE);
                    softly.assertThat(MimeMessageUtil.asString(mail.getMessage()))
                        .contains(MESSAGE_BODY_QUOTED_PRINTABLE);
                }));
            }
        }

        @Nested
        class WhenMultipart {
            @Test
            void serviceShouldKeepMessageTextContentUnTouch() throws Exception {
                Mail mail = FakeMail.builder()
                    .name("a-mail-with-7bit-encoding")
                    .mimeMessage(MimeMessageUtil.mimeMessageFromString(
                        fileContent("eml/multipart-7bit.eml")))
                    .build();

                testee.service(mail);
                MimeMultipart multipart = (MimeMultipart) mail.getMessage().getContent();

                SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                    softly.assertThat(multipart.getBodyPart(0).getHeader(CONTENT_TRANSFER_ENCODING))
                        .containsOnly(QUOTED_PRINTABLE);
                    softly.assertThat(MimeMessageUtil.asString(mail.getMessage()))
                        .contains(MESSAGE_BODY_QUOTED_PRINTABLE);
                }));
            }

            @Test
            void serviceShouldKeepMessageAttachmentsContentUnTouch() throws Exception {
                Mail mail = FakeMail.builder()
                    .name("a-mail-with-7bit-encoding")
                    .mimeMessage(MimeMessageUtil.mimeMessageFromString(
                        fileContent("eml/multipart-7bit.eml")))
                    .build();

                testee.service(mail);
                MimeMultipart multipart = (MimeMultipart) mail.getMessage().getContent();
                String messageAsString = MimeMessageUtil.asString(mail.getMessage());

                SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                    softly.assertThat(multipart.getBodyPart(1).getHeader(CONTENT_TRANSFER_ENCODING))
                        .containsOnly(BASE64);
                    softly.assertThat(messageAsString)
                        .contains(fileContent("eml/multipart-7bit-attachment-content.txt"));
                }));
            }
        }
    }

    @Nested
    class When8BitMail {
        @Nested
        class WhenTextContent {
            @Test
            void serviceShouldAlertHeaders() throws Exception {
                Mail mail = FakeMail.builder()
                    .name("a-mail-with-8bit-encoding")
                    .mimeMessage(MimeMessageUtil.mimeMessageFromString(
                        fileContent("eml/text-only-8bit.eml")))
                    .build();
                testee.service(mail);

                SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                   assertThat(mail.getMessage().getHeader(CONTENT_TRANSFER_ENCODING))
                       .containsOnly(QUOTED_PRINTABLE);
                   assertThat(mail.getMessage().getHeader(X_MIME_AUTOCONVERTED))
                       .containsOnly("from 8bit to quoted-printable by Mock Server");
                }));
            }

            @Test
            void serviceShouldConvertContentToQuotedPrintable() throws Exception {
                Mail mail = FakeMail.builder()
                    .name("a-mail-with-8bit-encoding")
                    .mimeMessage(MimeMessageUtil.mimeMessageFromString(
                        fileContent("eml/text-only-8bit.eml")))
                    .build();
                testee.service(mail);

                assertThat(MimeMessageUtil.asString(mail.getMessage()))
                    .contains(MESSAGE_BODY_QUOTED_PRINTABLE)
                    .doesNotContain(MESSAGE_BODY_8BIT);
            }
        }

        @Nested
        class WhenMultipart {

            @Test
            void serviceShouldAlertTextPartHeaders() throws Exception {
                Mail mail = FakeMail.builder()
                    .name("a-mail-with-8bit-encoding")
                    .mimeMessage(MimeMessageUtil.mimeMessageFromString(
                        fileContent("eml/multipart-8bit.eml")))
                    .build();

                testee.service(mail);

                BodyPart textPart = ((MimeMultipart) mail.getMessage().getContent())
                    .getBodyPart(0);

                SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                   assertThat(textPart.getHeader(CONTENT_TRANSFER_ENCODING))
                       .containsOnly(QUOTED_PRINTABLE);
                   assertThat(textPart.getHeader(X_MIME_AUTOCONVERTED))
                       .containsOnly("from 8bit to quoted-printable by Mock Server");
                }));
            }

            @Test
            void serviceShouldConvertTextPartContentToQuotedPrintable() throws Exception {
                Mail mail = FakeMail.builder()
                    .name("a-mail-with-8bit-encoding")
                    .mimeMessage(MimeMessageUtil.mimeMessageFromString(
                        fileContent("eml/multipart-8bit.eml")))
                    .build();

                testee.service(mail);

                assertThat(MimeMessageUtil.asString(mail.getMessage()))
                    .contains(MESSAGE_BODY_QUOTED_PRINTABLE)
                    .doesNotContain(MESSAGE_BODY_8BIT);
            }

            @Test
            void serviceShouldKeepAttachmentPartUnTouchWhenBase64Encoding() throws Exception {
                Mail mail = FakeMail.builder()
                    .name("a-mail-with-8bit-encoding")
                    .mimeMessage(MimeMessageUtil.mimeMessageFromString(
                        fileContent("eml/multipart-8bit.eml")))
                    .build();

                testee.service(mail);

                MimeMultipart multipart = (MimeMultipart) mail.getMessage().getContent();
                String messageAsString = MimeMessageUtil.asString(mail.getMessage());

                SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                    softly.assertThat(multipart.getBodyPart(1).getHeader(CONTENT_TRANSFER_ENCODING))
                        .containsOnly(BASE64);
                    softly.assertThat(messageAsString)
                        .contains(fileContent("eml/multipart-8bit-attachment-content.txt"));
                }));
            }
        }
    }

    private String fileContent(String fileName) throws IOException {
        return IOUtils.toString(
            ClassLoader.getSystemResourceAsStream(fileName),
            StandardCharsets.UTF_8);
    }
}