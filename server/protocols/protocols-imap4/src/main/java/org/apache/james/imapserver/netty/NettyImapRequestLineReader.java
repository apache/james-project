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

import java.io.InputStream;

import org.apache.commons.io.input.BoundedInputStream;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.decode.DecodingException;
import org.apache.james.imap.decode.ImapRequestLineReader;
import org.apache.james.imap.utils.EolInputStream;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.channel.Channel;

/**
 * {@link ImapRequestLineReader} implementation which will write to a
 * {@link Channel} and read from a {@link ChannelBuffer}. Please see the docs on
 * {@link #nextChar()} and {@link #read(int, boolean)} to understand the special behavior
 * of this implementation
 */
public class NettyImapRequestLineReader extends AbstractNettyImapRequestLineReader {

    private final ChannelBuffer buffer;
    private int read = 0;
    private final int maxLiteralSize;

    public NettyImapRequestLineReader(Channel channel, ChannelBuffer buffer, boolean retry, int maxLiteralSize) {
        super(channel, retry);
        this.buffer = buffer;
        this.maxLiteralSize  = maxLiteralSize;
    }
    

    /**
     * Return the next char to read. This will return the same char on every
     * call till {@link #consume()} was called.
     * 
     * This implementation will throw a {@link NotEnoughDataException} if the
     * wrapped {@link ChannelBuffer} contains not enough data to read the next
     * char
     */
    @Override
    public char nextChar() throws DecodingException {
        if (!nextSeen) {
            int next;

            if (buffer.readable()) {
                next = buffer.readByte();
                read++;
            } else {
                throw new NotEnoughDataException();
            }
            nextSeen = true;
            nextChar = (char) next;
        }
        return nextChar;
    }

    /**
     * Return a {@link ChannelBufferInputStream} if the wrapped
     * {@link ChannelBuffer} contains enough data. If not it will throw a
     * {@link NotEnoughDataException}
     */
    @Override
    public InputStream read(int size, boolean extraCRLF) throws DecodingException {
        int crlf = 0;
        if (extraCRLF) {
            crlf = 2;
        }
        
        if (maxLiteralSize > 0 && size > maxLiteralSize) {
            throw new DecodingException(HumanReadableText.FAILED, "Specified literal is greater then the allowed size");
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

        // limit the size via commons-io as ChannelBufferInputStream size limiting is buggy
        InputStream in = new BoundedInputStream(new ChannelBufferInputStream(buffer), size); 
        if (extraCRLF) {
            return new EolInputStream(this, in);
        } else {
            return in;
        }
    }

    /**
     * {@link RuntimeException} which will get thrown by
     * {@link NettyImapRequestLineReader#nextChar()} and
     * {@link NettyImapRequestLineReader#read(int, boolean)} if not enough data is
     * readable in the underlying {@link ChannelBuffer}
     */
    public static final class NotEnoughDataException extends RuntimeException {

        public static final int UNKNOWN_SIZE = -1;
        private final int size;

        public NotEnoughDataException(int size) {
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
