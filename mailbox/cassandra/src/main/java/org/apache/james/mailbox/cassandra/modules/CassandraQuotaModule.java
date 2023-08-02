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

import static com.datastax.oss.driver.api.core.type.DataTypes.BIGINT;
import static com.datastax.oss.driver.api.core.type.DataTypes.COUNTER;
import static com.datastax.oss.driver.api.core.type.DataTypes.TEXT;
import static com.datastax.oss.driver.api.querybuilder.SchemaBuilder.RowsPerPartition.rows;

import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.utils.CassandraConstants;
import org.apache.james.mailbox.cassandra.table.CassandraCurrentQuota;
import org.apache.james.mailbox.cassandra.table.CassandraDomainMaxQuota;
import org.apache.james.mailbox.cassandra.table.CassandraGlobalMaxQuota;
import org.apache.james.mailbox.cassandra.table.CassandraMaxQuota;
import org.apache.james.mailbox.cassandra.table.CassandraQuotaCurrentValue;
import org.apache.james.mailbox.cassandra.table.CassandraQuotaLimit;

public interface CassandraQuotaModule {
    CassandraModule MODULE = CassandraModule.builder()
        .table(CassandraCurrentQuota.TABLE_NAME)
        .comment("Holds per quota-root current values. Quota-roots defines groups of mailboxes which shares quotas limitations.")
        .options(options -> options
            .withCaching(true, rows(CassandraConstants.DEFAULT_CACHED_ROW_PER_PARTITION)))
        .statement(statement -> types -> statement
            .withPartitionKey(CassandraCurrentQuota.QUOTA_ROOT, TEXT)
            .withColumn(CassandraCurrentQuota.MESSAGE_COUNT, COUNTER)
            .withColumn(CassandraCurrentQuota.STORAGE, COUNTER))

        .table(CassandraMaxQuota.TABLE_NAME)
        .comment("Holds per quota-root limitations. Limitations can concern the number of messages in a quota-root or the total size of a quota-root.")
        .options(options -> options
            .withCaching(true, rows(CassandraConstants.DEFAULT_CACHED_ROW_PER_PARTITION)))
        .statement(statement -> types -> statement
            .withPartitionKey(CassandraMaxQuota.QUOTA_ROOT, TEXT)
            .withColumn(CassandraMaxQuota.MESSAGE_COUNT, BIGINT)
            .withColumn(CassandraMaxQuota.STORAGE, BIGINT))

        .table(CassandraDomainMaxQuota.TABLE_NAME)
        .comment("Holds per domain limitations. Limitations can concern the number of messages in a quota-root or the total size of a quota-root.")
        .options(options -> options
            .withCaching(true, rows(CassandraConstants.DEFAULT_CACHED_ROW_PER_PARTITION)))
        .statement(statement -> types -> statement
            .withPartitionKey(CassandraDomainMaxQuota.DOMAIN, TEXT)
            .withColumn(CassandraDomainMaxQuota.MESSAGE_COUNT, BIGINT)
            .withColumn(CassandraDomainMaxQuota.STORAGE, BIGINT))

        .table(CassandraGlobalMaxQuota.TABLE_NAME)
        .comment("Holds defaults limitations definition.")
        .options(options -> options
            .withCaching(true, rows(CassandraConstants.DEFAULT_CACHED_ROW_PER_PARTITION)))
        .statement(statement -> types -> statement
            .withPartitionKey(CassandraGlobalMaxQuota.KEY, TEXT)
            .withColumn(CassandraGlobalMaxQuota.STORAGE, BIGINT)
            .withColumn(CassandraGlobalMaxQuota.MESSAGE, BIGINT))

        .table(CassandraQuotaLimit.TABLE_NAME)
        .comment("Holds quota limit.")
        .options(options -> options
            .withCaching(true, rows(CassandraConstants.DEFAULT_CACHED_ROW_PER_PARTITION)))
        .statement(statement -> types -> statement
            .withPartitionKey(CassandraQuotaLimit.IDENTIFIER, TEXT)
            .withPartitionKey(CassandraQuotaLimit.QUOTA_COMPONENT, TEXT)
            .withPartitionKey(CassandraQuotaLimit.QUOTA_TYPE, TEXT)
            .withPartitionKey(CassandraQuotaLimit.QUOTA_SCOPE, TEXT)
            .withColumn(CassandraQuotaLimit.MAX_VALUE, BIGINT))

        .table(CassandraQuotaCurrentValue.TABLE_NAME)
        .comment("Holds quota limit.")
        .options(options -> options
            .withCaching(true, rows(CassandraConstants.DEFAULT_CACHED_ROW_PER_PARTITION)))
        .statement(statement -> types -> statement
            .withPartitionKey(CassandraQuotaCurrentValue.IDENTIFIER, TEXT)
            .withPartitionKey(CassandraQuotaCurrentValue.QUOTA_COMPONENT, TEXT)
            .withPartitionKey(CassandraQuotaCurrentValue.QUOTA_TYPE, TEXT)
            .withColumn(CassandraQuotaCurrentValue.CURRENT_VALUE, BIGINT))
        .build();
}
