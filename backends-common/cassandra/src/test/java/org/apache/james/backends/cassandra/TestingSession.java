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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.CloseFuture;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.RegularStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.ResultSetFuture;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.Statement;
import com.google.common.util.concurrent.ListenableFuture;

public class TestingSession implements Session {
    @FunctionalInterface
    interface Behavior {
        Behavior THROW = (session, statement) -> {
            RuntimeException injected_failure = new RuntimeException("Injected failure");
            injected_failure.printStackTrace();
            throw injected_failure;
        };

        Behavior EXECUTE_NORMALLY = Session::executeAsync;

        static Behavior awaitOn(Barrier barrier) {
            return (session, statement) -> {
                barrier.call();
                return session.executeAsync(statement);
            };
        }

        ResultSetFuture execute(Session session, Statement statement);
    }

    public static class Barrier {
        private final CountDownLatch callerLatch = new CountDownLatch(1);
        private final CountDownLatch awaitCallerLatch;

        public Barrier() {
            this(1);
        }

        public Barrier(int callerCount) {
            awaitCallerLatch = new CountDownLatch(callerCount);
        }

        public void awaitCaller() throws InterruptedException {
            awaitCallerLatch.await();
        }

        public void releaseCaller() {
            callerLatch.countDown();
        }

        void call() {
            awaitCallerLatch.countDown();
            try {
                callerLatch.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @FunctionalInterface
    interface StatementPredicate extends Predicate<Statement> {

    }

    static class BoundStatementStartingWith implements StatementPredicate {
        private final String queryStringPrefix;

        BoundStatementStartingWith(String queryStringPrefix) {
            this.queryStringPrefix = queryStringPrefix;
        }

        @Override
        public boolean test(Statement statement) {
            if (statement instanceof BoundStatement) {
                BoundStatement boundStatement = (BoundStatement) statement;
                return boundStatement.preparedStatement()
                    .getQueryString()
                    .startsWith(queryStringPrefix);
            }
            return false;
        }
    }

    @FunctionalInterface
    public interface RequiresCondition {
        RequiresApplyCount condition(StatementPredicate statementPredicate);

        default RequiresApplyCount always() {
            return condition(ALL_STATEMENTS);
        }

        default RequiresApplyCount whenBoundStatementStartsWith(String queryStringPrefix) {
            return condition(new BoundStatementStartingWith(queryStringPrefix));
        }
    }

    @FunctionalInterface
    public interface RequiresApplyCount {
        FinalStage times(int applyCount);
    }

    @FunctionalInterface
    public interface FinalStage {
        void setExecutionHook();
    }

    private static class ExecutionHook {
        final StatementPredicate statementPredicate;
        final Behavior behavior;
        final AtomicInteger remaining;

        private ExecutionHook(StatementPredicate statementPredicate, Behavior behavior, int applyCount) {
            this.statementPredicate = statementPredicate;
            this.behavior = behavior;
            this.remaining = new AtomicInteger(applyCount);
        }

        ResultSetFuture execute(Session session, Statement statement) {
            if (statementPredicate.test(statement)) {
                int hookPosition = remaining.getAndDecrement();
                if (hookPosition > 0) {
                    return behavior.execute(session, statement);
                }
            }
            return Behavior.EXECUTE_NORMALLY.execute(session, statement);
        }
    }

    private static StatementPredicate ALL_STATEMENTS = statement -> true;
    private static ExecutionHook NO_EXECUTION_HOOK = new ExecutionHook(ALL_STATEMENTS, Behavior.EXECUTE_NORMALLY, 0);

    private final Session delegate;
    private volatile ExecutionHook executionHook;

    TestingSession(Session delegate) {
        this.delegate = delegate;
        this.executionHook = NO_EXECUTION_HOOK;
    }

    public RequiresCondition fail() {
        return condition -> applyCount -> () -> executionHook = new ExecutionHook(condition, Behavior.THROW, applyCount);
    }

    public RequiresCondition awaitOn(Barrier barrier) {
        return condition -> applyCount -> () -> executionHook = new ExecutionHook(condition, Behavior.awaitOn(barrier), applyCount);
    }

    public void resetExecutionHook() {
        executionHook = NO_EXECUTION_HOOK;
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
        return delegate.execute(query);
    }

    @Override
    public ResultSet execute(String query, Object... values) {
        return delegate.execute(query, values);
    }

    @Override
    public ResultSet execute(String query, Map<String, Object> values) {
        return delegate.execute(query, values);
    }

    @Override
    public ResultSet execute(Statement statement) {
        return delegate.execute(statement);
    }

    @Override
    public ResultSetFuture executeAsync(String query) {
        return delegate.executeAsync(query);
    }

    @Override
    public ResultSetFuture executeAsync(String query, Object... values) {
        return delegate.executeAsync(query, values);
    }

    @Override
    public ResultSetFuture executeAsync(String query, Map<String, Object> values) {
        return delegate.executeAsync(query, values);
    }

    @Override
    public ResultSetFuture executeAsync(Statement statement) {
        return executionHook.execute(delegate, statement);
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
