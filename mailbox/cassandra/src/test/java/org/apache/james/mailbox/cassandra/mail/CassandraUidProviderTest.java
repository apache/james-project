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

import java.util.Optional;
import java.util.stream.LongStream;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.DockerCassandraRule;
import org.apache.james.backends.cassandra.init.CassandraConfiguration;
import org.apache.james.backends.cassandra.init.CassandraModuleComposite;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.cassandra.modules.CassandraAclModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxModule;
import org.apache.james.mailbox.cassandra.modules.CassandraUidModule;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailbox;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import com.github.fge.lambdas.Throwing;

public class CassandraUidProviderTest {

    @ClassRule public static DockerCassandraRule cassandraServer = new DockerCassandraRule();
    
    
    private final CassandraModuleComposite modules = new CassandraModuleComposite(
            new CassandraAclModule(),
            new CassandraMailboxModule(),
            new CassandraUidModule());
    
    private final CassandraCluster cassandra = CassandraCluster.create(modules, cassandraServer.getIp(), cassandraServer.getBindingPort());
    
    private CassandraUidProvider uidProvider;
    private CassandraMailboxMapper mapper;
    private SimpleMailbox mailbox;

    @Before
    public void setUpClass() throws Exception {
        uidProvider = new CassandraUidProvider(cassandra.getConf());
        CassandraMailboxDAO mailboxDAO = new CassandraMailboxDAO(cassandra.getConf(), cassandra.getTypesProvider());
        CassandraMailboxPathDAO mailboxPathDAO = new CassandraMailboxPathDAO(cassandra.getConf(), cassandra.getTypesProvider());
        mapper = new CassandraMailboxMapper(
            mailboxDAO,
            mailboxPathDAO,
            new CassandraACLMapper(cassandra.getConf(), CassandraConfiguration.DEFAULT_CONFIGURATION),
            CassandraConfiguration.DEFAULT_CONFIGURATION);
        MailboxPath path = new MailboxPath("gsoc", "ieugen", "Trash");
        mailbox = new SimpleMailbox(path, 1234);
        mapper.save(mailbox);
    }
    
    @After
    public void cleanUp() {
        cassandra.close();
    }

    @Test
    public void lastUidShouldRetrieveValueStoredByNextUid() throws Exception {
        int nbEntries = 100;
        Optional<MessageUid> result = uidProvider.lastUid(null, mailbox);
        assertThat(result).isEmpty();
        LongStream.range(0, nbEntries)
            .forEach(Throwing.longConsumer(value -> {
                        MessageUid uid = uidProvider.nextUid(null, mailbox);
                        assertThat(uid).isEqualTo(uidProvider.lastUid(null, mailbox).get());
                })
            );
    }

    @Test
    public void nextUidShouldIncrementValueByOne() throws Exception {
        int nbEntries = 100;
        LongStream.range(1, nbEntries)
            .forEach(Throwing.longConsumer(value -> {
                        MessageUid result = uidProvider.nextUid(null, mailbox);
                        assertThat(value).isEqualTo(result.asLong());
                })
            );
    }

    @Test
    public void nextUidShouldGenerateUniqueValuesWhenParallelCalls() throws Exception {
        int nbEntries = 100;
        long nbValues = LongStream.range(0, nbEntries)
            .parallel()
            .boxed()
            .map(Throwing.function(x -> uidProvider.nextUid(null, mailbox)))
            .distinct()
            .count();
        assertThat(nbValues).isEqualTo(nbEntries);
    }
}
