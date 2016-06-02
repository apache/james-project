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
package org.apache.james.jmap;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.BadCredentialsException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxACL.MailboxACLCommand;
import org.apache.james.mailbox.model.MailboxACL.MailboxACLEntryKey;
import org.apache.james.mailbox.model.MailboxACL.MailboxACLRight;
import org.apache.james.mailbox.model.MailboxACL.MailboxACLRights;
import org.apache.james.mailbox.model.MailboxAnnotation;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MailboxQuery;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.store.SimpleMailboxSession;
import org.apache.james.user.lib.mock.InMemoryUsersRepository;
import org.junit.Test;
import org.slf4j.Logger;

import com.google.testing.threadtester.AnnotatedTestRunner;
import com.google.testing.threadtester.ThreadedAfter;
import com.google.testing.threadtester.ThreadedBefore;
import com.google.testing.threadtester.ThreadedMain;
import com.google.testing.threadtester.ThreadedSecondary;

public class FirstUserConnectionFilterThreadTest {

    private FirstUserConnectionFilter sut;
    private InMemoryUsersRepository usersRepository;
    private MailboxSession session;
    private MailboxManager mailboxManager;

    @ThreadedBefore
    public void before() {
        usersRepository = new InMemoryUsersRepository();
        session = new SimpleMailboxSession(0, "username", null, null, null, ':', null);
        mailboxManager = new FakeMailboxManager(session) ;
        sut = new FirstUserConnectionFilter(usersRepository, mailboxManager);
    }
    
    @ThreadedMain
    public void mainThread() {
        sut.createAccountIfNeeded(session);
    }
    
    @ThreadedSecondary
    public void secondThread() {
        sut.createAccountIfNeeded(session);
    }
    
    @ThreadedAfter
    public void after() {
        // Exception is thrown if test fails
    }
    
    @Test
    public void testConcurrentAccessToFilterShouldNotThrow() {
        AnnotatedTestRunner runner = new AnnotatedTestRunner();
        runner.runTests(this.getClass(), FirstUserConnectionFilter.class);
    }
    
    private static class FakeMailboxManager implements MailboxManager {
        private MailboxSession mailboxSession;

        public FakeMailboxManager(MailboxSession mailboxSession) {
            this.mailboxSession = mailboxSession;
        }

        @Override
        public void startProcessingRequest(MailboxSession session) {
        }

        @Override
        public void endProcessingRequest(MailboxSession session) {
        }

        @Override
        public void addListener(MailboxPath mailboxPath, MailboxListener listener, MailboxSession session) throws MailboxException {
        }

        @Override
        public void removeListener(MailboxPath mailboxPath, MailboxListener listner, MailboxSession session) throws MailboxException {
        }

        @Override
        public void addGlobalListener(MailboxListener listener, MailboxSession session) throws MailboxException {
        }

        @Override
        public void removeGlobalListener(MailboxListener listner, MailboxSession session) throws MailboxException {
        }

        @Override
        public char getDelimiter() {
            return 0;
        }

        @Override
        public MessageManager getMailbox(MailboxPath mailboxPath, MailboxSession session) throws MailboxException {
            return null;
        }

        @Override
        public void createMailbox(MailboxPath mailboxPath, MailboxSession mailboxSession) throws MailboxException {
        }

        @Override
        public void deleteMailbox(MailboxPath mailboxPath, MailboxSession session) throws MailboxException {
        }

        @Override
        public void renameMailbox(MailboxPath from, MailboxPath to, MailboxSession session) throws MailboxException {
        }

        @Override
        public List<MessageRange> copyMessages(MessageRange set, MailboxPath from, MailboxPath to, MailboxSession session) throws MailboxException {
            return null;
        }

        @Override
        public List<MessageRange> moveMessages(MessageRange set, MailboxPath from, MailboxPath to, MailboxSession session) throws MailboxException {
            return null;
        }

        @Override
        public List<MailboxMetaData> search(MailboxQuery expression, MailboxSession session) throws MailboxException {
            return null;
        }

        @Override
        public boolean mailboxExists(MailboxPath mailboxPath, MailboxSession session) throws MailboxException {
            return false;
        }

        @Override
        public MailboxSession createSystemSession(String userName, Logger log) throws BadCredentialsException, MailboxException {
            return mailboxSession;
        }

        @Override
        public MailboxSession login(String userid, String passwd, Logger log) throws BadCredentialsException, MailboxException {
            return null;
        }

        @Override
        public void logout(MailboxSession session, boolean force) throws MailboxException {
        }

        @Override
        public boolean hasRight(MailboxPath mailboxPath, MailboxACLRight right, MailboxSession session) throws MailboxException {
            return false;
        }

        @Override
        public MailboxACLRights myRights(MailboxPath mailboxPath, MailboxSession session) throws MailboxException {
            return null;
        }

        @Override
        public MailboxACLRights[] listRigths(MailboxPath mailboxPath, MailboxACLEntryKey identifier, MailboxSession session) throws MailboxException {
            return null;
        }

        @Override
        public void setRights(MailboxPath mailboxPath, MailboxACLCommand mailboxACLCommand, MailboxSession session) throws MailboxException {
        }

        @Override
        public List<MailboxPath> list(MailboxSession session) throws MailboxException {
            return null;
        }

        @Override
        public EnumSet<MailboxCapabilities> getSupportedMailboxCapabilities() {
            return null;
        }
        
        @Override
        public EnumSet<MessageCapabilities> getSupportedMessageCapabilities() {
            return null;
        }

        @Override
        public List<MailboxAnnotation> getAllAnnotations(MailboxPath mailboxPath, MailboxSession session) throws MailboxException {
            return null;
        }

        @Override
        public List<MailboxAnnotation> getAnnotationsByKeys(MailboxPath mailboxPath, MailboxSession session, Set<String> keys) throws MailboxException {
            return null;
        }

        @Override
        public void updateAnnotations(MailboxPath mailboxPath, MailboxSession session, List<MailboxAnnotation> mailboxAnnotations) throws MailboxException {
            
        }

        @Override
        public boolean hasCapability(MailboxCapabilities capability) {
            return false;
        }
    }
}

