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
import static org.apache.james.jmap.JMAPTestingConstants.DOMAIN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;

import java.net.URL;
import java.util.List;
import java.util.Optional;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.MemoryJamesConfiguration;
import org.apache.james.MemoryJamesServerMain;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.jmap.api.filtering.FilteringManagement;
import org.apache.james.jmap.api.filtering.Rule;
import org.apache.james.jmap.api.filtering.Rules;
import org.apache.james.jmap.api.identity.CustomIdentityDAO;
import org.apache.james.jmap.api.identity.IdentityCreationRequest;
import org.apache.james.jmap.api.model.AccountId;
import org.apache.james.jmap.api.model.EmailAddress;
import org.apache.james.jmap.api.model.Identity;
import org.apache.james.jmap.api.model.PushSubscription;
import org.apache.james.jmap.api.model.PushSubscriptionCreationRequest;
import org.apache.james.jmap.api.model.PushSubscriptionServerURL;
import org.apache.james.jmap.api.pushsubscription.PushSubscriptionRepository;
import org.apache.james.jmap.draft.JmapGuiceProbe;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.modules.ACLProbeImpl;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.probe.DataProbe;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.GuiceProbe;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.vacation.api.Vacation;
import org.apache.james.vacation.api.VacationPatch;
import org.apache.james.webadmin.WebAdminUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.shaded.com.google.common.collect.ImmutableList;

import com.google.inject.Inject;
import com.google.inject.multibindings.Multibinder;

import io.restassured.specification.RequestSpecification;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import scala.None;
import scala.Option;
import scala.collection.immutable.Seq;

class MemoryUserDeletionIntegrationTest {
    public static class MemoryUserDeletionIntegrationTestProbe implements GuiceProbe {
        private final FilteringManagement filteringManagement;
        private final CustomIdentityDAO identityRepository;
        private final PushSubscriptionRepository pushSubscriptionRepository;

        @Inject
        public MemoryUserDeletionIntegrationTestProbe(FilteringManagement filteringManagement, CustomIdentityDAO identityRepository, PushSubscriptionRepository pushSubscriptionRepository) {
            this.filteringManagement = filteringManagement;
            this.identityRepository = identityRepository;
            this.pushSubscriptionRepository = pushSubscriptionRepository;
        }

        Rules listFilters(Username username) {
            return Mono.from(filteringManagement.listRulesForUser(username))
                .block();
        }

        void defineFilters(Username username, List<Rule> ruleList) {
            Mono.from(filteringManagement.defineRulesForUser(username, ruleList, Optional.empty())).block();
        }

        List<Identity> listIdentities(Username username) {
            return Flux.from(identityRepository.list(username))
                .collectList()
                .block();
        }

        void addIdentity(Username username, IdentityCreationRequest identity) {
            Mono.from(identityRepository.save(username, identity)).block();
        }

        List<PushSubscription> listPushSubscriptions(Username username) {
            return Flux.from(pushSubscriptionRepository.list(username))
                .collectList()
                .block();
        }

        void addPushSubscriptions(Username username, PushSubscriptionCreationRequest pushSubscription) {
            Mono.from(pushSubscriptionRepository.save(username, pushSubscription)).block();
        }
    }

    @RegisterExtension
    static JamesServerExtension jamesServerExtension = new JamesServerBuilder<MemoryJamesConfiguration>(tmpDir ->
        MemoryJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .usersRepository(DEFAULT)
            .build())
        .server(configuration -> MemoryJamesServerMain.createServer(configuration)
            .overrideWith(new TestJMAPServerModule(),
                binder -> Multibinder.newSetBinder(binder, GuiceProbe.class)
                    .addBinding()
                    .to(MemoryUserDeletionIntegrationTestProbe.class)))
        .build();

    private RequestSpecification webAdminApi;

    @BeforeEach
    void setUp(GuiceJamesServer jmapServer) throws Exception {
        DataProbe dataProbe = jmapServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DOMAIN);
        dataProbe.addUser(ALICE.asString(), ALICE_PASSWORD);
        dataProbe.addUser(BOB.asString(), BOB_PASSWORD);

        webAdminApi = WebAdminUtils.spec(jmapServer.getProbe(WebAdminGuiceProbe.class).getWebAdminPort());
    }

    @Test
    void shouldDeleteMailboxes() {
        webAdminApi.put("/users/" + ALICE.asString() + "/mailboxes/test");

        String taskId = webAdminApi
            .queryParam("action", "deleteData")
            .post("/users/" + ALICE.asString())
            .jsonPath()
            .get("taskId");

        webAdminApi.get("/tasks/" + taskId + "/await");

        webAdminApi.get("/users/" + ALICE.asString() + "/mailboxes")
            .then()
            .body(".", hasSize(0));
    }

    @Test
    void shouldDeleteACLs(GuiceJamesServer server) throws Exception {
        server.getProbe(MailboxProbeImpl.class).createMailbox(MailboxPath.inbox(BOB));
        server.getProbe(ACLProbeImpl.class).addRights(MailboxPath.inbox(BOB), ALICE.asString(), MailboxACL.FULL_RIGHTS);

        String taskId = webAdminApi
            .queryParam("action", "deleteData")
            .post("/users/" + ALICE.asString())
            .jsonPath()
            .get("taskId");

        webAdminApi.get("/tasks/" + taskId + "/await");

        // Bob Inbox should no longer accept Alice access
        MailboxACL acls = server.getProbe(ACLProbeImpl.class).retrieveRights(MailboxPath.inbox(BOB));
        assertThat(acls.getEntries())
            .hasSize(1)
            .containsEntry(MailboxACL.EntryKey.deserialize("owner"), MailboxACL.FULL_RIGHTS);
    }

    @Test
    void shouldDeleteRRTs(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class)
            .addAddressMapping(ALICE.getLocalPart(), DOMAIN, BOB.asString());

        String taskId = webAdminApi
            .queryParam("action", "deleteData")
            .post("/users/" + ALICE.asString())
            .jsonPath()
            .get("taskId");

        webAdminApi.get("/tasks/" + taskId + "/await");

        assertThat(server.getProbe(DataProbeImpl.class).listMappings())
            .isEmpty();
    }

    @Test
    void shouldDeleteDelegation(GuiceJamesServer server) {
        server.getProbe(DataProbeImpl.class)
            .addAuthorizedUser(ALICE, BOB);

        String taskId = webAdminApi
            .queryParam("action", "deleteData")
            .post("/users/" + ALICE.asString())
            .jsonPath()
            .get("taskId");

        webAdminApi.get("/tasks/" + taskId + "/await");

        assertThat(server.getProbe(DataProbeImpl.class).listAuthorizedUsers(ALICE))
            .isEmpty();
    }

    @Test
    void shouldDeleteVacation(GuiceJamesServer server) {
        server.getProbe(JmapGuiceProbe.class)
            .modifyVacation(AccountId.fromUsername(ALICE), VacationPatch.builder()
                .textBody("text body")
                .build());

        String taskId = webAdminApi
            .queryParam("action", "deleteData")
            .post("/users/" + ALICE.asString())
            .jsonPath()
            .get("taskId");

        webAdminApi.get("/tasks/" + taskId + "/await");

        assertThat(server.getProbe(JmapGuiceProbe.class).retrieveVacation(AccountId.fromUsername(ALICE)))
            .isEqualTo(Vacation.builder()
                .enabled(false)
                .build());
    }

    @Test
    void shouldDeleteFilters(GuiceJamesServer server) {
        Rule.Condition CONDITION = Rule.Condition.of(Rule.Condition.Field.CC, Rule.Condition.Comparator.CONTAINS, "something");
        Rule.Action ACTION = Rule.Action.of(Rule.Action.AppendInMailboxes.withMailboxIds("id-01"));
        Rule.Builder RULE_BUILDER = Rule.builder().name("A name").conditionGroup(CONDITION).action(ACTION);
        Rule RULE_1 = RULE_BUILDER.id(Rule.Id.of("1")).build();
        server.getProbe(MemoryUserDeletionIntegrationTestProbe.class)
            .defineFilters(ALICE, ImmutableList.of(RULE_1));

        String taskId = webAdminApi
            .queryParam("action", "deleteData")
            .post("/users/" + ALICE.asString())
            .jsonPath()
            .get("taskId");

        webAdminApi.get("/tasks/" + taskId + "/await");

        assertThat(server.getProbe(MemoryUserDeletionIntegrationTestProbe.class).listFilters(ALICE).getRules())
            .isEmpty();
    }

    @Test
    void shouldDeleteIdentities(GuiceJamesServer server) throws Exception {
        server.getProbe(MemoryUserDeletionIntegrationTestProbe.class)
            .addIdentity(ALICE, IdentityCreationRequest.fromJava(
                ALICE.asMailAddress(),
                Optional.of("identity name 1"),
                Optional.of(List.of(EmailAddress.from(Optional.of("reply name 1"), new MailAddress("reply1@domain.org")))),
                Optional.of(List.of(EmailAddress.from(Optional.of("bcc name 1"), new MailAddress("bcc1@domain.org")))),
                Optional.of(1),
                Optional.of("textSignature 1"),
                Optional.of("htmlSignature 1")));

        String taskId = webAdminApi
            .queryParam("action", "deleteData")
            .post("/users/" + ALICE.asString())
            .jsonPath()
            .get("taskId");

        webAdminApi.get("/tasks/" + taskId + "/await");

        assertThat(server.getProbe(MemoryUserDeletionIntegrationTestProbe.class).listIdentities(ALICE))
            .isEmpty();
    }

    @Test
    void shouldDeletePushSubscriptions(GuiceJamesServer server) throws Exception {
        server.getProbe(MemoryUserDeletionIntegrationTestProbe.class)
            .addPushSubscriptions(ALICE, new PushSubscriptionCreationRequest(
                "device",
                new PushSubscriptionServerURL(new URL("http://whatever/toto")),
                Option.empty(),
                Option.empty(),
                PushSubscriptionCreationRequest.noTypes()));

        String taskId = webAdminApi
            .queryParam("action", "deleteData")
            .post("/users/" + ALICE.asString())
            .jsonPath()
            .get("taskId");

        webAdminApi.get("/tasks/" + taskId + "/await");

        assertThat(server.getProbe(MemoryUserDeletionIntegrationTestProbe.class).listPushSubscriptions(ALICE))
            .isEmpty();
    }
}
