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

package org.apache.james.mailbox.store.mail.model.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.Optional;

import org.apache.james.mime4j.Charsets;
import org.apache.james.mime4j.dom.BinaryBody;
import org.apache.james.mime4j.dom.Disposable;
import org.apache.james.mime4j.dom.SingleBody;
import org.apache.james.mime4j.dom.TextBody;
import org.apache.james.mime4j.io.InputStreams;
import org.apache.james.mime4j.message.BasicBodyFactory;
import org.apache.james.mime4j.message.BodyFactory;
import org.apache.james.mime4j.util.ByteArrayOutputStreamRecycler;
import org.apache.james.mime4j.util.ContentUtil;
import org.apache.james.util.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.CountingOutputStream;
import com.google.common.io.FileBackedOutputStream;

/**
 * Factory for creating message bodies.
 */
public class FileBufferedBodyFactory implements BodyFactory, Disposable {

    public static final BasicBodyFactory INSTANCE = new BasicBodyFactory();
    public static final Logger LOGGER = LoggerFactory.getLogger(FileBufferedBodyFactory.class);
    public static final int FILE_THRESHOLD = Optional.ofNullable(System.getProperty("james.mime4j.buffered.body.factory.file.threshold"))
        .map(s -> Size.parse(s, Size.Unit.NoUnit))
        .map(s -> (int) s.asBytes())
        .orElse(100 * 1024);



    private final Charset defaultCharset;
    private final ArrayList<Disposable> disposables = new ArrayList<>();

    public FileBufferedBodyFactory() {
        this(true);
    }

    public FileBufferedBodyFactory(final Charset defaultCharset) {
        this.defaultCharset = defaultCharset;
    }

    public FileBufferedBodyFactory(final boolean lenient) {
        this(lenient ? Charset.defaultCharset() : null);
    }

    /**
     * @return the defaultCharset
     */
    public Charset getDefaultCharset() {
        return defaultCharset;
    }

    /**
     * <p>
     * Select the Charset for the given <code>mimeCharset</code> string.
     * </p>
     * <p>
     * If you need support for non standard or invalid <code>mimeCharset</code> specifications you might want to
     * create your own derived {@link BodyFactory} extending {@link BasicBodyFactory} and overriding this method as
     * suggested by <a href="https://issues.apache.org/jira/browse/MIME4J-218">MIME4J-218</a>
     * </p>
     * <p>
     * The default behavior is lenient, invalid <code>mimeCharset</code> specifications will return the
     * <code>defaultCharset</code>.
     * </p>
     *
     * @param mimeCharset - the string specification for a Charset e.g. "UTF-8"
     * @throws UnsupportedEncodingException if the mimeCharset is invalid
     */
    protected Charset resolveCharset(final String mimeCharset) throws UnsupportedEncodingException {
        if (mimeCharset != null) {
            try {
                return Charset.forName(mimeCharset);
            } catch (UnsupportedCharsetException ex) {
                if (defaultCharset == null) {
                    throw new UnsupportedEncodingException(mimeCharset);
                }
            } catch (IllegalCharsetNameException ex) {
                if (defaultCharset == null) {
                    throw new UnsupportedEncodingException(mimeCharset);
                }
            }
        }
        return defaultCharset;
    }

    public TextBody textBody(final String text, final String mimeCharset) throws UnsupportedEncodingException {
        if (text == null) {
            throw new IllegalArgumentException("Text may not be null");
        }
        return new StringBody1(text, resolveCharset(mimeCharset));
    }

    public TextBody textBody(final byte[] content, final Charset charset) {
        if (content == null) {
            throw new IllegalArgumentException("Content may not be null");
        }
        return new StringBody2(content, charset);
    }

    public TextBody textBody(final InputStream content, final String mimeCharset) throws IOException {
        if (content == null) {
            throw new IllegalArgumentException("Input stream may not be null");
        }
        return new StringBody3(ContentUtil.bufferEfficient(content), resolveCharset(mimeCharset));
    }

    public TextBody textBody(final String text, final Charset charset) {
        if (text == null) {
            throw new IllegalArgumentException("Text may not be null");
        }
        return new StringBody1(text, charset);
    }

    public TextBody textBody(final String text) {
        return textBody(text, Charsets.DEFAULT_CHARSET);
    }

    public BinaryBody binaryBody(final String content, final Charset charset) {
        if (content == null) {
            throw new IllegalArgumentException("Content may not be null");
        }
        return new BinaryBody2(content, charset);
    }

    public BinaryBody binaryBody(final InputStream is) throws IOException {
        try (FileBackedOutputStream out = new FileBackedOutputStream(FILE_THRESHOLD)) {
            disposables.add(() -> {
                try {
                    out.reset();
                } catch (IOException e) {
                    LOGGER.error("Cannot delete {}", out, e);
                }
            });
            CountingOutputStream countingOutputStream = new CountingOutputStream(out);
            is.transferTo(countingOutputStream);
            return new BinaryBody3(out, countingOutputStream.getCount());
        }
    }

    public BinaryBody binaryBody(final byte[] buf) {
        return new BinaryBody1(buf);
    }

    static class StringBody1 extends TextBody {

        private final String content;
        private final Charset charset;

        StringBody1(final String content, final Charset charset) {
            super();
            this.content = content;
            this.charset = charset;
        }

        @Override
        public String getMimeCharset() {
            return this.charset != null ? this.charset.name() : null;
        }

        @Override
        public Charset getCharset() {
            return charset;
        }

        @Override
        public Reader getReader() throws IOException {
            return new StringReader(this.content);
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return InputStreams.create(this.content,
                    this.charset != null ? this.charset : Charsets.DEFAULT_CHARSET);
        }

        @Override
        public void dispose() {
        }

        @Override
        public SingleBody copy() {
            return new StringBody1(this.content, this.charset);
        }

    }

    static class StringBody2 extends TextBody {

        private final byte[] content;
        private final Charset charset;

        StringBody2(final byte[] content, final Charset charset) {
            super();
            this.content = content;
            this.charset = charset;
        }

        @Override
        public String getMimeCharset() {
            return this.charset != null ? this.charset.name() : null;
        }

        @Override
        public Charset getCharset() {
            return charset;
        }

        @Override
        public Reader getReader() throws IOException {
            return new InputStreamReader(InputStreams.create(this.content), this.charset);
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return InputStreams.create(this.content);
        }

        @Override
        public void writeTo(OutputStream out) throws IOException {
            out.write(content);
        }

        @Override
        public long size() {
            return content.length;
        }

        @Override
        public void dispose() {
        }

        @Override
        public SingleBody copy() {
            return new StringBody2(this.content, this.charset);
        }

    }

    static class StringBody3 extends TextBody {

        private final ByteArrayOutputStreamRecycler.Wrapper content;
        private final Charset charset;

        StringBody3(final ByteArrayOutputStreamRecycler.Wrapper content, final Charset charset) {
            super();
            this.content = content;
            this.charset = charset;
        }

        @Override
        public String getMimeCharset() {
            return this.charset != null ? this.charset.name() : null;
        }

        @Override
        public Charset getCharset() {
            return charset;
        }

        @Override
        public Reader getReader() throws IOException {
            return new InputStreamReader(this.content.getValue().toInputStream(), this.charset);
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return this.content.getValue().toInputStream();
        }

        @Override
        public long size() {
            return content.getValue().size();
        }

        @Override
        public void writeTo(OutputStream out) throws IOException {
            content.getValue().writeTo(out);
        }

        @Override
        public void dispose() {
            this.content.release();
        }

        @Override
        public SingleBody copy() {
            return new StringBody3(this.content, this.charset);
        }

    }

    static class BinaryBody1 extends BinaryBody {

        private final byte[] content;

        BinaryBody1(final byte[] content) {
            this.content = content;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return InputStreams.create(this.content);
        }

        @Override
        public void writeTo(OutputStream out) throws IOException {
            out.write(content);
        }

        @Override
        public long size() {
            return content.length;
        }

        @Override
        public void dispose() {
        }

        @Override
        public SingleBody copy() {
            return new BinaryBody1(this.content);
        }

    }

    static class BinaryBody3 extends BinaryBody {

        private final FileBackedOutputStream data;
        private final long size;

        BinaryBody3(FileBackedOutputStream data, long size) {
            super();
            this.data = data;
            this.size = size;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return data.asByteSource().openStream();
        }

        @Override
        public void writeTo(OutputStream out) throws IOException {
            try (InputStream inputStream = getInputStream()) {
                inputStream.transferTo(out);
            }
        }

        @Override
        public long size() {
            return size;
        }

        @Override
        public void dispose() {
            try {
                data.close();
                data.reset();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public SingleBody copy() {
            return new BinaryBody3(this.data, this.size);
        }

    }

    static class BinaryBody2 extends BinaryBody {

        private final String content;
        private final Charset charset;

        BinaryBody2(final String content, final Charset charset) {
            super();
            this.content = content;
            this.charset = charset;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return InputStreams.create(this.content,
                    this.charset != null ? this.charset : Charsets.DEFAULT_CHARSET);
        }

        @Override
        public void dispose() {
        }

        @Override
        public SingleBody copy() {
            return new BinaryBody2(this.content, this.charset);
        }

    }

    @Override
    public void dispose() {
        disposables.forEach(Disposable::dispose);
    }
}
