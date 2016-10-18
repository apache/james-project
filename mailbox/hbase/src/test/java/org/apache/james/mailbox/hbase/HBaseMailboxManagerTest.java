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
package org.apache.james.mailbox.hbase;

import static org.apache.james.mailbox.hbase.HBaseNames.MAILBOXES;
import static org.apache.james.mailbox.hbase.HBaseNames.MAILBOXES_TABLE;
import static org.apache.james.mailbox.hbase.HBaseNames.MAILBOX_CF;
import static org.apache.james.mailbox.hbase.HBaseNames.MESSAGES;
import static org.apache.james.mailbox.hbase.HBaseNames.MESSAGES_META_CF;
import static org.apache.james.mailbox.hbase.HBaseNames.MESSAGES_TABLE;
import static org.apache.james.mailbox.hbase.HBaseNames.MESSAGE_DATA_BODY_CF;
import static org.apache.james.mailbox.hbase.HBaseNames.MESSAGE_DATA_HEADERS_CF;
import static org.apache.james.mailbox.hbase.HBaseNames.SUBSCRIPTIONS;
import static org.apache.james.mailbox.hbase.HBaseNames.SUBSCRIPTIONS_TABLE;
import static org.apache.james.mailbox.hbase.HBaseNames.SUBSCRIPTION_CF;

import org.apache.james.mailbox.acl.SimpleGroupMembershipResolver;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.hbase.mail.HBaseModSeqProvider;
import org.apache.james.mailbox.hbase.mail.HBaseUidProvider;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.mail.model.DefaultMessageId;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.junit.runner.RunWith;
import org.xenei.junit.contract.Contract;
import org.xenei.junit.contract.ContractImpl;
import org.xenei.junit.contract.ContractSuite;
import org.xenei.junit.contract.IProducer;

import com.google.common.base.Throwables;

@RunWith(ContractSuite.class)
@ContractImpl(HBaseMailboxManager.class)
public class HBaseMailboxManagerTest {

    private static final HBaseClusterSingleton CLUSTER = HBaseClusterSingleton.build();

    private IProducer<HBaseMailboxManager> producer = new IProducer<HBaseMailboxManager>() {

        @Override
        public HBaseMailboxManager newInstance() {
            ensureTables();

            HBaseUidProvider uidProvider = new HBaseUidProvider(CLUSTER.getConf());
            HBaseModSeqProvider modSeqProvider = new HBaseModSeqProvider(CLUSTER.getConf());
            MessageId.Factory messageIdFactory = new DefaultMessageId.Factory();
            HBaseMailboxSessionMapperFactory mapperFactory = new HBaseMailboxSessionMapperFactory(CLUSTER.getConf(),
                uidProvider, modSeqProvider, messageIdFactory);

            HBaseMailboxManager manager = new HBaseMailboxManager(mapperFactory,
                null,
                new UnionMailboxACLResolver(),
                new SimpleGroupMembershipResolver(),
                new MessageParser(),
                messageIdFactory
                );

            try {
                manager.init();
            } catch (MailboxException e) {
                throw Throwables.propagate(e);
            }

            return manager;
        }

        @Override
        public void cleanUp() {
            CLUSTER.clearTable(MAILBOXES);
            CLUSTER.clearTable(MESSAGES);
            CLUSTER.clearTable(SUBSCRIPTIONS);
        }
    };

    @Contract.Inject
    public IProducer<HBaseMailboxManager> getProducer() {
        return producer;
    }

    private void ensureTables() {
        try {
            CLUSTER.ensureTable(MAILBOXES_TABLE, new byte[][]{MAILBOX_CF});
            CLUSTER.ensureTable(MESSAGES_TABLE,
                new byte[][]{MESSAGES_META_CF, MESSAGE_DATA_HEADERS_CF, MESSAGE_DATA_BODY_CF});
            CLUSTER.ensureTable(SUBSCRIPTIONS_TABLE, new byte[][]{SUBSCRIPTION_CF});
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }
}
