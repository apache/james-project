/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 * *
 * http://www.apache.org/licenses/LICENSE-2.0                 *
 * *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.mailbox.cassandra;

import static org.mockito.Mockito.mock;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.quota.CassandraCurrentQuotaManager;
import org.apache.james.mailbox.cassandra.quota.CassandraPerUserMaxQuotaManager;
import org.apache.james.mailbox.quota.CurrentQuotaManager;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.store.Authenticator;
import org.apache.james.mailbox.store.Authorizator;
import org.apache.james.mailbox.store.NoMailboxPathLocker;
import org.apache.james.mailbox.store.StoreMessageIdManager;
import org.apache.james.mailbox.store.event.MailboxEventDispatcher;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.apache.james.mailbox.store.quota.DefaultQuotaRootResolver;
import org.apache.james.mailbox.store.quota.StoreQuotaManager;

public class CassandraTestSystemFixture {
    
    public static final int MOD_SEQ = 452;

    public static CassandraMailboxSessionMapperFactory createMapperFactory(CassandraCluster cassandra) {
        CassandraMessageId.Factory messageIdFactory = new CassandraMessageId.Factory();

        return TestCassandraMailboxSessionMapperFactory.forTests(
            cassandra.getConf(),
            cassandra.getTypesProvider(),
            messageIdFactory);
    }

    public static CassandraMailboxManager createMailboxManager(CassandraMailboxSessionMapperFactory mapperFactory) throws Exception{
        CassandraMailboxManager cassandraMailboxManager = new CassandraMailboxManager(mapperFactory, mock(Authenticator.class), mock(Authorizator.class),
            new NoMailboxPathLocker(), new MessageParser(), new CassandraMessageId.Factory());
        cassandraMailboxManager.init();

        return cassandraMailboxManager;
    }

    public static StoreMessageIdManager createMessageIdManager(CassandraMailboxSessionMapperFactory mapperFactory, QuotaManager quotaManager, MailboxEventDispatcher dispatcher) throws Exception {
        return new StoreMessageIdManager(
            createMailboxManager(mapperFactory),
            mapperFactory,
            dispatcher,
            new CassandraMessageId.Factory(),
            quotaManager,
            new DefaultQuotaRootResolver(mapperFactory));
    }

    public static MaxQuotaManager createMaxQuotaManager(CassandraCluster cassandra) {
        return new CassandraPerUserMaxQuotaManager(cassandra.getConf());
    }

    public static CurrentQuotaManager createCurrentQuotaManager(CassandraCluster cassandra) {
        return new CassandraCurrentQuotaManager(cassandra.getConf());
    }

    public static QuotaManager createQuotaManager(CassandraCluster cassandra, MaxQuotaManager maxQuotaManager) {
        return new StoreQuotaManager(new CassandraCurrentQuotaManager(cassandra.getConf()), maxQuotaManager);
    }

}
