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

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.CharBuffer;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;

import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.stream.EntityState;
import org.apache.james.mime4j.stream.MimeConfig;
import org.apache.james.mime4j.stream.MimeTokenStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Searches an email for content. This class should be safe for use by
 * concurrent threads.
 */
public class MessageSearcher {

    private Logger logger;

    private CharSequence searchContent = null;

    private boolean isCaseInsensitive = false;

    private boolean includeHeaders = false;

    public MessageSearcher() {
    }

    public MessageSearcher(CharSequence searchContent,
            boolean isCaseInsensitive, boolean includeHeaders) {
        super();
        this.searchContent = searchContent;
        this.isCaseInsensitive = isCaseInsensitive;
        this.includeHeaders = includeHeaders;
    }

    /**
     * Is the search to include headers?
     * 
     * @return true if header values are included, false otherwise
     */
    public boolean isIncludeHeaders() {
        return includeHeaders;
    }

    /**
     * Sets whether the search should include headers.
     * 
     * @param includesHeaders
     *            <code>true</code> if header values are included, <code>false</code> otherwise
     */
    public synchronized void setIncludeHeaders(boolean includesHeaders) {
        this.includeHeaders = includesHeaders;
    }

    /**
     * Is this search case insensitive?
     * 
     * @return true if the search should be case insensitive, false otherwise
     */
    public boolean isCaseInsensitive() {
        return isCaseInsensitive;
    }

    /**
     * Sets whether the search should be case insensitive.
     * 
     * @param isCaseInsensitive
     *            true for case insensitive searches, false otherwise
     */
    public synchronized void setCaseInsensitive(boolean isCaseInsensitive) {
        this.isCaseInsensitive = isCaseInsensitive;
    }

    /**
     * Gets the content to be searched for.
     * 
     * @return search content, initially null
     */
    public synchronized CharSequence getSearchContent() {
        return searchContent;
    }

    /**
     * Sets the content sought.
     * 
     * @param searchContent
     *            content sought
     */
    public synchronized void setSearchContent(CharSequence searchContent) {
        this.searchContent = searchContent;
    }

    /**
     * Is {@link #getSearchContent()} found in the given input?
     * 
     * @param input
     *            <code>InputStream</code> containing an email
     * @return true if the content exists and the stream contains the content,
     *         false otherwise
     * @throws IOException
     * @throws MimeException
     */
    public boolean isFoundIn(final InputStream input) throws IOException,
            MimeException {
        final boolean includeHeaders;
        final CharSequence searchContent;
        final boolean isCaseInsensitive;
        synchronized (this) {
            includeHeaders = this.includeHeaders;
            searchContent = this.searchContent;
            isCaseInsensitive = this.isCaseInsensitive;
        }
        final boolean result;
        if (searchContent == null || "".equals(searchContent)) {
            final Logger logger = getLogger();
            logger.debug("Nothing to search for. ");
            result = false;
        } else {
            final CharBuffer buffer = createBuffer(searchContent,
                    isCaseInsensitive);
            result = parse(input, isCaseInsensitive, includeHeaders, buffer);
        }
        return result;
    }

    private boolean parse(final InputStream input,
            final boolean isCaseInsensitive, final boolean includeHeaders,
            final CharBuffer buffer) throws IOException, MimeException {
        try {
            boolean result = false;
            MimeConfig config = MimeConfig.custom().setMaxLineLen(-1).setMaxHeaderLen(-1).build();

            MimeTokenStream parser = new MimeTokenStream(config);            parser.parse(input);
            while (!result && parser.next() != EntityState.T_END_OF_STREAM) {
                final EntityState state = parser.getState();
                switch (state) {
                    case T_BODY:
                    case T_PREAMBLE:
                    case T_EPILOGUE:
                        result = checkBody(isCaseInsensitive, buffer, result,
                                parser);
                        break;
                    case T_FIELD:
                        if (includeHeaders) {
                            result = checkHeader(isCaseInsensitive, buffer,
                                    result, parser);
                        }
                        break;
                case T_END_BODYPART:
                case T_END_HEADER:
                case T_END_MESSAGE:
                case T_END_MULTIPART:
                case T_END_OF_STREAM:
                case T_RAW_ENTITY:
                case T_START_BODYPART:
                case T_START_HEADER:
                case T_START_MESSAGE:
                case T_START_MULTIPART:
                    break;
                }
            }
            return result;
        } catch (IllegalCharsetNameException e) {
            handle(e);
        } catch (UnsupportedCharsetException e) {
            handle(e);
        } catch (IllegalStateException e) {
            handle(e);
        }
        return false;
    }

    private boolean checkHeader(final boolean isCaseInsensitive,
            final CharBuffer buffer, boolean result, MimeTokenStream parser)
            throws IOException {
        final String value = parser.getField().getBody();
        final StringReader reader = new StringReader(value);
        if (isFoundIn(reader, buffer, isCaseInsensitive)) {
            result = true;
        }
        return result;
    }

    private boolean checkBody(final boolean isCaseInsensitive,
            final CharBuffer buffer, boolean result, MimeTokenStream parser)
            throws IOException {
        final Reader reader = parser.getReader();
        if (isFoundIn(reader, buffer, isCaseInsensitive)) {
            result = true;
        }
        return result;
    }

    private CharBuffer createBuffer(final CharSequence searchContent,
            final boolean isCaseInsensitive) {
        final CharBuffer buffer;
        if (isCaseInsensitive) {
            final int length = searchContent.length();
            buffer = CharBuffer.allocate(length);
            for (int i = 0; i < length; i++) {
                final char next = searchContent.charAt(i);
                final char upperCase = Character.toUpperCase(next);
                buffer.put(upperCase);
            }
            buffer.flip();
        } else {
            buffer = CharBuffer.wrap(searchContent);
        }
        return buffer;
    }

    protected void handle(Exception e) throws IOException, MimeException {
        final Logger logger = getLogger();
        logger.warn("Cannot read MIME body.");
        logger.debug("Failed to read body.", e);
    }

    private boolean isFoundIn(final Reader reader, final CharBuffer buffer,
            final boolean isCaseInsensitive) throws IOException {
        boolean result = false;
        int read;
        while (!result && (read = reader.read()) != -1) {
            final char next;
            if (isCaseInsensitive) {
                next = Character.toUpperCase((char) read);
            } else {
                next = (char) read;
            }
            result = matches(buffer, next);
        }
        return result;
    }

    private boolean matches(final CharBuffer buffer, final char next) {
        boolean result = false;
        if (buffer.hasRemaining()) {
            final boolean partialMatch = (buffer.position() > 0);
            final char matching = buffer.get();
            if (next != matching) {
                buffer.rewind();
                if (partialMatch) {
                    result = matches(buffer, next);
                }
            }
        } else {
            result = true;
        }
        return result;
    }

    public final Logger getLogger() {
        if (logger == null) {
            logger = LoggerFactory.getLogger(MessageSearcher.class);
        }
        return logger;
    }

    public final void setLogger(Logger logger) {
        this.logger = logger;
    }
}
