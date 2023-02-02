package org.apache.james.mailbox.extractor;

import org.apache.james.mailbox.model.ContentType;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

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
}