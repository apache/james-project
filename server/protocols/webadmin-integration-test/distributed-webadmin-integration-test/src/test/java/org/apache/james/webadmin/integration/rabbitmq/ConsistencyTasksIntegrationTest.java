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

package org.apache.james.webadmin.integration.rabbitmq;

import static io.restassured.RestAssured.when;
import static io.restassured.RestAssured.with;
import static org.apache.james.backends.cassandra.Scenario.Builder.awaitOn;
import static org.apache.james.backends.cassandra.Scenario.Builder.fail;
import static org.apache.james.jmap.JMAPTestingConstants.BOB;
import static org.apache.james.jmap.JMAPTestingConstants.BOB_PASSWORD;
import static org.apache.james.webadmin.Constants.SEPARATOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.Matchers.is;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.Optional;

import javax.mail.Flags;

import org.apache.james.CassandraExtension;
import org.apache.james.CassandraRabbitMQJamesServerMain;
import org.apache.james.DockerElasticSearchExtension;
import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.backends.cassandra.Scenario.Barrier;
import org.apache.james.backends.cassandra.TestingSession;
import org.apache.james.backends.cassandra.init.SessionWithInitializedTablesFactory;
import org.apache.james.junit.categories.BasicFeature;
import org.apache.james.mailbox.events.RetryBackoffConfiguration;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.modules.AwsS3BlobStoreExtension;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.modules.QuotaProbesImpl;
import org.apache.james.modules.RabbitMQExtension;
import org.apache.james.probe.DataProbe;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.GuiceProbe;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.integration.WebadminIntegrationTestModule;
import org.apache.james.webadmin.routes.AliasRoutes;
import org.apache.james.webadmin.routes.CassandraMappingsRoutes;
import org.apache.james.webadmin.routes.TasksRoutes;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.datastax.driver.core.Session;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;

@Tag(BasicFeature.TAG)
class ConsistencyTasksIntegrationTest {

    private static class TestingSessionProbe implements GuiceProbe {
        private final TestingSession testingSession;

        @Inject
        private TestingSessionProbe(TestingSession testingSession) {
            this.testingSession = testingSession;
        }

        public TestingSession getTestingSession() {
            return testingSession;
        }
    }

    private static class TestingSessionModule extends AbstractModule {
        @Override
        protected void configure() {
            Multibinder.newSetBinder(binder(), GuiceProbe.class)
                .addBinding()
                .to(TestingSessionProbe.class);

            bind(Session.class).to(TestingSession.class);
        }

        @Provides
        @Singleton
        TestingSession provideSession(SessionWithInitializedTablesFactory factory) {
            return new TestingSession(factory.get());
        }
    }

    @RegisterExtension
    static JamesServerExtension testExtension = new JamesServerBuilder()
        .extension(new DockerElasticSearchExtension())
        .extension(new CassandraExtension())
        .extension(new AwsS3BlobStoreExtension())
        .extension(new RabbitMQExtension())
        .server(configuration -> GuiceJamesServer.forConfiguration(configuration)
            .combineWith(CassandraRabbitMQJamesServerMain.MODULES)
            .overrideWith(new WebadminIntegrationTestModule())
            // Enforce a single eventBus retry. Required as Current Quotas are handled by the eventBus.
            .overrideWith(binder -> binder.bind(RetryBackoffConfiguration.class)
                .toInstance(RetryBackoffConfiguration.builder()
                    .maxRetries(1)
                    .firstBackoff(Duration.ofMillis(2))
                    .jitterFactor(0.5)
                    .build()))
            .overrideWith(new TestingSessionModule()))
        .build();

    private static final String VERSION = "/cassandra/version";
    private static final String UPGRADE_VERSION = VERSION + "/upgrade";
    private static final String UPGRADE_TO_LATEST_VERSION = UPGRADE_VERSION + "/latest";
    private static final String DOMAIN = "domain";
    private static final String USERNAME = "username@" + DOMAIN;
    private static final String ALIAS_1 = "alias1@" + DOMAIN;
    private static final String ALIAS_2 = "alias2@" + DOMAIN;
    private static final boolean IS_RECENT = true;

    private DataProbe dataProbe;

    @BeforeEach
    void setUp(GuiceJamesServer guiceJamesServer) throws Exception {
        dataProbe = guiceJamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DOMAIN);
        WebAdminGuiceProbe webAdminGuiceProbe = guiceJamesServer.getProbe(WebAdminGuiceProbe.class);

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminGuiceProbe.getWebAdminPort())
            .build();
    }

    @Test
    void shouldSolveCassandraMappingInconsistency(GuiceJamesServer server) {
        server.getProbe(TestingSessionProbe.class)
            .getTestingSession().registerScenario(fail()
            .times(1)
            .whenQueryStartsWith("INSERT INTO mappings_sources"));

        with()
            .put(AliasRoutes.ROOT_PATH + SEPARATOR + USERNAME + "/sources/" + ALIAS_1);
        with()
            .put(AliasRoutes.ROOT_PATH + SEPARATOR + USERNAME + "/sources/" + ALIAS_2);

        String taskId = with()
            .queryParam("action", "SolveInconsistencies")
            .post(CassandraMappingsRoutes.ROOT_PATH)
            .jsonPath()
            .get("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        when()
            .get(AliasRoutes.ROOT_PATH + SEPARATOR + USERNAME)
        .then()
            .contentType(ContentType.JSON)
        .statusCode(HttpStatus.OK_200)
            .body("source", hasItems(ALIAS_1, ALIAS_2));
    }

    @Test
    void shouldSolveMailboxesInconsistency(GuiceJamesServer server) {
        MailboxProbeImpl probe = server.getProbe(MailboxProbeImpl.class);

        server.getProbe(TestingSessionProbe.class)
            .getTestingSession().registerScenario(fail()
            .times(6) // Insertion in the DAO is retried 5 times once it failed
            .whenQueryStartsWith("INSERT INTO mailbox (id,name,uidvalidity,mailboxbase)"));

        try {
            probe.createMailbox(MailboxPath.inbox(BOB));
        } catch (Exception e) {
            // Failure is expected
        }

        // schema version 6 or higher required to run solve mailbox inconsistencies task
        String taskId = with().post(UPGRADE_TO_LATEST_VERSION)
            .jsonPath()
            .get("taskId");

        with()
            .get("/tasks/" + taskId + "/await")
        .then()
            .body("status", is("completed"));

        taskId = with()
            .header("I-KNOW-WHAT-I-M-DOING", "ALL-SERVICES-ARE-OFFLINE")
            .queryParam("task", "SolveInconsistencies")
            .post("/mailboxes")
            .jsonPath()
            .get("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        // The mailbox is removed as it is not in the mailboxDAO source of truth.
        assertThat(probe.listUserMailboxes(BOB.asString()))
            .isEmpty();
    }

    @Test
    void shouldRecomputeMailboxCounters(GuiceJamesServer server) throws MailboxException {
        MailboxProbeImpl probe = server.getProbe(MailboxProbeImpl.class);
        MailboxPath inbox = MailboxPath.inbox(BOB);
        probe.createMailbox(inbox);

        server.getProbe(TestingSessionProbe.class)
            .getTestingSession().registerScenario(fail()
            .times(1)
            .whenQueryStartsWith("INSERT INTO messageCounter (nextUid,mailboxId)"));

        try {
            probe.appendMessage(BOB.asString(), inbox,
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags(Flags.Flag.SEEN));
        } catch (Exception e) {
            // Expected to fail
        }

        String taskId = with()
            .basePath("/mailboxes")
            .queryParam("task", "RecomputeMailboxCounters")
            .post()
            .jsonPath()
            .get("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        assertThat(probe.retrieveCounters(inbox).getCount()).isEqualTo(1);
    }

    @Test
    void shouldRecomputeQuotas(GuiceJamesServer server) throws Exception {
        dataProbe.fluent()
            .addDomain(BOB.getDomainPart().get().asString())
            .addUser(BOB.asString(), BOB_PASSWORD);
        MailboxProbeImpl probe = server.getProbe(MailboxProbeImpl.class);
        MailboxPath inbox = MailboxPath.inbox(BOB);
        probe.createMailbox(inbox);

        Barrier barrier1 = new Barrier();
        Barrier barrier2 = new Barrier();
        String updatedQuotaQueryString = "UPDATE currentQuota SET messageCount=messageCount+?,storage=storage+? WHERE quotaRoot=?;";
        server.getProbe(TestingSessionProbe.class)
            .getTestingSession().registerScenario(
                awaitOn(barrier1) // Event bus first execution
                    .thenFail()
                    .times(1)
                    .whenQueryStartsWith(updatedQuotaQueryString),
                awaitOn(barrier2) // scenari for event bus retry
                    .thenFail()
                    .times(1)
                    .whenQueryStartsWith(updatedQuotaQueryString));

        probe.appendMessage(BOB.asString(), inbox,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(),
            !IS_RECENT, new Flags(Flags.Flag.SEEN));

        // Await first execution
        barrier1.awaitCaller();
        barrier1.releaseCaller();
        // Await event bus retry
        barrier2.awaitCaller();
        barrier2.releaseCaller();

        String taskId = with()
            .basePath("/quota/users")
            .queryParam("task", "RecomputeCurrentQuotas")
            .post()
            .jsonPath()
            .get("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        QuotaProbesImpl quotaProbe = server.getProbe(QuotaProbesImpl.class);
        assertThat(
            quotaProbe.getMessageCountQuota(QuotaRoot.quotaRoot("#private&" + BOB.asString(), Optional.empty()))
                .getUsed()
                .asLong())
            .isEqualTo(1);
    }

    @Test
    void solveCassandraMappingInconsistencyShouldSolveNotingWhenNoInconsistencies() {
        with()
            .put(AliasRoutes.ROOT_PATH + SEPARATOR + USERNAME + "/sources/" + ALIAS_1);
        with()
            .put(AliasRoutes.ROOT_PATH + SEPARATOR + USERNAME + "/sources/" + ALIAS_2);

        String taskId = with()
            .queryParam("action", "SolveInconsistencies")
            .post(CassandraMappingsRoutes.ROOT_PATH)
            .jsonPath()
            .get("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        when()
            .get(AliasRoutes.ROOT_PATH + SEPARATOR + USERNAME)
        .then()
            .contentType(ContentType.JSON)
        .statusCode(HttpStatus.OK_200)
            .body("source", hasItems(ALIAS_1, ALIAS_2));
    }

    @Test
    void solveMailboxesInconsistencyShouldSolveNotingWhenNoInconsistencies(GuiceJamesServer server) {
        MailboxProbeImpl probe = server.getProbe(MailboxProbeImpl.class);

        try {
            probe.createMailbox(MailboxPath.inbox(BOB));
        } catch (Exception e) {
            // Failure is expected
        }

        // schema version 6 or higher required to run solve mailbox inconsistencies task
        String taskId = with().post(UPGRADE_TO_LATEST_VERSION)
            .jsonPath()
            .get("taskId");

        with()
            .get("/tasks/" + taskId + "/await")
        .then()
            .body("status", is("completed"));

        taskId = with()
            .header("I-KNOW-WHAT-I-M-DOING", "ALL-SERVICES-ARE-OFFLINE")
            .queryParam("task", "SolveInconsistencies")
            .post("/mailboxes")
            .jsonPath()
            .get("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        // The mailbox is removed as it is not in the mailboxDAO source of truth.
        assertThat(probe.listUserMailboxes(BOB.asString()))
            .containsOnly(MailboxConstants.INBOX);
    }

    @Test
    void recomputeMailboxCountersShouldSolveNotingWhenNoInconsistencies(GuiceJamesServer server) throws MailboxException {
        MailboxProbeImpl probe = server.getProbe(MailboxProbeImpl.class);
        MailboxPath inbox = MailboxPath.inbox(BOB);
        probe.createMailbox(inbox);

        try {
            probe.appendMessage(BOB.asString(), inbox,
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags(Flags.Flag.SEEN));
        } catch (Exception e) {
            // Expected to fail
        }

        String taskId = with()
            .basePath("/mailboxes")
            .queryParam("task", "RecomputeMailboxCounters")
            .post()
            .jsonPath()
            .get("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        assertThat(probe.retrieveCounters(inbox).getCount()).isEqualTo(1);
    }

    @Test
    void recomputeQuotasShouldSolveNotingWhenNoInconsistencies(GuiceJamesServer server) throws Exception {
        dataProbe.fluent()
            .addDomain(BOB.getDomainPart().get().asString())
            .addUser(BOB.asString(), BOB_PASSWORD);
        MailboxProbeImpl probe = server.getProbe(MailboxProbeImpl.class);
        MailboxPath inbox = MailboxPath.inbox(BOB);
        probe.createMailbox(inbox);

        Barrier barrier = new Barrier();
        String updatedQuotaQueryString = "UPDATE currentQuota SET messageCount=messageCount+?,storage=storage+? WHERE quotaRoot=?;";
        server.getProbe(TestingSessionProbe.class)
            .getTestingSession().registerScenario(
                awaitOn(barrier) // Event bus first execution
                    .thenExecuteNormally()
                    .times(1)
                    .whenQueryStartsWith(updatedQuotaQueryString));

        probe.appendMessage(BOB.asString(), inbox,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(),
            !IS_RECENT, new Flags(Flags.Flag.SEEN));

        // Await first execution
        barrier.awaitCaller();
        barrier.releaseCaller();

        String taskId = with()
            .basePath("/quota/users")
            .queryParam("task", "RecomputeCurrentQuotas")
            .post()
            .jsonPath()
            .get("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        QuotaProbesImpl quotaProbe = server.getProbe(QuotaProbesImpl.class);
        assertThat(
            quotaProbe.getMessageCountQuota(QuotaRoot.quotaRoot("#private&" + BOB.asString(), Optional.empty()))
                .getUsed()
                .asLong())
            .isEqualTo(1);
    }
}