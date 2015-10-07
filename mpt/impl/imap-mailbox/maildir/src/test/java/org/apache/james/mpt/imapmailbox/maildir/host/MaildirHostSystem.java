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
package org.apache.james.mpt.imapmailbox.maildir.host;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.encode.main.DefaultImapEncoderFactory;
import org.apache.james.imap.main.DefaultImapDecoderFactory;
import org.apache.james.imap.processor.main.DefaultImapProcessorFactory;
import org.apache.james.mailbox.acl.GroupMembershipResolver;
import org.apache.james.mailbox.acl.MailboxACLResolver;
import org.apache.james.mailbox.acl.SimpleGroupMembershipResolver;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.maildir.MaildirId;
import org.apache.james.mailbox.maildir.MaildirMailboxSessionMapperFactory;
import org.apache.james.mailbox.maildir.MaildirStore;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.JVMMailboxPathLocker;
import org.apache.james.mailbox.store.MockAuthenticator;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
import org.apache.james.mailbox.store.quota.DefaultQuotaRootResolver;
import org.apache.james.mailbox.store.quota.NoQuotaManager;
import org.apache.james.mpt.host.JamesImapHostSystem;
import org.apache.james.mpt.imapmailbox.MailboxCreationDelegate;

public class MaildirHostSystem extends JamesImapHostSystem {

    public static final String META_DATA_DIRECTORY = "target/user-meta-data";
    private static final String MAILDIR_HOME = "target/Maildir";
    
    private final StoreMailboxManager<MaildirId> mailboxManager;
    private final MockAuthenticator userManager;
    private final MaildirMailboxSessionMapperFactory mailboxSessionMapperFactory;
    
    public static JamesImapHostSystem build() throws Exception {
        return new MaildirHostSystem();
    }
    
    public MaildirHostSystem() throws MailboxException {
        userManager = new MockAuthenticator();
        JVMMailboxPathLocker locker = new JVMMailboxPathLocker();
        MaildirStore store = new MaildirStore(MAILDIR_HOME + "/%user", locker);
        mailboxSessionMapperFactory = new MaildirMailboxSessionMapperFactory(store);
        StoreSubscriptionManager sm = new StoreSubscriptionManager(mailboxSessionMapperFactory);
        
        MailboxACLResolver aclResolver = new UnionMailboxACLResolver();
        GroupMembershipResolver groupMembershipResolver = new SimpleGroupMembershipResolver();

        mailboxManager = new StoreMailboxManager<MaildirId>(mailboxSessionMapperFactory, userManager, locker, aclResolver, groupMembershipResolver);
        mailboxManager.init();

        final ImapProcessor defaultImapProcessorFactory = DefaultImapProcessorFactory.createDefaultProcessor(mailboxManager, sm, new NoQuotaManager(), new DefaultQuotaRootResolver(mailboxSessionMapperFactory));
        configure(new DefaultImapDecoderFactory().buildImapDecoder(),
                new DefaultImapEncoderFactory().buildImapEncoder(),
                defaultImapProcessorFactory);
        (new File(MAILDIR_HOME)).mkdirs();
    }
    
    public boolean addUser(String user, String password) throws Exception {
        userManager.addUser(user, password);
        return true;
    }

    @Override
    public void resetData() throws Exception {
        resetUserMetaData();
        try {
        	FileUtils.deleteDirectory(new File(MAILDIR_HOME));
        } catch (Exception e) {
        	e.printStackTrace();
        }
    }
    
    public void resetUserMetaData() throws Exception {
        File dir = new File(META_DATA_DIRECTORY);
        if (dir.exists()) {
            FileUtils.deleteDirectory(dir);
        }
        dir.mkdirs();
    }

    @Override
    public void createMailbox(MailboxPath mailboxPath) throws Exception {
        new MailboxCreationDelegate(mailboxManager).createMailbox(mailboxPath);
    }



}
