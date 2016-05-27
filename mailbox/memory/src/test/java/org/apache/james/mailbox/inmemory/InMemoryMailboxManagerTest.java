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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.apache.james.mailbox.AbstractMailboxManagerTest;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.acl.GroupMembershipResolver;
import org.apache.james.mailbox.acl.MailboxACLResolver;
import org.apache.james.mailbox.acl.SimpleGroupMembershipResolver;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.exception.BadCredentialsException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MailboxQuery;
import org.apache.james.mailbox.store.JVMMailboxPathLocker;
import org.apache.james.mailbox.store.MockAuthenticator;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;

/**
 * InMemoryMailboxManagerTest that extends the MailboxManagerTest.
 */
public class InMemoryMailboxManagerTest extends AbstractMailboxManagerTest {

    private MailboxSession session;

    /**
     * Setup the mailboxManager.
     * 
     * @throws Exception
     */
    @Before
    public void setup() throws Exception {
        createMailboxManager();

        session = getMailboxManager().createSystemSession(USER_1, LoggerFactory.getLogger("Test"));
        getMailboxManager().startProcessingRequest(session);
    }
    
    /**
     * Close the system session and entityManagerFactory
     * 
     * @throws MailboxException 
     * @throws BadCredentialsException 
     */
    @After
    public void tearDown() throws MailboxException {
        getMailboxManager().logout(session, true);
        getMailboxManager().endProcessingRequest(session);
        getMailboxManager().createSystemSession("test", LoggerFactory.getLogger("Test")).close();
    }

    /* (non-Javadoc)
     * @see org.apache.james.mailbox.MailboxManagerTest#createMailboxManager()
     */
    @Override
    protected void createMailboxManager() throws MailboxException {

        MailboxACLResolver aclResolver = new UnionMailboxACLResolver();
        GroupMembershipResolver groupMembershipResolver = new SimpleGroupMembershipResolver();
        MessageParser messageParser = new MessageParser();

        InMemoryMailboxSessionMapperFactory mailboxSessionMapperFactory = new InMemoryMailboxSessionMapperFactory();
        StoreMailboxManager mailboxManager = new InMemoryMailboxManager(mailboxSessionMapperFactory, new MockAuthenticator(), new JVMMailboxPathLocker(), aclResolver, groupMembershipResolver, messageParser);
        mailboxManager.init();
        
        setMailboxManager(mailboxManager);

    }

    @Test
    public void searchShouldNotReturnResultsFromOtherNamespaces() throws Exception {
        getMailboxManager().createMailbox(new MailboxPath("#namespace", USER_1, "Other"), session);
        getMailboxManager().createMailbox(MailboxPath.inbox(session), session);
        List<MailboxMetaData> metaDatas = getMailboxManager().search(new MailboxQuery(new MailboxPath("#private", USER_1, ""), "*", '.'), session);
        assertThat(metaDatas).hasSize(1);
        assertThat(metaDatas.get(0).getPath()).isEqualTo(MailboxPath.inbox(session));
    }

    @Test
    public void searchShouldNotReturnResultsFromOtherUsers() throws Exception {
        getMailboxManager().createMailbox(new MailboxPath("#namespace", USER_2, "Other"), session);
        getMailboxManager().createMailbox(MailboxPath.inbox(session), session);
        List<MailboxMetaData> metaDatas = getMailboxManager().search(new MailboxQuery(new MailboxPath("#private", USER_1, ""), "*", '.'), session);
        assertThat(metaDatas).hasSize(1);
        assertThat(metaDatas.get(0).getPath()).isEqualTo(MailboxPath.inbox(session));
    }
    
}
