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


import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Predicate;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.Statement;
import com.google.common.collect.ImmutableList;

public class StatementRecorder {
    @FunctionalInterface
    public interface Selector {
        Selector ALL = statements -> statements;

        static Selector preparedStatement(String statementString) {
            return preparedStatementMatching(statement -> statement.preparedStatement().getQueryString().equals(statementString));
        }

        static Selector preparedStatementStartingWith(String statementString) {
            return preparedStatementMatching(statement -> statement.preparedStatement().getQueryString().startsWith(statementString));
        }

        private static StatementRecorder.Selector preparedStatementMatching(Predicate<BoundStatement> condition) {
            return statements -> statements.stream()
                .filter(BoundStatement.class::isInstance)
                .map(BoundStatement.class::cast)
                .filter(condition)
                .collect(ImmutableList.toImmutableList());
        }

        List<Statement> select(List<Statement> statements);
    }

    private final StatementRecorder.Selector selector;
    private final ConcurrentLinkedDeque statements;

    public StatementRecorder() {
        this(Selector.ALL);
    }

    StatementRecorder(Selector selector) {
        this.selector = selector;
        statements = new ConcurrentLinkedDeque();
    }

    void recordStatement(Statement statement) {
        if (statements.addAll(selector.select(ImmutableList.of(statement)))) {
            System.out.println("recordStatement");
        }
    }

    public List<Statement> listExecutedStatements() {
        return ImmutableList.copyOf(statements);
    }

    public List<Statement> listExecutedStatements(Selector selector) {
        return selector.select(ImmutableList.copyOf(statements));
    }
}
