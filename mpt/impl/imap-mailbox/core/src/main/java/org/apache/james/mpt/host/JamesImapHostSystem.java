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

import java.util.HashSet;
import java.util.Set;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.plist.PropertyListConfiguration;
import org.apache.james.adapter.mailbox.store.UserRepositoryAuthenticator;
import org.apache.james.adapter.mailbox.store.UserRepositoryAuthorizator;
import org.apache.james.imap.api.ImapConfiguration;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.decode.ImapDecoder;
import org.apache.james.imap.decode.main.ImapRequestStreamHandler;
import org.apache.james.imap.encode.ImapEncoder;
import org.apache.james.mailbox.MailboxSession.User;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.Authenticator;
import org.apache.james.mailbox.store.Authorizator;
import org.apache.james.mpt.api.Continuation;
import org.apache.james.mpt.api.ImapHostSystem;
import org.apache.james.mpt.helper.ByteBufferInputStream;
import org.apache.james.mpt.helper.ByteBufferOutputStream;
import org.apache.james.mpt.session.ImapSessionImpl;
import org.apache.james.user.memory.MemoryUsersRepository;

import com.google.common.base.Throwables;

public abstract class JamesImapHostSystem implements ImapHostSystem {

    private final MemoryUsersRepository memoryUsersRepository;
    private final Set<User> users;
    protected final Authorizator authorizator;
    protected final Authenticator authenticator;

    private ImapDecoder decoder;
    private ImapEncoder encoder;
    private ImapProcessor processor;

    public JamesImapHostSystem() {
        super();
        users = new HashSet<>();
        memoryUsersRepository = MemoryUsersRepository.withoutVirtualHosting();
        try {
            memoryUsersRepository.configure(userRepositoryConfiguration());
        } catch (ConfigurationException e) {
            throw Throwables.propagate(e);
        }
        authenticator = new UserRepositoryAuthenticator(memoryUsersRepository);
        authorizator = new UserRepositoryAuthorizator(memoryUsersRepository);
    }

    public void configure(ImapDecoder decoder, ImapEncoder encoder,
            final ImapProcessor processor) {
        this.decoder = decoder;
        this.encoder = encoder;
        this.processor = processor;
    }

    @Override
    public boolean addUser(String user, String password) throws Exception {
        memoryUsersRepository.addUser(user, password);
        return true;
    }

    public Session newSession(Continuation continuation)
            throws Exception {
        return new Session(continuation);
    }

    public void beforeTest() throws Exception {
    }
    
    public void afterTest() throws Exception {
        users.clear();
        memoryUsersRepository.clear();
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
            session = new ImapSessionImpl();
        }

        public String readLine() throws Exception {
            if (!isReadLast) {
                handler.handleRequest(in, out, session);
                isReadLast = true;
            }
            return out.nextLine();
        }

        public void start() throws Exception {
            // Welcome message handled in the server
            out.write("* OK IMAP4rev1 Server ready\r\n");
        }

        public void restart() throws Exception {
            session = new ImapSessionImpl();
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

    private HierarchicalConfiguration userRepositoryConfiguration() {
        PropertyListConfiguration configuration = new PropertyListConfiguration();
        configuration.addProperty("administratorId", "imapuser");
        return configuration;
    }

    public void configure(ImapConfiguration imapConfiguration) {
        processor.configure(imapConfiguration);
    }
}
