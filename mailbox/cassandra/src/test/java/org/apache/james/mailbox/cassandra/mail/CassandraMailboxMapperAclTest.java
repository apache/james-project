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
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionDataDefinition;
import org.apache.james.blob.cassandra.CassandraBlobDataDefinition;
import org.apache.james.eventsourcing.eventstore.cassandra.CassandraEventStoreDataDefinition;
import org.apache.james.junit.categories.Unstable;
import org.apache.james.mailbox.cassandra.mail.utils.GuiceUtils;
import org.apache.james.mailbox.cassandra.modules.CassandraAclDataDefinition;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxDataDefinition;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.model.MailboxMapperACLTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraMailboxMapperAclTest extends MailboxMapperACLTest {

    private static final CassandraDataDefinition MODULES = CassandraDataDefinition.aggregateModules(
        CassandraEventStoreDataDefinition.MODULE(),
        CassandraSchemaVersionDataDefinition.MODULE,
        CassandraAclDataDefinition.MODULE,
        CassandraMailboxDataDefinition.MODULE,
        CassandraBlobDataDefinition.MODULE);

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(MODULES);

    @Override
    protected MailboxMapper createMailboxMapper() {
        return GuiceUtils.testInjector(cassandraCluster.getCassandraCluster())
            .getInstance(CassandraMailboxMapper.class);
    }

    @Test
    @Override
    @Tag(Unstable.TAG)
    protected void updateAclShouldCombineStoredAclWhenAdd() {
        super.updateAclShouldCombineStoredAclWhenAdd();
    }
}
