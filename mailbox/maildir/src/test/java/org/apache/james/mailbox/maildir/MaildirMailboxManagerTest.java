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
package org.apache.james.mailbox.maildir;

import java.io.IOException;

import org.apache.james.mailbox.AbstractMailboxManagerTest;
import org.apache.james.mailbox.acl.GroupMembershipResolver;
import org.apache.james.mailbox.acl.MailboxACLResolver;
import org.apache.james.mailbox.acl.SimpleGroupMembershipResolver;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.store.JVMMailboxPathLocker;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.junit.runners.Suite.SuiteClasses;

@SuiteClasses({
    MaildirMailboxManagerTest.DomainUser.class,
    MaildirMailboxManagerTest.User.class,
    MaildirMailboxManagerTest.FullUser.class})

public class MaildirMailboxManagerTest {

    public static abstract class AbstractMaildirMailboxManagerTest extends AbstractMailboxManagerTest {
        @Rule public TemporaryFolder tmpFolder = new TemporaryFolder();
        
        @Before
        public void setup() throws Exception {
            if (OsDetector.isWindows()) {
                System.out.println("Maildir tests work only on non-windows systems. So skip the test");
            } else {
                createMailboxManager();
            }
        }
        
        protected void createMailboxManager(String configuration) throws MailboxException, IOException {
            MaildirStore store = new MaildirStore(tmpFolder.newFolder().getPath() + configuration, new JVMMailboxPathLocker());
            MaildirMailboxSessionMapperFactory mf = new MaildirMailboxSessionMapperFactory(store);
            
            MailboxACLResolver aclResolver = new UnionMailboxACLResolver();
            GroupMembershipResolver groupMembershipResolver = new SimpleGroupMembershipResolver();

            StoreMailboxManager<MaildirId> manager = new StoreMailboxManager<MaildirId>(mf, null, new JVMMailboxPathLocker(), aclResolver, groupMembershipResolver);
            manager.init();
            setMailboxManager(manager);
        }

    }
    
    public static class DomainUser extends AbstractMaildirMailboxManagerTest {
        @Override
        protected void createMailboxManager() throws MailboxException,
                IOException {
            createMailboxManager("/%domain/%user");
        }
    }
    
    @Ignore
    public static class User extends AbstractMaildirMailboxManagerTest {
        @Override
        protected void createMailboxManager() throws MailboxException,
                IOException {
            createMailboxManager("/%user");
        }
    }
    
    public static class FullUser extends AbstractMaildirMailboxManagerTest {
        @Override
        protected void createMailboxManager() throws MailboxException,
                IOException {
            createMailboxManager("/%fulluser");
        }
    }

}
