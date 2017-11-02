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

package org.apache.james.mailbox.inmemory.mail;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.InputStream;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.acl.GroupMembershipResolver;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.InMemoryMailboxSessionMapperFactory;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.AbstractMailboxManagerAttachmentTest;
import org.apache.james.mailbox.store.Authenticator;
import org.apache.james.mailbox.store.Authorizator;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.NoMailboxPathLocker;
import org.apache.james.mailbox.store.StoreRightManager;
import org.apache.james.mailbox.store.event.DefaultDelegatingMailboxListener;
import org.apache.james.mailbox.store.event.MailboxEventDispatcher;
import org.apache.james.mailbox.store.mail.AttachmentMapperFactory;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.junit.Before;

public class InMemoryMailboxManagerAttachmentTest extends AbstractMailboxManagerAttachmentTest {

    private InMemoryMailboxSessionMapperFactory mailboxSessionMapperFactory;
    private InMemoryMailboxManager mailboxManager;
    private InMemoryMailboxManager parseFailingMailboxManager;


    @Before
    public void setUp() throws Exception {
        mailboxSessionMapperFactory = new InMemoryMailboxSessionMapperFactory();
        Authenticator noAuthenticator = null;
        Authorizator noAuthorizator = null;
        MessageId.Factory messageIdFactory = new InMemoryMessageId.Factory();
        GroupMembershipResolver groupMembershipResolver = null;
        UnionMailboxACLResolver aclResolver = new UnionMailboxACLResolver();
        StoreRightManager storeRightManager = new StoreRightManager(mailboxSessionMapperFactory, aclResolver, groupMembershipResolver);

        DefaultDelegatingMailboxListener delegatingListener = new DefaultDelegatingMailboxListener();
        MailboxEventDispatcher mailboxEventDispatcher = new MailboxEventDispatcher(delegatingListener);
        mailboxManager = new InMemoryMailboxManager(mailboxSessionMapperFactory, noAuthenticator, noAuthorizator, new NoMailboxPathLocker(),
                new MessageParser(), messageIdFactory, mailboxEventDispatcher, delegatingListener, storeRightManager);
        mailboxManager.init();
        MessageParser failingMessageParser = mock(MessageParser.class);
        when(failingMessageParser.retrieveAttachments(any(InputStream.class)))
            .thenThrow(new RuntimeException("Message parser set to fail"));
        parseFailingMailboxManager = new InMemoryMailboxManager(mailboxSessionMapperFactory, noAuthenticator, noAuthorizator, new NoMailboxPathLocker(),
            failingMessageParser, messageIdFactory, mailboxEventDispatcher, delegatingListener, storeRightManager);
        parseFailingMailboxManager.init();
        super.setUp();
    }

    @Override
    protected MailboxManager getMailboxManager() {
        return mailboxManager;
    }

    @Override
    protected MailboxSessionMapperFactory getMailboxSessionMapperFactory() {
        return mailboxSessionMapperFactory;
    }

    @Override
    protected MailboxManager getParseFailingMailboxManager() {
        return parseFailingMailboxManager;
    }

    @Override
    protected AttachmentMapperFactory getAttachmentMapperFactory() {
        return mailboxSessionMapperFactory;
    }
}
