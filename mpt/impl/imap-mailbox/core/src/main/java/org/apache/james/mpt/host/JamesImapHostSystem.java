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

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.plist.PropertyListConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.adapter.mailbox.UserRepositoryAuthenticator;
import org.apache.james.adapter.mailbox.UserRepositoryAuthorizator;
import org.apache.james.core.Username;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.imap.api.ImapConfiguration;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.decode.ImapDecoder;
import org.apache.james.imap.decode.main.ImapRequestStreamHandler;
import org.apache.james.imap.encode.FakeImapSession;
import org.apache.james.imap.encode.ImapEncoder;
import org.apache.james.mailbox.Authenticator;
import org.apache.james.mailbox.Authorizator;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mpt.api.Continuation;
import org.apache.james.mpt.api.ImapHostSystem;
import org.apache.james.mpt.helper.ByteBufferInputStream;
import org.apache.james.mpt.helper.ByteBufferOutputStream;
import org.apache.james.mpt.imapmailbox.GrantRightsOnHost;
import org.apache.james.user.memory.MemoryUsersRepository;

public abstract class JamesImapHostSystem implements ImapHostSystem, GrantRightsOnHost {
    private static final DomainList NO_DOMAIN_LIST = null;

    private MemoryUsersRepository memoryUsersRepository;
    protected Authorizator authorizator;
    protected Authenticator authenticator;

    private ImapDecoder decoder;
    private ImapEncoder encoder;
    private ImapProcessor processor;


    @Override
    public void beforeTest() throws Exception {
        memoryUsersRepository = MemoryUsersRepository.withoutVirtualHosting(NO_DOMAIN_LIST);
        try {
            memoryUsersRepository.configure(userRepositoryConfiguration());
        } catch (ConfigurationException e) {
            throw new RuntimeException(e);
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
        configure(ImapConfiguration.builder().isProvisionDefaultMailboxes(false).build());
    }

    @Override
    public boolean addUser(Username user, String password) throws Exception {
        memoryUsersRepository.addUser(user, password);
        return true;
    }

    @Override
    public Session newSession(Continuation continuation)
            throws Exception {
        return new Session(continuation);
    }

    protected abstract MailboxManager getMailboxManager();

    protected abstract void await() throws Exception;
    
    @Override
    public void createMailbox(MailboxPath mailboxPath) throws Exception {
        MailboxManager mailboxManager = getMailboxManager();
        MailboxSession mailboxSession = mailboxManager.createSystemSession(mailboxPath.getUser());
        mailboxManager.startProcessingRequest(mailboxSession);
        mailboxManager.createMailbox(mailboxPath, mailboxSession);
        mailboxManager.endProcessingRequest(mailboxSession);
    }

    @Override
    public void grantRights(MailboxPath mailboxPath, Username username, MailboxACL.Rfc4314Rights rights) throws Exception {
        MailboxManager mailboxManager = getMailboxManager();
        MailboxSession mailboxSession = mailboxManager.createSystemSession(mailboxPath.getUser());
        mailboxManager.startProcessingRequest(mailboxSession);
        mailboxManager.setRights(mailboxPath,
            MailboxACL.EMPTY.apply(MailboxACL.command()
                .forUser(username)
                .rights(rights)
                .asAddition()),
            mailboxSession);
        mailboxManager.endProcessingRequest(mailboxSession);
    }

    class Session implements org.apache.james.mpt.api.Session {
        ByteBufferOutputStream out;

        ByteBufferInputStream in;

        ImapRequestStreamHandler handler;

        FakeImapSession session;

        boolean isReadLast = true;

        public Session(Continuation continuation) {
            out = new ByteBufferOutputStream(continuation);
            in = new ByteBufferInputStream();
            handler = new ImapRequestStreamHandler(decoder, processor, encoder);
            session = new FakeImapSession();
        }

        @Override
        public String readLine() throws Exception {
            if (!isReadLast) {
                handler.handleRequest(in, out, session);
                isReadLast = true;
            }
            return out.nextLine();
        }

        @Override
        public void start() throws Exception {
            // Welcome message handled in the server
            out.write("* OK IMAP4rev1 Server ready\r\n");
        }

        @Override
        public void restart() throws Exception {
            session = new FakeImapSession();
        }

        @Override
        public void stop() throws Exception {
            session.deselect().block();
        }

        @Override
        public void writeLine(String line) throws Exception {
            isReadLast = false;
            in.nextLine(line);
        }

        @Override
        public void await() throws Exception {
            JamesImapHostSystem.this.await();
        }
    }

    private HierarchicalConfiguration<ImmutableNode> userRepositoryConfiguration() {
        PropertyListConfiguration configuration = new PropertyListConfiguration();
        configuration.addProperty("administratorId", "imapuser");
        return configuration;
    }

    public void configure(ImapConfiguration imapConfiguration) {
        processor.configure(imapConfiguration);
    }
}
