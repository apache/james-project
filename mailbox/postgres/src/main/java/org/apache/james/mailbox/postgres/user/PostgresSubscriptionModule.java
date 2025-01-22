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

package org.apache.james.mailbox.postgres.user;

import static org.apache.james.mailbox.postgres.user.PostgresSubscriptionTable.MAILBOX;
import static org.apache.james.mailbox.postgres.user.PostgresSubscriptionTable.TABLE_NAME;
import static org.apache.james.mailbox.postgres.user.PostgresSubscriptionTable.USER;

import org.apache.james.backends.postgres.PostgresIndex;
import org.apache.james.backends.postgres.PostgresModule;
import org.apache.james.backends.postgres.PostgresTable;
import org.jooq.impl.DSL;

public interface PostgresSubscriptionModule {
    PostgresTable TABLE = PostgresTable.name(TABLE_NAME.getName())
        .createTableStep(((dsl, tableName) -> dsl.createTable(tableName)
            .column(MAILBOX)
            .column(USER)
            .constraint(DSL.unique(MAILBOX, USER))))
        .enableRowLevelSecurity();
    PostgresIndex INDEX = PostgresIndex.name("subscription_user_index")
        .createIndexStep((dsl, indexName) -> dsl.createIndex(indexName)
            .on(TABLE_NAME, USER));
    PostgresModule MODULE = PostgresModule.builder()
        .addTable(TABLE)
        .addIndex(INDEX)
        .build();
}
