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

import org.apache.james.backends.cassandra.components.CassandraDataDefinition;
import org.apache.james.backends.cassandra.components.CassandraMutualizedQuotaDataDefinition;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionDataDefinition;
import org.apache.james.blob.cassandra.CassandraBlobDataDefinition;
import org.apache.james.eventsourcing.eventstore.cassandra.CassandraEventStoreDataDefinition;
import org.apache.james.mailbox.cassandra.modules.CassandraAclDataDefinition;
import org.apache.james.mailbox.cassandra.modules.CassandraAnnotationDataDefinition;
import org.apache.james.mailbox.cassandra.modules.CassandraApplicableFlagsDataDefinition;
import org.apache.james.mailbox.cassandra.modules.CassandraAttachmentDataDefinition;
import org.apache.james.mailbox.cassandra.modules.CassandraDeletedMessageDataDefinition;
import org.apache.james.mailbox.cassandra.modules.CassandraFirstUnseenDataDefinition;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxCounterDataDefinition;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxDataDefinition;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxQuotaDataDefinition;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxRecentsDataDefinition;
import org.apache.james.mailbox.cassandra.modules.CassandraMessageDataDefinition;
import org.apache.james.mailbox.cassandra.modules.CassandraModSeqDataDefinition;
import org.apache.james.mailbox.cassandra.modules.CassandraSubscriptionDataDefinition;
import org.apache.james.mailbox.cassandra.modules.CassandraThreadDataDefinition;
import org.apache.james.mailbox.cassandra.modules.CassandraUidDataDefinition;

public interface MailboxAggregateModule {
    CassandraDataDefinition MODULE = CassandraDataDefinition.aggregateModules(
        CassandraAclDataDefinition.MODULE,
        CassandraAnnotationDataDefinition.MODULE,
        CassandraApplicableFlagsDataDefinition.MODULE,
        CassandraAttachmentDataDefinition.MODULE,
        CassandraBlobDataDefinition.MODULE,
        CassandraEventStoreDataDefinition.MODULE(),
        CassandraDeletedMessageDataDefinition.MODULE,
        CassandraFirstUnseenDataDefinition.MODULE,
        CassandraMailboxCounterDataDefinition.MODULE,
        CassandraMailboxDataDefinition.MODULE,
        CassandraMailboxRecentsDataDefinition.MODULE,
        CassandraMessageDataDefinition.MODULE,
        CassandraModSeqDataDefinition.MODULE,
        CassandraSchemaVersionDataDefinition.MODULE,
        CassandraSubscriptionDataDefinition.MODULE,
        CassandraUidDataDefinition.MODULE,
        CassandraThreadDataDefinition.MODULE);

    CassandraDataDefinition MODULE_WITH_QUOTA = CassandraDataDefinition.aggregateModules(CassandraMailboxQuotaDataDefinition.MODULE, CassandraMutualizedQuotaDataDefinition.MODULE, MODULE);
}
