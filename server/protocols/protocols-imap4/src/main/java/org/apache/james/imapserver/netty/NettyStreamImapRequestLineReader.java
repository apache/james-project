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

import java.io.Closeable;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.decode.DecodingException;
import org.apache.james.imap.message.Literal;
import org.apache.james.imap.utils.EolInputStream;

import com.github.fge.lambdas.Throwing;
import com.google.common.io.ByteStreams;
import com.google.common.io.CountingInputStream;

import io.netty.channel.Channel;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class NettyStreamImapRequestLineReader extends AbstractNettyImapRequestLineReader implements Closeable {
    private static class FileLiteral implements Literal, Closeable {
        private final long offset;
        private final int size;
        private final boolean extraCRLF;
        private final ImapRequestFrameDecoder.FileHolder file;
        private final AbstractNettyImapRequestLineReader reader;

        private FileLiteral(long offset, int size, boolean extraCRLF, ImapRequestFrameDecoder.FileHolder file, AbstractNettyImapRequestLineReader reader) {
            this.offset = offset;
            this.size = size;
            this.extraCRLF = extraCRLF;
            this.file = file;
            this.reader = reader;
        }

        @Override
        public void close() {
            Mono.fromRunnable(Throwing.runnable(() -> {
                    file.dispose();
                    reader.close();
                }))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
        }

        @Override
        public long size() {
            return Math.min(file.getFile().length() - offset, size);
        }

        @Override
        public InputStream getInputStream() throws IOException {
            FileInputStream fileInputStream = new FileInputStream(file.getFile());
            fileInputStream.skip(offset);
            InputStream limitedStream = ByteStreams.limit(fileInputStream, size);
            if (extraCRLF) {
                return new EolInputStream(reader, limitedStream);
            } else {
                return limitedStream;
            }
        }
    }

    private final ImapRequestFrameDecoder.FileHolder backingFile;
    private final CountingInputStream in;

    public NettyStreamImapRequestLineReader(Channel channel, ImapRequestFrameDecoder.FileHolder file, boolean retry) {
        super(channel, retry);
        this.backingFile = file;
        try {
            this.in = new CountingInputStream(new FileInputStream(file.getFile()));
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Reads the next character in the current line. This method will continue
     * to return the same character until the {@link #consume()} method is
     * called.
     * 
     * @return The next character TODO: character encoding is variable and
     *         cannot be determine at the token level; this char is not accurate
     *         reported; should be an octet
     * @throws DecodingException
     *             If the end-of-stream is reached.
     */
    @Override
    public char nextChar() throws DecodingException {
        if (!nextSeen) {
            int next;
            try {
                next = in.read();
            } catch (IOException e) {
                throw new DecodingException(HumanReadableText.SOCKET_IO_FAILURE, "Error reading from stream.", e);
            }
            if (next == -1) {
                throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Unexpected end of stream.");
            }
            nextSeen = true;
            nextChar = (char) next;
        }
        
        return nextChar;
    
    }

    /**
     * Reads and consumes a number of characters from the underlying reader,
     * filling the char array provided.
     * 
     * @param size
     *            number of characters to read and consume
     * @param extraCRLF
     *            Add extra CRLF
     * @throws DecodingException
     *             If a char can't be read into each array element.
     */
    @Override
    public Literal read(int size, boolean extraCRLF) throws DecodingException {

        // Unset the next char.
        nextSeen = false;
        nextChar = 0;

        try {
            long offset = in.getCount();
            in.skip(size);
            return new FileLiteral(offset, size, extraCRLF, backingFile, this);
        } catch (IOException e) {
            throw new DecodingException(HumanReadableText.SOCKET_IO_FAILURE, "Could not copy litteral", e);
        }
    }

    @Override
    public void close() throws IOException {
        in.close();
    }

}
