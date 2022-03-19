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

package org.apache.james.transport.matchers;

import static org.apache.mailet.base.RFC2822Headers.CONTENT_TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.mail.internet.MimeMessage;

import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.mailet.Mail;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMatcherConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HasMimeTypeTest {

    private static final String RECIPIENT = "test@james.apache.org";
    private static final String FROM = "test@james.apache.org";
    private static final String MIME_TYPES = "multipart/mixed";
    private static final String NON_MATCHING_MIME_TYPES = "text/plain, application/zip";

    private HasMimeType matcher;

    @BeforeEach
    void setUp() throws Exception {
        matcher = new HasMimeType();
    }

    @Test
    void hasMimeType() throws Exception {
        matcher.init(FakeMatcherConfig.builder()
                .matcherName("HasMimeType")
                .condition(MIME_TYPES)
                .build());

        MimeMessageBuilder message = MimeMessageBuilder.mimeMessageBuilder()
            .setMultipartWithBodyParts(
                MimeMessageBuilder.bodyPartBuilder()
                    .data("simple text")
                    .disposition("text"),
                MimeMessageBuilder.bodyPartBuilder()
                    .filename("text_file.txt")
                    .disposition("attachment"),
                MimeMessageBuilder.bodyPartBuilder()
                    .type("application/zip")
                    .filename("zip_file.zip")
                    .disposition("attachment"))
            .setSubject("test");

        Mail mail = FakeMail.builder()
            .name("mail")
            .mimeMessage(message)
            .sender(FROM)
            .recipient(RECIPIENT)
            .build();

        assertThat(matcher.match(mail)).containsAll(mail.getRecipients());
    }

    @Test
    void doesNotHaveMimeType() throws Exception {
        matcher.init(FakeMatcherConfig.builder()
                .matcherName("HasMimeType")
                .condition(NON_MATCHING_MIME_TYPES)
                .build());

        MimeMessageBuilder message = MimeMessageBuilder.mimeMessageBuilder()
            .setMultipartWithBodyParts(
                MimeMessageBuilder.bodyPartBuilder()
                    .data("simple text")
                    .disposition("text"),
                MimeMessageBuilder.bodyPartBuilder()
                    .filename("text_file.txt")
                    .disposition("attachment"),
                MimeMessageBuilder.bodyPartBuilder()
                    .type("image/png")
                    .filename("file.png")
                    .disposition("attachment"))
            .setSubject("test");

        Mail mail = FakeMail.builder()
            .name("mail")
            .mimeMessage(message)
            .sender(FROM)
            .recipient(RECIPIENT)
            .build();

        assertThat(matcher.match(mail)).isEmpty();
    }

    @Test
    void shouldNotMatchSubParts() throws Exception {
        matcher.init(FakeMatcherConfig.builder()
                .matcherName("HasMimeType")
                .condition("application/zip")
                .build());

        MimeMessageBuilder message = MimeMessageBuilder.mimeMessageBuilder()
            .setMultipartWithBodyParts(
                MimeMessageBuilder.bodyPartBuilder()
                    .data("simple text")
                    .disposition("text"),
                MimeMessageBuilder.bodyPartBuilder()
                    .filename("text_file.txt")
                    .disposition("attachment"),
                MimeMessageBuilder.bodyPartBuilder()
                    .type("application/zip")
                    .filename("zip_file.zip")
                    .disposition("attachment"))
            .setSubject("test");

        Mail mail = FakeMail.builder()
            .name("mail")
            .mimeMessage(message)
            .sender(FROM)
            .recipient(RECIPIENT)
            .build();

        assertThat(matcher.match(mail)).isEmpty();
    }

    @Test
    void matchShouldReturnRecipientsWhenAtLeastOneMimeTypeMatch() throws Exception {
        matcher.init(FakeMatcherConfig.builder()
            .matcherName("HasMimeType")
            .condition("text/md, text/html")
            .build());

        MimeMessageBuilder message = MimeMessageBuilder.mimeMessageBuilder()
            .setText("content <b>in</b> <i>HTML</i>", "text/html")
            .setSubject("test");

        Mail mail = FakeMail.builder()
            .name("mail")
            .mimeMessage(message)
            .sender(FROM)
            .recipient(RECIPIENT)
            .build();

        assertThat(matcher.match(mail)).containsExactlyElementsOf(mail.getRecipients());
    }

    @Test
    void matchShouldNotFailOnEmptyCharset() throws Exception {
        matcher.init(FakeMatcherConfig.builder()
            .matcherName("HasMimeType")
            .condition("text/html")
            .build());

        MimeMessage message = mock(MimeMessage.class);
        when(message.getHeader(CONTENT_TYPE)).thenReturn(new String[] {"text/html; charset="});

        Mail mail = FakeMail.builder()
            .name("mail")
            .mimeMessage(message)
            .sender(FROM)
            .recipient(RECIPIENT)
            .build();

        assertThat(matcher.match(mail)).containsExactlyElementsOf(mail.getRecipients());
    }
}
