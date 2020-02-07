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

package org.apache.james.mailbox.store.extractor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.james.mailbox.extractor.ParsedContent;
import org.apache.james.mailbox.extractor.TextExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class JsoupTextExtractorTest {

    private static final String TEXT_HTML_CONTENT_TYPE = "text/html";

    TextExtractor textExtractor;

    @BeforeEach
    void setUp() {
        textExtractor = new JsoupTextExtractor();
    }

    @Test
    void extractedTextFromHtmlShouldNotContainTheContentOfTitleTag() throws Exception {
        InputStream inputStream = ClassLoader.getSystemResourceAsStream("documents/html.txt");

        assertThat(textExtractor.extractContent(inputStream, TEXT_HTML_CONTENT_TYPE).getTextualContent().get())
                .doesNotContain("*|MC:SUBJECT|*");
    }

    @Test
    void extractContentShouldHandlePlainText() throws Exception {
        InputStream inputStream = new ByteArrayInputStream("myText".getBytes(StandardCharsets.UTF_8));

        assertThat(textExtractor.extractContent(inputStream, "text/plain").getTextualContent())
                .contains("myText");
    }

    @Test
    void extractContentShouldHandleArbitraryTextMediaType() throws Exception {
        InputStream inputStream = new ByteArrayInputStream("myText".getBytes(StandardCharsets.UTF_8));

        assertThat(textExtractor.extractContent(inputStream, "text/arbitrary").getTextualContent())
                .isEmpty();
    }

    @Test
    void extractContentShouldReturnEmptyWhenNullData() throws Exception {
        assertThat(textExtractor.extractContent(null, TEXT_HTML_CONTENT_TYPE))
            .isEqualTo(ParsedContent.empty());
    }

    @Test
    void extractContentShouldReturnEmptyWhenNullContentType() throws Exception {
        InputStream inputStream = ClassLoader.getSystemResourceAsStream("documents/html.txt");

        assertThat(textExtractor.extractContent(inputStream, null))
            .isEqualTo(ParsedContent.empty());
    }

    @Disabled("JAMES-3044 java.io.IOException: Input is binary and unsupported")
    @Test
    void extractContentShouldNotThrowWhenContainingNullCharacters() {
        InputStream inputStream = textContentWithManyNullCharacters();

        assertThatCode(() -> textExtractor.extractContent(inputStream, TEXT_HTML_CONTENT_TYPE))
            .doesNotThrowAnyException();
    }

    private InputStream textContentWithManyNullCharacters() {
        String htmlTextContent = "HTML pages can include a lot of null '\0' character. But still expecting the content can be parsed." +
            "Jsoup 1.21.1 thinks a file containing more than 10 null characters can be a binary file";
        byte[] htmlBytesContent = htmlTextContent.getBytes(StandardCharsets.UTF_8);
        byte[] nullCharacters = {'\0', '\0', '\0', '\0', '\0', '\0', '\0', '\0', '\0', '\0', '\0'};

        byte[] fullContent = new byte[htmlBytesContent.length + nullCharacters.length];
        System.arraycopy(htmlBytesContent, 0, fullContent, 0, htmlBytesContent.length);
        System.arraycopy(nullCharacters, 0, fullContent, htmlBytesContent.length, nullCharacters.length);

        return new ByteArrayInputStream(fullContent);
    }
}