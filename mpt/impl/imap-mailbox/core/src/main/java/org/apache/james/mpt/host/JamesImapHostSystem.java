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
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.Authenticator;
import org.apache.james.mailbox.store.Authorizator;
import org.apache.james.mpt.api.Continuation;
import org.apache.james.mpt.api.ImapHostSystem;
import org.apache.james.mpt.helper.ByteBufferInputStream;
import org.apache.james.mpt.helper.ByteBufferOutputStream;
import org.apache.james.mpt.imapmailbox.GrantRightsOnHost;
import org.apache.james.mpt.session.ImapSessionImpl;
import org.apache.james.user.memory.MemoryUsersRepository;

import com.google.common.base.Throwables;

public abstract class JamesImapHostSystem implements ImapHostSystem, GrantRightsOnHost {

    private MemoryUsersRepository memoryUsersRepository;
    protected Authorizator authorizator;
    protected Authenticator authenticator;

    private ImapDecoder decoder;
    private ImapEncoder encoder;
    private ImapProcessor processor;


    @Override
    public void beforeTest() throws Exception {
        memoryUsersRepository = MemoryUsersRepository.withoutVirtualHosting();
        try {
            memoryUsersRepository.configure(userRepositoryConfiguration());
        } catch (ConfigurationException e) {
            throw Throwables.propagate(e);
        }
        authenticator = new UserRepositoryAuthenticator(memoryUsersRepository);
        authorizator = new UserRepositoryAuthorizator(memoryUsersRepository);
    }
    
    @Override
    public void afterTest() throws Exception {
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

    protected abstract MailboxManager getMailboxManager();
    
    public void createMailbox(MailboxPath mailboxPath) throws Exception {
        MailboxManager mailboxManager = getMailboxManager();
        MailboxSession mailboxSession = mailboxManager.createSystemSession(mailboxPath.getUser());
        mailboxManager.startProcessingRequest(mailboxSession);
        mailboxManager.createMailbox(mailboxPath, mailboxSession);
        mailboxManager.logout(mailboxSession, true);
        mailboxManager.endProcessingRequest(mailboxSession);
    }

    public void grantRights(MailboxPath mailboxPath, String userName, MailboxACL.Rfc4314Rights rights) throws Exception {
        MailboxManager mailboxManager = getMailboxManager();
        MailboxSession mailboxSession = mailboxManager.createSystemSession(mailboxPath.getUser());
        mailboxManager.startProcessingRequest(mailboxSession);
        mailboxManager.setRights(mailboxPath,
            MailboxACL.EMPTY.apply(MailboxACL.command()
                .forUser(userName)
                .rights(rights)
                .asAddition()),
            mailboxManager.createSystemSession(userName));
        mailboxManager.logout(mailboxSession, true);
        mailboxManager.endProcessingRequest(mailboxSession);
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

    private HierarchicalConfiguration userRepositoryConfiguration() {
        PropertyListConfiguration configuration = new PropertyListConfiguration();
        configuration.addProperty("administratorId", "imapuser");
        return configuration;
    }

    public void configure(ImapConfiguration imapConfiguration) {
        processor.configure(imapConfiguration);
    }
}
