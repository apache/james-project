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
package org.apache.james.mailbox.cassandra;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.init.CassandraModuleComposite;
import org.apache.james.mailbox.acl.GroupMembershipResolver;
import org.apache.james.mailbox.acl.MailboxACLResolver;
import org.apache.james.mailbox.acl.SimpleGroupMembershipResolver;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.cassandra.mail.CassandraFirstUnseenDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxCounterDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxPathDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxRecentsDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdToImapUidDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraModSeqProvider;
import org.apache.james.mailbox.cassandra.mail.CassandraUidProvider;
import org.apache.james.mailbox.cassandra.modules.CassandraAclModule;
import org.apache.james.mailbox.cassandra.modules.CassandraAnnotationModule;
import org.apache.james.mailbox.cassandra.modules.CassandraAttachmentModule;
import org.apache.james.mailbox.cassandra.modules.CassandraFirstUnseenModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxCounterModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxRecentsModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMessageModule;
import org.apache.james.mailbox.cassandra.modules.CassandraModSeqModule;
import org.apache.james.mailbox.cassandra.modules.CassandraSubscriptionModule;
import org.apache.james.mailbox.cassandra.modules.CassandraUidModule;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.store.Authenticator;
import org.apache.james.mailbox.store.Authorizator;
import org.apache.james.mailbox.store.NoMailboxPathLocker;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.junit.runner.RunWith;
import org.xenei.junit.contract.Contract;
import org.xenei.junit.contract.ContractImpl;
import org.xenei.junit.contract.ContractSuite;
import org.xenei.junit.contract.IProducer;

import com.google.common.base.Throwables;

@RunWith(ContractSuite.class)
@ContractImpl(CassandraMailboxManager.class)
public class CassandraMailboxManagerTest {
    private static final int LIMIT_ANNOTATIONS = 3;
    private static final int LIMIT_ANNOTATION_SIZE = 30;
    public static final int MAX_ACL_RETRY = 10;

    private static final CassandraCluster CASSANDRA = CassandraCluster.create(new CassandraModuleComposite(
        new CassandraAclModule(),
        new CassandraMailboxModule(),
        new CassandraMessageModule(),
        new CassandraMailboxCounterModule(),
        new CassandraMailboxRecentsModule(),
        new CassandraFirstUnseenModule(),
        new CassandraUidModule(),
        new CassandraModSeqModule(),
        new CassandraSubscriptionModule(),
        new CassandraAttachmentModule(),
        new CassandraAnnotationModule()));

    private IProducer<CassandraMailboxManager> producer = new IProducer<CassandraMailboxManager>() {

        @Override
        public CassandraMailboxManager newInstance() {
            CASSANDRA.ensureAllTables();
            CassandraUidProvider uidProvider = new CassandraUidProvider(CASSANDRA.getConf());
            CassandraModSeqProvider modSeqProvider = new CassandraModSeqProvider(CASSANDRA.getConf());
            CassandraMessageId.Factory messageIdFactory = new CassandraMessageId.Factory();
            CassandraMessageIdDAO messageIdDAO = new CassandraMessageIdDAO(CASSANDRA.getConf(), messageIdFactory);
            CassandraMessageIdToImapUidDAO imapUidDAO = new CassandraMessageIdToImapUidDAO(CASSANDRA.getConf(), messageIdFactory);
            CassandraMessageDAO messageDAO = new CassandraMessageDAO(CASSANDRA.getConf(), CASSANDRA.getTypesProvider(), messageIdFactory);
            CassandraMailboxCounterDAO mailboxCounterDAO = new CassandraMailboxCounterDAO(CASSANDRA.getConf());
            CassandraMailboxRecentsDAO mailboxRecentsDAO = new CassandraMailboxRecentsDAO(CASSANDRA.getConf());
            CassandraMailboxDAO mailboxDAO = new CassandraMailboxDAO(CASSANDRA.getConf(), CASSANDRA.getTypesProvider(), MAX_ACL_RETRY);
            CassandraMailboxPathDAO mailboxPathDAO = new CassandraMailboxPathDAO(CASSANDRA.getConf(), CASSANDRA.getTypesProvider());
            CassandraFirstUnseenDAO firstUnseenDAO = new CassandraFirstUnseenDAO(CASSANDRA.getConf());

            CassandraMailboxSessionMapperFactory mapperFactory = new CassandraMailboxSessionMapperFactory(uidProvider,
                modSeqProvider,
                CASSANDRA.getConf(),
                messageDAO,
                messageIdDAO,
                imapUidDAO,
                mailboxCounterDAO,
                mailboxRecentsDAO,
                mailboxDAO,
                mailboxPathDAO,
                firstUnseenDAO);

            MailboxACLResolver aclResolver = new UnionMailboxACLResolver();
            GroupMembershipResolver groupMembershipResolver = new SimpleGroupMembershipResolver();
            MessageParser messageParser = new MessageParser();

            Authenticator noAuthenticator = null;
            Authorizator noAuthorizator = null;
            CassandraMailboxManager manager = new CassandraMailboxManager(mapperFactory, noAuthenticator, noAuthorizator, new NoMailboxPathLocker(), aclResolver, groupMembershipResolver, 
                    messageParser, messageIdFactory, LIMIT_ANNOTATIONS, LIMIT_ANNOTATION_SIZE);
            try {
                manager.init();
            } catch (MailboxException e) {
                throw Throwables.propagate(e);
            }

            return manager;
        }

        @Override
        public void cleanUp() {
            CASSANDRA.clearAllTables();
        }
    };

    @Contract.Inject
    public IProducer<CassandraMailboxManager> getProducer() {
        return producer;
    }

}
