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

import static io.restassured.RestAssured.when;
import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.RandomPortSupplier;
import org.apache.james.webadmin.WebAdminConfiguration;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.routes.HealthCheckRoutes;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.restassured.RestAssured;

class HealthCheckExtensionTest {
    @RegisterExtension
    static JamesServerExtension jamesServerExtension = new JamesServerBuilder<MemoryJamesConfiguration>(tmpDir ->
        MemoryJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .usersRepository(DEFAULT)
            .build())
        .server(configuration -> MemoryJamesServerMain.createServer(configuration)
            .overrideWith(binder -> binder.bind(WebAdminConfiguration.class)
                .toInstance(WebAdminConfiguration.builder()
                    .enabled()
                    .port(new RandomPortSupplier())
                    .build()))
            .overrideWith(binder -> binder.bind(PeriodicalHealthChecksConfiguration.class)
                .toInstance(PeriodicalHealthChecksConfiguration.builder()
                    .period(Duration.ofSeconds(60))
                    .additionalHealthChecks(List.of(MyHealthCheck.class.getCanonicalName()))
                    .build())))
        .build();


    @Test
    void customHealthCheckShouldBeBinded(GuiceJamesServer jamesServer) {
        WebAdminGuiceProbe probe = jamesServer.getProbe(WebAdminGuiceProbe.class);
        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(probe.getWebAdminPort()).build();

        List<String> listComponentNames =
            when()
                .get(HealthCheckRoutes.HEALTHCHECK)
            .then()
                .statusCode(HttpStatus.OK_200)
                .extract()
                .body()
                .jsonPath()
                .getList("checks.componentName", String.class);

        assertThat(listComponentNames).contains(MyHealthCheck.COMPONENT_NAME.getName());
    }
}
