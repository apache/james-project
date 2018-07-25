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

import static com.datastax.driver.core.DataType.text;

import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.mailbox.cassandra.table.CassandraMailboxPathRegisterTable;

import com.datastax.driver.core.schemabuilder.SchemaBuilder;

public interface CassandraRegistrationModule {
    CassandraModule MODULE = CassandraModule.builder()
        .type(CassandraMailboxPathRegisterTable.MAILBOX_PATH)
        .statement(statement -> statement
            .addColumn(CassandraMailboxPathRegisterTable.MailboxPath.NAMESPACE, text())
            .addColumn(CassandraMailboxPathRegisterTable.MailboxPath.NAME, text())
            .addColumn(CassandraMailboxPathRegisterTable.MailboxPath.USER, text()))
        .table(CassandraMailboxPathRegisterTable.TABLE_NAME)
        .statement(statement -> statement
            .addUDTPartitionKey(CassandraMailboxPathRegisterTable.MAILBOX_PATH, SchemaBuilder.frozen(CassandraMailboxPathRegisterTable.MAILBOX_PATH))
            .addClusteringColumn(CassandraMailboxPathRegisterTable.TOPIC, text())
            .withOptions()
            .compactionOptions(SchemaBuilder.dateTieredStrategy()))
        .build();
}
