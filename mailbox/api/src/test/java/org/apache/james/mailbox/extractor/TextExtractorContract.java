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
package org.apache.james.mailbox.extractor;

import org.apache.james.mailbox.model.ContentType;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchException;
import static org.mockito.Mockito.*;

public interface TextExtractorContract {

    TextExtractor testee();
    ContentType supportedContentType();

    byte[] supportedContent();

    @Test
    default void extractContentShouldCloseInputStreamOnSuccess() throws Exception {
        InputStream stream = spy(new ByteArrayInputStream(supportedContent()));

        testee().extractContent(stream, supportedContentType());

        verify(stream).close();
    }

    @Test
    default void extractContentShouldCloseInputStreamOnException() throws Exception {
        InputStream stream = mock(InputStream.class);

        when(stream.read(any(), anyInt(), anyInt())).thenThrow(new IOException(""));

        catchException(() -> testee().extractContent(stream, supportedContentType()));

        verify(stream).close();
    }

    @Test
    default void extractContentReactiveShouldCloseInputStreamOnSuccess() throws Exception {
        InputStream stream = spy(new ByteArrayInputStream(supportedContent()));

        testee().extractContentReactive(stream, supportedContentType()).block();

        verify(stream).close();
    }

    @Test
    default void extractContentReactiveShouldCloseInputStreamOnException() throws Exception {
        InputStream stream = mock(InputStream.class);

        when(stream.read(any(), anyInt(), anyInt())).thenThrow(new IOException(""));

        catchException(() -> testee().extractContentReactive(stream, supportedContentType()).block());

        verify(stream).close();
    }

    @Test
    default void applicableShouldReturnFalseOnNull() {
        assertThat(testee().applicable(null)).isFalse();
    }
}