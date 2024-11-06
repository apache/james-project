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

import static org.apache.james.transport.mailets.FoldLongLines.MAX_CHARACTERS_PARAMETER_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

public class FoldLongLinesTest {
    static final String HEADER_NAME = "References";
    static final String HEADER_VALUE = "<a1@gmailcom> <a2@gmailcom> <a3@gmailcom>";
    static final String FOLDED_LINE = "<a1@gmailcom>\r\n" +
        " <a2@gmailcom> <a3@gmailcom>";

    private Mailet foldMailet;
    private MailetContext mailetContext;

    @BeforeEach
    void beforeEach() throws MessagingException {
        foldMailet = new FoldLongLines();
        mailetContext = FakeMailContext.builder()
            .logger(mock(Logger.class))
            .build();
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .mailetName("Test")
            .mailetContext(mailetContext)
            .setProperty(MAX_CHARACTERS_PARAMETER_NAME, "30")
            .build();
        foldMailet.init(mailetConfig);
    }

    @Test
    void serviceShouldFoldLinesWhenTheyExceedMaxCharacters() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .mailetName("Test")
            .mailetContext(mailetContext)
            .setProperty(MAX_CHARACTERS_PARAMETER_NAME, "30")
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
        assertThat(headers.getFirst().getValue()).isEqualTo(HEADER_VALUE);
    }
}
