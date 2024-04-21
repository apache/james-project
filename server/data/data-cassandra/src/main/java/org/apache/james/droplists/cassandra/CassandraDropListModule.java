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
package org.apache.james.droplists.cassandra;

import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.droplists.cassandra.tables.CassandraDomainDropListTable;
import org.apache.james.droplists.cassandra.tables.CassandraGlobalDropListTable;
import org.apache.james.droplists.cassandra.tables.CassandraUserDropListTable;

import com.datastax.oss.driver.api.core.type.DataTypes;

public interface CassandraDropListModule {
    CassandraModule MODULE = CassandraModule.table(CassandraGlobalDropListTable.TABLE_NAME)
        .comment("Holds Global DropLists of this James server.")
        .statement(statement -> types -> statement
            .withPartitionKey(CassandraGlobalDropListTable.DENIED_ENTITY, DataTypes.TEXT)
            .withColumn(CassandraGlobalDropListTable.DENIED_ENTITY_TYPE, DataTypes.TEXT))
        .table(CassandraDomainDropListTable.TABLE_NAME)
        .comment("Holds Domain DropLists of this James server.")
        .statement(statement -> types -> statement
            .withPartitionKey(CassandraDomainDropListTable.OWNER, DataTypes.TEXT)
            .withPartitionKey(CassandraDomainDropListTable.DENIED_ENTITY, DataTypes.TEXT)
            .withColumn(CassandraDomainDropListTable.DENIED_ENTITY_TYPE, DataTypes.TEXT))
        .table(CassandraUserDropListTable.TABLE_NAME)
        .comment("Holds User DropLists of this James server.")
        .statement(statement -> types -> statement
            .withPartitionKey(CassandraUserDropListTable.OWNER, DataTypes.TEXT)
            .withPartitionKey(CassandraUserDropListTable.DENIED_ENTITY, DataTypes.TEXT)
            .withColumn(CassandraUserDropListTable.DENIED_ENTITY_TYPE, DataTypes.TEXT))
        .build();
}