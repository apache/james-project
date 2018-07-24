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

import java.util.List;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.DockerCassandraRule;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.mailbox.cassandra.CassandraMailboxSessionMapperFactory;
import org.apache.james.mailbox.cassandra.TestCassandraMailboxSessionMapperFactory;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.mock.MockMailboxSession;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.MessageIdMapperTest;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import com.google.common.collect.ImmutableList;

public class CassandraMessageIdMapperTest extends MessageIdMapperTest {

    public static final MockMailboxSession MAILBOX_SESSION = new MockMailboxSession("benwa");
    @ClassRule public static DockerCassandraRule cassandraServer = new DockerCassandraRule();

    private static CassandraCluster cassandra;

    @BeforeClass
    public static void setUpClass() {
        cassandra = CassandraCluster.create(MailboxAggregateModule.MODULE, cassandraServer.getHost());
    }

    @Override
    @Before
    public void setUp() throws MailboxException {
        super.setUp();
    }
    
    @After
    public void tearDown() {
        cassandra.clearTables();
    }

    @AfterClass
    public static void tearDownClass() {
        cassandra.closeCluster();
    }
    
    @Override
    protected CassandraMapperProvider provideMapper() {
        return new CassandraMapperProvider(cassandra);
    }

    @Test
    public void findShouldReturnCorrectElementsWhenChunking() throws Exception {
        CassandraMessageId.Factory messageIdFactory = new CassandraMessageId.Factory();
        CassandraMailboxSessionMapperFactory mapperFactory = TestCassandraMailboxSessionMapperFactory.forTests(
            cassandra.getConf(), cassandra.getTypesProvider(), messageIdFactory,
            CassandraConfiguration.builder()
                .messageReadChunkSize(3)
                .build());

        saveMessages();

        List<MailboxMessage> messages = mapperFactory.getMessageIdMapper(MAILBOX_SESSION)
            .find(
                ImmutableList.of(message1.getMessageId(),
                    message2.getMessageId(),
                    message3.getMessageId(),
                    message4.getMessageId()),
                MessageMapper.FetchType.Metadata);

        assertThat(messages)
            .containsOnly(message1, message2, message3, message4);
    }
}
