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
package org.apache.james.mpt.imapmailbox.jcr.host;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.apache.jackrabbit.core.RepositoryImpl;
import org.apache.jackrabbit.core.config.RepositoryConfig;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.encode.main.DefaultImapEncoderFactory;
import org.apache.james.imap.main.DefaultImapDecoderFactory;
import org.apache.james.imap.processor.main.DefaultImapProcessorFactory;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.acl.GroupMembershipResolver;
import org.apache.james.mailbox.acl.MailboxACLResolver;
import org.apache.james.mailbox.acl.SimpleGroupMembershipResolver;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.jcr.GlobalMailboxSessionJCRRepository;
import org.apache.james.mailbox.jcr.JCRId;
import org.apache.james.mailbox.jcr.JCRMailboxManager;
import org.apache.james.mailbox.jcr.JCRMailboxSessionMapperFactory;
import org.apache.james.mailbox.jcr.JCRSubscriptionManager;
import org.apache.james.mailbox.jcr.JCRUtils;
import org.apache.james.mailbox.jcr.mail.JCRModSeqProvider;
import org.apache.james.mailbox.jcr.mail.JCRUidProvider;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.JVMMailboxPathLocker;
import org.apache.james.mailbox.store.MockAuthenticator;
import org.apache.james.mailbox.store.quota.DefaultQuotaRootResolver;
import org.apache.james.mailbox.store.quota.NoQuotaManager;
import org.apache.james.mpt.host.JamesImapHostSystem;
import org.apache.james.mpt.imapmailbox.MailboxCreationDelegate;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;

public class JCRHostSystem extends JamesImapHostSystem{

    public static JamesImapHostSystem build() throws Exception {
        return new JCRHostSystem();
    }
    
    private final JCRMailboxManager mailboxManager;
    private final MockAuthenticator userManager; 

    private static final String JACKRABBIT_HOME = "target/jackrabbit";
    public static final String META_DATA_DIRECTORY = "target/user-meta-data";
    private RepositoryImpl repository;
    
    public JCRHostSystem() throws Exception {

        delete(new File(JACKRABBIT_HOME));
        
        try {
            
            String user = "user";
            String pass = "pass";
            String workspace = null;
            RepositoryConfig config = RepositoryConfig.create(new InputSource(this.getClass().getClassLoader().getResourceAsStream("test-repository.xml")), JACKRABBIT_HOME);
            repository =  RepositoryImpl.create(config);
            GlobalMailboxSessionJCRRepository sessionRepos = new GlobalMailboxSessionJCRRepository(repository, workspace, user, pass);
            
            // Register imap cnd file
            JCRUtils.registerCnd(repository, workspace, user, pass);
            
            userManager = new MockAuthenticator();
            JVMMailboxPathLocker locker = new JVMMailboxPathLocker();
            JCRUidProvider uidProvider = new JCRUidProvider(locker, sessionRepos);
            JCRModSeqProvider modSeqProvider = new JCRModSeqProvider(locker, sessionRepos);
            JCRMailboxSessionMapperFactory mf = new JCRMailboxSessionMapperFactory(sessionRepos, uidProvider, modSeqProvider);

            MailboxACLResolver aclResolver = new UnionMailboxACLResolver();
            GroupMembershipResolver groupMembershipResolver = new SimpleGroupMembershipResolver();

            mailboxManager = new JCRMailboxManager(mf, userManager, locker, aclResolver, groupMembershipResolver);
            mailboxManager.init();

            final ImapProcessor defaultImapProcessorFactory = DefaultImapProcessorFactory.createDefaultProcessor(mailboxManager, new JCRSubscriptionManager(mf), new NoQuotaManager(), new DefaultQuotaRootResolver(mf));
            resetUserMetaData();
            MailboxSession session = mailboxManager.createSystemSession("test", LoggerFactory.getLogger("TestLog"));
            mailboxManager.startProcessingRequest(session);
            //mailboxManager.deleteEverything(session);
            mailboxManager.endProcessingRequest(session);
            mailboxManager.logout(session, false);
            
            configure(new DefaultImapDecoderFactory().buildImapDecoder(), new DefaultImapEncoderFactory().buildImapEncoder(), defaultImapProcessorFactory);
        } catch (Exception e) {
            shutdownRepository();
            throw e;
        }
    }

   
    public boolean addUser(String user, String password) {
        userManager.addUser(user, password);
        return true;
    }

    public void resetData() throws Exception {
        resetUserMetaData();
      
    }
    
    public void resetUserMetaData() throws Exception {
        File dir = new File(META_DATA_DIRECTORY);
        if (dir.exists()) {
            FileUtils.deleteDirectory(dir);
        }
        dir.mkdirs();
    }


    @Override
    public void afterTests() throws Exception {
        shutdownRepository();
    }
    
    private void shutdownRepository() throws Exception{
        if (repository != null) {
            repository.shutdown();
            repository = null;
        }
    }
    
    private void delete(File home) throws Exception{
        if (home.exists()) {
            File[] files = home.listFiles();
            if (files == null) return;
            for (int i = 0;i < files.length; i++) {
                File f = files[i];
                if (f.isDirectory()) {
                    delete(f);
                } else {
                    f.delete();
                }            
            }
            home.delete();
        }
    }

    @Override
    public void createMailbox(MailboxPath mailboxPath) throws Exception {
        new MailboxCreationDelegate(mailboxManager).createMailbox(mailboxPath);
    }
    
}
