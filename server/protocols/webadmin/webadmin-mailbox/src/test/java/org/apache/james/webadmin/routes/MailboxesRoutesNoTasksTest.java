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

import org.apache.james.json.DTOConverter;
import org.apache.james.task.Hostname;
import org.apache.james.task.MemoryTaskManager;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.tasks.TaskFromRequestRegistry;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableSet;

import io.restassured.RestAssured;

class MailboxesRoutesNoTasksTest {
    private static final ImmutableSet<TaskFromRequestRegistry.TaskRegistration> NO_ADDITIONAL_REGISTRATION = ImmutableSet.of();

    private WebAdminServer webAdminServer;
    private MemoryTaskManager taskManager;

    @BeforeEach
    void beforeEach() {
        taskManager = new MemoryTaskManager(new Hostname("foo"));
        JsonTransformer jsonTransformer = new JsonTransformer();

        webAdminServer = WebAdminUtils.createWebAdminServer(
                new TasksRoutes(taskManager, jsonTransformer, DTOConverter.of()),
                new MailboxesRoutes(taskManager,
                    jsonTransformer,
                    NO_ADDITIONAL_REGISTRATION,
                    NO_ADDITIONAL_REGISTRATION,
                    NO_ADDITIONAL_REGISTRATION))
            .start();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer).build();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @AfterEach
    void tearDown() {
        webAdminServer.destroy();
        taskManager.stop();
    }

    @Test
    void allMailboxesEndpointShouldNotBeExposedWhenNoTasks() {
        when()
            .post("/mailboxes")
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404)
            .body("statusCode", Matchers.is(404))
            .body("type", Matchers.is(ErrorResponder.ErrorType.NOT_FOUND.getType()))
            .body("message", Matchers.is("POST /mailboxes can not be found"));
    }

    @Test
    void oneMailboxEndpointShouldNotBeExposedWhenNoTasks() {
        when()
            .post("/mailboxes/36")
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404)
            .body("statusCode", Matchers.is(404))
            .body("type", Matchers.is(ErrorResponder.ErrorType.NOT_FOUND.getType()))
            .body("message", Matchers.is("POST /mailboxes/36 can not be found"));
    }

    @Test
    void oneMailEndpointShouldNotBeExposedWhenNoTasks() {
        when()
            .post("/mailboxes/36/mails/7")
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404)
            .body("statusCode", Matchers.is(404))
            .body("type", Matchers.is(ErrorResponder.ErrorType.NOT_FOUND.getType()))
            .body("message", Matchers.is("POST /mailboxes/36/mails/7 can not be found"));
    }
}