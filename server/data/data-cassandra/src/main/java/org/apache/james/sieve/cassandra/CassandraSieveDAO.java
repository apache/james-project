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


import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.deleteFrom;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.insertInto;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.update;
import static org.apache.james.sieve.cassandra.tables.CassandraSieveTable.IS_ACTIVE;
import static org.apache.james.sieve.cassandra.tables.CassandraSieveTable.SCRIPT_CONTENT;
import static org.apache.james.sieve.cassandra.tables.CassandraSieveTable.SCRIPT_NAME;
import static org.apache.james.sieve.cassandra.tables.CassandraSieveTable.SIZE;
import static org.apache.james.sieve.cassandra.tables.CassandraSieveTable.TABLE_NAME;
import static org.apache.james.sieve.cassandra.tables.CassandraSieveTable.USER_NAME;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.core.Username;
import org.apache.james.sieve.cassandra.model.Script;
import org.apache.james.sieverepository.api.ScriptName;
import org.apache.james.sieverepository.api.ScriptSummary;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.querybuilder.select.Select;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraSieveDAO {

    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final PreparedStatement insertScriptStatement;
    private final PreparedStatement selectScriptsStatement;
    private final PreparedStatement selectScriptStatement;
    private final PreparedStatement updateScriptActivationStatement;
    private final PreparedStatement deleteScriptStatement;

    @Inject
    public CassandraSieveDAO(CqlSession session) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);

        insertScriptStatement = session.prepare(insertInto(TABLE_NAME)
            .value(USER_NAME, bindMarker(USER_NAME))
            .value(SCRIPT_NAME, bindMarker(SCRIPT_NAME))
            .value(SCRIPT_CONTENT, bindMarker(SCRIPT_CONTENT))
            .value(IS_ACTIVE, bindMarker(IS_ACTIVE))
            .value(SIZE, bindMarker(SIZE))
            .build());

        selectScriptsStatement = session.prepare(getScriptsQuery().build());

        selectScriptStatement = session.prepare(getScriptsQuery()
            .whereColumn(SCRIPT_NAME).isEqualTo(bindMarker(SCRIPT_NAME))
            .build());

        updateScriptActivationStatement = session.prepare(
            update(TABLE_NAME)
                .setColumn(IS_ACTIVE, bindMarker(IS_ACTIVE))
                .whereColumn(USER_NAME).isEqualTo(bindMarker(USER_NAME))
                .whereColumn(SCRIPT_NAME).isEqualTo(bindMarker(SCRIPT_NAME))
                .ifExists().build());

        deleteScriptStatement = session.prepare(
            deleteFrom(TABLE_NAME)
                .whereColumn(USER_NAME).isEqualTo(bindMarker(USER_NAME))
                .whereColumn(SCRIPT_NAME).isEqualTo(bindMarker(SCRIPT_NAME))
                .ifExists()
                .build());
    }

    private Select getScriptsQuery() {
        return selectFrom(TABLE_NAME)
            .columns(SCRIPT_CONTENT, IS_ACTIVE, SCRIPT_NAME, SIZE)
            .whereColumn(USER_NAME).isEqualTo(bindMarker(USER_NAME));
    }

    public Mono<Void> insertScript(Username username, Script script) {
        return cassandraAsyncExecutor.executeVoid(
            insertScriptStatement.bind()
                .setString(USER_NAME, username.asString())
                .setString(SCRIPT_NAME, script.getName().getValue())
                .setString(SCRIPT_CONTENT, script.getContent().getValue())
                .setBoolean(IS_ACTIVE, script.isActive())
                .setLong(SIZE, script.getSize()));
    }

    public Flux<ScriptSummary> listScripts(Username username) {
        return cassandraAsyncExecutor.executeRows(
                selectScriptsStatement.bind()
                    .setString(USER_NAME, username.asString()))
            .map(row -> new ScriptSummary(
                new ScriptName(row.getString(SCRIPT_NAME)),
                row.getBoolean(IS_ACTIVE),
                row.getLong(SIZE)));
    }

    public Mono<Boolean> updateScriptActivation(Username username, ScriptName scriptName, boolean active) {
        return cassandraAsyncExecutor.executeReturnApplied(
            updateScriptActivationStatement.bind()
                .setString(USER_NAME, username.asString())
                .setString(SCRIPT_NAME, scriptName.getValue())
                .setBoolean(IS_ACTIVE, active));
    }

    public Mono<Script> getScript(Username username, ScriptName name) {
        return getScriptRow(username, name).map(row -> Script.builder()
            .content(row.getString(SCRIPT_CONTENT))
            .isActive(row.getBoolean(IS_ACTIVE))
            .name(name)
            .size(row.getLong(SIZE))
            .build());
    }

    public Mono<Boolean> deleteScriptInCassandra(Username username, ScriptName name) {
        return cassandraAsyncExecutor.executeReturnApplied(
            deleteScriptStatement.bind()
                .setString(USER_NAME, username.asString())
                .setString(SCRIPT_NAME, name.getValue()));
    }

    private Mono<Row> getScriptRow(Username username, ScriptName name) {
        return cassandraAsyncExecutor.executeSingleRow(
            selectScriptStatement.bind()
                .setString(USER_NAME, username.asString())
                .setString(SCRIPT_NAME, name.getValue()));
    }

}
