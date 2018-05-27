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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

import org.apache.james.mailbox.extractor.ParsedContent;
import org.apache.james.mailbox.extractor.TextExtractor;
import org.junit.Before;
import org.junit.Test;
import org.testcontainers.shaded.com.google.common.collect.ImmutableMap;

public class CachingTextExtractorTest {

    public static final ParsedContent RESULT = new ParsedContent("content", ImmutableMap.of());
    public static final Supplier<InputStream> INPUT_STREAM_1 = () -> new ByteArrayInputStream("content1".getBytes(StandardCharsets.UTF_8));

    private TextExtractor textExtractor;
    private TextExtractor wrappedTextExtractor;

    @Before
    public void setUp() throws Exception {
        wrappedTextExtractor = mock(TextExtractor.class);
        textExtractor = new CachingTextExtractor(wrappedTextExtractor,
            TikaConfiguration.DEFAULT_CACHE_EVICTION_PERIOD,
            TikaConfiguration.DEFAULT_CACHE_LIMIT_100_MB);

        when(wrappedTextExtractor.extractContent(any(), any()))
            .thenReturn(RESULT);
    }

    @Test
    public void extractContentShouldCallUnderlyingTextExtractor() throws Exception {
        textExtractor.extractContent(INPUT_STREAM_1.get(), "application/bytes");

        verify(wrappedTextExtractor, times(1)).extractContent(any(), any());
        verifyNoMoreInteractions(wrappedTextExtractor);
    }

    @Test
    public void extractContentShouldAvoidCallingUnderlyingTextExtractorWhenPossible() throws Exception {
        textExtractor.extractContent(INPUT_STREAM_1.get(), "application/bytes");
        textExtractor.extractContent(INPUT_STREAM_1.get(), "application/bytes");

        verify(wrappedTextExtractor, times(1)).extractContent(any(), any());
        verifyNoMoreInteractions(wrappedTextExtractor);
    }

    @Test
    public void extractContentShouldPropagateCheckedException() throws Exception {
        IOException ioException = new IOException("Any");
        when(wrappedTextExtractor.extractContent(any(), any()))
            .thenThrow(ioException);

        assertThatThrownBy(() -> textExtractor.extractContent(INPUT_STREAM_1.get(), "application/bytes"))
            .isEqualTo(ioException);
    }

    @Test
    public void extractContentShouldPropagateRuntimeException() throws Exception {
        RuntimeException runtimeException = new RuntimeException("Any");
        when(wrappedTextExtractor.extractContent(any(), any()))
            .thenThrow(runtimeException);

        assertThatThrownBy(() -> textExtractor.extractContent(INPUT_STREAM_1.get(), "application/bytes"))
            .isEqualTo(runtimeException);
    }

}