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

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.CloseFuture;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.RegularStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.ResultSetFuture;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.SimpleStatement;
import com.datastax.driver.core.Statement;
import com.google.common.util.concurrent.ListenableFuture;

public class TestingSession implements Session {
    private final Session delegate;
    private volatile Scenario scenario;
    private volatile boolean printStatements;
    private volatile Optional<StatementRecorder> statementRecorder;

    public TestingSession(Session delegate) {
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

    public StatementRecorder recordStatements(StatementRecorder statementRecorder) {
        this.statementRecorder = Optional.of(statementRecorder);
        return statementRecorder;
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

    @Override
    public String getLoggedKeyspace() {
        return delegate.getLoggedKeyspace();
    }

    @Override
    public Session init() {
        return delegate.init();
    }

    @Override
    public ListenableFuture<Session> initAsync() {
        return delegate.initAsync();
    }

    @Override
    public ResultSet execute(String query) {
        printStatement(query);
        return delegate.execute(query);
    }

    @Override
    public ResultSet execute(String query, Object... values) {
        printStatement(query);
        return delegate.execute(query, values);
    }

    @Override
    public ResultSet execute(String query, Map<String, Object> values) {
        printStatement(query);
        return delegate.execute(query, values);
    }

    @Override
    public ResultSet execute(Statement statement) {
        printStatement(statement);
        statementRecorder.ifPresent(recorder -> recorder.recordStatement(statement));
        return delegate.execute(statement);
    }

    @Override
    public ResultSetFuture executeAsync(String query) {
        printStatement(query);
        return delegate.executeAsync(query);
    }

    @Override
    public ResultSetFuture executeAsync(String query, Object... values) {
        printStatement(query);
        return delegate.executeAsync(query, values);
    }

    @Override
    public ResultSetFuture executeAsync(String query, Map<String, Object> values) {
        printStatement(query);
        return delegate.executeAsync(query, values);
    }

    @Override
    public ResultSetFuture executeAsync(Statement statement) {
        printStatement(statement);
        statementRecorder.ifPresent(recorder -> recorder.recordStatement(statement));
        return scenario
            .getCorrespondingBehavior(statement)
            .execute(delegate, statement);
    }

    private void printStatement(String query) {
        if (printStatements) {
            System.out.println("Executing: " + query);
        }
    }

    private void printStatement(Statement statement) {
        if (printStatements) {
            if (statement instanceof BoundStatement) {
                BoundStatement boundStatement = (BoundStatement) statement;
                System.out.println("Executing: " + boundStatement.preparedStatement().getQueryString());
            } else if (statement instanceof SimpleStatement) {
                SimpleStatement simpleStatement = (SimpleStatement) statement;
                System.out.println("Executing: " + simpleStatement.getQueryString());
            } else {
                System.out.println("Executing: " + statement);
            }
        }
    }

    @Override
    public PreparedStatement prepare(String query) {
        return delegate.prepare(query);
    }

    @Override
    public PreparedStatement prepare(RegularStatement statement) {
        return delegate.prepare(statement);
    }

    @Override
    public ListenableFuture<PreparedStatement> prepareAsync(String query) {
        return delegate.prepareAsync(query);
    }

    @Override
    public ListenableFuture<PreparedStatement> prepareAsync(RegularStatement statement) {
        return delegate.prepareAsync(statement);
    }

    @Override
    public CloseFuture closeAsync() {
        return delegate.closeAsync();
    }

    @Override
    public void close() {
        delegate.close();
    }

    @Override
    public boolean isClosed() {
        return delegate.isClosed();
    }

    @Override
    public Cluster getCluster() {
        return delegate.getCluster();
    }

    @Override
    public State getState() {
        return delegate.getState();
    }
}
