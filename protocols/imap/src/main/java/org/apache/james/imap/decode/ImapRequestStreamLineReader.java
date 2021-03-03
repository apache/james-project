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

package org.apache.james.imap.decode;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.message.BytesBackedLiteral;
import org.apache.james.imap.message.Literal;
import org.apache.james.imap.utils.EolInputStream;

import com.google.common.io.ByteStreams;

/**
 * {@link ImapRequestLineReader} which use normal IO Streaming
 */
public class ImapRequestStreamLineReader extends ImapRequestLineReader implements Closeable {
    private final InputStream input;
    private final OutputStream output;

    public ImapRequestStreamLineReader(InputStream input, OutputStream output) {
        this.input = input;
        this.output = output;
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
            int next = -1;

            try {
                next = input.read();
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

    @Override
    public Literal read(int size, boolean extraCRLF) throws IOException {

        // Unset the next char.
        nextSeen = false;
        nextChar = 0;
        InputStream limited = ByteStreams.limit(input, size);

        if (extraCRLF) {
            return BytesBackedLiteral.copy(new EolInputStream(this, limited));
        } else {
            return BytesBackedLiteral.copy(limited);
        }
    }

    /**
     * Sends a server command continuation request '+' back to the client,
     * requesting more data to be sent.
     */
    @Override
    protected void commandContinuationRequest() throws DecodingException {
        try {
            output.write('+');
            output.write('\r');
            output.write('\n');
            output.flush();
        } catch (IOException e) {
            throw new DecodingException(HumanReadableText.SOCKET_IO_FAILURE, "Unexpected exception in sending command continuation request.", e);
        }
    }

    @Override
    public void close() throws IOException {
        try {
            input.close();
        } finally {
            output.close();
        }
    }
}
