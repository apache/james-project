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
import org.apache.commons.lang.NotImplementedException;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.encode.main.DefaultImapEncoderFactory;
import org.apache.james.imap.main.DefaultImapDecoderFactory;
import org.apache.james.imap.processor.main.DefaultImapProcessorFactory;
import org.apache.james.mailbox.acl.GroupMembershipResolver;
import org.apache.james.mailbox.acl.MailboxACLResolver;
import org.apache.james.mailbox.acl.SimpleGroupMembershipResolver;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.maildir.MaildirMailboxSessionMapperFactory;
import org.apache.james.mailbox.maildir.MaildirStore;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.JVMMailboxPathLocker;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
import org.apache.james.mailbox.store.mail.model.DefaultMessageId;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.apache.james.mailbox.store.quota.DefaultQuotaRootResolver;
import org.apache.james.mailbox.store.quota.NoQuotaManager;
import org.apache.james.metrics.logger.DefaultMetricFactory;
import org.apache.james.mpt.api.ImapFeatures;
import org.apache.james.mpt.api.ImapFeatures.Feature;
import org.apache.james.mpt.host.JamesImapHostSystem;
import org.apache.james.mpt.imapmailbox.MailboxCreationDelegate;

public class MaildirHostSystem extends JamesImapHostSystem {

    public static final String META_DATA_DIRECTORY = "target/user-meta-data";
    private static final String MAILDIR_HOME = "target/Maildir";
    private static final ImapFeatures SUPPORTED_FEATURES = ImapFeatures.of();
    
    private StoreMailboxManager mailboxManager;

    public static JamesImapHostSystem build() throws Exception {
        return new MaildirHostSystem();
    }
    
    @Override
    public void beforeTest() throws Exception {
        super.beforeTest();
        JVMMailboxPathLocker locker = new JVMMailboxPathLocker();
        MaildirStore store = new MaildirStore(MAILDIR_HOME + "/%user", locker);
        MaildirMailboxSessionMapperFactory mailboxSessionMapperFactory = new MaildirMailboxSessionMapperFactory(store);
        StoreSubscriptionManager sm = new StoreSubscriptionManager(mailboxSessionMapperFactory);
        
        MailboxACLResolver aclResolver = new UnionMailboxACLResolver();
        GroupMembershipResolver groupMembershipResolver = new SimpleGroupMembershipResolver();
        MessageParser messageParser = new MessageParser();

        mailboxManager = new StoreMailboxManager(mailboxSessionMapperFactory, authenticator, authorizator, locker, aclResolver,
                groupMembershipResolver, messageParser, new DefaultMessageId.Factory());
        mailboxManager.init();

        final ImapProcessor defaultImapProcessorFactory = 
                DefaultImapProcessorFactory.createDefaultProcessor(
                        mailboxManager, 
                        sm, 
                        new NoQuotaManager(), 
                        new DefaultQuotaRootResolver(mailboxSessionMapperFactory),
                        new DefaultMetricFactory());
        configure(new DefaultImapDecoderFactory().buildImapDecoder(),
                new DefaultImapEncoderFactory().buildImapEncoder(),
                defaultImapProcessorFactory);
        (new File(MAILDIR_HOME)).mkdirs();
    }


    @Override
    public void afterTest() throws Exception {
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

    @Override
    public boolean supports(Feature... features) {
        return SUPPORTED_FEATURES.supports(features);
    }

    @Override
    public void setQuotaLimits(long maxMessageQuota, long maxStorageQuota) throws Exception {
        throw new NotImplementedException();
    }

    @Override
    public void grantRights(MailboxPath mailboxPath, String userName, MailboxACL.Rfc4314Rights rights) throws Exception {
        mailboxManager.setRights(mailboxPath,
            MailboxACL.EMPTY.apply(MailboxACL.command()
                .forUser(userName)
                .rights(rights)
                .asAddition()),
            mailboxManager.createSystemSession(userName));
    }
}
