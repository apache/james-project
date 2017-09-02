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
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxManagerTest;
import org.apache.james.mailbox.cassandra.modules.CassandraAclModule;
import org.apache.james.mailbox.cassandra.modules.CassandraAnnotationModule;
import org.apache.james.mailbox.cassandra.modules.CassandraApplicableFlagsModule;
import org.apache.james.mailbox.cassandra.modules.CassandraAttachmentModule;
import org.apache.james.mailbox.cassandra.modules.CassandraBlobModule;
import org.apache.james.mailbox.cassandra.modules.CassandraDeletedMessageModule;
import org.apache.james.mailbox.cassandra.modules.CassandraFirstUnseenModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxCounterModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxRecentsModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMessageModule;
import org.apache.james.mailbox.cassandra.modules.CassandraModSeqModule;
import org.apache.james.mailbox.cassandra.modules.CassandraSubscriptionModule;
import org.apache.james.mailbox.cassandra.modules.CassandraUidModule;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;

public class CassandraMailboxManagerTest extends MailboxManagerTest {

    private static CassandraCluster cassandra;
    
    @BeforeClass
    public static void init() {
        cassandra = CassandraCluster.create(
                new CassandraModuleComposite(
                    new CassandraAclModule(),
                    new CassandraMailboxModule(),
                    new CassandraMessageModule(),
                    new CassandraBlobModule(),
                    new CassandraMailboxCounterModule(),
                    new CassandraMailboxRecentsModule(),
                    new CassandraFirstUnseenModule(),
                    new CassandraUidModule(),
                    new CassandraModSeqModule(),
                    new CassandraSubscriptionModule(),
                    new CassandraAttachmentModule(),
                    new CassandraDeletedMessageModule(),
                    new CassandraAnnotationModule(),
                    new CassandraApplicableFlagsModule()));
    }

    @AfterClass
    public static void close() {
        cassandra.close();
    }

    @Override
    protected MailboxManager provideMailboxManager() {
        return CassandraMailboxManagerProvider.provideMailboxManager(cassandra.getConf(), cassandra.getTypesProvider());
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
        cassandra.clearAllTables();
    }

}
