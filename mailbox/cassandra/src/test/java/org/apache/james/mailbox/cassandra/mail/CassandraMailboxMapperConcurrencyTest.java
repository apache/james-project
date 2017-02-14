/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.mailbox.cassandra.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.init.CassandraModuleComposite;
import org.apache.james.mailbox.cassandra.modules.CassandraAclModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxModule;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailbox;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.junit.Before;
import org.junit.Test;


public class CassandraMailboxMapperConcurrencyTest {

    public static final int MAX_RETRY = 10;
    public static final int UID_VALIDITY = 52;
    public static final MailboxPath MAILBOX_PATH = new MailboxPath("#private", "user", "name");
    public static final int THREAD_COUNT = 10;
    public static final int OPERATION_COUNT = 10;
    private CassandraCluster cassandra;
    private CassandraMailboxMapper testee;

    @Before
    public void setUp() {
        cassandra = CassandraCluster.create(new CassandraModuleComposite(new CassandraMailboxModule(), new CassandraAclModule()));
        cassandra.ensureAllTables();

        CassandraMailboxDAO mailboxDAO = new CassandraMailboxDAO(cassandra.getConf(), cassandra.getTypesProvider(), MAX_RETRY);
        CassandraMailboxPathDAO mailboxPathDAO = new CassandraMailboxPathDAO(cassandra.getConf(), cassandra.getTypesProvider());
        testee = new CassandraMailboxMapper(cassandra.getConf(), mailboxDAO, mailboxPathDAO, MAX_RETRY);
    }

    @Test
    public void saveShouldBeThreadSafe() throws Exception {
        boolean termination = new ConcurrentTestRunner(THREAD_COUNT, OPERATION_COUNT,
            (a, b) -> testee.save(new SimpleMailbox(MAILBOX_PATH, UID_VALIDITY)))
            .run()
            .awaitTermination(1, TimeUnit.MINUTES);

        assertThat(termination).isTrue();
        assertThat(testee.list()).hasSize(1);
    }
}
