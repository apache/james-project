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

package org.apache.james.jmap.cassandra.pushsubscription;

import static com.datastax.oss.driver.api.core.type.DataTypes.BOOLEAN;
import static com.datastax.oss.driver.api.core.type.DataTypes.TEXT;
import static com.datastax.oss.driver.api.core.type.DataTypes.TIMESTAMP;
import static com.datastax.oss.driver.api.core.type.DataTypes.UUID;
import static com.datastax.oss.driver.api.core.type.DataTypes.frozenSetOf;
import static org.apache.james.jmap.cassandra.pushsubscription.tables.CassandraPushSubscriptionTable.DEVICE_CLIENT_ID;
import static org.apache.james.jmap.cassandra.pushsubscription.tables.CassandraPushSubscriptionTable.ENCRYPT_AUTH_SECRET;
import static org.apache.james.jmap.cassandra.pushsubscription.tables.CassandraPushSubscriptionTable.ENCRYPT_PUBLIC_KEY;
import static org.apache.james.jmap.cassandra.pushsubscription.tables.CassandraPushSubscriptionTable.EXPIRES;
import static org.apache.james.jmap.cassandra.pushsubscription.tables.CassandraPushSubscriptionTable.ID;
import static org.apache.james.jmap.cassandra.pushsubscription.tables.CassandraPushSubscriptionTable.TYPES;
import static org.apache.james.jmap.cassandra.pushsubscription.tables.CassandraPushSubscriptionTable.URL;
import static org.apache.james.jmap.cassandra.pushsubscription.tables.CassandraPushSubscriptionTable.USER;
import static org.apache.james.jmap.cassandra.pushsubscription.tables.CassandraPushSubscriptionTable.VALIDATED;
import static org.apache.james.jmap.cassandra.pushsubscription.tables.CassandraPushSubscriptionTable.VERIFICATION_CODE;

import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.jmap.cassandra.pushsubscription.tables.CassandraPushSubscriptionTable;

public interface CassandraPushSubscriptionModule {
    CassandraModule MODULE = CassandraModule.builder()
        .table(CassandraPushSubscriptionTable.TABLE_NAME)
        .comment("Hold user push subscriptions data")
        .statement(statement -> types -> statement
            .withPartitionKey(USER, TEXT)
            .withClusteringColumn(DEVICE_CLIENT_ID, TEXT)
            .withColumn(ID, UUID)
            .withColumn(EXPIRES, TIMESTAMP)
            .withColumn(TYPES, frozenSetOf(TEXT))
            .withColumn(URL, TEXT)
            .withColumn(VERIFICATION_CODE, TEXT)
            .withColumn(ENCRYPT_PUBLIC_KEY, TEXT)
            .withColumn(ENCRYPT_AUTH_SECRET, TEXT)
            .withColumn(VALIDATED, BOOLEAN))
        .build();
}