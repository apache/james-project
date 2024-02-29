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

package org.apache.james.jmap.postgres.pushsubscription;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.apache.james.backends.postgres.PostgresCommons;
import org.apache.james.backends.postgres.PostgresIndex;
import org.apache.james.backends.postgres.PostgresModule;
import org.apache.james.backends.postgres.PostgresTable;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

public interface PostgresPushSubscriptionModule {

    interface PushSubscriptionTable {
        Table<Record> TABLE_NAME = DSL.table("push_subscription");
        Field<String> USER = DSL.field("username", SQLDataType.VARCHAR.notNull());
        Field<String> DEVICE_CLIENT_ID = DSL.field("device_client_id", SQLDataType.VARCHAR.notNull());

        Field<UUID> ID = DSL.field("id", SQLDataType.UUID.notNull());
        Field<OffsetDateTime> EXPIRES = DSL.field("expires", PostgresCommons.DataTypes.TIMESTAMP_WITH_TIMEZONE);
        Field<String[]> TYPES = DSL.field("types", PostgresCommons.DataTypes.STRING_ARRAY.notNull());

        Field<String> URL = DSL.field("url", SQLDataType.VARCHAR.notNull());
        Field<String> VERIFICATION_CODE = DSL.field("verification_code", SQLDataType.VARCHAR);
        Field<String> ENCRYPT_PUBLIC_KEY = DSL.field("encrypt_public_key", SQLDataType.VARCHAR);
        Field<String> ENCRYPT_AUTH_SECRET = DSL.field("encrypt_auth_secret", SQLDataType.VARCHAR);
        Field<Boolean> VALIDATED = DSL.field("validated", SQLDataType.BOOLEAN.notNull());

        PostgresTable TABLE = PostgresTable.name(TABLE_NAME.getName())
            .createTableStep(((dsl, tableName) -> dsl.createTableIfNotExists(tableName)
                .column(USER)
                .column(DEVICE_CLIENT_ID)
                .column(ID)
                .column(EXPIRES)
                .column(TYPES)
                .column(URL)
                .column(VERIFICATION_CODE)
                .column(ENCRYPT_PUBLIC_KEY)
                .column(ENCRYPT_AUTH_SECRET)
                .column(VALIDATED)
                .primaryKey(USER, DEVICE_CLIENT_ID)))
            .supportsRowLevelSecurity()
            .build();

        PostgresIndex USERNAME_INDEX = PostgresIndex.name("push_subscription_username_index")
            .createIndexStep((dslContext, indexName) -> dslContext.createIndexIfNotExists(indexName)
                .on(TABLE_NAME, USER));
        PostgresIndex USERNAME_ID_INDEX = PostgresIndex.name("push_subscription_username_id_index")
            .createIndexStep((dslContext, indexName) -> dslContext.createIndexIfNotExists(indexName)
                .on(TABLE_NAME, USER, ID));
    }

    PostgresModule MODULE = PostgresModule.builder()
        .addTable(PushSubscriptionTable.TABLE)
        .addIndex(PushSubscriptionTable.USERNAME_INDEX, PushSubscriptionTable.USERNAME_ID_INDEX)
        .build();
}
