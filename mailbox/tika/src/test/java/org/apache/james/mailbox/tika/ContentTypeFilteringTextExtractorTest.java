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

package org.apache.james.mailbox.tika;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.apache.james.mailbox.extractor.ParsedContent;
import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.model.ContentType;
import org.apache.james.mailbox.model.ContentType.MimeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.google.common.collect.ImmutableSet;

class ContentTypeFilteringTextExtractorTest {

    @Mock
    TextExtractor textExtractor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    void extractContentReturnEmptyWithContentTypeInBlacklist() throws Exception {
        ContentTypeFilteringTextExtractor contentTypeFilteringTextExtractor =
            new ContentTypeFilteringTextExtractor(textExtractor,
                ImmutableSet.of(MimeType.of("application/ics"), MimeType.of("application/zip")));

        assertThat(contentTypeFilteringTextExtractor
            .extractContent(IOUtils.toInputStream("", StandardCharsets.UTF_8), ContentType.of("application/ics")))
            .isEqualTo(ParsedContent.empty());
        verifyNoMoreInteractions(textExtractor);
    }

    @Test
    void extractContentShouldIgnoreContentTypeParameters() throws Exception {
        ContentTypeFilteringTextExtractor contentTypeFilteringTextExtractor =
            new ContentTypeFilteringTextExtractor(textExtractor,
                ImmutableSet.of(MimeType.of("application/ics"), MimeType.of("application/zip")));

        assertThat(contentTypeFilteringTextExtractor
            .extractContent(IOUtils.toInputStream("", StandardCharsets.UTF_8), ContentType.of("application/ics; charset=utf-8")))
            .isEqualTo(ParsedContent.empty());
        verifyNoMoreInteractions(textExtractor);
    }

    @Test
    void extractContentCallUnderlyingWithContentTypeNotInBlacklist() throws Exception {
        InputStream inputStream = ClassLoader.getSystemResourceAsStream("documents/Text.txt");
        ContentTypeFilteringTextExtractor contentTypeFilteringTextExtractor =
            new ContentTypeFilteringTextExtractor(textExtractor,
                ImmutableSet.of(MimeType.of("application/ics"), MimeType.of("application/zip")));
        contentTypeFilteringTextExtractor.extractContent(inputStream, ContentType.of("text/plain"));

        verify(textExtractor, times(1)).extractContent(any(), any());
    }
}
