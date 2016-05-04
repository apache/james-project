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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

import java.util.stream.LongStream;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.init.CassandraModuleComposite;
import org.apache.james.mailbox.cassandra.modules.CassandraAclModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxModule;
import org.apache.james.mailbox.cassandra.modules.CassandraUidModule;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailbox;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.github.fge.lambdas.Throwing;

public class CassandraUidProviderTest {

    private static final CassandraCluster CASSANDRA = CassandraCluster.create(new CassandraModuleComposite(
        new CassandraAclModule(),
        new CassandraMailboxModule(),
        new CassandraUidModule()));
    
    private static final int MAX_RETRY = 100;
    
    private CassandraUidProvider uidProvider;
    private CassandraMailboxMapper mapper;
    private SimpleMailbox mailbox;

    @Before
    public void setUpClass() throws Exception {
        CASSANDRA.ensureAllTables();
        uidProvider = new CassandraUidProvider(CASSANDRA.getConf());
        mapper = new CassandraMailboxMapper(CASSANDRA.getConf(), CASSANDRA.getTypesProvider(), MAX_RETRY);
        MailboxPath path = new MailboxPath("gsoc", "ieugen", "Trash");
        mailbox = new SimpleMailbox(path, 1234);
        mapper.save(mailbox);
    }
    
    @After
    public void cleanUp() {
        CASSANDRA.clearAllTables();
    }

    @Test
    public void lastUidShouldRetrieveValueStoredByNextUid() throws Exception {
        int nbEntries = 100;
        long result = uidProvider.lastUid(null, mailbox);
        assertEquals(0, result);
        LongStream.range(0, nbEntries)
            .forEach(Throwing.longConsumer(value -> {
                        long uid = uidProvider.nextUid(null, mailbox);
                        assertThat(uid).isEqualTo(uidProvider.lastUid(null, mailbox));
                })
            );
    }

    @Test
    public void nextUidShouldIncrementValueByOne() throws Exception {
        int nbEntries = 100;
        long lastUid = uidProvider.lastUid(null, mailbox);
        LongStream.range(lastUid + 1, lastUid + nbEntries)
            .forEach(Throwing.longConsumer(value -> {
                        long result = uidProvider.nextUid(null, mailbox);
                        assertThat(value).isEqualTo(result);
                })
            );
    }

    @Test
    public void nextUidShouldGenerateUniqueValuesWhenParallelCalls() throws Exception {
        int nbEntries = 100;
        long nbValues = LongStream.range(0, nbEntries)
            .parallel()
            .map(Throwing.longUnaryOperator(x -> uidProvider.nextUid(null, mailbox)))
            .distinct()
            .count();
        assertThat(nbValues).isEqualTo(nbEntries);
    }
}
