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

package org.apache.james.transport.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import jakarta.mail.internet.MimeMessage;

import org.apache.commons.io.IOUtils;
import org.apache.james.util.MimeMessageUtil;
import org.apache.james.util.html.HtmlTextExtractor;
import org.junit.Before;
import org.junit.Test;

public class MimeMessageBodyGeneratorTest {

    private MimeMessageBodyGenerator mimeMessageBodyGenerator;
    private HtmlTextExtractor htmlTextExtractor;
    private MimeMessage original;

    @Before
    public void setUp() {
        original = MimeMessageUtil.defaultMimeMessage();
        htmlTextExtractor = mock(HtmlTextExtractor.class);
        mimeMessageBodyGenerator = new MimeMessageBodyGenerator(htmlTextExtractor);
    }

    @Test
    public void fromShouldNotWriteAMultipartWhenOnlyPlainText() throws Exception {
        assertThat(IOUtils.toString(
            mimeMessageBodyGenerator.from(original,
                Optional.of("Plain text"),
                Optional.empty())
                .getInputStream(), StandardCharsets.UTF_8))
            .isEqualTo("Plain text");
        verifyNoMoreInteractions(htmlTextExtractor);
    }

    @Test
    public void fromShouldPreservePreviouslySetHeaders() throws Exception {
        String subject = "Important, I should be kept";
        original.setHeader("Subject", subject);

        mimeMessageBodyGenerator.from(original,
            Optional.of("Plain text"),
            Optional.empty())
            .getInputStream();

        assertThat(original.getSubject()).isEqualTo(subject);
        verifyNoMoreInteractions(htmlTextExtractor);
    }

    @Test
    public void fromShouldProvideAPlainTextVersionWhenOnlyHtml() throws Exception {
        String htmlText = "<p>HTML text</p>";
        String plainText = "Plain text";
        when(htmlTextExtractor.toPlainText(htmlText)).thenReturn(plainText);

        String rowContent = IOUtils.toString(
            mimeMessageBodyGenerator.from(original,
                Optional.empty(),
                Optional.of(htmlText))
                .getInputStream(), StandardCharsets.UTF_8);

        assertThat(rowContent).containsSequence(htmlText);
        assertThat(rowContent).containsSequence(plainText);
    }

    @Test
    public void fromShouldProvideAPlainTextVersionWhenHtmlAndEmptyText() throws Exception {
        String htmlText = "<p>HTML text</p>";
        String plainText = "Plain text";
        when(htmlTextExtractor.toPlainText(htmlText)).thenReturn(plainText);

        String rowContent = IOUtils.toString(
            mimeMessageBodyGenerator.from(original,
                    Optional.of(""),
                    Optional.of(htmlText))
                .getInputStream(), StandardCharsets.UTF_8);

        assertThat(rowContent).containsSequence(htmlText);
        assertThat(rowContent).containsSequence(plainText);
    }

    @Test
    public void fromShouldCombinePlainTextAndHtml() throws Exception {
        String htmlText = "<p>HTML text</p>";
        String plainText = "Plain text";

        String rowContent = IOUtils.toString(
            mimeMessageBodyGenerator.from(original,
                Optional.of(plainText),
                Optional.of(htmlText))
                .getInputStream(), StandardCharsets.UTF_8);

        assertThat(rowContent).containsSequence(htmlText);
        assertThat(rowContent).containsSequence(plainText);
        verifyNoMoreInteractions(htmlTextExtractor);
    }

    @Test
    public void fromShouldUseEmptyTextWhenNoPlainTextNorHtmlBody() throws Exception {
        String rowContent = IOUtils.toString(mimeMessageBodyGenerator.from(original,
            Optional.empty(),
            Optional.empty())
            .getInputStream(), StandardCharsets.UTF_8);

        assertThat(rowContent).isEmpty();
    }

    @Test
    public void fromShouldThrowOnNullPlainText() throws Exception {
        assertThatThrownBy(() -> mimeMessageBodyGenerator.from(original,
            null,
            Optional.empty()))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void fromShouldThrowOnNullHtml() throws Exception {
        assertThatThrownBy(() -> mimeMessageBodyGenerator.from(original,
            Optional.empty(),
            null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void fromShouldThrowOnNullMimeMessageToDecorate() throws Exception {
        assertThatThrownBy(() -> mimeMessageBodyGenerator.from(null,
            Optional.empty(),
            Optional.empty()))
            .isInstanceOf(NullPointerException.class);
    }

}
