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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import org.apache.james.mailbox.extractor.ParsedContent;
import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.model.ContentType;
import org.apache.james.metrics.api.NoopGaugeRegistry;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Strings;

import reactor.core.publisher.Mono;

class CachingTextExtractorTest {

    private static final ParsedContent RESULT = ParsedContent.of("content");
    public static final String BIG_STRING = Strings.repeat("0123456789", 103 * 1024);
    private static final ParsedContent _2MiB_RESULT = ParsedContent.of(BIG_STRING);
    private static final Function<Integer, InputStream> STREAM_GENERATOR =
        i -> new ByteArrayInputStream(String.format("content%d", i).getBytes(StandardCharsets.UTF_8));
    private static final Supplier<InputStream> INPUT_STREAM = () -> STREAM_GENERATOR.apply(1);
    private static final long CACHE_LIMIT_10_MiB = 10 * 1024 * 1024;
    private static final ContentType CONTENT_TYPE = ContentType.of("application/bytes");

    private CachingTextExtractor textExtractor;
    private TextExtractor wrappedTextExtractor;

    @BeforeEach
    void setUp() {
        wrappedTextExtractor = mock(TextExtractor.class);
        textExtractor = new CachingTextExtractor(wrappedTextExtractor,
            TikaConfiguration.DEFAULT_CACHE_EVICTION_PERIOD,
            CACHE_LIMIT_10_MiB,
            new RecordingMetricFactory(),
            new NoopGaugeRegistry());

        when(wrappedTextExtractor.extractContentReactive(any(), any()))
            .thenReturn(Mono.just(RESULT));
    }

    @Test
    void extractContentShouldCallUnderlyingTextExtractor() throws Exception {
        textExtractor.extractContent(INPUT_STREAM.get(), CONTENT_TYPE);

        verify(wrappedTextExtractor, times(1)).extractContentReactive(any(), any());
        verifyNoMoreInteractions(wrappedTextExtractor);
    }

    @Test
    void extractContentShouldAvoidCallingUnderlyingTextExtractorWhenPossible() throws Exception {
        textExtractor.extractContent(INPUT_STREAM.get(), CONTENT_TYPE);
        textExtractor.extractContent(INPUT_STREAM.get(), CONTENT_TYPE);

        verify(wrappedTextExtractor, times(1)).extractContentReactive(any(), any());
        verifyNoMoreInteractions(wrappedTextExtractor);
    }

    @Test
    void extractContentShouldPropagateCheckedException() {
        IOException ioException = new IOException("Any");
        when(wrappedTextExtractor.extractContentReactive(any(), any()))
            .thenReturn(Mono.error(ioException));

        assertThatThrownBy(() -> textExtractor.extractContent(INPUT_STREAM.get(), CONTENT_TYPE))
            .hasCause(ioException);
    }

    @Test
    void extractContentShouldPropagateRuntimeException() {
        RuntimeException runtimeException = new RuntimeException("Any");
        when(wrappedTextExtractor.extractContentReactive(any(), any()))
            .thenReturn(Mono.error(runtimeException));

        assertThatThrownBy(() -> textExtractor.extractContent(INPUT_STREAM.get(), CONTENT_TYPE))
            .isEqualTo(runtimeException);
    }

    @Test
    void frequentlyAccessedEntriesShouldBePreservedByEviction() throws Exception {
        when(wrappedTextExtractor.extractContent(any(), any()))
            .thenReturn(_2MiB_RESULT);

        IntStream.range(0, 10)
            .mapToObj(STREAM_GENERATOR::apply)
            .peek(Throwing.consumer(any -> textExtractor.extractContent(STREAM_GENERATOR.apply(0), CONTENT_TYPE)))
            .forEach(Throwing.consumer(inputStream -> textExtractor.extractContent(inputStream, CONTENT_TYPE)));

        reset(wrappedTextExtractor);

        textExtractor.extractContent(STREAM_GENERATOR.apply(0), CONTENT_TYPE);

        verifyNoMoreInteractions(wrappedTextExtractor);
    }

    @RepeatedTest(10)
    void concurrentValueComputationShouldNotLeadToDuplicatedBackendAccess() throws Exception {
        ConcurrentTestRunner.builder()
            .operation((a, b) -> textExtractor.extractContent(INPUT_STREAM.get(), CONTENT_TYPE))
            .threadCount(10)
            .runSuccessfullyWithin(Duration.ofMinutes(1));

        verify(wrappedTextExtractor, times(1)).extractContentReactive(any(), any());
    }

}