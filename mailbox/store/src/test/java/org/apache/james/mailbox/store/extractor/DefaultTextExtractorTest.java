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

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.extractor.TextExtractorContract;
import org.apache.james.mailbox.model.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultTextExtractorTest implements TextExtractorContract {
    TextExtractor textExtractor;

    @Override
    public TextExtractor testee() {
        return textExtractor;
    }

    @Override
    public ContentType supportedContentType() {
        return ContentType.of("text/plain");
    }

    @Override
    public byte[] supportedContent() {
        return "foo".getBytes(StandardCharsets.UTF_8);
    }

    @BeforeEach
    void setUp() {
        textExtractor = new DefaultTextExtractor();
    }

    @Test
    void textTest() throws Exception {
        InputStream inputStream = ClassLoader.getSystemResourceAsStream("documents/Text.txt");
        assertThat(inputStream).isNotNull();
        assertThat(textExtractor.extractContent(inputStream, ContentType.of("text/plain"))
            .getTextualContent())
            .contains("This is some awesome text text.\n\n");
    }

    @Test
    void extractContentShouldTakeIntoAccountCharset() throws Exception {
        InputStream inputStream = ClassLoader.getSystemResourceAsStream("documents/simple-text-iso-8859-1.txt");
        assertThat(inputStream).isNotNull();
        assertThat(textExtractor.extractContent(inputStream, ContentType.of("text/plain; charset=ISO-8859-1"))
            .getTextualContent())
            .contains("\"é\" This text is not UTF-8 \"à\"");
    }

    @Test
    void textMicrosoftWorldTest() throws Exception {
        InputStream inputStream = ClassLoader.getSystemResourceAsStream("documents/writter.docx");
        assertThat(inputStream).isNotNull();
        assertThat(textExtractor.extractContent(
            inputStream,
            ContentType.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
            .getTextualContent())
            .isEmpty();
    }
}
