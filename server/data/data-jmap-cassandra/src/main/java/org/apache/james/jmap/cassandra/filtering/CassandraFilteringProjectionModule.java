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

package org.apache.james.jmap.cassandra.filtering;

import static com.datastax.oss.driver.api.core.type.DataTypes.INT;
import static com.datastax.oss.driver.api.core.type.DataTypes.TEXT;
import static com.datastax.oss.driver.api.core.type.DataTypes.frozenListOf;

import org.apache.james.backends.cassandra.components.CassandraModule;

public interface CassandraFilteringProjectionModule {
    String TABLE_NAME = "filters_projection";

    String AGGREGATE_ID = "aggregate_id";
    String EVENT_ID = "event_id";
    String RULES = "rules";

    CassandraModule MODULE = CassandraModule.table(TABLE_NAME)
        .comment("Holds read projection for the event sourcing system managing JMAP filters.")
        .statement(statement -> types -> statement
            .withPartitionKey(AGGREGATE_ID, TEXT)
            .withColumn(EVENT_ID, INT)
            .withColumn(RULES, TEXT))
        .build();
}
