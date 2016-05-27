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
package org.apache.james.mailbox.cassandra.mail;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.init.CassandraModuleComposite;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.cassandra.CassandraMailboxManager;
import org.apache.james.mailbox.cassandra.CassandraMailboxSessionMapperFactory;
import org.apache.james.mailbox.cassandra.modules.CassandraAclModule;
import org.apache.james.mailbox.cassandra.modules.CassandraAttachmentModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxCounterModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMessageModule;
import org.apache.james.mailbox.cassandra.modules.CassandraModSeqModule;
import org.apache.james.mailbox.cassandra.modules.CassandraUidModule;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.store.AbstractMailboxManagerTest;
import org.apache.james.mailbox.store.JVMMailboxPathLocker;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;

public class CassandraMailboxMapperTest extends AbstractMailboxManagerTest {
    private static final CassandraCluster cassandra = CassandraCluster.create(new CassandraModuleComposite(
            new CassandraAclModule(),
            new CassandraMailboxModule(),
            new CassandraMessageModule(),
            new CassandraMailboxCounterModule(),
            new CassandraModSeqModule(),
            new CassandraUidModule(),
            new CassandraAttachmentModule()));

    private CassandraMailboxSessionMapperFactory mailboxSessionMapperFactory;
    private CassandraMailboxManager mailboxManager;

    public CassandraMailboxMapperTest() throws MailboxException {
        mailboxSessionMapperFactory = new CassandraMailboxSessionMapperFactory(
                new CassandraUidProvider(cassandra.getConf()),
                new CassandraModSeqProvider(cassandra.getConf()),
                cassandra.getConf(),
                cassandra.getTypesProvider());
        mailboxManager = new CassandraMailboxManager(mailboxSessionMapperFactory, null, new JVMMailboxPathLocker());
        mailboxManager.init();
    }

    @Override
    protected MailboxManager getMailboxManager() {
        return mailboxManager;
    }

    @Override
    protected MailboxSessionMapperFactory getMailboxSessionMapperFactory() {
        return mailboxSessionMapperFactory;
    }

    @Override
    protected void clean() {
        cassandra.clearAllTables();
    }
}
