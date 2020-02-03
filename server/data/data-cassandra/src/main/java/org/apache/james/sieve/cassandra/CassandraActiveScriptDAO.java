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

package org.apache.james.sieve.cassandra;

import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.driver.core.querybuilder.QueryBuilder.delete;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static org.apache.james.sieve.cassandra.tables.CassandraSieveActiveTable.DATE;
import static org.apache.james.sieve.cassandra.tables.CassandraSieveActiveTable.SCRIPT_NAME;
import static org.apache.james.sieve.cassandra.tables.CassandraSieveActiveTable.TABLE_NAME;
import static org.apache.james.sieve.cassandra.tables.CassandraSieveActiveTable.USER_NAME;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.core.Username;
import org.apache.james.sieve.cassandra.model.ActiveScriptInfo;
import org.apache.james.sieverepository.api.ScriptName;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;

import reactor.core.publisher.Mono;

public class CassandraActiveScriptDAO {
    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final PreparedStatement insertActive;
    private final PreparedStatement deleteActive;
    private final PreparedStatement selectActiveName;

    @Inject
    public CassandraActiveScriptDAO(Session session) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        this.insertActive = session.prepare(insertInto(TABLE_NAME)
            .value(SCRIPT_NAME, bindMarker(SCRIPT_NAME))
            .value(USER_NAME, bindMarker(USER_NAME))
            .value(DATE, bindMarker(DATE)));
        this.deleteActive = session.prepare(delete()
            .from(TABLE_NAME)
            .where(eq(USER_NAME, bindMarker(USER_NAME))));
        this.selectActiveName = session.prepare(select(SCRIPT_NAME, DATE)
            .from(TABLE_NAME)
            .where(eq(USER_NAME, bindMarker(USER_NAME))));
    }

    public Mono<ActiveScriptInfo> getActiveSctiptInfo(Username username) {
        return cassandraAsyncExecutor.executeSingleRow(
            selectActiveName.bind()
                .setString(USER_NAME, username.asString()))
            .map(row -> new ActiveScriptInfo(
                new ScriptName(row.getString(SCRIPT_NAME)),
                ZonedDateTime.ofInstant(row.getTimestamp(DATE).toInstant(), ZoneOffset.UTC)));
    }

    public Mono<Void> unactivate(Username username) {
        return cassandraAsyncExecutor.executeVoid(
            deleteActive.bind()
                .setString(USER_NAME, username.asString()));
    }

    public Mono<Void> activate(Username username, ScriptName scriptName) {
        return cassandraAsyncExecutor.executeVoid(
            insertActive.bind()
                .setString(USER_NAME, username.asString())
                .setString(SCRIPT_NAME, scriptName.getValue())
                .setTimestamp(DATE, new Date()));
    }
}
