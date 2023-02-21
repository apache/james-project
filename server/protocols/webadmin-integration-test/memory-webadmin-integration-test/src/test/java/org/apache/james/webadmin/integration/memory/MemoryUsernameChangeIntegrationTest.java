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

package org.apache.james.webadmin.integration.memory;

import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;
import static org.apache.james.jmap.JMAPTestingConstants.ALICE;
import static org.apache.james.jmap.JMAPTestingConstants.ALICE_PASSWORD;
import static org.apache.james.jmap.JMAPTestingConstants.BOB;
import static org.apache.james.jmap.JMAPTestingConstants.BOB_PASSWORD;
import static org.apache.james.jmap.JMAPTestingConstants.CEDRIC;
import static org.apache.james.jmap.JMAPTestingConstants.CEDRIC_PASSWORD;
import static org.apache.james.jmap.JMAPTestingConstants.DOMAIN;
import static org.apache.james.jmap.JMAPTestingConstants.jmapRequestSpecBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasSize;

import java.util.List;
import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.MemoryJamesConfiguration;
import org.apache.james.MemoryJamesServerMain;
import org.apache.james.core.Username;
import org.apache.james.jmap.api.filtering.FilteringManagement;
import org.apache.james.jmap.api.filtering.Rule;
import org.apache.james.jmap.api.filtering.Rules;
import org.apache.james.jmap.api.filtering.Version;
import org.apache.james.jmap.draft.JmapGuiceProbe;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.probe.DataProbe;
import org.apache.james.util.Port;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.GuiceProbe;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.inject.multibindings.Multibinder;

import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;
import reactor.core.publisher.Mono;

class MemoryUsernameChangeIntegrationTest {
    public static final class FilterProbe implements GuiceProbe {
        private final FilteringManagement filteringManagement;

        @Inject
        public FilterProbe(FilteringManagement filteringManagement) {
            this.filteringManagement = filteringManagement;
        }

        public void defineRulesForUser(Username username, List<Rule> rules, Optional<Version> ifInState) {
            Mono.from(filteringManagement.defineRulesForUser(username, rules, ifInState))
                .block();
        }

        public Rules listRulesForUser(Username username) {
            return Mono.from(filteringManagement.listRulesForUser(username))
                .block();
        }
    }

    private static final String NAME = "a name";
    private static final Rule.Condition CONDITION = Rule.Condition.of(Rule.Condition.Field.CC, Rule.Condition.Comparator.CONTAINS, "something");
    private static final Rule.Action ACTION = Rule.Action.of(Rule.Action.AppendInMailboxes.withMailboxIds("id-01"));
    private static final Rule.Builder RULE_BUILDER = Rule.builder().name(NAME).condition(CONDITION).action(ACTION);
    private static final Rule RULE_1 = RULE_BUILDER.id(Rule.Id.of("1")).build();
    private static final Rule RULE_2 = RULE_BUILDER.id(Rule.Id.of("2")).build();
    private static final Optional<Version> NO_VERSION = Optional.empty();

    @RegisterExtension
    static JamesServerExtension jamesServerExtension = new JamesServerBuilder<MemoryJamesConfiguration>(tmpDir ->
        MemoryJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .usersRepository(DEFAULT)
            .build())
        .server(configuration -> MemoryJamesServerMain.createServer(configuration)
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class)
                .addBinding().to(FilterProbe.class))
            .overrideWith(new TestJMAPServerModule()))
        .build();

    private RequestSpecification webAdminApi;

    @BeforeEach
    void setUp(GuiceJamesServer jmapServer) throws Exception {
        DataProbe dataProbe = jmapServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DOMAIN);
        dataProbe.addUser(BOB.asString(), BOB_PASSWORD);
        dataProbe.addUser(ALICE.asString(), ALICE_PASSWORD);
        dataProbe.addUser(CEDRIC.asString(), CEDRIC_PASSWORD);

        Port jmapPort = jmapServer.getProbe(JmapGuiceProbe.class).getJmapPort();
        RestAssured.requestSpecification = jmapRequestSpecBuilder
            .setPort(jmapPort.getValue())
            .build();

        webAdminApi = WebAdminUtils.spec(jmapServer.getProbe(WebAdminGuiceProbe.class).getWebAdminPort());
    }

    @Test
    void shouldMigrateMailboxes() {
        webAdminApi.put("/users/" + ALICE.asString() + "/mailboxes/test").prettyPeek();

        String taskId = webAdminApi
            .queryParam("action", "rename")
            .post("/users/" + ALICE.asString() + "/rename/" + BOB.asString()).prettyPeek()
            .jsonPath()
            .get("taskId");

        webAdminApi.get("/tasks/" + taskId + "/await");

        webAdminApi.get("/users/" + ALICE.asString() + "/mailboxes").prettyPeek()
            .then()
            .body(".", hasSize(0));

        webAdminApi.get("/users/" + BOB.asString() + "/mailboxes").prettyPeek()
            .then()
            .body(".", hasSize(1))
            .body("[0].mailboxName", is("test"));
    }

    @Test
    void shouldAdaptForwards() {
        String taskId = webAdminApi
            .queryParam("action", "rename")
            .post("/users/" + ALICE.asString() + "/rename/" + BOB.asString())
            .jsonPath()
            .get("taskId");

        webAdminApi.get("/tasks/" + taskId + "/await");

        webAdminApi.get("/address/forwards/" + BOB.asString())
            .then()
            .statusCode(404);

        webAdminApi.get("/address/forwards/" + ALICE.asString())
            .then()
            .body(".", hasSize(1))
            .body("[0].mailAddress", is(BOB.asString()));
    }

    @Test
    void shouldAdaptDelegation() {
        webAdminApi.put("/users/" + ALICE.asString() + "/authorizedUsers/" + CEDRIC.asString());

        String taskId = webAdminApi
            .queryParam("action", "rename")
            .post("/users/" + ALICE.asString() + "/rename/" + BOB.asString())
            .jsonPath()
            .get("taskId");

        webAdminApi.get("/tasks/" + taskId + "/await");

        webAdminApi.get("/users/" + ALICE.asString() + "/authorizedUsers")
            .then()
                .body(".", hasSize(0));
        webAdminApi.get("/users/" + BOB.asString() + "/authorizedUsers")
            .then()
                .body(".", hasSize(1))
                .body("[0]", is(CEDRIC.asString()));
    }

    @Test
    void shouldAdaptFilters(GuiceJamesServer server) {
        FilterProbe filterProbe = server.getProbe(FilterProbe.class);
        filterProbe.defineRulesForUser(ALICE, List.of(RULE_1), NO_VERSION);

        String taskId = webAdminApi
            .queryParam("action", "rename")
            .post("/users/" + ALICE.asString() + "/rename/" + BOB.asString())
            .jsonPath()
            .get("taskId");

        webAdminApi.get("/tasks/" + taskId + "/await");

        assertThat(filterProbe.listRulesForUser(BOB).getRules())
            .containsOnly(RULE_1);
        assertThat(filterProbe.listRulesForUser(ALICE).getRules())
            .isEmpty();
    }
}
