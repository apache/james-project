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
package org.apache.james.mailbox.copier;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageManager.MetaData.FetchGroup;
import org.apache.james.mailbox.acl.GroupMembershipResolver;
import org.apache.james.mailbox.acl.MailboxACLResolver;
import org.apache.james.mailbox.acl.SimpleGroupMembershipResolver;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.exception.BadCredentialsException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.InMemoryMailboxSessionMapperFactory;
import org.apache.james.mailbox.mock.MockMailboxManager;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.Authenticator;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;

/**
 * Test class for the {@link MailboxCopierImpl} implementation.
 * 
 * The InMemoryMailboxManager will be used as source and destination
 * Mailbox Manager.
 *
 */
public class MailboxCopierTest {
    
    /**
     * The instance for the test mailboxCopier.
     */
    private MailboxCopierImpl mailboxCopier;
    
    /**
     * The instance for the source Mailbox Manager.
     */
    private MailboxManager srcMemMailboxManager;
    
    /**
     * The instance for the destination Mailbox Manager.
     */
    private MailboxManager dstMemMailboxManager;
    
    /**
     * Setup the mailboxCopier and the source and destination
     * Mailbox Manager.
     * 
     * We use a InMemoryMailboxManager implementation.
     * 
     * @throws BadCredentialsException
     * @throws MailboxException
     */
    @Before
    public void setup() throws BadCredentialsException, MailboxException {
        
        mailboxCopier = new MailboxCopierImpl();
        mailboxCopier.setLog(LoggerFactory.getLogger(MailboxCopierTest.class.getName()));
        
        srcMemMailboxManager = newInMemoryMailboxManager();
        dstMemMailboxManager = newInMemoryMailboxManager();
        
    }
    
    /**
     * Feed the source MailboxManager with the number of mailboxes and
     * messages per mailbox.
     * 
     * Copy the mailboxes to the destination Mailbox Manager, and assert the number 
     * of mailboxes and messages per mailbox is the same as in the source
     * Mailbox Manager.
     * 
     * @throws MailboxException 
     * @throws IOException 
     */
    @Test
    public void testMailboxCopy() throws MailboxException, IOException {
    	 if (srcMemMailboxManager instanceof StoreMailboxManager) {
             ((StoreMailboxManager<?>) srcMemMailboxManager).init();
         }
         if (dstMemMailboxManager instanceof StoreMailboxManager) {
             ((StoreMailboxManager<?>) dstMemMailboxManager).init();
         }
    
        srcMemMailboxManager = new MockMailboxManager(srcMemMailboxManager).getMockMailboxManager();
       
        assertMailboxManagerSize(srcMemMailboxManager, 1);
        
        mailboxCopier.copyMailboxes(srcMemMailboxManager, dstMemMailboxManager);
        assertMailboxManagerSize(dstMemMailboxManager, 1);
        
        // We copy a second time to assert existing mailboxes does not give issue.
        mailboxCopier.copyMailboxes(srcMemMailboxManager, dstMemMailboxManager);
        assertMailboxManagerSize(dstMemMailboxManager, 2);
        
    }
    
    /**
     * Utility method to assert the number of mailboxes and messages per mailbox
     * are the ones expected.
     * 
     * @throws MailboxException 
     * @throws BadCredentialsException 
     */
    private void assertMailboxManagerSize(MailboxManager mailboxManager, int multiplicationFactor) throws BadCredentialsException, MailboxException {
        
        MailboxSession mailboxSession = mailboxManager.createSystemSession("manager", LoggerFactory.getLogger("src-mailbox-copier"));        
        mailboxManager.startProcessingRequest(mailboxSession);

        List<MailboxPath> mailboxPathList = mailboxManager.list(mailboxSession);
        
        assertThat(mailboxPathList).hasSize(MockMailboxManager.EXPECTED_MAILBOXES_COUNT);
        
        for (MailboxPath mailboxPath: mailboxPathList) {
            MessageManager messageManager = mailboxManager.getMailbox(mailboxPath, mailboxSession);
            assertThat(messageManager.getMetaData(false, mailboxSession, FetchGroup.NO_UNSEEN).getMessageCount()).isEqualTo(MockMailboxManager.MESSAGE_PER_MAILBOX_COUNT * multiplicationFactor);
        }
        
        mailboxManager.endProcessingRequest(mailboxSession);
        mailboxManager.logout(mailboxSession, true);
        
    }
    
    /**
     * Utility method to instanciate a new InMemoryMailboxManger with 
     * the needed MailboxSessionMapperFactory, Authenticator and UidProvider.
     * 
     * @return a new InMemoryMailboxManager
     */
    private MailboxManager newInMemoryMailboxManager() {
        MailboxACLResolver aclResolver = new UnionMailboxACLResolver();
        GroupMembershipResolver groupMembershipResolver = new SimpleGroupMembershipResolver();

        return new StoreMailboxManager<InMemoryId>(
            new InMemoryMailboxSessionMapperFactory(), 
            new Authenticator() {
                public boolean isAuthentic(String userid, CharSequence passwd) {
                    return true;
                }
            },
            aclResolver,
            groupMembershipResolver
            );
    
    }

}
