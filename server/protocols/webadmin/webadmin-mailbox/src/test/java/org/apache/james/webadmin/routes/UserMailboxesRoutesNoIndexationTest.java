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

import static io.restassured.RestAssured.when;
import static org.apache.james.webadmin.Constants.SEPARATOR;
import static org.apache.james.webadmin.routes.UserMailboxesRoutes.USERS_BASE;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.james.core.Username;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.task.Hostname;
import org.apache.james.task.MemoryTaskManager;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.service.UserMailboxesService;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableSet;

import io.restassured.RestAssured;

class UserMailboxesRoutesNoIndexationTest {
    private static final Username USERNAME = Username.of("username");

    private WebAdminServer webAdminServer;
    private MemoryTaskManager taskManager;


    @BeforeEach
    void setUp() throws Exception {
        InMemoryMailboxManager mailboxManager = InMemoryIntegrationResources.defaultResources().getMailboxManager();
        UsersRepository usersRepository = mock(UsersRepository.class);
        when(usersRepository.contains(USERNAME)).thenReturn(true);

        taskManager = new MemoryTaskManager(new Hostname("foo"));

        webAdminServer = WebAdminUtils.createWebAdminServer(
            new UserMailboxesRoutes(new UserMailboxesService(mailboxManager, usersRepository), new JsonTransformer(),
                taskManager,
                ImmutableSet.of()),
            new TasksRoutes(taskManager, new JsonTransformer()))
            .start();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
            .setBasePath(USERS_BASE + SEPARATOR + USERNAME.asString() + SEPARATOR + UserMailboxesRoutes.MAILBOXES)
            .build();
    }

    @AfterEach
    void tearDown() {
        webAdminServer.destroy();
        taskManager.stop();
    }

    @Test
    void postShouldNotBeExposedWhenNoTasks() {
        when()
            .post()
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404)
            .body("statusCode", Matchers.is(404))
            .body("type", Matchers.is(ErrorResponder.ErrorType.NOT_FOUND.getType()))
            .body("message", Matchers.is("POST /users/username/mailboxes can not be found"));
    }
}
