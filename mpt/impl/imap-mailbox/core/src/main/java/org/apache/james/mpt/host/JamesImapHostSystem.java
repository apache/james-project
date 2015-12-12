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

import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.decode.ImapDecoder;
import org.apache.james.imap.decode.main.ImapRequestStreamHandler;
import org.apache.james.imap.encode.ImapEncoder;
import org.apache.james.mailbox.MailboxSession.User;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mpt.api.Continuation;
import org.apache.james.mpt.api.ImapHostSystem;
import org.apache.james.mpt.helper.ByteBufferInputStream;
import org.apache.james.mpt.helper.ByteBufferOutputStream;
import org.apache.james.mpt.session.ImapSessionImpl;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

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

    public abstract void createMailbox(MailboxPath mailboxPath) throws Exception;

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

    }

    public void afterTests() throws Exception {
        // default do nothing
    }

    public void beforeTests() throws Exception {
        // default do nothing
    }
    
}
