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

import org.testcontainers.shaded.com.google.common.collect.ImmutableList;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.RegularStatement;
import com.datastax.driver.core.ResultSetFuture;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.Statement;
import com.google.common.base.Preconditions;

public class Scenario {
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
                    return boundStatement.preparedStatement()
                        .getQueryString()
                        .startsWith(queryStringPrefix);
                }
                if (statement instanceof RegularStatement) {
                    RegularStatement regularStatement = (RegularStatement) statement;
                    return regularStatement.getQueryString()
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

        static RequiresValidity fail() {
            return validity -> statementPredicate -> new ExecutionHook(
                statementPredicate,
                Behavior.THROW,
                validity);
        }

        static RequiresValidity executeNormally() {
            return validity -> statementPredicate -> new ExecutionHook(
                statementPredicate,
                Behavior.EXECUTE_NORMALLY,
                validity);
        }

        static RequiresValidity awaitOn(Barrier barrier) {
            return validity -> statementPredicate -> new ExecutionHook(
                statementPredicate,
                Behavior.awaitOn(barrier),
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
