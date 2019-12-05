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
package org.apache.james.eventsourcing.eventstore.cassandra

import org.apache.james.backends.cassandra.components.CassandraModule
import org.apache.james.backends.cassandra.utils.CassandraConstants
import com.datastax.driver.core.DataType
import com.datastax.driver.core.schemabuilder.{Create, SchemaBuilder}

object CassandraEventStoreModule {
  val MODULE = CassandraModule.table(CassandraEventStoreTable.EVENTS_TABLE)
    .comment("Store events of a EventSourcing aggregate")
    .options((options: Create.Options) => options.caching(
      SchemaBuilder.KeyCaching.ALL,
      SchemaBuilder.rows(CassandraConstants.DEFAULT_CACHED_ROW_PER_PARTITION)))
    .statement(_.addPartitionKey(CassandraEventStoreTable.AGGREGATE_ID, DataType.varchar)
      .addClusteringColumn(CassandraEventStoreTable.EVENT_ID, DataType.cint)
      .addColumn(CassandraEventStoreTable.EVENT, DataType.text))
    .build
}