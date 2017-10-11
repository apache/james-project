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
import org.apache.commons.lang.NotImplementedException;
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
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
import org.apache.james.mailbox.store.mail.model.DefaultMessageId;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.apache.james.mailbox.store.quota.DefaultQuotaRootResolver;
import org.apache.james.mailbox.store.quota.NoQuotaManager;
import org.apache.james.metrics.api.NoopMetricFactory;
import org.apache.james.mpt.api.ImapFeatures;
import org.apache.james.mpt.api.ImapFeatures.Feature;
import org.apache.james.mpt.host.JamesImapHostSystem;
import org.apache.james.mpt.imapmailbox.MailboxCreationDelegate;

public class HBaseHostSystem extends JamesImapHostSystem {

    public static final String META_DATA_DIRECTORY = "target/user-meta-data";
    private static final ImapFeatures SUPPORTED_FEATURES = ImapFeatures.of(Feature.NAMESPACE_SUPPORT);

    public static HBaseHostSystem host = null;
    /** Set this to false if you wish to test it against a real cluster.
     * In that case you should provide the configuration file for the real
     * cluster on the classpath. 
     */
    public static Boolean useMiniCluster = true;

    private MiniHBaseCluster hbaseCluster;
    private final Configuration conf;
    private HBaseMailboxManager mailboxManager;

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
    }


    @Override
    public void beforeTest() throws Exception {
        super.beforeTest();
        final HBaseModSeqProvider modSeqProvider = new HBaseModSeqProvider(conf);
        final HBaseUidProvider uidProvider = new HBaseUidProvider(conf);
        DefaultMessageId.Factory messageIdFactory = new DefaultMessageId.Factory();
        final HBaseMailboxSessionMapperFactory mapperFactory = new HBaseMailboxSessionMapperFactory(
                conf, uidProvider, modSeqProvider, messageIdFactory);
        MailboxACLResolver aclResolver = new UnionMailboxACLResolver();
        GroupMembershipResolver groupMembershipResolver = new SimpleGroupMembershipResolver();
        MessageParser messageParser = new MessageParser();
        
        mailboxManager = new HBaseMailboxManager(mapperFactory, authenticator, authorizator, aclResolver, groupMembershipResolver,
                messageParser, messageIdFactory);
        mailboxManager.init();

        SubscriptionManager subscriptionManager = new StoreSubscriptionManager(mapperFactory);

        final ImapProcessor defaultImapProcessorFactory = 
                DefaultImapProcessorFactory.createDefaultProcessor(
                        mailboxManager, 
                        subscriptionManager, 
                        new NoQuotaManager(), 
                        new DefaultQuotaRootResolver(mapperFactory),
                        new NoopMetricFactory());

        resetUserMetaData();

        configure(new DefaultImapDecoderFactory().buildImapDecoder(),
                new DefaultImapEncoderFactory().buildImapEncoder(),
                defaultImapProcessorFactory);
    }
    @Override
    public void afterTest() throws Exception {
        super.afterTest();
        resetUserMetaData();
        if (mailboxManager != null) {
            MailboxSession session = mailboxManager.createSystemSession("test");
            mailboxManager.startProcessingRequest(session);
            mailboxManager.deleteEverything(session);
            mailboxManager.endProcessingRequest(session);
            mailboxManager.logout(session, false);
        }
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
