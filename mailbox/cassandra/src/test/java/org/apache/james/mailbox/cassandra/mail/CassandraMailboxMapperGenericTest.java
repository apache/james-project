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

import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraDataDefinition;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionDAO;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionDataDefinition;
import org.apache.james.backends.cassandra.versions.SchemaVersion;
import org.apache.james.blob.cassandra.CassandraBlobDataDefinition;
import org.apache.james.eventsourcing.eventstore.cassandra.CassandraEventStoreDataDefinition;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.mail.utils.GuiceUtils;
import org.apache.james.mailbox.cassandra.modules.CassandraAclDataDefinition;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxDataDefinition;
import org.apache.james.mailbox.cassandra.modules.CassandraModSeqDataDefinition;
import org.apache.james.mailbox.cassandra.modules.CassandraUidDataDefinition;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.model.MailboxMapperTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraMailboxMapperGenericTest {
    private static final CassandraDataDefinition MODULES = CassandraDataDefinition.aggregateModules(
        CassandraAclDataDefinition.MODULE,
        CassandraBlobDataDefinition.MODULE,
        CassandraEventStoreDataDefinition.MODULE(),
        CassandraMailboxDataDefinition.MODULE,
        CassandraModSeqDataDefinition.MODULE,
        CassandraSchemaVersionDataDefinition.MODULE,
        CassandraUidDataDefinition.MODULE);

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(MODULES);

    @Nested
    class V5 extends MailboxMapperTest {
        @Override
        protected MailboxMapper createMailboxMapper() {
            return GuiceUtils.testInjector(cassandraCluster.getCassandraCluster())
                .getInstance(CassandraMailboxMapper.class);
        }

        @Override
        protected MailboxId generateId() {
            return CassandraId.timeBased();
        }
    }

    @Nested
    class V7 extends MailboxMapperTest {
        @Override
        protected MailboxMapper createMailboxMapper() {
            new CassandraSchemaVersionDAO(cassandraCluster.getCassandraCluster().getConf())
                .updateVersion(new SchemaVersion(7))
                .block();
            return GuiceUtils.testInjector(cassandraCluster.getCassandraCluster())
                .getInstance(CassandraMailboxMapper.class);
        }

        @Override
        protected MailboxId generateId() {
            return CassandraId.timeBased();
        }
    }

    @Nested
    class V10 extends MailboxMapperTest {
        @Override
        protected MailboxMapper createMailboxMapper() {
            new CassandraSchemaVersionDAO(cassandraCluster.getCassandraCluster().getConf())
                .updateVersion(new SchemaVersion(10))
                .block();
            return GuiceUtils.testInjector(cassandraCluster.getCassandraCluster())
                .getInstance(CassandraMailboxMapper.class);
        }

        @Override
        protected MailboxId generateId() {
            return CassandraId.timeBased();
        }
    }

    @Nested
    class V10RelaxedConsistency extends MailboxMapperTest {
        @Override
        protected MailboxMapper createMailboxMapper() {
            new CassandraSchemaVersionDAO(cassandraCluster.getCassandraCluster().getConf())
                .updateVersion(new SchemaVersion(10))
                .block();
            return GuiceUtils.testInjector(cassandraCluster.getCassandraCluster().getConf(),
                cassandraCluster.getCassandraCluster().getTypesProvider(), new CassandraMessageId.Factory(),
                CassandraConfiguration.builder()
                    .mailboxReadStrongConsistency(false)
                    .build())
                .getInstance(CassandraMailboxMapper.class);
        }

        @Override
        protected MailboxId generateId() {
            return CassandraId.timeBased();
        }
    }
}
