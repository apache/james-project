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

package org.apache.james.jmap.mime4j;

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

import org.apache.james.mime4j.Charsets;
import org.apache.james.mime4j.dom.BinaryBody;
import org.apache.james.mime4j.dom.SingleBody;
import org.apache.james.mime4j.dom.TextBody;
import org.apache.james.mime4j.io.InputStreams;
import org.apache.james.mime4j.message.BasicBodyFactory;
import org.apache.james.mime4j.message.BodyFactory;
import org.apache.james.mime4j.util.ByteArrayOutputStreamRecycler;
import org.apache.james.mime4j.util.ContentUtil;

/**
 * Factory for creating message bodies.
 */
public class AvoidBinaryBodyReadingBodyFactory implements BodyFactory {

    public static final BasicBodyFactory INSTANCE = new BasicBodyFactory();

    private final Charset defaultCharset;

    public AvoidBinaryBodyReadingBodyFactory() {
        this(true);
    }

    public AvoidBinaryBodyReadingBodyFactory(final Charset defaultCharset) {
        this.defaultCharset = defaultCharset;
    }

    public AvoidBinaryBodyReadingBodyFactory(final boolean lenient) {
        this(lenient ? Charset.defaultCharset() : null);
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
            } catch (UnsupportedCharsetException | IllegalCharsetNameException ex) {
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


    public BinaryBody binaryBody(final InputStream is) {
        return new FakeBinaryBody(-1);
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
}
