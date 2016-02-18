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
import static com.datastax.driver.core.querybuilder.QueryBuilder.incr;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static com.datastax.driver.core.querybuilder.QueryBuilder.set;
import static com.datastax.driver.core.querybuilder.QueryBuilder.update;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.sieve.cassandra.model.ScriptContentAndActivation;
import org.apache.james.sieve.cassandra.tables.CassandraSieveClusterQuotaTable;
import org.apache.james.sieve.cassandra.tables.CassandraSieveQuotaTable;
import org.apache.james.sieve.cassandra.tables.CassandraSieveSpaceTable;
import org.apache.james.sieve.cassandra.tables.CassandraSieveTable;
import org.apache.james.sieverepository.api.ScriptSummary;
import org.joda.time.DateTime;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.Select;

public class CassandraSieveDAO {

    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final PreparedStatement insertScriptStatement;
    private final PreparedStatement selectActiveScriptStatement;
    private final PreparedStatement selectActiveScriptMetadataStatement;
    private final PreparedStatement selectActiveScriptNameStatement;
    private final PreparedStatement selectClusterQuotaStatement;
    private final PreparedStatement selectScriptsStatement;
    private final PreparedStatement selectScriptStatement;
    private final PreparedStatement selectScriptMetadataStatement;
    private final PreparedStatement selectSpaceUsedByUserStatement;
    private final PreparedStatement selectUserQuotaStatement;
    private final PreparedStatement updateClusterQuotaStatement;
    private final PreparedStatement updateUserQuotaStatement;
    private final PreparedStatement updateScriptActivationStatement;
    private final PreparedStatement updateSpaceUsedStatement;
    private final PreparedStatement deleteClusterQuotaStatement;
    private final PreparedStatement deleteScriptStatement;
    private final PreparedStatement deleteUserQuotaStatement;

    @Inject
    public CassandraSieveDAO(Session session) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);

        insertScriptStatement = session.prepare(
            insertInto(CassandraSieveTable.TABLE_NAME)
                .value(CassandraSieveTable.USER_NAME, bindMarker(CassandraSieveTable.USER_NAME))
                .value(CassandraSieveTable.SCRIPT_NAME, bindMarker(CassandraSieveTable.SCRIPT_NAME))
                .value(CassandraSieveTable.SCRIPT_CONTENT, bindMarker(CassandraSieveTable.SCRIPT_CONTENT))
                .value(CassandraSieveTable.IS_ACTIVE, bindMarker(CassandraSieveTable.IS_ACTIVE))
                .value(CassandraSieveTable.SIZE, bindMarker(CassandraSieveTable.SIZE))
                .value(CassandraSieveTable.DATE, bindMarker(CassandraSieveTable.DATE)));

        selectActiveScriptStatement = session.prepare(getScriptQuery(CassandraSieveTable.SCRIPT_CONTENT, CassandraSieveTable.DATE)
            .and(eq(CassandraSieveTable.IS_ACTIVE, bindMarker(CassandraSieveTable.IS_ACTIVE))));

        selectActiveScriptMetadataStatement = session.prepare(getScriptQuery(CassandraSieveTable.DATE)
            .and(eq(CassandraSieveTable.IS_ACTIVE, bindMarker(CassandraSieveTable.IS_ACTIVE))));

        selectActiveScriptNameStatement = session.prepare(getScriptQuery(CassandraSieveTable.SCRIPT_NAME)
            .and(eq(CassandraSieveTable.IS_ACTIVE, bindMarker(CassandraSieveTable.IS_ACTIVE))));

        selectClusterQuotaStatement = session.prepare(
            select(CassandraSieveClusterQuotaTable.VALUE)
                .from(CassandraSieveClusterQuotaTable.TABLE_NAME)
                .where(eq(CassandraSieveClusterQuotaTable.NAME, bindMarker(CassandraSieveClusterQuotaTable.NAME))));

        selectScriptsStatement = session.prepare(
            select()
                .from(CassandraSieveTable.TABLE_NAME)
                .where(eq(CassandraSieveTable.USER_NAME, bindMarker(CassandraSieveTable.USER_NAME))));

        selectScriptStatement = session.prepare(getScriptQuery(CassandraSieveTable.SCRIPT_CONTENT, CassandraSieveTable.IS_ACTIVE)
            .and(eq(CassandraSieveTable.SCRIPT_NAME, bindMarker(CassandraSieveTable.SCRIPT_NAME))));

        selectScriptMetadataStatement = session.prepare(getScriptQuery(CassandraSieveTable.SIZE, CassandraSieveTable.IS_ACTIVE, CassandraSieveTable.DATE)
            .and(eq(CassandraSieveTable.SCRIPT_NAME, bindMarker(CassandraSieveTable.SCRIPT_NAME))));

        selectSpaceUsedByUserStatement = session.prepare(
            select(CassandraSieveSpaceTable.SPACE_USED)
                .from(CassandraSieveSpaceTable.TABLE_NAME)
                .where(eq(CassandraSieveSpaceTable.USER_NAME, bindMarker(CassandraSieveSpaceTable.USER_NAME))));

        selectUserQuotaStatement = session.prepare(
            select(CassandraSieveQuotaTable.QUOTA)
                .from(CassandraSieveQuotaTable.TABLE_NAME)
                .where(eq(CassandraSieveQuotaTable.USER_NAME, bindMarker(CassandraSieveQuotaTable.USER_NAME))));

        updateClusterQuotaStatement = session.prepare(
            update(CassandraSieveClusterQuotaTable.TABLE_NAME)
                .with(set(CassandraSieveClusterQuotaTable.VALUE, bindMarker(CassandraSieveClusterQuotaTable.VALUE)))
                .where(eq(CassandraSieveClusterQuotaTable.NAME, bindMarker(CassandraSieveClusterQuotaTable.NAME))));

        updateScriptActivationStatement = session.prepare(
            update(CassandraSieveTable.TABLE_NAME)
                .with(set(CassandraSieveTable.IS_ACTIVE, bindMarker(CassandraSieveTable.IS_ACTIVE)))
                .where(eq(CassandraSieveTable.USER_NAME, bindMarker(CassandraSieveTable.USER_NAME)))
                .and(eq(CassandraSieveTable.SCRIPT_NAME, bindMarker(CassandraSieveTable.SCRIPT_NAME)))
                .ifExists());

        updateSpaceUsedStatement = session.prepare(
            update(CassandraSieveSpaceTable.TABLE_NAME)
                .with(incr(CassandraSieveSpaceTable.SPACE_USED, bindMarker(CassandraSieveSpaceTable.SPACE_USED)))
                .where(eq(CassandraSieveSpaceTable.USER_NAME, bindMarker(CassandraSieveSpaceTable.USER_NAME))));

        updateUserQuotaStatement = session.prepare(
            update(CassandraSieveQuotaTable.TABLE_NAME)
                .with(set(CassandraSieveQuotaTable.QUOTA, bindMarker(CassandraSieveQuotaTable.QUOTA)))
                .where(eq(CassandraSieveQuotaTable.USER_NAME, bindMarker(CassandraSieveQuotaTable.USER_NAME))));

        deleteScriptStatement = session.prepare(
            delete()
                .from(CassandraSieveTable.TABLE_NAME)
                .where(eq(CassandraSieveTable.USER_NAME, bindMarker(CassandraSieveTable.USER_NAME)))
                .and(eq(CassandraSieveTable.SCRIPT_NAME, bindMarker(CassandraSieveTable.SCRIPT_NAME)))
                .ifExists());

        deleteClusterQuotaStatement = session.prepare(
            delete()
                .from(CassandraSieveClusterQuotaTable.TABLE_NAME)
                .where(eq(CassandraSieveClusterQuotaTable.NAME, bindMarker(CassandraSieveClusterQuotaTable.NAME)))
                .ifExists());

        deleteUserQuotaStatement = session.prepare(
            delete()
                .from(CassandraSieveQuotaTable.TABLE_NAME)
                .where(eq(CassandraSieveQuotaTable.USER_NAME, bindMarker(CassandraSieveQuotaTable.USER_NAME)))
                .ifExists());
    }

    private Select.Where getScriptQuery(String... selectedRows) {
        return select(selectedRows)
            .from(CassandraSieveTable.TABLE_NAME)
            .where(eq(CassandraSieveTable.USER_NAME, bindMarker(CassandraSieveTable.USER_NAME)));
    }

    public CompletableFuture<Long> spaceUsedBy(String user) {
        return cassandraAsyncExecutor.executeSingleRow(
            selectSpaceUsedByUserStatement.bind()
                .setString(CassandraSieveSpaceTable.USER_NAME, user))
            .thenApply(optional -> optional.map(row -> row.getLong(CassandraSieveSpaceTable.SPACE_USED))
                .orElse(0L));
    }

    public CompletableFuture<Void> insertScript(String user, String name, String content, boolean isActive) {
        return cassandraAsyncExecutor.executeVoid(
            insertScriptStatement.bind()
                .setString(CassandraSieveTable.USER_NAME, user)
                .setString(CassandraSieveTable.SCRIPT_NAME, name)
                .setString(CassandraSieveTable.SCRIPT_CONTENT, content)
                .setBool(CassandraSieveTable.IS_ACTIVE, isActive)
                .setLong(CassandraSieveTable.SIZE, content.getBytes().length)
                .setDate(CassandraSieveTable.DATE, new Date()));
    }

    public CompletableFuture<List<ScriptSummary>> listScripts(String user) {
        return cassandraAsyncExecutor.execute(
            selectScriptsStatement.bind()
                .setString(CassandraSieveTable.USER_NAME, user))
            .thenApply(resultSet -> resultSet.all()
                .stream()
                .map(row -> new ScriptSummary(
                    row.getString(CassandraSieveTable.SCRIPT_NAME),
                    row.getBool(CassandraSieveTable.IS_ACTIVE)))
                .collect(Collectors.toList()));
    }

    public CompletableFuture<Void> updateSpaceUsed(String user, long spaceUsed) {
        return cassandraAsyncExecutor.executeVoid(
            updateSpaceUsedStatement.bind()
                .setLong(CassandraSieveSpaceTable.SPACE_USED, spaceUsed)
                .setString(CassandraSieveSpaceTable.USER_NAME, user));
    }

    public CompletableFuture<Boolean> updateScriptActivation(String user, String scriptName, boolean active) {
        return cassandraAsyncExecutor.executeReturnApplied(
            updateScriptActivationStatement.bind()
                .setString(CassandraSieveTable.USER_NAME, user)
                .setString(CassandraSieveTable.SCRIPT_NAME, scriptName)
                .setBool(CassandraSieveTable.IS_ACTIVE, active));
    }

    public CompletableFuture<Optional<ScriptContentAndActivation>> getScriptContentAndActivation(String user, String name) {
        return getScriptRow(user, name).thenApply(opt -> opt.map(row -> new ScriptContentAndActivation(
            row.getString(CassandraSieveTable.SCRIPT_CONTENT),
            row.getBool(CassandraSieveTable.IS_ACTIVE))));
    }

    public CompletableFuture<Optional<String>> getScriptContent(String user, String name) {
        return getScriptRow(user, name).thenApply(opt -> opt.map(row -> row.getString(CassandraSieveTable.SCRIPT_CONTENT)));
    }

    public CompletableFuture<Optional<Long>> getScriptSize(String user, String name) {
        return cassandraAsyncExecutor.executeSingleRow(
            selectScriptMetadataStatement.bind()
                .setString(CassandraSieveTable.USER_NAME, user)
                .setString(CassandraSieveTable.SCRIPT_NAME, name))
            .thenApply(rowOptional -> rowOptional.map(row -> row.getLong(CassandraSieveTable.SIZE)));
    }

    public CompletableFuture<Optional<DateTime>> getActiveScriptActivationDate(String user) {
        return cassandraAsyncExecutor.executeSingleRow(
            selectActiveScriptMetadataStatement.bind()
                .setString(CassandraSieveTable.USER_NAME, user)
                .setBool(CassandraSieveTable.IS_ACTIVE, true))
            .thenApply(rowOptional -> rowOptional.map(row -> new DateTime(row.getDate(CassandraSieveTable.DATE).getTime())));
    }

    public CompletableFuture<Boolean> deleteScriptInCassandra(String user, String name) {
        return cassandraAsyncExecutor.executeReturnApplied(
            deleteScriptStatement.bind()
                .setString(CassandraSieveTable.USER_NAME, user)
                .setString(CassandraSieveTable.SCRIPT_NAME, name));
    }

    public CompletableFuture<Boolean> scriptExists(String user, String name) {
        return getScriptSize(user, name).thenApply(Optional::isPresent);
    }

    public CompletableFuture<Optional<Long>> getQuota() {
        return cassandraAsyncExecutor.executeSingleRow(
            selectClusterQuotaStatement.bind()
                .setString(CassandraSieveClusterQuotaTable.NAME, CassandraSieveClusterQuotaTable.DEFAULT_NAME))
            .thenApply(optional -> optional.map(row -> row.getLong(CassandraSieveClusterQuotaTable.VALUE)));
    }

    public CompletableFuture<Void> setQuota(long quota) {
        return cassandraAsyncExecutor.executeVoid(
            updateClusterQuotaStatement.bind()
                .setLong(CassandraSieveClusterQuotaTable.VALUE, quota)
                .setString(CassandraSieveClusterQuotaTable.NAME, CassandraSieveClusterQuotaTable.DEFAULT_NAME));
    }

    public CompletableFuture<Boolean> removeQuota() {
        return cassandraAsyncExecutor.executeReturnApplied(
            deleteClusterQuotaStatement.bind()
                .setString(CassandraSieveClusterQuotaTable.NAME, CassandraSieveClusterQuotaTable.DEFAULT_NAME));
    }

    public CompletableFuture<Optional<Long>> getQuota(String user) {
        return cassandraAsyncExecutor.executeSingleRow(
            selectUserQuotaStatement.bind()
                .setString(CassandraSieveQuotaTable.USER_NAME, user))
            .thenApply(optional -> optional.map(row -> row.getLong(CassandraSieveQuotaTable.QUOTA)));
    }

    public CompletableFuture<Optional<String>> getActiveName(String user) {
        return cassandraAsyncExecutor.executeSingleRow(
            selectActiveScriptNameStatement.bind()
                .setString(CassandraSieveTable.USER_NAME, user)
                .setBool(CassandraSieveTable.IS_ACTIVE, true))
            .thenApply(optional -> optional.map(row -> row.getString(CassandraSieveTable.SCRIPT_NAME)));
    }

    public CompletableFuture<Void> setQuota(String user, long quota) {
        return cassandraAsyncExecutor.executeVoid(
            updateUserQuotaStatement.bind()
                .setLong(CassandraSieveQuotaTable.QUOTA, quota)
                .setString(CassandraSieveQuotaTable.USER_NAME, user));
    }

    public CompletableFuture<Boolean> removeQuota(String user)  {
        return cassandraAsyncExecutor.executeReturnApplied(
            deleteUserQuotaStatement.bind()
                .setString(CassandraSieveQuotaTable.USER_NAME, user));
    }

    public CompletableFuture<Optional<String>> getActive(String user) {
        return cassandraAsyncExecutor.executeSingleRow(
            selectActiveScriptStatement.bind()
                .setString(CassandraSieveTable.USER_NAME, user)
                .setBool(CassandraSieveTable.IS_ACTIVE, true))
            .thenApply(rowOptional -> rowOptional.map(row -> row.getString(CassandraSieveTable.SCRIPT_CONTENT)));
    }

    private CompletableFuture<Optional<Row>> getScriptRow(String user, String name) {
        return cassandraAsyncExecutor.executeSingleRow(
            selectScriptStatement.bind()
                .setString(CassandraSieveTable.USER_NAME, user)
                .setString(CassandraSieveTable.SCRIPT_NAME, name));
    }

}
