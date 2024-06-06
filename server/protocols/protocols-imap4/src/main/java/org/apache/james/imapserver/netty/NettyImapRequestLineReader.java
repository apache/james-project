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

package org.apache.james.imapserver.netty;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import org.apache.commons.io.input.BoundedInputStream;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.decode.DecodingException;
import org.apache.james.imap.decode.ImapRequestLineReader;
import org.apache.james.imap.message.BytesBackedLiteral;
import org.apache.james.imap.message.Literal;
import org.apache.james.imap.utils.EolInputStream;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.Channel;

/**
 * {@link ImapRequestLineReader} implementation which will write to a
 * {@link Channel} and read from a {@link ByteBuf}. Please see the docs on
 * {@link #nextChar()} and {@link #read(int, boolean)} to understand the special behavior
 * of this implementation
 */
public class NettyImapRequestLineReader extends AbstractNettyImapRequestLineReader {
    public static final int MAXIMUM_LITERAL_COUNT = Optional.ofNullable(System.getProperty("james.imap.literal.count.max"))
        .map(Integer::parseInt)
        .orElse(64);
    private final int initialReaderIndex;

    private final ByteBuf buffer;
    private int read = 0;
    private int literalCount = 0;
    private final int maxLiteralSize;
    private final int maxFrameLength;

    public NettyImapRequestLineReader(Channel channel, ByteBuf buffer, boolean retry, int initialReaderIndex, int maxLiteralSize, int maxFrameLength) {
        super(channel, retry);
        this.buffer = buffer;
        this.initialReaderIndex = initialReaderIndex;
        this.maxLiteralSize  = maxLiteralSize;
        this.maxFrameLength = maxFrameLength;
    }
    

    /**
     * Return the next char to read. This will return the same char on every
     * call till {@link #consume()} was called.
     * 
     * This implementation will throw a {@link NotEnoughDataException} if the
     * wrapped {@link ByteBuf} contains not enough data to read the next
     * char
     */
    @Override
    public char nextChar() throws DecodingException {
        if (!nextSeen) {
            if (buffer.isReadable()) {
                nextChar = (char) buffer.readByte();
                read++;
                nextSeen = true;
                if (read > maxFrameLength) {
                    throw new DecodingException(HumanReadableText.FAILED, "Line length exceeded.");
                }
            } else {
                throw new NotEnoughDataException();
            }
        }
        return nextChar;
    }

    /**
     * Return a {@link ByteBufInputStream} if the wrapped
     * {@link ByteBuf} contains enough data. If not it will throw a
     * {@link NotEnoughDataException}
     */
    @Override
    public Literal read(int size, boolean extraCRLF) throws DecodingException {
        int crlf = 0;
        if (extraCRLF) {
            crlf = 2;
        }
        int readSoFar = buffer.readerIndex() - initialReaderIndex;
        if (literalCount > MAXIMUM_LITERAL_COUNT) {
            throw new DecodingException(HumanReadableText.FAILED_LITERAL_SIZE_EXCEEDED, "Too many literals. " + MAXIMUM_LITERAL_COUNT + " allowed but got " + literalCount);
        }
        literalCount++;
        if (maxLiteralSize > 0 && (readSoFar + size) > maxLiteralSize) {
            throw new DecodingException(HumanReadableText.FAILED_LITERAL_SIZE_EXCEEDED, "Specified literals total size is greater then the allowed size. " + (readSoFar + size) + " instead of " + maxLiteralSize + " limit.");
        }
        // Check if we have enough data
        if (size + crlf > buffer.readableBytes()) {
            // ok let us throw a exception which till the decoder how many more
            // bytes we need
            throw new NotEnoughDataException(size + read + crlf);
        }

        // Unset the next char.
        nextSeen = false;
        nextChar = 0;


        try {
            // limit the size via commons-io as ByteBufInputStream size limiting is buggy
            InputStream in = new BoundedInputStream(new ByteBufInputStream(buffer), size);
            if (extraCRLF) {
                return BytesBackedLiteral.copy(new EolInputStream(this, in), size);
            } else {
                return BytesBackedLiteral.copy(in, size);
            }
        } catch (IOException e) {
            throw new DecodingException(HumanReadableText.SOCKET_IO_FAILURE, "Can not read literal", e);
        }
    }

    /**
     * {@link RuntimeException} which will get thrown by
     * {@link NettyImapRequestLineReader#nextChar()} and
     * {@link NettyImapRequestLineReader#read(int, boolean)} if not enough data is
     * readable in the underlying {@link ByteBuf}
     */
    public static final class NotEnoughDataException extends RuntimeException {
        public static final int UNKNOWN_SIZE = -1;
        private static final String NO_MESSAGE = null;
        private static final Throwable NO_CAUSE = null;
        private static final boolean DISABLE_SUPPRESSION = false;

        private final int size;

        public NotEnoughDataException(int size) {
            super(NO_MESSAGE, NO_CAUSE, DISABLE_SUPPRESSION, false);
            this.size = size;
        }

        public NotEnoughDataException() {
            this(UNKNOWN_SIZE);
        }

        /**
         * Return the size of the data which is needed
         * 
         * @return size
         */
        public int getNeededSize() {
            return size;
        }
    }

}
