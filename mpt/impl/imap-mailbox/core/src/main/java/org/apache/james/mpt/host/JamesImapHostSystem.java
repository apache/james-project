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

package org.apache.james.mpt.host;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.HashSet;
import java.util.Set;

import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.decode.ImapDecoder;
import org.apache.james.imap.decode.main.ImapRequestStreamHandler;
import org.apache.james.imap.encode.ImapEncoder;
import org.apache.james.mailbox.MailboxSession.User;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mpt.api.Continuation;
import org.apache.james.mpt.api.ImapHostSystem;
import org.apache.james.mpt.session.ImapSessionImpl;
import org.slf4j.LoggerFactory;

public abstract class JamesImapHostSystem implements ImapHostSystem {

    private ImapDecoder decoder;

    private ImapEncoder encoder;

    private ImapProcessor processor;

    private final Set<User> users;

    public JamesImapHostSystem() {
        super();
        users = new HashSet<User>();
    }

    public void configure(final ImapDecoder decoder, final ImapEncoder encoder,
            final ImapProcessor processor) {
        this.decoder = decoder;
        this.encoder = encoder;
        this.processor = processor;
    }

    public Session newSession(Continuation continuation)
            throws Exception {
        return new Session(continuation);
    }

    public void beforeTest() throws Exception {
    }
    
    public void afterTest() throws Exception {
        users.clear();
        resetData();
    }
    
    protected abstract void resetData() throws Exception;

    /* (non-Javadoc)
     * @see org.apache.james.mpt.host.ImapHostSystem#createMailbox(org.apache.james.mailbox.model.MailboxPath)
     */
    public abstract void createMailbox(MailboxPath mailboxPath) throws Exception;

    public String getHelloName() {
        return "JAMES";
    }

    public ImapDecoder getImapDecoder() {
        return decoder;
    }

    public ImapEncoder getImapEncoder() {
        return encoder;
    }

    public ImapProcessor getImapProcessor() {
        return processor;
    }

    public int getResetLength() {
        return Integer.MAX_VALUE;
    }

    public int countUsers() {
        return users.size();
    }

    public String getRealName(String name) {
        return name;
    }

    class Session implements org.apache.james.mpt.api.Session {
        ByteBufferOutputStream out;

        ByteBufferInputStream in;

        ImapRequestStreamHandler handler;

        ImapSessionImpl session;

        boolean isReadLast = true;

        public Session(Continuation continuation) {
            out = new ByteBufferOutputStream(continuation);
            in = new ByteBufferInputStream();
            handler = new ImapRequestStreamHandler(decoder, processor, encoder);
            session = new ImapSessionImpl(LoggerFactory.getLogger("sessionLog"));
        }

        public String readLine() throws Exception {
            if (!isReadLast) {
                handler.handleRequest(in, out, session);
                isReadLast = true;
            }
            final String result = out.nextLine();
            return result;
        }

        public void start() throws Exception {
            // Welcome message handled in the server
            out.write("* OK IMAP4rev1 Server ready\r\n");
        }

        public void stop() throws Exception {
            session.deselect();
        }

        public void writeLine(String line) throws Exception {
            isReadLast = false;
            in.nextLine(line);
        }

        public void forceConnectionClose(String byeMessage) {
            try {
                out.write(byeMessage);
                session.deselect();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    static class ByteBufferInputStream extends InputStream {

        ByteBuffer buffer = ByteBuffer.allocate(16384);

        CharsetEncoder encoder = Charset.forName("ASCII").newEncoder();

        boolean readLast = true;

        public int read() throws IOException {
            if (!readLast) {
                readLast = true;
                buffer.flip();
            }
            int result = -1;
            if (buffer.hasRemaining()) {
                result = buffer.get();
            }
            return result;
        }

        public void nextLine(String line) {
            if (buffer.position() > 0 && readLast) {
                buffer.compact();
            }
            encoder.encode(CharBuffer.wrap(line), buffer, true);
            buffer.put((byte) '\r');
            buffer.put((byte) '\n');
            readLast = false;
        }
    }

    static class ByteBufferOutputStream extends OutputStream {
        ByteBuffer buffer = ByteBuffer.allocate(65536);

        Charset ascii = Charset.forName("ASCII");

        Continuation continuation;

        boolean matchPlus = false;

        boolean matchCR = false;

        boolean matchLF = false;

        public ByteBufferOutputStream(Continuation continuation) {
            this.continuation = continuation;
        }

        public void write(String message) throws IOException {
            ascii.newEncoder().encode(CharBuffer.wrap(message), buffer, true);
        }

        public void write(int b) throws IOException {
            buffer.put((byte) b);
            if (b == '\n' && matchPlus && matchCR && matchLF) {
                matchPlus = false;
                matchCR = false;
                matchLF = false;
                continuation.doContinue();
            } else if (b == '\n') {
                matchLF = true;
                matchPlus = false;
                matchCR = false;
            } else if (b == '+' && matchLF) {
                matchPlus = true;
                matchCR = false;
            } else if (b == '\r' && matchPlus && matchLF) {
                matchCR = true;
            } else {
                matchPlus = false;
                matchCR = false;
                matchLF = false;
            }
        }

        public String nextLine() throws Exception {
            buffer.flip();
            byte last = 0;
            while (buffer.hasRemaining()) {
                byte next = buffer.get();
                if (last == '\r' && next == '\n') {
                    break;
                }
                last = next;
            }
            final ByteBuffer readOnlyBuffer = buffer.asReadOnlyBuffer();
            readOnlyBuffer.flip();
            int limit = readOnlyBuffer.limit() - 2;
            if (limit < 0) {
                limit = 0;
            }
            readOnlyBuffer.limit(limit);
            String result = ascii.decode(readOnlyBuffer).toString();
            buffer.compact();
            return result;
        }
    }

    public void afterTests() throws Exception {
        // default do nothing
    }

    public void beforeTests() throws Exception {
        // default do nothing
    }
    
}
