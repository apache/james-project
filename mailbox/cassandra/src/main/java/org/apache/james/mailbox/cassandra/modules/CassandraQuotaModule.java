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

package org.apache.james.mailbox.cassandra.modules;

import static com.datastax.driver.core.DataType.bigint;
import static com.datastax.driver.core.DataType.counter;
import static com.datastax.driver.core.DataType.text;

import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.utils.CassandraConstants;
import org.apache.james.mailbox.cassandra.table.CassandraCurrentQuota;
import org.apache.james.mailbox.cassandra.table.CassandraDomainMaxQuota;
import org.apache.james.mailbox.cassandra.table.CassandraGlobalMaxQuota;
import org.apache.james.mailbox.cassandra.table.CassandraMaxQuota;

import com.datastax.driver.core.schemabuilder.SchemaBuilder;

public interface CassandraQuotaModule {
    CassandraModule MODULE = CassandraModule.builder()
        .table(CassandraCurrentQuota.TABLE_NAME)
        .statement(statement -> statement
            .addPartitionKey(CassandraCurrentQuota.QUOTA_ROOT, text())
            .addColumn(CassandraCurrentQuota.MESSAGE_COUNT, counter())
            .addColumn(CassandraCurrentQuota.STORAGE, counter())
            .withOptions()
            .comment("Holds per quota-root current values. Quota-roots defines groups of mailboxes which shares quotas limitations.")
            .caching(SchemaBuilder.KeyCaching.ALL,
                SchemaBuilder.rows(CassandraConstants.DEFAULT_CACHED_ROW_PER_PARTITION)))
        .table(CassandraMaxQuota.TABLE_NAME)
        .statement(statement -> statement
            .addPartitionKey(CassandraMaxQuota.QUOTA_ROOT, text())
            .addColumn(CassandraMaxQuota.MESSAGE_COUNT, bigint())
            .addColumn(CassandraMaxQuota.STORAGE, bigint())
            .withOptions()
            .comment("Holds per quota-root limitations. Limitations can concern the number of messages in a quota-root or the total size of a quota-root.")
            .caching(SchemaBuilder.KeyCaching.ALL,
                SchemaBuilder.rows(CassandraConstants.DEFAULT_CACHED_ROW_PER_PARTITION)))
        .table(CassandraDomainMaxQuota.TABLE_NAME)
        .statement(statement -> statement
            .addPartitionKey(CassandraDomainMaxQuota.DOMAIN, text())
            .addColumn(CassandraDomainMaxQuota.MESSAGE_COUNT, bigint())
            .addColumn(CassandraDomainMaxQuota.STORAGE, bigint())
            .withOptions()
            .comment("Holds per domain limitations. Limitations can concern the number of messages in a quota-root or the total size of a quota-root.")
            .caching(SchemaBuilder.KeyCaching.ALL,
                SchemaBuilder.rows(CassandraConstants.DEFAULT_CACHED_ROW_PER_PARTITION)))
        .table(CassandraGlobalMaxQuota.TABLE_NAME)
        .statement(statement -> statement
            .addPartitionKey(CassandraGlobalMaxQuota.TYPE, text())
            .addColumn(CassandraGlobalMaxQuota.VALUE, bigint())
            .withOptions()
            .comment("Holds defaults limitations definition.")
            .caching(SchemaBuilder.KeyCaching.ALL,
                SchemaBuilder.rows(CassandraConstants.DEFAULT_CACHED_ROW_PER_PARTITION)))
        .build();
}
