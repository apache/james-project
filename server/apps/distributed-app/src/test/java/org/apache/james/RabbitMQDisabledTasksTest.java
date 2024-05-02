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

package org.apache.james;

import static io.restassured.config.ParamConfig.UpdateStrategy.REPLACE;
import static org.hamcrest.Matchers.is;

import org.apache.james.modules.AwsS3BlobStoreExtension;
import org.apache.james.modules.RabbitMQExtension;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.task.eventsourcing.distributed.RabbitMQWorkQueueConfiguration;
import org.apache.james.task.eventsourcing.distributed.RabbitMQWorkQueueConfiguration$;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminConfiguration;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.routes.TasksRoutes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.restassured.RestAssured;
import io.restassured.config.ParamConfig;
import io.restassured.parsing.Parser;
import io.restassured.specification.RequestSpecification;

class RabbitMQDisabledTasksTest {
    private RabbitMQExtension rabbitMQExtension = new RabbitMQExtension();
    private RequestSpecification webAdminApi;

    @RegisterExtension
    JamesServerExtension jamesServerExtension = CassandraRabbitMQJamesServerFixture
        .baseExtensionBuilder(rabbitMQExtension)
        .extension(new AwsS3BlobStoreExtension())
        .server(configuration -> CassandraRabbitMQJamesServerMain.createServer(configuration)
            .overrideWith(new TestJMAPServerModule())
            .overrideWith(binder -> binder.bind(RabbitMQWorkQueueConfiguration.class)
                .toInstance(RabbitMQWorkQueueConfiguration$.MODULE$.disabled()))
            .overrideWith(binder -> binder.bind(WebAdminConfiguration.class).toInstance(WebAdminConfiguration.TEST_CONFIGURATION)))
        .build();

    @BeforeEach
    void setUp(GuiceJamesServer server) {
        RestAssured.defaultParser = Parser.JSON;
        webAdminApi = WebAdminUtils.spec(server.getProbe(WebAdminGuiceProbe.class).getWebAdminPort())
            .config(WebAdminUtils.defaultConfig()
                .paramConfig(new ParamConfig(REPLACE, REPLACE, REPLACE)));
    }

    @Test
    void tasksShouldBeCreatedButNotConsumed() throws Exception {
        String taskId = webAdminApi
            .post("/mailboxes?task=reIndex")
            .jsonPath()
            .get("taskId");

        Thread.sleep(1000);

        webAdminApi.basePath(TasksRoutes.BASE)
            .when()
                .get(taskId)
            .then()
                .body("status", is("waiting"));
    }
}
