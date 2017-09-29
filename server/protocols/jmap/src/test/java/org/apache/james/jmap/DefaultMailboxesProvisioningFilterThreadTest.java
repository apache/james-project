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
import java.util.Optional;
import java.util.Set;

import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.BadCredentialsException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.mock.MockMailboxSession;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxAnnotation;
import org.apache.james.mailbox.model.MailboxAnnotationKey;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MailboxQuery;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MultimailboxesSearchQuery;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.metrics.api.NoopMetricFactory;
import org.junit.Test;

import com.google.testing.threadtester.AnnotatedTestRunner;
import com.google.testing.threadtester.ThreadedAfter;
import com.google.testing.threadtester.ThreadedBefore;
import com.google.testing.threadtester.ThreadedMain;
import com.google.testing.threadtester.ThreadedSecondary;

public class DefaultMailboxesProvisioningFilterThreadTest {

    private DefaultMailboxesProvisioningFilter sut;
    private MailboxSession session;
    private MailboxManager mailboxManager;

    @ThreadedBefore
    public void before() {
        session = new MockMailboxSession("username");
        mailboxManager = new FakeMailboxManager(session) ;
        sut = new DefaultMailboxesProvisioningFilter(mailboxManager, new NoopMetricFactory());
    }
    
    @ThreadedMain
    public void mainThread() {
        sut.createMailboxesIfNeeded(session);
    }
    
    @ThreadedSecondary
    public void secondThread() {
        sut.createMailboxesIfNeeded(session);
    }
    
    @ThreadedAfter
    public void after() {
        // Exception is thrown if test fails
    }
    
    @Test
    public void testConcurrentAccessToFilterShouldNotThrow() {
        AnnotatedTestRunner runner = new AnnotatedTestRunner();
        runner.runTests(this.getClass(), DefaultMailboxesProvisioningFilter.class);
    }
    
    private static class FakeMailboxManager implements MailboxManager {
        private MailboxSession mailboxSession;

        public FakeMailboxManager(MailboxSession mailboxSession) {
            this.mailboxSession = mailboxSession;
        }

        @Override
        public EnumSet<SearchCapabilities> getSupportedSearchCapabilities() {
            return EnumSet.noneOf(SearchCapabilities.class);
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
        public MessageManager getMailbox(MailboxId mailboxId, MailboxSession session) throws MailboxException {
            return null;
        }

        @Override
        public Optional<MailboxId> createMailbox(MailboxPath mailboxPath, MailboxSession mailboxSession) throws MailboxException {
            return Optional.of(TestId.of(18L));
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
        public List<MessageRange> copyMessages(MessageRange set, MailboxId from, MailboxId to, MailboxSession session)
                throws MailboxException {
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
        public MailboxSession createSystemSession(String userName) throws BadCredentialsException, MailboxException {
            return mailboxSession;
        }

        @Override
        public MailboxSession login(String userid, String passwd) throws BadCredentialsException, MailboxException {
            return null;
        }

        @Override
        public void logout(MailboxSession session, boolean force) throws MailboxException {
        }

        @Override
        public boolean hasRight(MailboxPath mailboxPath, MailboxACL.Right right, MailboxSession session) throws MailboxException {
            return false;
        }

        @Override
        public MailboxACL.Rfc4314Rights myRights(MailboxPath mailboxPath, MailboxSession session) throws MailboxException {
            return null;
        }

        @Override
        public MailboxACL.Rfc4314Rights[] listRigths(MailboxPath mailboxPath, MailboxACL.EntryKey identifier, MailboxSession session) throws MailboxException {
            return null;
        }

        @Override
        public void applyRightsCommand(MailboxPath mailboxPath, MailboxACL.ACLCommand mailboxACLCommand, MailboxSession session) throws MailboxException {
        }

        @Override
        public void setRights(MailboxPath mailboxPath, MailboxACL mailboxACL, MailboxSession session) throws MailboxException {
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
        public List<MailboxAnnotation> getAnnotationsByKeys(MailboxPath mailboxPath, MailboxSession session, Set<MailboxAnnotationKey> keys) throws MailboxException {
            return null;
        }

        @Override
        public void updateAnnotations(MailboxPath mailboxPath, MailboxSession session, List<MailboxAnnotation> mailboxAnnotations) throws MailboxException {
            
        }

        @Override
        public boolean hasCapability(MailboxCapabilities capability) {
            return false;
        }

        @Override
        public List<MessageId> search(MultimailboxesSearchQuery expression, MailboxSession session, long limit) throws MailboxException {
            return null;
        }

        @Override
        public List<MailboxAnnotation> getAnnotationsByKeysWithOneDepth(MailboxPath mailboxPath, MailboxSession session,
                Set<MailboxAnnotationKey> keys) throws MailboxException {
            return null;
        }

        @Override
        public List<MailboxAnnotation> getAnnotationsByKeysWithAllDepth(MailboxPath mailboxPath, MailboxSession session,
                Set<MailboxAnnotationKey> keys) throws MailboxException {
            return null;
        }

        @Override
        public boolean hasChildren(MailboxPath mailboxPath, MailboxSession session) throws MailboxException {
            return false;
        }

        @Override
        public MailboxSession loginAsOtherUser(String adminUserId, String passwd, String realUserId) throws BadCredentialsException, MailboxException {
            return null;
        }
    }
}

