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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import com.datastax.dse.driver.api.core.cql.reactive.ReactiveResultSet;
import com.datastax.dse.driver.api.core.cql.reactive.ReactiveRow;
import com.datastax.dse.driver.api.core.cql.reactive.ReactiveSession;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.ColumnDefinitions;
import com.datastax.oss.driver.api.core.cql.ExecutionInfo;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.driver.api.core.cql.Statement;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class Scenario {
    public static class InjectedFailureException extends RuntimeException {
        public InjectedFailureException() {
            super("Injected failure");
        }
    }

    @FunctionalInterface
    interface Behavior {
        Behavior THROW = (session, statement) -> new FailingReactiveResultSet(session.executeReactive(statement));

        Behavior EXECUTE_NORMALLY = ReactiveSession::executeReactive;

        // Hack. We rely on version key unicity (because UUID) to create an empty ResultSet
        Behavior RETURN_EMPTY = (session, statement) -> session.executeReactive(
            "SELECT value FROM schemaVersion WHERE key=49128560-bb80-11ea-bad6-e3b96c9cd431;");

        static Behavior awaitOn(Barrier barrier, Behavior behavior) {
            return (session, statement) -> new AwaitingReactiveResultSet(behavior.execute(session, statement), barrier);
        }

        ReactiveResultSet execute(CqlSession session, Statement statement);
    }

    static class FailingReactiveResultSet implements ReactiveResultSet {
        private final ReactiveResultSet delegate;

        FailingReactiveResultSet(ReactiveResultSet delegate) {
            this.delegate = delegate;
        }

        @NotNull
        @Override
        public Publisher<? extends ColumnDefinitions> getColumnDefinitions() {
            return delegate.getColumnDefinitions();
        }

        @NotNull
        @Override
        public Publisher<? extends ExecutionInfo> getExecutionInfos() {
            return delegate.getExecutionInfos();
        }

        @NotNull
        @Override
        public Publisher<Boolean> wasApplied() {
            return delegate.wasApplied();
        }

        @Override
        public void subscribe(Subscriber<? super ReactiveRow> s) {
            //JAMES-3289 add a delay in the throwing behavior to avoid the reactor bug defined in https://github.com/reactor/reactor-core/issues/1941
            //which cause flacky tests.
            //once this bug is solved this delay could be removed.
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                //DO NOTHING
            }
            s.onSubscribe(new Subscription() {
                @Override
                public void request(long n) {
                    s.onError(new InjectedFailureException());
                }

                @Override
                public void cancel() {

                }
            });
        }
    }

    static class AwaitingReactiveResultSet implements ReactiveResultSet {
        private final ReactiveResultSet delegate;
        private final Barrier barrier;

        AwaitingReactiveResultSet(ReactiveResultSet delegate, Barrier barrier) {
            this.delegate = delegate;
            this.barrier = barrier;
        }

        @NotNull
        @Override
        public Publisher<? extends ColumnDefinitions> getColumnDefinitions() {
            return delegate.getColumnDefinitions();
        }

        @NotNull
        @Override
        public Publisher<? extends ExecutionInfo> getExecutionInfos() {
            return delegate.getExecutionInfos();
        }

        @NotNull
        @Override
        public Publisher<Boolean> wasApplied() {
            return delegate.wasApplied();
        }

        @Override
        public void subscribe(Subscriber<? super ReactiveRow> s) {
            // Might be called from the Cassandra driver event loop.
            // If synchronisation is attempted on request B and code looks like A.then(B)
            // Then the event loop will be blocked.
            // Switch the blocking synchronization to another thread to not starve the driver.
            Mono.fromRunnable(() -> {
                barrier.call();
                delegate.subscribe(s);
            }).subscribeOn(Schedulers.newSingle("await"))
                .subscribe();
        }
    }

    @FunctionalInterface
    interface StatementPredicate {
        class StatementStartingWith implements StatementPredicate {
            private final String queryStringPrefix;

            StatementStartingWith(String queryStringPrefix) {
                this.queryStringPrefix = queryStringPrefix;
            }

            @Override
            public boolean test(Statement statement) {
                if (statement instanceof BoundStatement) {
                    BoundStatement boundStatement = (BoundStatement) statement;
                    return boundStatement.getPreparedStatement()
                        .getQuery()
                        .startsWith(queryStringPrefix);
                }
                if (statement instanceof SimpleStatement) {
                    SimpleStatement regularStatement = (SimpleStatement) statement;
                    return regularStatement.getQuery()
                        .startsWith(queryStringPrefix);
                }
                return false;
            }
        }

        StatementPredicate ALL_STATEMENTS = statement -> true;

        boolean test(Statement statement);
    }

    @FunctionalInterface
    interface Validity {
        class LimitedValidity implements Validity {
            final AtomicInteger remaining;

            private LimitedValidity(int applyCount) {
                Preconditions.checkArgument(applyCount > 0, "'applyCount' needs to be strictly positive");
                this.remaining = new AtomicInteger(applyCount);
            }

            @Override
            public boolean isApplicable() {
                return remaining.getAndDecrement() > 0;
            }
        }

        Validity FOREVER = () -> true;

        boolean isApplicable();
    }

    public interface Builder {
        @FunctionalInterface
        interface RequiresValidity {
            RequiresStatementPredicate validity(Validity validity);

            default RequiresStatementPredicate forever() {
                return validity(Validity.FOREVER);
            }

            default RequiresStatementPredicate times(int applyCount) {
                return validity(new Validity.LimitedValidity(applyCount));
            }
        }

        @FunctionalInterface
        interface RequiresStatementPredicate {
            ExecutionHook statementPredicate(StatementPredicate statementPredicate);

            default ExecutionHook forAllQueries() {
                return statementPredicate(StatementPredicate.ALL_STATEMENTS);
            }

            default ExecutionHook whenQueryStartsWith(String queryStringPrefix) {
                return statementPredicate(new StatementPredicate.StatementStartingWith(queryStringPrefix));
            }
        }

        @FunctionalInterface
        interface ComposeBehavior {
            RequiresValidity then(Behavior behavior);

            default RequiresValidity thenExecuteNormally() {
                return then(Behavior.EXECUTE_NORMALLY);
            }

            default RequiresValidity thenFail() {
                return then(Behavior.THROW);
            }
        }

        static RequiresValidity fail() {
            return validity -> statementPredicate -> new ExecutionHook(
                statementPredicate,
                Behavior.THROW,
                validity);
        }

        static RequiresValidity returnEmpty() {
            return validity -> statementPredicate -> new ExecutionHook(
                statementPredicate,
                Behavior.RETURN_EMPTY,
                validity);
        }

        static RequiresValidity executeNormally() {
            return validity -> statementPredicate -> new ExecutionHook(
                statementPredicate,
                Behavior.EXECUTE_NORMALLY,
                validity);
        }

        static ComposeBehavior awaitOn(Barrier barrier) {
            return behavior -> validity -> statementPredicate -> new ExecutionHook(
                statementPredicate,
                Behavior.awaitOn(barrier, behavior),
                validity);
        }
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

    public static class ExecutionHook {
        final StatementPredicate statementPredicate;
        final Behavior behavior;
        final Validity validity;

        private ExecutionHook(StatementPredicate statementPredicate, Behavior behavior, Validity validity) {
            this.statementPredicate = statementPredicate;
            this.behavior = behavior;
            this.validity = validity;
        }

        /**
         * Returns the behaviour of this hook if it should be applied
         */
        Stream<Behavior> asBehavior(Statement statement) {
            if (statementPredicate.test(statement)) {
                if (validity.isApplicable()) {
                    return Stream.of(behavior);
                }
            }
            return Stream.empty();
        }
    }

    public static final Scenario NOTHING = new Scenario(ImmutableList.of());

    public static Scenario combine(ExecutionHook... hooks) {
        return new Scenario(ImmutableList.copyOf(hooks));
    }

    private final ImmutableList<ExecutionHook> hooks;

    private Scenario(ImmutableList<ExecutionHook> hooks) {
        this.hooks = hooks;
    }

    Behavior getCorrespondingBehavior(Statement statement) {
        return hooks.stream()
            .flatMap(executionHook -> executionHook.asBehavior(statement))
            .findFirst()
            .orElse(Behavior.EXECUTE_NORMALLY);
    }
}