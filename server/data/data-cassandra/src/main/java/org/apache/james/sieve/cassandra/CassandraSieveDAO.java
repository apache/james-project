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
import static com.datastax.driver.core.querybuilder.QueryBuilder.set;
import static com.datastax.driver.core.querybuilder.QueryBuilder.update;
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

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.Select;

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
    public CassandraSieveDAO(Session session) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);

        insertScriptStatement = session.prepare(
            insertInto(TABLE_NAME)
                .value(USER_NAME, bindMarker(USER_NAME))
                .value(SCRIPT_NAME, bindMarker(SCRIPT_NAME))
                .value(SCRIPT_CONTENT, bindMarker(SCRIPT_CONTENT))
                .value(IS_ACTIVE, bindMarker(IS_ACTIVE))
                .value(SIZE, bindMarker(SIZE)));

        selectScriptsStatement = session.prepare(getScriptsQuery());

        selectScriptStatement = session.prepare(getScriptsQuery()
            .and(eq(SCRIPT_NAME, bindMarker(SCRIPT_NAME))));

        updateScriptActivationStatement = session.prepare(
            update(TABLE_NAME)
                .with(set(IS_ACTIVE, bindMarker(IS_ACTIVE)))
                .where(eq(USER_NAME, bindMarker(USER_NAME)))
                .and(eq(SCRIPT_NAME, bindMarker(SCRIPT_NAME)))
                .ifExists());

        deleteScriptStatement = session.prepare(
            delete()
                .from(TABLE_NAME)
                .where(eq(USER_NAME, bindMarker(USER_NAME)))
                .and(eq(SCRIPT_NAME, bindMarker(SCRIPT_NAME)))
                .ifExists());
    }

    private Select.Where getScriptsQuery() {
        return select(SCRIPT_CONTENT, IS_ACTIVE, SCRIPT_NAME, SIZE)
            .from(TABLE_NAME)
            .where(eq(USER_NAME, bindMarker(USER_NAME)));
    }

    public Mono<Void> insertScript(Username username, Script script) {
        return cassandraAsyncExecutor.executeVoid(
            insertScriptStatement.bind()
                .setString(USER_NAME, username.asString())
                .setString(SCRIPT_NAME, script.getName().getValue())
                .setString(SCRIPT_CONTENT, script.getContent().getValue())
                .setBool(IS_ACTIVE, script.isActive())
                .setLong(SIZE, script.getSize()));
    }

    public Flux<ScriptSummary> listScripts(Username username) {
        return cassandraAsyncExecutor.executeRows(
                selectScriptsStatement.bind()
                    .setString(USER_NAME, username.asString()))
            .map(row -> new ScriptSummary(
                    new ScriptName(row.getString(SCRIPT_NAME)),
                    row.getBool(IS_ACTIVE)));
    }

    public Mono<Boolean> updateScriptActivation(Username username, ScriptName scriptName, boolean active) {
        return cassandraAsyncExecutor.executeReturnApplied(
            updateScriptActivationStatement.bind()
                .setString(USER_NAME, username.asString())
                .setString(SCRIPT_NAME, scriptName.getValue())
                .setBool(IS_ACTIVE, active));
    }

    public Mono<Script> getScript(Username username, ScriptName name) {
        return getScriptRow(username, name).map(row -> Script.builder()
                .content(row.getString(SCRIPT_CONTENT))
                .isActive(row.getBool(IS_ACTIVE))
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
