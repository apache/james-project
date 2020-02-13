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

import static org.mockito.Mockito.spy;

import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.init.CassandraTypesProvider;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxPathDAOImpl;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxPathV2DAO;
import org.apache.james.mailbox.cassandra.mail.MailboxAggregateModule;
import org.apache.james.mailbox.store.PreDeletionHooks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.datastax.driver.core.Session;

class CassandraMailboxManagerConsistencyTest {
    @RegisterExtension
    static CassandraClusterExtension cassandra = new CassandraClusterExtension(MailboxAggregateModule.MODULE_WITH_QUOTA);

    private CassandraMailboxManager testee;

    @BeforeEach
    void setUp() {
        Session session = cassandra.getCassandraCluster().getConf();
        CassandraTypesProvider typesProvider = cassandra.getCassandraCluster().getTypesProvider();

        CassandraMailboxDAO mailboxDAO = spy(new CassandraMailboxDAO(session, typesProvider));
        CassandraMailboxPathDAOImpl mailboxPathDAO = spy(new CassandraMailboxPathDAOImpl(session, typesProvider));
        CassandraMailboxPathV2DAO mailboxPathV2DAO = spy(new CassandraMailboxPathV2DAO(session, CassandraUtils.WITH_DEFAULT_CONFIGURATION));

        testee = CassandraMailboxManagerProvider.provideMailboxManager(
            session,
            typesProvider,
            PreDeletionHooks.NO_PRE_DELETION_HOOK,
            binder -> binder.bind(CassandraMailboxDAO.class).toInstance(mailboxDAO),
            binder -> binder.bind(CassandraMailboxPathDAOImpl.class).toInstance(mailboxPathDAO),
            binder -> binder.bind(CassandraMailboxPathV2DAO.class).toInstance(mailboxPathV2DAO));
    }
}
