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
package org.apache.james.mpt.imapmailbox.hbase.host;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.apache.hadoop.hbase.MiniHBaseCluster;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.encode.main.DefaultImapEncoderFactory;
import org.apache.james.imap.main.DefaultImapDecoderFactory;
import org.apache.james.imap.processor.main.DefaultImapProcessorFactory;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.acl.GroupMembershipResolver;
import org.apache.james.mailbox.acl.MailboxACLResolver;
import org.apache.james.mailbox.acl.SimpleGroupMembershipResolver;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.hbase.HBaseMailboxManager;
import org.apache.james.mailbox.hbase.HBaseMailboxSessionMapperFactory;
import org.apache.james.mailbox.hbase.mail.HBaseModSeqProvider;
import org.apache.james.mailbox.hbase.mail.HBaseUidProvider;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.MockAuthenticator;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
import org.apache.james.mailbox.store.quota.DefaultQuotaRootResolver;
import org.apache.james.mailbox.store.quota.NoQuotaManager;
import org.apache.james.mpt.api.ImapFeatures;
import org.apache.james.mpt.api.ImapFeatures.Feature;
import org.apache.james.mpt.host.JamesImapHostSystem;
import org.apache.james.mpt.imapmailbox.MailboxCreationDelegate;
import org.slf4j.LoggerFactory;

public class HBaseHostSystem extends JamesImapHostSystem {

    public static final String META_DATA_DIRECTORY = "target/user-meta-data";
    private static final ImapFeatures SUPPORTED_FEATURES = ImapFeatures.of(Feature.NAMESPACE_SUPPORT);

    public static HBaseHostSystem host = null;
    /** Set this to false if you wish to test it against a real cluster.
     * In that case you should provide the configuration file for the real
     * cluster on the classpath. 
     */
    public static Boolean useMiniCluster = true;
    
    private final HBaseMailboxManager mailboxManager;
    private final MockAuthenticator userManager;
    private MiniHBaseCluster hbaseCluster;
    private Configuration conf;

    public static synchronized JamesImapHostSystem build() throws Exception {
        if (host == null) {
            host = new HBaseHostSystem(useMiniCluster);
        }
        return host;
    }

    public HBaseHostSystem(boolean useMiniCluster) throws Exception {
        if (useMiniCluster) {
            HBaseTestingUtility htu = new HBaseTestingUtility();
            htu.getConfiguration().setBoolean("dfs.support.append", true);
            try {
                hbaseCluster = htu.startMiniCluster();
                conf = hbaseCluster.getConfiguration();
            } catch (Exception e) {
                throw new Exception("Error starting MiniCluster ", e);
            }
        } else {
            conf = HBaseConfiguration.create();
        }

        userManager = new MockAuthenticator();

        final HBaseModSeqProvider modSeqProvider = new HBaseModSeqProvider(conf);
        final HBaseUidProvider uidProvider = new HBaseUidProvider(conf);

        final HBaseMailboxSessionMapperFactory mapperFactory = new HBaseMailboxSessionMapperFactory(
                conf, uidProvider, modSeqProvider);
        MailboxACLResolver aclResolver = new UnionMailboxACLResolver();
        GroupMembershipResolver groupMembershipResolver = new SimpleGroupMembershipResolver();
        
        mailboxManager = new HBaseMailboxManager(mapperFactory, userManager, aclResolver, groupMembershipResolver);
        mailboxManager.init();

        SubscriptionManager subscriptionManager = new StoreSubscriptionManager(mapperFactory);

        final ImapProcessor defaultImapProcessorFactory = DefaultImapProcessorFactory.createDefaultProcessor(mailboxManager, subscriptionManager, new NoQuotaManager(), new DefaultQuotaRootResolver(mapperFactory));

        resetUserMetaData();

        configure(new DefaultImapDecoderFactory().buildImapDecoder(),
                new DefaultImapEncoderFactory().buildImapEncoder(),
                defaultImapProcessorFactory);
    }

    @Override
    protected void resetData() throws Exception {
        resetUserMetaData();
        MailboxSession session = mailboxManager.createSystemSession("test", LoggerFactory.getLogger("TestLog"));
        mailboxManager.startProcessingRequest(session);
        mailboxManager.deleteEverything(session);
        mailboxManager.endProcessingRequest(session);
        mailboxManager.logout(session, false);
    }

    public boolean addUser(String user, String password) {
        userManager.addUser(user, password);
        return true;
    }

    public final void resetUserMetaData() throws Exception {
        File dir = new File(META_DATA_DIRECTORY);
        if (dir.exists()) {
            FileUtils.deleteDirectory(dir);
        }
        dir.mkdirs();
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        if (useMiniCluster) {
            if (hbaseCluster != null) {
                hbaseCluster.shutdown();
            }
        }
    }

    @Override
    public void createMailbox(MailboxPath mailboxPath) throws Exception{
        new MailboxCreationDelegate(mailboxManager).createMailbox(mailboxPath);
    }
    
    @Override
    public boolean supports(Feature... features) {
        return SUPPORTED_FEATURES.supports(features);
    }
}
