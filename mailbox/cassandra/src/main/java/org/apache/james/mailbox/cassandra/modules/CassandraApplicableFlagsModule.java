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

import static com.datastax.driver.core.DataType.set;
import static com.datastax.driver.core.DataType.text;
import static com.datastax.driver.core.DataType.timeuuid;

import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.utils.CassandraConstants;
import org.apache.james.mailbox.cassandra.table.CassandraApplicableFlagTable;
import org.apache.james.mailbox.cassandra.table.Flag;

import com.datastax.driver.core.schemabuilder.SchemaBuilder;

public interface CassandraApplicableFlagsModule {
    CassandraModule MODULE = CassandraModule.table(CassandraApplicableFlagTable.TABLE_NAME)
        .comment("Holds flags being used on specific mailboxes. As system flags are implicit, this table " +
            "stores user flags.")
        .options(options -> options
            .compactionOptions(SchemaBuilder.sizedTieredStategy())
            .caching(SchemaBuilder.KeyCaching.ALL,
                SchemaBuilder.rows(CassandraConstants.DEFAULT_CACHED_ROW_PER_PARTITION)))
        .statement(statement -> statement
            .addPartitionKey(CassandraApplicableFlagTable.MAILBOX_ID, timeuuid())
            .addColumn(Flag.USER_FLAGS, set(text())))
        .build();
}
