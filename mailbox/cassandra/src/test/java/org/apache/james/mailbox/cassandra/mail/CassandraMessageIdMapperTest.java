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

import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.CassandraRestartExtension;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.cassandra.CassandraMailboxSessionMapperFactory;
import org.apache.james.mailbox.cassandra.TestCassandraMailboxSessionMapperFactory;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.MessageIdMapperTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableList;

@ExtendWith(CassandraRestartExtension.class)
class CassandraMessageIdMapperTest extends MessageIdMapperTest {

    private static final MailboxSession MAILBOX_SESSION = MailboxSessionUtil.create(Username.of("benwa"));

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(MailboxAggregateModule.MODULE);
    
    @Override
    protected CassandraMapperProvider provideMapper() {
        return new CassandraMapperProvider(cassandraCluster.getCassandraCluster());
    }

    @Test
    void findShouldReturnCorrectElementsWhenChunking() throws Exception {
        CassandraMessageId.Factory messageIdFactory = new CassandraMessageId.Factory();
        CassandraMailboxSessionMapperFactory mapperFactory = TestCassandraMailboxSessionMapperFactory.forTests(
            cassandraCluster.getCassandraCluster().getConf(),
            cassandraCluster.getCassandraCluster().getTypesProvider(),
            messageIdFactory,
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
