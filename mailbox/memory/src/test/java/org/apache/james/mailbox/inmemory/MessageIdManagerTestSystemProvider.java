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
package org.apache.james.mailbox.inmemory;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.acl.GroupMembershipResolver;
import org.apache.james.mailbox.acl.MailboxACLResolver;
import org.apache.james.mailbox.acl.SimpleGroupMembershipResolver;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.exception.BadCredentialsException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.FakeAuthenticator;
import org.apache.james.mailbox.store.MessageIdManagerTestSystem;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Throwables;

public class MessageIdManagerTestSystemProvider {

    private static final int LIMIT_ANNOTATIONS = 3;
    private static final int LIMIT_ANNOTATION_SIZE = 30;
    private static final int UID_VALIDITY = 1024;
    private static final Logger LOGGER = LoggerFactory.getLogger(InMemoryMessageIdManagerStorageTest.class);
    private static final String USER = "user";
    private static final String PASSWORD = "password";

    public static MessageIdManagerTestSystem createTestingData() {
        InMemoryMailboxManager mailboxManager = createMailboxManager();

        MailboxSession mailboxSession = createMailboxSession(mailboxManager);
        MailboxPath mailboxPath = new MailboxPath("#private", USER, "INBOX");
        SimpleMailbox mailbox1 = createMailbox(mailboxManager, mailboxPath, mailboxSession);
        MailboxPath mailboxPath2 = new MailboxPath("#private", USER, "mailbox2");
        SimpleMailbox mailbox2 = createMailbox(mailboxManager, mailboxPath2, mailboxSession);
        MailboxPath mailboxPath3 = new MailboxPath("#private", USER, "mailbox3");
        SimpleMailbox mailbox3 = createMailbox(mailboxManager, mailboxPath3, mailboxSession);
        return new InMemoryMessageIdManagerTestSystem(mailboxManager, mailboxSession, mailbox1, mailbox2, mailbox3);
    }

    private static MailboxSession createMailboxSession(InMemoryMailboxManager mailboxManager) {
        try {
            return mailboxManager.login(USER, PASSWORD, LOGGER);
        } catch (BadCredentialsException e) {
            throw Throwables.propagate(e);
        } catch (MailboxException e) {
            throw Throwables.propagate(e);
        }
    }

    private static SimpleMailbox createMailbox(InMemoryMailboxManager mailboxManager, MailboxPath mailboxPath, MailboxSession mailboxSession) {
        try {
            mailboxManager.createMailbox(mailboxPath, mailboxSession);
            MessageManager messageManager = mailboxManager.getMailbox(mailboxPath, mailboxSession);
            return new SimpleMailbox(mailboxPath, UID_VALIDITY, messageManager.getId());
        } catch (MailboxException e) {
            throw Throwables.propagate(e);
        }
    }

    private static InMemoryMailboxManager createMailboxManager() {
        MailboxACLResolver aclResolver = new UnionMailboxACLResolver();
        GroupMembershipResolver groupMembershipResolver = new SimpleGroupMembershipResolver();
        MessageParser messageParser = new MessageParser();

        InMemoryMailboxSessionMapperFactory mailboxSessionMapperFactory = new InMemoryMailboxSessionMapperFactory();
        MessageId.Factory messageIdFactory = new InMemoryMessageId.Factory();
        FakeAuthenticator authenticator = new FakeAuthenticator();
        authenticator.addUser(USER, PASSWORD);
        InMemoryMailboxManager mailboxManager = new InMemoryMailboxManager(mailboxSessionMapperFactory, authenticator, 
                aclResolver, groupMembershipResolver, messageParser, messageIdFactory, LIMIT_ANNOTATIONS, LIMIT_ANNOTATION_SIZE);

        try {
            mailboxManager.init();
        } catch (MailboxException e) {
            Throwables.propagate(e);
        }
        return mailboxManager;
    }
}
