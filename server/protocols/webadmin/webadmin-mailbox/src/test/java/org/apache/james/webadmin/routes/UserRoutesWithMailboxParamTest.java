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

package org.apache.james.webadmin.routes;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.apache.james.mailbox.DefaultMailboxes.DEFAULT_MAILBOXES;
import static org.apache.james.webadmin.condition.user.HasNoMailboxesCondition.HAS_NO_MAILBOXES_PARAM;
import static org.apache.james.webadmin.condition.user.HasNotAllSystemMailboxesCondition.HAS_NOT_ALL_SYSTEM_MAILBOXES_PARAM;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.domainlist.api.mock.SimpleDomainList;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.rrt.api.RecipientRewriteTableConfiguration;
import org.apache.james.rrt.lib.AliasReverseResolverImpl;
import org.apache.james.rrt.lib.CanSendFromImpl;
import org.apache.james.rrt.memory.MemoryRecipientRewriteTable;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.memory.MemoryDelegationStore;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.condition.user.HasNoMailboxesCondition;
import org.apache.james.webadmin.condition.user.HasNotAllSystemMailboxesCondition;
import org.apache.james.webadmin.service.UserMailboxesService;
import org.apache.james.webadmin.service.UserService;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;

public class UserRoutesWithMailboxParamTest {
    private static final Domain DOMAIN = Domain.of("domain");
    private static final Username ALICE  = Username.fromLocalPartWithDomain("alice", DOMAIN);
    private static final Username BOB  = Username.fromLocalPartWithDomain("bob", DOMAIN);

    WebAdminServer webAdminServer;
    MemoryUsersRepository usersRepository;
    MailboxManager mailboxManager;

    @BeforeEach
    public void beforeEach() throws DomainListException {
        SimpleDomainList domainList = new SimpleDomainList();
        domainList.addDomain(DOMAIN);
        usersRepository = MemoryUsersRepository.withVirtualHosting(domainList);
        InMemoryIntegrationResources memoryResources = InMemoryIntegrationResources.defaultResources();
        mailboxManager = memoryResources.getMailboxManager();
        UserMailboxesService userMailboxService = new UserMailboxesService(mailboxManager, usersRepository);
        MemoryRecipientRewriteTable recipientRewriteTable = new MemoryRecipientRewriteTable();
        recipientRewriteTable.setDomainList(domainList);
        recipientRewriteTable.setConfiguration(RecipientRewriteTableConfiguration.DEFAULT_ENABLED);
        webAdminServer = WebAdminUtils.createWebAdminServer(new UserRoutes(new UserService(usersRepository),
                new CanSendFromImpl(new AliasReverseResolverImpl(recipientRewriteTable)),
                new JsonTransformer(),
                new MemoryDelegationStore(),
                ImmutableMap.of(HAS_NO_MAILBOXES_PARAM, new HasNoMailboxesCondition(userMailboxService),
                    HAS_NOT_ALL_SYSTEM_MAILBOXES_PARAM, new HasNotAllSystemMailboxesCondition(userMailboxService))))
            .start();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
            .setBasePath(UserRoutes.USERS)
            .setUrlEncodingEnabled(false) // no further automatically encoding by Rest Assured client. rf: https://issues.apache.org/jira/projects/JAMES/issues/JAMES-3936
            .build();
    }

    @AfterEach
    public void afterEach() {
        webAdminServer.destroy();
    }

    @Test
    void getUsersShouldReturnUsers()
        throws UsersRepositoryException {
        usersRepository.addUser(ALICE, "");
        usersRepository.addUser(BOB, "");

        List<String> users =
            when()
                .get()
                .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .extract()
                .body()
                .jsonPath()
                .getList(".", Map.class)
                .stream()
                .flatMap(map -> map.values().stream())
                .toList();

        assertThat(users).containsExactlyInAnyOrder(ALICE.asString(), BOB.asString());
    }

    @Test
    void getUsersShouldReturnUsersWithNoMailboxWhenHasNoMailboxesParaIsSet()
        throws UsersRepositoryException, MailboxException {
        usersRepository.addUser(ALICE, "");
        usersRepository.addUser(BOB, "");
        mailboxManager.createMailbox(MailboxPath.forUser(ALICE, DefaultMailboxes.INBOX), mailboxManager.createSystemSession(ALICE));

        List<String> users =
            given()
                .queryParam("hasNoMailboxes")
                .when()
                .get()
                .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .extract()
                .body()
                .jsonPath()
                .getList(".", Map.class)
                .stream()
                .flatMap(map -> map.values().stream())
                .toList();

        assertThat(users).containsExactly(BOB.asString());
    }

    @Test
    void getUsersShouldReturnUsersNotHavingAllSystemMailboxesWhenHasNotAllSystemMailboxesParaIsSet()
        throws UsersRepositoryException, MailboxException {
        usersRepository.addUser(ALICE, "");
        usersRepository.addUser(BOB, "");
        DEFAULT_MAILBOXES.forEach(Throwing.consumer(mailbox -> mailboxManager.createMailbox(MailboxPath.forUser(ALICE, mailbox), mailboxManager.createSystemSession(ALICE))));
        ImmutableList.of(DefaultMailboxes.INBOX, DefaultMailboxes.SENT)
            .forEach(Throwing.consumer(mailbox -> mailboxManager.createMailbox(MailboxPath.forUser(BOB, mailbox), mailboxManager.createSystemSession(BOB))));

        List<String> users =
            given()
                .queryParam("hasNotAllSystemMailboxes")
                .when()
                .get()
                .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .extract()
                .body()
                .jsonPath()
                .getList(".", Map.class)
                .stream()
                .flatMap(map -> map.values().stream())
                .toList();

        assertThat(users).containsExactly(BOB.asString());
    }
}
