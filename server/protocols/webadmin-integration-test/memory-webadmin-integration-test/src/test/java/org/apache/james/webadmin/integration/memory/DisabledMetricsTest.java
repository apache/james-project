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

import static io.restassured.RestAssured.when;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.MemoryJamesServerMain;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.RandomPortSupplier;
import org.apache.james.webadmin.WebAdminConfiguration;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.integration.UnauthorizedModule;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.restassured.RestAssured;

class DisabledMetricsTest {
    @RegisterExtension
    static JamesServerExtension jamesServerExtension = new JamesServerBuilder<>(JamesServerBuilder.defaultConfigurationProvider())
        .server(configuration -> MemoryJamesServerMain.createServer(configuration)
            .overrideWith(new UnauthorizedModule())
            .overrideWith(binder -> binder.bind(WebAdminConfiguration.class)
                .toInstance(WebAdminConfiguration.builder()
                    .enabled()
                    .host("127.0.0.1")
                    .port(new RandomPortSupplier())
                    .build())))
        .lifeCycle(JamesServerExtension.Lifecycle.PER_CLASS)
        .build();


    @BeforeEach
    void setUp(GuiceJamesServer guiceJamesServer) {
        WebAdminGuiceProbe webAdminGuiceProbe = guiceJamesServer.getProbe(WebAdminGuiceProbe.class);

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminGuiceProbe.getWebAdminPort())
            .build();
    }

    @Test
    void getMetricsShouldReturnNotFoundWhenAdditionalRouteNotConfigured() {
        when()
            .get("/metrics")
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }
}