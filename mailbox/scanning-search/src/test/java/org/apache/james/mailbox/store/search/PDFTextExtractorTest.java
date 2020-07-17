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

package org.apache.james.mailbox.store.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.james.mailbox.model.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PDFTextExtractorTest {

    PDFTextExtractor testee;

    @BeforeEach
    void setUp() {
        testee = new PDFTextExtractor();
    }

    @Test
    void extractContentShouldThrowWhenNullInputStream() {
        assertThatThrownBy(() ->
            testee.extractContent(null, ContentType.of("any/any")))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void extractContentShouldThrowWhenNullContentType() {
        InputStream inputStream = new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> testee.extractContent(inputStream, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void extractContentShouldExtractPlainText() throws Exception {
        String content = "content";
        InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        assertThat(testee.extractContent(inputStream, ContentType.of("text/plain"))
            .getTextualContent())
            .contains(content);
    }

    @Test
    void extractContentShouldExtractPDF() throws Exception {
        String content = "Little PDF\n";
        InputStream inputStream = ClassLoader.getSystemResourceAsStream("pdf.pdf");

        assertThat(testee.extractContent(inputStream, ContentType.of(PDFTextExtractor.PDF_TYPE))
            .getTextualContent())
            .contains(content);
    }

}