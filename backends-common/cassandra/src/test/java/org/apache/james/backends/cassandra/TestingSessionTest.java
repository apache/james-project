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

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static org.apache.james.backends.cassandra.Scenario.Builder.awaitOn;
import static org.apache.james.backends.cassandra.Scenario.Builder.executeNormally;
import static org.apache.james.backends.cassandra.Scenario.Builder.fail;
import static org.apache.james.backends.cassandra.Scenario.Builder.returnEmpty;
import static org.apache.james.backends.cassandra.versions.table.CassandraSchemaVersionTable.TABLE_NAME;
import static org.apache.james.backends.cassandra.versions.table.CassandraSchemaVersionTable.VALUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.james.backends.cassandra.Scenario.Barrier;
import org.apache.james.backends.cassandra.Scenario.InjectedFailureException;
import org.apache.james.backends.cassandra.StatementRecorder.Selector;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionDAO;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.apache.james.backends.cassandra.versions.SchemaVersion;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.datastax.oss.driver.api.core.cql.BoundStatement;

import reactor.core.scheduler.Schedulers;

class TestingSessionTest {
    public static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraSchemaVersionModule.MODULE);

    private CassandraSchemaVersionDAO dao;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        dao = new CassandraSchemaVersionDAO(cassandra.getConf());
        dao.truncateVersion().block();
    }

    @Test
    void daoOperationShouldNotBeInstrumentedByDefault() {
        assertThatCode(() -> dao.getCurrentSchemaVersion().block())
            .doesNotThrowAnyException();
    }

    @Test
    void daoOperationShouldNotBeInstrumentedWhenExecuteNormally(CassandraCluster cassandra) {
        cassandra.getConf()
            .registerScenario(executeNormally()
                .times(1)
                .whenQueryStartsWith("SELECT value FROM schemaversion"));

        assertThatCode(() -> dao.getCurrentSchemaVersion().block())
            .doesNotThrowAnyException();
    }

    @Test
    void daoOperationShouldNotBeInstrumentedWhenReturnEmpty(CassandraCluster cassandra) {
        cassandra.getConf()
            .registerScenario(returnEmpty()
                .times(1)
                .whenQueryStartsWith("SELECT value FROM schemaversion"));

        assertThat(dao.getCurrentSchemaVersion().block())
            .isEmpty();
    }

    @Test
    void recordStatementsShouldKeepTraceOfExecutedStatement(CassandraCluster cassandra) {
        StatementRecorder statementRecorder = cassandra.getConf().recordStatements();

        dao.getCurrentSchemaVersion().block();

        assertThat(statementRecorder.listExecutedStatements(
                Selector.preparedStatement("SELECT value FROM schemaversion")))
            .hasSize(1);
    }

    @Test
    void recordStatementsShouldKeepTraceOfExecutedStatements(CassandraCluster cassandra) {
        StatementRecorder statementRecorder = cassandra.getConf().recordStatements();

        dao.updateVersion(new SchemaVersion(36)).block();
        dao.getCurrentSchemaVersion().block();

        assertThat(statementRecorder.listExecutedStatements(Selector.ALL))
            .filteredOn(statement -> statement instanceof BoundStatement)
            .extracting(BoundStatement.class::cast)
            .extracting(statement -> statement.getPreparedStatement().getQuery())
            .containsExactly("INSERT INTO schemaversion (key,value) VALUES (:key,:value)", "SELECT value FROM schemaversion");
    }

    @Test
    void recordStatementsShouldNotKeepTraceOfExecutedStatementsBeforeRecording(CassandraCluster cassandra) {
        dao.getCurrentSchemaVersion().block();

        StatementRecorder statementRecorder = cassandra.getConf().recordStatements();

        assertThat(statementRecorder.listExecutedStatements())
            .isEmpty();
    }

    @Test
    void daoOperationShouldNotBeInstrumentedWhenNotMatching(CassandraCluster cassandra) {
        cassandra.getConf()
            .registerScenario(fail()
                .times(1)
                .whenQueryStartsWith("non matching"));

        assertThatCode(() -> dao.getCurrentSchemaVersion().block())
            .doesNotThrowAnyException();
    }

    @Test
    void daoOperationShouldFailWhenInstrumented(CassandraCluster cassandra) {
        cassandra.getConf()
            .registerScenario(fail()
                .times(1)
                .whenQueryStartsWith("SELECT value FROM schemaversion"));

        assertThatThrownBy(() -> dao.getCurrentSchemaVersion().block())
            .hasCauseInstanceOf(InjectedFailureException.class);
    }

    @Test
    void regularStatementsShouldBeInstrumented(CassandraCluster cassandra) {
        cassandra.getConf()
            .registerScenario(fail()
                .times(1)
                .whenQueryStartsWith("SELECT value FROM schemaversion"));

        assertThatThrownBy(() -> new CassandraAsyncExecutor(cassandra.getConf())
                .executeVoid(selectFrom(TABLE_NAME).column(VALUE).build())
                .block())
            .hasCauseInstanceOf(InjectedFailureException.class);
    }

    @Test
    void forAllQueriesShouldMatchAllStatements(CassandraCluster cassandra) {
        cassandra.getConf()
            .registerScenario(fail()
                .times(1)
                .forAllQueries());

        assertThatThrownBy(() -> dao.getCurrentSchemaVersion().block())
            .hasCauseInstanceOf(InjectedFailureException.class);
    }

    @Test
    void daoShouldNotBeInstrumentedWhenTimesIsExceeded(CassandraCluster cassandra) {
        cassandra.getConf()
            .registerScenario(fail()
                .times(1)
                .whenQueryStartsWith("SELECT value FROM schemaversion"));

        try {
            dao.getCurrentSchemaVersion().block();
        } catch (Exception e) {
            // discard expected exception
        }

        assertThatCode(() -> dao.getCurrentSchemaVersion().block())
            .doesNotThrowAnyException();
    }

    @Test
    void timesShouldSpecifyExactlyTheFailureCount(CassandraCluster cassandra) {
        cassandra.getConf()
            .registerScenario(fail()
                .times(2)
                .whenQueryStartsWith("SELECT value FROM schemaversion"));

        SoftAssertions.assertSoftly(softly -> {
            assertThatThrownBy(() -> dao.getCurrentSchemaVersion().block())
                .hasCauseInstanceOf(InjectedFailureException.class);
            assertThatThrownBy(() -> dao.getCurrentSchemaVersion().block())
                .hasCauseInstanceOf(InjectedFailureException.class);
            assertThatCode(() -> dao.getCurrentSchemaVersion().block())
                .doesNotThrowAnyException();
        });
    }

    @Test
    void scenarioShouldDefiningSeveralHooks(CassandraCluster cassandra) {
        cassandra.getConf()
            .registerScenario(
                executeNormally()
                    .times(1)
                    .whenQueryStartsWith("SELECT value FROM schemaversion"),
                fail()
                    .times(1)
                    .whenQueryStartsWith("SELECT value FROM schemaversion"));

        SoftAssertions.assertSoftly(softly -> {
            assertThatCode(() -> dao.getCurrentSchemaVersion().block())
                .doesNotThrowAnyException();
            assertThatThrownBy(() -> dao.getCurrentSchemaVersion().block())
                .hasCauseInstanceOf(InjectedFailureException.class);
            assertThatCode(() -> dao.getCurrentSchemaVersion().block())
                .doesNotThrowAnyException();
        });
    }

    @Test
    void foreverShouldAlwaysApplyBehaviour(CassandraCluster cassandra) {
        cassandra.getConf()
            .registerScenario(fail()
                .forever()
                .whenQueryStartsWith("SELECT value FROM schemaversion"));

        SoftAssertions.assertSoftly(softly -> {
            assertThatThrownBy(() -> dao.getCurrentSchemaVersion().block())
                .hasCauseInstanceOf(InjectedFailureException.class);
            assertThatThrownBy(() -> dao.getCurrentSchemaVersion().block())
                .hasCauseInstanceOf(InjectedFailureException.class);
            assertThatThrownBy(() -> dao.getCurrentSchemaVersion().block())
                .hasCauseInstanceOf(InjectedFailureException.class);
        });
    }

    @Test
    void timesShouldBeTakenIntoAccountOnlyForMatchingStatements(CassandraCluster cassandra) {
        cassandra.getConf()
            .registerScenario(fail()
                .times(1)
                .whenQueryStartsWith("SELECT value FROM schemaversion"));

        dao.updateVersion(new SchemaVersion(36)).block();

        assertThatThrownBy(() -> dao.getCurrentSchemaVersion().block())
            .hasCauseInstanceOf(InjectedFailureException.class);
    }

    @Timeout(10)
    @Test
    void statementShouldNotBeAppliedBeforeBarrierIsReleased(CassandraCluster cassandra) throws Exception {
        SchemaVersion originalSchemaVersion = new SchemaVersion(32);
        dao.updateVersion(originalSchemaVersion).block();
        Barrier barrier = new Barrier();
        cassandra.getConf()
            .registerScenario(awaitOn(barrier)
                .thenExecuteNormally()
                .times(1)
                .whenQueryStartsWith("INSERT INTO schemaversion"));

        dao.updateVersion(new SchemaVersion(36)).subscribeOn(Schedulers.fromExecutor(EXECUTOR)).subscribe();

        Thread.sleep(100);

        assertThat(dao.getCurrentSchemaVersion().block())
            .contains(originalSchemaVersion);
    }

    @Test
    void statementShouldBeAppliedWhenBarrierIsReleased(CassandraCluster cassandra) throws Exception {
        SchemaVersion originalSchemaVersion = new SchemaVersion(32);
        SchemaVersion newVersion = new SchemaVersion(36);

        dao.updateVersion(originalSchemaVersion).block();
        Barrier barrier = new Barrier();
        cassandra.getConf()
            .registerScenario(awaitOn(barrier)
                .thenExecuteNormally()
                .times(1)
                .whenQueryStartsWith("INSERT INTO schemaversion"));

        CompletableFuture<Void> operation = dao.updateVersion(newVersion)
            .subscribeOn(Schedulers.fromExecutor(EXECUTOR))
            .toFuture();

        barrier.releaseCaller();
        operation.get();

        assertThat(dao.getCurrentSchemaVersion().block())
            .contains(newVersion);
    }

    @Test
    @Timeout(10)
    void testShouldBeAbleToAwaitCaller(CassandraCluster cassandra) throws Exception {
        SchemaVersion originalSchemaVersion = new SchemaVersion(32);
        SchemaVersion newVersion = new SchemaVersion(36);

        dao.updateVersion(originalSchemaVersion).block();
        Barrier barrier = new Barrier();
        cassandra.getConf()
            .registerScenario(awaitOn(barrier)
                .thenExecuteNormally()
                .times(1)
                .whenQueryStartsWith("INSERT INTO schemaversion"));

        CompletableFuture<Void> operation = dao.updateVersion(newVersion)
            .subscribeOn(Schedulers.fromExecutor(EXECUTOR))
            .toFuture();

        barrier.awaitCaller();
        barrier.releaseCaller();
        operation.get();

        assertThat(dao.getCurrentSchemaVersion().block())
            .contains(newVersion);
    }

    @Test
    @Timeout(10)
    void awaitOnShouldBeAbleToInjectFailure(CassandraCluster cassandra) throws Exception {
        SchemaVersion originalSchemaVersion = new SchemaVersion(32);
        SchemaVersion newVersion = new SchemaVersion(36);

        dao.updateVersion(originalSchemaVersion).block();
        Barrier barrier = new Barrier();
        cassandra.getConf()
            .registerScenario(awaitOn(barrier)
                .thenFail()
                .times(1)
                .whenQueryStartsWith("INSERT INTO schemaversion"));

        CompletableFuture<Void> operation = dao.updateVersion(newVersion)
            .subscribeOn(Schedulers.fromExecutor(EXECUTOR))
            .onErrorMap(Throwable::getCause)
            .toFuture();

        barrier.awaitCaller();
        barrier.releaseCaller();

        assertThatThrownBy(operation::get)
            .hasCauseInstanceOf(InjectedFailureException.class);
    }
}