/******************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one     *
 * or more contributor license agreements.  See the NOTICE file   *
 * distributed with this work for additional information          *
 * regarding copyright ownership.  The ASF licenses this file     *
 * to you under the Apache License, Version 2.0 (the              *
 * "License"); you may not use this file except in compliance     *
 * with the License.  You may obtain a copy of the License at     *
 *                                                                *
 * http://www.apache.org/licenses/LICENSE-2.0                     *
 *                                                                *
 * Unless required by applicable law or agreed to in writing,     *
 * software distributed under the License is distributed on an    *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY         *
 * KIND, either express or implied.  See the License for the      *
 * specific language governing permissions and limitations        *
 * under the License.                                             *
 ******************************************************************/

package org.apache.james.mailbox.cassandra.modules;

import static com.datastax.driver.core.DataType.text;
import static com.datastax.driver.core.DataType.timeuuid;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageIdTable.THREAD_ID;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageIds.MESSAGE_ID;
import static org.apache.james.mailbox.cassandra.table.CassandraThreadTable.BASE_SUBJECT;
import static org.apache.james.mailbox.cassandra.table.CassandraThreadTable.MIME_MESSAGE_ID;
import static org.apache.james.mailbox.cassandra.table.CassandraThreadTable.TABLE_NAME;
import static org.apache.james.mailbox.cassandra.table.CassandraThreadTable.USERNAME;

import org.apache.james.backends.cassandra.components.CassandraModule;

public interface CassandraThreadModule {
    CassandraModule MODULE = CassandraModule.builder()
        .table(TABLE_NAME)
        .comment("Related data needed for guessing threadId algorithm")
        .statement(statement -> statement
            .addPartitionKey(USERNAME, text())
            .addPartitionKey(MIME_MESSAGE_ID, text())
            .addClusteringColumn(MESSAGE_ID, timeuuid())
            .addColumn(THREAD_ID, timeuuid())
            .addColumn(BASE_SUBJECT, text()))
        .build();

}
