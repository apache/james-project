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

package org.apache.james.backends.cassandra;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.datastax.dse.driver.api.core.cql.continuous.ContinuousAsyncResultSet;
import com.datastax.dse.driver.api.core.cql.continuous.ContinuousResultSet;
import com.datastax.dse.driver.api.core.cql.continuous.reactive.ContinuousReactiveResultSet;
import com.datastax.dse.driver.api.core.cql.reactive.ReactiveResultSet;
import com.datastax.dse.driver.api.core.graph.AsyncGraphResultSet;
import com.datastax.dse.driver.api.core.graph.GraphResultSet;
import com.datastax.dse.driver.api.core.graph.GraphStatement;
import com.datastax.dse.driver.api.core.graph.reactive.ReactiveGraphResultSet;
import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.context.DriverContext;
import com.datastax.oss.driver.api.core.cql.AsyncResultSet;
import com.datastax.oss.driver.api.core.cql.PrepareRequest;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.driver.api.core.cql.Statement;
import com.datastax.oss.driver.api.core.metadata.Metadata;
import com.datastax.oss.driver.api.core.metrics.Metrics;
import com.datastax.oss.driver.api.core.session.Request;
import com.datastax.oss.driver.api.core.type.reflect.GenericType;

public class TestingSession implements CqlSession {
    private final CqlSession delegate;
    private volatile Scenario scenario;
    private volatile boolean printStatements;
    private volatile Optional<StatementRecorder> statementRecorder;

    public TestingSession(CqlSession delegate) {
        this.delegate = delegate;
        this.scenario = Scenario.NOTHING;
        this.printStatements = false;
        this.statementRecorder = Optional.empty();
    }

    public void printStatements() {
        printStatements = true;
    }

    public void resetInstrumentation() {
        stopRecordingStatements();
        stopPrintingStatements();
        registerScenario(Scenario.NOTHING);
    }

    public void stopPrintingStatements() {
        printStatements = false;
    }

    public void registerScenario(Scenario scenario) {
        this.scenario = scenario;
    }

    public void registerScenario(Scenario.ExecutionHook... hooks) {
        this.scenario = Scenario.combine(hooks);
    }

    public StatementRecorder recordStatements() {
        return recordStatements(StatementRecorder.Selector.ALL);
    }

    public StatementRecorder recordStatements(StatementRecorder.Selector selector) {
        StatementRecorder statementRecorder = new StatementRecorder(selector);
        this.statementRecorder = Optional.of(statementRecorder);
        return statementRecorder;
    }

    public void stopRecordingStatements() {
        this.statementRecorder = Optional.empty();
    }

    private void printStatement(String query) {
        if (printStatements) {
            print(query);
        }
    }

    private void printStatement(Statement statement) {
        if (printStatements) {
            print(CassandraAsyncExecutor.asString(statement));
        }
    }

    private void print(String statement) {
        System.out.println("Executing: " + statement);
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @NotNull
    @Override
    public Metadata getMetadata() {
        return delegate.getMetadata();
    }

    @Override
    public boolean isSchemaMetadataEnabled() {
        return delegate.isSchemaMetadataEnabled();
    }

    @NotNull
    @Override
    public CompletionStage<Metadata> setSchemaMetadataEnabled(@Nullable Boolean aBoolean) {
        return delegate.setSchemaMetadataEnabled(aBoolean);
    }

    @NotNull
    @Override
    public CompletionStage<Metadata> refreshSchemaAsync() {
        return delegate.refreshSchemaAsync();
    }

    @NotNull
    @Override
    public CompletionStage<Boolean> checkSchemaAgreementAsync() {
        return delegate.checkSchemaAgreementAsync();
    }

    @NotNull
    @Override
    public DriverContext getContext() {
        return delegate.getContext();
    }

    @NotNull
    @Override
    public Optional<CqlIdentifier> getKeyspace() {
        return delegate.getKeyspace();
    }

    @NotNull
    @Override
    public Optional<Metrics> getMetrics() {
        return delegate.getMetrics();
    }

    @Nullable
    @Override
    public <RequestT extends Request, ResultT> ResultT execute(@NotNull RequestT requestT, @NotNull GenericType<ResultT> genericType) {
        return delegate.execute(requestT, genericType);
    }

    @NotNull
    @Override
    public CompletionStage<Void> closeFuture() {
        return delegate.closeFuture();
    }

    @NotNull
    @Override
    public CompletionStage<Void> closeAsync() {
        return delegate.closeAsync();
    }

    @NotNull
    @Override
    public CompletionStage<Void> forceCloseAsync() {
        return delegate.forceCloseAsync();
    }

    @NotNull
    @Override
    public ContinuousResultSet executeContinuously(@NotNull Statement<?> statement) {
        return delegate.executeContinuously(statement);
    }

    @NotNull
    @Override
    public CompletionStage<ContinuousAsyncResultSet> executeContinuouslyAsync(@NotNull Statement<?> statement) {
        return delegate.executeContinuouslyAsync(statement);
    }

    @NotNull
    @Override
    public ContinuousReactiveResultSet executeContinuouslyReactive(@NotNull String query) {
        return delegate.executeContinuouslyReactive(query);
    }

    @NotNull
    @Override
    public ContinuousReactiveResultSet executeContinuouslyReactive(@NotNull Statement<?> statement) {
        return delegate.executeContinuouslyReactive(statement);
    }

    @NotNull
    @Override
    public ReactiveResultSet executeReactive(@NotNull String query) {
        printStatement(query);
        return delegate.executeReactive(query);
    }

    @NotNull
    @Override
    public ReactiveResultSet executeReactive(@NotNull String query, @NotNull Object... values) {
        printStatement(query);
        return delegate.executeReactive(query, values);
    }

    @NotNull
    @Override
    public ReactiveResultSet executeReactive(@NotNull String query, @NotNull Map<String, Object> values) {
        printStatement(query);
        return delegate.executeReactive(query, values);
    }

    @NotNull
    @Override
    public ReactiveResultSet executeReactive(@NotNull Statement<?> statement) {
        printStatement(statement);
        statementRecorder.ifPresent(r -> r.recordStatement(statement));
        return scenario.getCorrespondingBehavior(statement)
            .execute(delegate, statement);
    }

    @NotNull
    @Override
    public GraphResultSet execute(@NotNull GraphStatement<?> graphStatement) {
        return delegate.execute(graphStatement);
    }

    @NotNull
    @Override
    public CompletionStage<AsyncGraphResultSet> executeAsync(@NotNull GraphStatement<?> graphStatement) {
        return delegate.executeAsync(graphStatement);
    }

    @NotNull
    @Override
    public ReactiveGraphResultSet executeReactive(@NotNull GraphStatement<?> statement) {
        return delegate.executeReactive(statement);
    }

    @NotNull
    @Override
    public CompletionStage<AsyncResultSet> executeAsync(@NotNull Statement<?> statement) {
        printStatement(statement);
        statementRecorder.ifPresent(r -> r.recordStatement(statement));
        return delegate.executeAsync(statement);
    }

    @NotNull
    @Override
    public CompletionStage<AsyncResultSet> executeAsync(@NotNull String query) {
        printStatement(query);
        return delegate.executeAsync(query);
    }

    @NotNull
    @Override
    public CompletionStage<AsyncResultSet> executeAsync(@NotNull String query, @NotNull Object... values) {
        printStatement(query);
        return delegate.executeAsync(query, values);
    }

    @NotNull
    @Override
    public CompletionStage<AsyncResultSet> executeAsync(@NotNull String query, @NotNull Map<String, Object> values) {
        printStatement(query);
        return delegate.executeAsync(query, values);
    }

    @NotNull
    @Override
    public CompletionStage<PreparedStatement> prepareAsync(@NotNull SimpleStatement statement) {
        printStatement(statement);
        return delegate.prepareAsync(statement);
    }

    @NotNull
    @Override
    public CompletionStage<PreparedStatement> prepareAsync(@NotNull String query) {
        printStatement(query);
        return delegate.prepareAsync(query);
    }

    @NotNull
    @Override
    public CompletionStage<PreparedStatement> prepareAsync(PrepareRequest request) {
        return delegate.prepareAsync(request);
    }

    @NotNull
    @Override
    public ResultSet execute(@NotNull Statement<?> statement) {
        printStatement(statement);
        statementRecorder.ifPresent(r -> r.recordStatement(statement));
        return delegate.execute(statement);
    }

    @NotNull
    @Override
    public ResultSet execute(@NotNull String query) {
        printStatement(query);
        return delegate.execute(query);
    }

    @NotNull
    @Override
    public ResultSet execute(@NotNull String query, @NotNull Object... values) {
        printStatement(query);
        return delegate.execute(query, values);
    }

    @NotNull
    @Override
    public ResultSet execute(@NotNull String query, @NotNull Map<String, Object> values) {
        return delegate.execute(query, values);
    }

    @NotNull
    @Override
    public PreparedStatement prepare(@NotNull SimpleStatement statement) {
        return delegate.prepare(statement);
    }

    @NotNull
    @Override
    public PreparedStatement prepare(@NotNull String query) {
        return delegate.prepare(query);
    }

    @NotNull
    @Override
    public PreparedStatement prepare(@NotNull PrepareRequest request) {
        return delegate.prepare(request);
    }

    @NotNull
    @Override
    public Metadata refreshSchema() {
        return delegate.refreshSchema();
    }

    @Override
    public boolean checkSchemaAgreement() {
        return delegate.checkSchemaAgreement();
    }

    @Override
    public boolean isClosed() {
        return delegate.isClosed();
    }

    @Override
    public void close() {
        delegate.close();
    }
}
