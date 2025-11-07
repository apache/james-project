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

package org.apache.james.transport.mailets;

import static org.apache.james.transport.mailets.FoldLongLines.HEADER_SEPARATOR;
import static org.apache.james.transport.mailets.FoldLongLines.MAX_CHARACTERS_PARAMETER_NAME;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.mail.Header;
import jakarta.mail.MessagingException;

import org.apache.commons.lang3.stream.Streams;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.mailet.Mail;
import org.apache.mailet.Mailet;
import org.apache.mailet.MailetContext;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class FoldLongLinesTest {
    static final String HEADER_NAME = "References";
    static final String HEADER_VALUE = "<a1@gmailcom> <a2@gmailcom> <a3@gmailcom>";
    static final String FOLDED_LINE = "<a1@gmailcom> <a2@gmailcom>\r\n" +
        " <a3@gmailcom>";

    private Mailet foldMailet;
    private MailetContext mailetContext;

    @BeforeEach
    void beforeEach() {
        foldMailet = new FoldLongLines();
        mailetContext = FakeMailContext.defaultContext();
    }

    @Test
    void serviceShouldFoldLinesWhenTheyExceedMaxCharacters() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .mailetName("Test")
            .mailetContext(mailetContext)
            .setProperty(MAX_CHARACTERS_PARAMETER_NAME, String.valueOf(HEADER_NAME.length() + HEADER_SEPARATOR.length() + HEADER_VALUE.length() - 1))
            .build();
        foldMailet.init(mailetConfig);

        Mail mail = FakeMail.builder()
            .name("mail").mimeMessage(MimeMessageBuilder.mimeMessageBuilder().addHeader(HEADER_NAME, HEADER_VALUE).build())
            .build();
        foldMailet.service(mail);

        List<Header> headers = Streams.of(mail.getMessage().getAllHeaders()).filter(header -> header.getName().equals(HEADER_NAME)).toList();
        assertThat(headers).hasSize(1);
        assertThat(headers.getFirst().getValue()).isEqualTo(FOLDED_LINE);
    }

    @Test
    void serviceShouldFoldLinesWhenTheyExceedMaxCharactersAndTheHeaderHasMultiLines() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .mailetName("Test")
            .mailetContext(mailetContext)
            .setProperty(MAX_CHARACTERS_PARAMETER_NAME, "30")
            .build();
        foldMailet.init(mailetConfig);

        Mail mail = FakeMail.builder()
            .name("mail").mimeMessage(MimeMessageBuilder.mimeMessageBuilder().addHeader(HEADER_NAME, "<a1@gmailcom>\n<a2@gmailcom> <a3@gmailcom> <a4@gmailcom>").build())
            .build();
        foldMailet.service(mail);

        List<Header> headers = Streams.of(mail.getMessage().getAllHeaders()).filter(header -> header.getName().equals(HEADER_NAME)).toList();
        assertThat(headers).hasSize(1);
        assertThat(headers.getFirst().getValue()).isEqualTo("<a1@gmailcom><a2@gmailcom>\r\n" +
            " <a3@gmailcom> <a4@gmailcom>");
    }

    @Test
    void serviceShouldNotFoldLinesWhenTheyDoNotExceedMaxCharacters() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .mailetName("Test")
            .mailetContext(mailetContext)
            .setProperty(MAX_CHARACTERS_PARAMETER_NAME, "60")
            .build();
        foldMailet.init(mailetConfig);

        Mail mail = FakeMail.builder()
            .name("mail").mimeMessage(MimeMessageBuilder.mimeMessageBuilder().addHeader(HEADER_NAME, HEADER_VALUE).build())
            .build();
        foldMailet.service(mail);

        List<Header> headers = Streams.of(mail.getMessage().getAllHeaders()).filter(header -> header.getName().equals(HEADER_NAME)).toList();
        assertThat(headers).hasSize(1);
        assertThat(headers.getFirst().getValue()).isEqualTo(HEADER_VALUE);
    }

    @Test
    void serviceShouldNotFoldLinesWhenTheirLengthEqualToMaxCharacters() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .mailetName("Test")
            .mailetContext(mailetContext)
            .setProperty(MAX_CHARACTERS_PARAMETER_NAME, String.valueOf(HEADER_NAME.length() + HEADER_SEPARATOR.length() + HEADER_VALUE.length()))
            .build();
        foldMailet.init(mailetConfig);

        Mail mail = FakeMail.builder()
            .name("mail").mimeMessage(MimeMessageBuilder.mimeMessageBuilder().addHeader(HEADER_NAME, HEADER_VALUE).build())
            .build();
        foldMailet.service(mail);

        List<Header> headers = Streams.of(mail.getMessage().getAllHeaders()).filter(header -> header.getName().equals(HEADER_NAME)).toList();
        assertThat(headers).hasSize(1);
        assertThat(headers.getFirst().getValue()).isEqualTo(HEADER_VALUE);
    }

    @Test
    void serviceShouldNotRemoveTheHeaderThatHasTheSameNameAsHeadersWithLongLine() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .mailetName("Test")
            .mailetContext(mailetContext)
            .setProperty(MAX_CHARACTERS_PARAMETER_NAME, "40")
            .build();
        foldMailet.init(mailetConfig);

        Mail mail = FakeMail.builder()
            .name("mail").mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .addHeader(HEADER_NAME, "<b1@gmailcom>")
                .addHeader(HEADER_NAME, HEADER_VALUE)
                .build()
            )
            .build();
        foldMailet.service(mail);

        List<Header> headers = Streams.of(mail.getMessage().getAllHeaders()).filter(header -> header.getName().equals(HEADER_NAME)).toList();
        assertThat(headers).hasSize(2);
        SoftAssertions.assertSoftly(softly -> {
            assertThat(headers.getFirst().getValue()).isEqualTo("<b1@gmailcom>");
            assertThat(headers.getLast().getValue()).isEqualTo(FOLDED_LINE);
        });
    }

    @Test
    void shouldBeIdempotent() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .mailetName("Test")
            .mailetContext(mailetContext)
            .setProperty(MAX_CHARACTERS_PARAMETER_NAME, "40")
            .build();
        foldMailet.init(mailetConfig);

        Mail mail = FakeMail.builder()
            .name("mail").mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .addHeader(HEADER_NAME, "a".repeat(40) + "\r\n a")
                .build())
            .build();
        foldMailet.service(mail);

        assertThat(mail.getMessage().getHeader(HEADER_NAME)[0])
            .isEqualTo("a".repeat(40) + "\r\n a");
    }

    @Test
    void shouldTolerateLongerFoldedLines() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .mailetName("Test")
            .mailetContext(mailetContext)
            .setProperty(MAX_CHARACTERS_PARAMETER_NAME, "40")
            .build();
        foldMailet.init(mailetConfig);

        Mail mail = FakeMail.builder()
            .name("mail").mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .addHeader(HEADER_NAME, "a a".repeat(20) + "\r\n a")
                .build())
            .build();
        foldMailet.service(mail);

        assertThat(mail.getMessage().getHeader(HEADER_NAME)[0])
            .isEqualTo("a aa aa aa aa aa aa aa aa aa\r\n" +
                " aa aa aa aa aa aa aa aa aa aa a a");
    }

    @Test
    void serviceShouldNotChangeTheRelativePositionOfTheHeaderThatHasTheSameNameAsHeadersWithLongLine() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .mailetName("Test")
            .mailetContext(mailetContext)
            .setProperty(MAX_CHARACTERS_PARAMETER_NAME, "30")
            .build();
        foldMailet.init(mailetConfig);

        Mail mail = FakeMail.builder()
            .name("mail").mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .addHeader(HEADER_NAME, HEADER_VALUE)
                .addHeader(HEADER_NAME, "<b1@gmailcom>")
                .addHeader(HEADER_NAME, HEADER_VALUE)
                .build()
            )
            .build();
        foldMailet.service(mail);

        List<Header> headers = Streams.of(mail.getMessage().getAllHeaders()).filter(header -> header.getName().equals(HEADER_NAME)).toList();
        assertThat(headers).hasSize(3);
        assertThat(headers.get(1).getValue()).isEqualTo("<b1@gmailcom>");
    }
}
