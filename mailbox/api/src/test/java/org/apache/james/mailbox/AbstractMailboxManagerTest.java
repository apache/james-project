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
package org.apache.james.mailbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Date;

import javax.mail.Flags;

import org.apache.james.mailbox.exception.BadCredentialsException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.mock.MockMailboxManager;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.junit.After;
import org.junit.Test;
import org.slf4j.LoggerFactory;

/**
 * Test the {@link StoreMailboxManager} methods that 
 * are not covered by the protocol-tester suite.
 * 
 * This class needs to be extended by the different mailbox 
 * implementations which are responsible to setup and 
 * implement the test methods.
 * 
 */
public abstract class AbstractMailboxManagerTest {
    
    public final static String USER_1 = "USER_1";
    public final static String USER_2 = "USER_2";

    /**
     * The mailboxManager that needs to get instanciated
     * by the mailbox implementations.
     */
    protected MailboxManager mailboxManager;
    private MailboxSession session;
    
    @After
    public void teardown() throws MailboxException {
        getMailboxManager().logout(session, false);
        getMailboxManager().endProcessingRequest(session);
    }
    
    @Test 
    public void createUser1SystemSessionShouldReturnValidSession() throws UnsupportedEncodingException, MailboxException {
        setMailboxManager(new MockMailboxManager(getMailboxManager()).getMockMailboxManager());
        session = getMailboxManager().createSystemSession(USER_1, LoggerFactory.getLogger("Mock"));
        
        assertThat(session.getUser().getUserName()).isEqualTo(USER_1);
    }

    @Test
    public void user1ShouldNotHaveAnInbox() throws UnsupportedEncodingException, MailboxException {
        setMailboxManager(new MockMailboxManager(getMailboxManager()).getMockMailboxManager());
        session = getMailboxManager().createSystemSession(USER_1, LoggerFactory.getLogger("Mock"));
        getMailboxManager().startProcessingRequest(session);
        
        MailboxPath inbox = MailboxPath.inbox(session);
        assertThat(getMailboxManager().mailboxExists(inbox, session)).isFalse();
    }
    
    @Test
    public void user1ShouldBeAbleToCreateInbox() throws MailboxException, UnsupportedEncodingException {
        setMailboxManager(new MockMailboxManager(getMailboxManager()).getMockMailboxManager());
        session = getMailboxManager().createSystemSession(USER_1, LoggerFactory.getLogger("Mock"));
        getMailboxManager().startProcessingRequest(session);
     
        MailboxPath inbox = MailboxPath.inbox(session);
        getMailboxManager().createMailbox(inbox, session);
        
        assertThat(getMailboxManager().mailboxExists(inbox, session)).isTrue();
    }

    @Test(expected=MailboxException.class)
    public void user1ShouldNotBeAbleToCreateInboxTwice() throws MailboxException, UnsupportedEncodingException {
        setMailboxManager(new MockMailboxManager(getMailboxManager()).getMockMailboxManager());
        session = getMailboxManager().createSystemSession(USER_1, LoggerFactory.getLogger("Mock"));
        getMailboxManager().startProcessingRequest(session);
        MailboxPath inbox = MailboxPath.inbox(session);
        getMailboxManager().createMailbox(inbox, session);
        getMailboxManager().createMailbox(inbox, session);
    }

    @Test
    public void user1ShouldNotHaveTestSubmailbox() throws MailboxException, UnsupportedEncodingException {
        setMailboxManager(new MockMailboxManager(getMailboxManager()).getMockMailboxManager());
        session = getMailboxManager().createSystemSession(USER_1, LoggerFactory.getLogger("Mock"));
        getMailboxManager().startProcessingRequest(session);

        MailboxPath inbox = MailboxPath.inbox(session);
        getMailboxManager().createMailbox(inbox, session);
        
        assertThat(getMailboxManager().mailboxExists(new MailboxPath(inbox, "INBOX.Test"), session)).isFalse();
    }
    
    @Test
    public void user1ShouldBeAbleToCreateTestSubmailbox() throws MailboxException, UnsupportedEncodingException {
        setMailboxManager(new MockMailboxManager(getMailboxManager()).getMockMailboxManager());
        session = getMailboxManager().createSystemSession(USER_1, LoggerFactory.getLogger("Mock"));
        getMailboxManager().startProcessingRequest(session);
        MailboxPath inbox = MailboxPath.inbox(session);
        getMailboxManager().createMailbox(inbox, session);
        
        MailboxPath inboxSubMailbox = new MailboxPath(inbox, "INBOX.Test");
        getMailboxManager().createMailbox(inboxSubMailbox, session);
        
        assertThat(getMailboxManager().mailboxExists(inboxSubMailbox, session)).isTrue();
    }
    
    @Test
    public void user1ShouldBeAbleToDeleteInbox() throws MailboxException, UnsupportedEncodingException {
        setMailboxManager(new MockMailboxManager(getMailboxManager()).getMockMailboxManager());
        session = getMailboxManager().createSystemSession(USER_1, LoggerFactory.getLogger("Mock"));
        getMailboxManager().startProcessingRequest(session);
     
        MailboxPath inbox = MailboxPath.inbox(session);
        getMailboxManager().createMailbox(inbox, session);
        MailboxPath inboxSubMailbox = new MailboxPath(inbox, "INBOX.Test");
        getMailboxManager().createMailbox(inboxSubMailbox, session);
        
        getMailboxManager().deleteMailbox(inbox, session);
        
        assertThat(getMailboxManager().mailboxExists(inbox, session)).isFalse();
        assertThat(getMailboxManager().mailboxExists(inboxSubMailbox, session)).isTrue();
    }
    
    @Test
    public void user1ShouldBeAbleToDeleteSubmailbox() throws MailboxException, UnsupportedEncodingException {
        setMailboxManager(new MockMailboxManager(getMailboxManager()).getMockMailboxManager());
        session = getMailboxManager().createSystemSession(USER_1, LoggerFactory.getLogger("Mock"));
        getMailboxManager().startProcessingRequest(session);
     
        MailboxPath inbox = MailboxPath.inbox(session);
        getMailboxManager().createMailbox(inbox, session);
        MailboxPath inboxSubMailbox = new MailboxPath(inbox, "INBOX.Test");
        getMailboxManager().createMailbox(inboxSubMailbox, session);
        
        getMailboxManager().deleteMailbox(inboxSubMailbox, session);
        
        assertThat(getMailboxManager().mailboxExists(inbox, session)).isTrue();
        assertThat(getMailboxManager().mailboxExists(inboxSubMailbox, session)).isFalse();
    }

    @Test
    public void closingSessionShouldWork() throws BadCredentialsException, MailboxException, UnsupportedEncodingException {
        setMailboxManager(new MockMailboxManager(getMailboxManager()).getMockMailboxManager());
        session = getMailboxManager().createSystemSession(USER_1, LoggerFactory.getLogger("Mock"));
        getMailboxManager().startProcessingRequest(session);

        getMailboxManager().logout(session, false);
        getMailboxManager().endProcessingRequest(session);
        
        assertThat(session.isOpen()).isFalse();
    }

    @Test
    public void listShouldReturnMailboxes() throws MailboxException, UnsupportedEncodingException {
        setMailboxManager(new MockMailboxManager(getMailboxManager()).getMockMailboxManager());
        session = getMailboxManager().createSystemSession("manager", LoggerFactory.getLogger("testList"));
        getMailboxManager().startProcessingRequest(session);
        
        assertThat(getMailboxManager().list(session)).hasSize(MockMailboxManager.EXPECTED_MAILBOXES_COUNT);
    }

    @Test
    public void user2ShouldBeAbleToCreateRootlessFolder() throws BadCredentialsException, MailboxException {
        session = getMailboxManager().createSystemSession(USER_2, LoggerFactory.getLogger("Test"));
        MailboxPath trash = new MailboxPath(MailboxConstants.USER_NAMESPACE, USER_2, "Trash");
        getMailboxManager().createMailbox(trash, session);
        
        assertThat(getMailboxManager().mailboxExists(trash, session)).isTrue();
    }
    
    @Test
    public void user2ShouldBeAbleToCreateNestedFoldersWithoutTheirParents() throws BadCredentialsException, MailboxException {
        session = getMailboxManager().createSystemSession(USER_2, LoggerFactory.getLogger("Test"));
        MailboxPath nestedFolder = new MailboxPath(MailboxConstants.USER_NAMESPACE, USER_2, "INBOX.testfolder");
        getMailboxManager().createMailbox(nestedFolder, session);
        
        assertThat(getMailboxManager().mailboxExists(nestedFolder, session)).isTrue();
        getMailboxManager().getMailbox(MailboxPath.inbox(session), session).appendMessage(new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), session, false, new Flags());
    }
    
    protected abstract void createMailboxManager() throws MailboxException, IOException;
    
    protected void setMailboxManager(MailboxManager mailboxManager) {
        this.mailboxManager = mailboxManager;
    }

    protected MailboxManager getMailboxManager() {
        if (mailboxManager == null) {
            throw new IllegalStateException("Please setMailboxManager with a non null value before requesting getMailboxManager()");
        }
        return mailboxManager;
    }

}
