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
import org.apache.james.backends.cassandra.DockerCassandraRule;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxManagerStressTest;
import org.apache.james.mailbox.cassandra.mail.MailboxAggregateModule;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;

public class CassandraMailboxManagerStressTest extends MailboxManagerStressTest {
    
    @ClassRule public static DockerCassandraRule cassandraServer = new DockerCassandraRule();

    private static CassandraCluster cassandra;

    @BeforeClass
    public static void setUpClass() {
        cassandra = CassandraCluster.create(MailboxAggregateModule.MODULE_WITH_QUOTA, cassandraServer.getHost());
    }
    
    @Before
    public void setup() throws Exception {
        super.setUp();
    }
    
    @Override
    protected MailboxManager provideManager() {
        return CassandraMailboxManagerProvider.provideMailboxManager(cassandra.getConf(), cassandra.getTypesProvider());
    }


    @After
    public void tearDown() throws Exception {
        cassandra.clearTables();
    }

    @AfterClass
    public static void tearDownClass() {
        cassandra.closeCluster();
    }
}
