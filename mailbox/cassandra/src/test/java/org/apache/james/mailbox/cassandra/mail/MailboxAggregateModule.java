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

import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.components.CassandraMutualizedQuotaModule;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.apache.james.blob.cassandra.CassandraBlobModule;
import org.apache.james.eventsourcing.eventstore.cassandra.CassandraEventStoreModule;
import org.apache.james.mailbox.cassandra.modules.CassandraAclModule;
import org.apache.james.mailbox.cassandra.modules.CassandraAnnotationModule;
import org.apache.james.mailbox.cassandra.modules.CassandraApplicableFlagsModule;
import org.apache.james.mailbox.cassandra.modules.CassandraAttachmentModule;
import org.apache.james.mailbox.cassandra.modules.CassandraDeletedMessageModule;
import org.apache.james.mailbox.cassandra.modules.CassandraFirstUnseenModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxCounterModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxQuotaModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxRecentsModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMessageModule;
import org.apache.james.mailbox.cassandra.modules.CassandraModSeqModule;
import org.apache.james.mailbox.cassandra.modules.CassandraQuotaModule;
import org.apache.james.mailbox.cassandra.modules.CassandraSubscriptionModule;
import org.apache.james.mailbox.cassandra.modules.CassandraThreadModule;
import org.apache.james.mailbox.cassandra.modules.CassandraUidModule;

public interface MailboxAggregateModule {
    CassandraModule MODULE = CassandraModule.aggregateModules(
        CassandraAclModule.MODULE,
        CassandraAnnotationModule.MODULE,
        CassandraApplicableFlagsModule.MODULE,
        CassandraAttachmentModule.MODULE,
        CassandraBlobModule.MODULE,
        CassandraEventStoreModule.MODULE(),
        CassandraDeletedMessageModule.MODULE,
        CassandraFirstUnseenModule.MODULE,
        CassandraMailboxCounterModule.MODULE,
        CassandraMailboxModule.MODULE,
        CassandraMailboxRecentsModule.MODULE,
        CassandraMessageModule.MODULE,
        CassandraModSeqModule.MODULE,
        CassandraSchemaVersionModule.MODULE,
        CassandraSubscriptionModule.MODULE,
        CassandraUidModule.MODULE,
        CassandraThreadModule.MODULE);

    CassandraModule MODULE_WITH_QUOTA = CassandraModule.aggregateModules(CassandraQuotaModule.MODULE, CassandraMailboxQuotaModule.MODULE, CassandraMutualizedQuotaModule.MODULE, MODULE);
}
