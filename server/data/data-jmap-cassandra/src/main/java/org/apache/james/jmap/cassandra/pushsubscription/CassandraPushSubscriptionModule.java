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

import static com.datastax.driver.core.DataType.cboolean;
import static com.datastax.driver.core.DataType.frozenSet;
import static com.datastax.driver.core.DataType.text;
import static com.datastax.driver.core.DataType.timestamp;
import static com.datastax.driver.core.DataType.uuid;
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
        .statement(statement -> statement
            .addPartitionKey(USER, text())
            .addClusteringColumn(DEVICE_CLIENT_ID, text())
            .addColumn(ID, uuid())
            .addColumn(EXPIRES, timestamp())
            .addColumn(TYPES, frozenSet(text()))
            .addColumn(URL, text())
            .addColumn(VERIFICATION_CODE, text())
            .addColumn(ENCRYPT_PUBLIC_KEY, text())
            .addColumn(ENCRYPT_AUTH_SECRET, text())
            .addColumn(VALIDATED, cboolean()))
        .build();
}