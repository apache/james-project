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

package org.apache.mailet.base;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.mail.BodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

import org.apache.commons.io.IOUtils;
import org.apache.james.util.MimeMessageUtil;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.github.fge.lambdas.Throwing;

class Converter7BitTest {

    private static final String CONTENT_TRANSFER_ENCODING = "Content-Transfer-Encoding";
    private static final String X_MIME_AUTOCONVERTED = "X-MIME-Autoconverted";
    private static final String QUOTED_PRINTABLE = "quoted-printable";
    private static final String MESSAGE_BODY_8BIT = "A 8bit encoded body with €uro symbol.";
    private static final String MESSAGE_BODY_QUOTED_PRINTABLE = "A 8bit encoded body with =E2=82=ACuro symbol.";
    private static final String BASE64 = "base64";

    private Converter7Bit testee;

    @BeforeEach
    void setUp() {
        testee = new Converter7Bit(FakeMailetConfig.builder()
            .build()
            .getMailetContext());
    }

    @Nested
    class When7BitMail {
        @Nested
        class WhenTextContent {
            @Test
            void convertTo7BitShouldKeepMessageContentUnTouched() throws Exception {
                MimeMessage mimeMessage = MimeMessageUtil.mimeMessageFromString(
                        fileContent("eml/text-only-7bit.eml"));
                testee.convertTo7Bit(mimeMessage);

                SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                    softly.assertThat(mimeMessage.getEncoding())
                        .isEqualTo(QUOTED_PRINTABLE);
                    softly.assertThat(MimeMessageUtil.asString(mimeMessage))
                        .contains(MESSAGE_BODY_QUOTED_PRINTABLE);
                }));
            }
        }

        @Nested
        class WhenMultipart {
            @Test
            void convertTo7BitShouldKeepMessageTextContentUnTouched() throws Exception {
                MimeMessage mimeMessage = MimeMessageUtil.mimeMessageFromString(
                        fileContent("eml/multipart-7bit.eml"));

                testee.convertTo7Bit(mimeMessage);
                MimeMultipart multipart = (MimeMultipart) mimeMessage.getContent();

                SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                    softly.assertThat(multipart.getBodyPart(0).getHeader(CONTENT_TRANSFER_ENCODING))
                        .containsOnly(QUOTED_PRINTABLE);
                    softly.assertThat(MimeMessageUtil.asString(mimeMessage))
                        .contains(MESSAGE_BODY_QUOTED_PRINTABLE);
                }));
            }

            @Test
            void convertTo7BitShouldKeepMessageAttachmentsContentUnTouched() throws Exception {
                MimeMessage mimeMessage = MimeMessageUtil.mimeMessageFromString(
                        fileContent("eml/multipart-7bit.eml"));

                testee.convertTo7Bit(mimeMessage);
                MimeMultipart multipart = (MimeMultipart) mimeMessage.getContent();
                String messageAsString = MimeMessageUtil.asString(mimeMessage);

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
            void convertTo7BitShouldAlertHeaders() throws Exception {
                MimeMessage mimeMessage = MimeMessageUtil.mimeMessageFromString(
                        fileContent("eml/text-only-8bit.eml"));
                testee.convertTo7Bit(mimeMessage);

                SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                   assertThat(mimeMessage.getHeader(CONTENT_TRANSFER_ENCODING))
                       .containsOnly(QUOTED_PRINTABLE);
                   assertThat(mimeMessage.getHeader(X_MIME_AUTOCONVERTED))
                       .containsOnly("from 8bit to quoted-printable by Mock Server");
                }));
            }

            @Test
            void convertTo7BitShouldConvertContentToQuotedPrintable() throws Exception {
                MimeMessage mimeMessage = MimeMessageUtil.mimeMessageFromString(
                        fileContent("eml/text-only-8bit.eml"));
                testee.convertTo7Bit(mimeMessage);

                assertThat(MimeMessageUtil.asString(mimeMessage))
                    .contains(MESSAGE_BODY_QUOTED_PRINTABLE)
                    .doesNotContain(MESSAGE_BODY_8BIT);
            }
        }

        @Nested
        class WhenMultipart {

            @Test
            void convertTo7BitShouldAlertTextPartHeaders() throws Exception {
                MimeMessage mimeMessage = MimeMessageUtil.mimeMessageFromString(
                        fileContent("eml/multipart-8bit.eml"));

                testee.convertTo7Bit(mimeMessage);

                BodyPart textPart = ((MimeMultipart) mimeMessage.getContent())
                    .getBodyPart(0);

                SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                   assertThat(textPart.getHeader(CONTENT_TRANSFER_ENCODING))
                       .containsOnly(QUOTED_PRINTABLE);
                   assertThat(textPart.getHeader(X_MIME_AUTOCONVERTED))
                       .containsOnly("from 8bit to quoted-printable by Mock Server");
                }));
            }

            @Test
            void convertTo7BitShouldConvertTextPartContentToQuotedPrintable() throws Exception {
                MimeMessage mimeMessage = MimeMessageUtil.mimeMessageFromString(
                        fileContent("eml/multipart-8bit.eml"));

                testee.convertTo7Bit(mimeMessage);

                assertThat(MimeMessageUtil.asString(mimeMessage))
                    .contains(MESSAGE_BODY_QUOTED_PRINTABLE)
                    .doesNotContain(MESSAGE_BODY_8BIT);
            }

            @Test
            void convertTo7BitShouldKeepAttachmentPartUnTouchedWhenBase64Encoding() throws Exception {
                MimeMessage mimeMessage = MimeMessageUtil.mimeMessageFromString(
                        fileContent("eml/multipart-8bit.eml"));

                testee.convertTo7Bit(mimeMessage);

                MimeMultipart multipart = (MimeMultipart) mimeMessage.getContent();
                String messageAsString = MimeMessageUtil.asString(mimeMessage);

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