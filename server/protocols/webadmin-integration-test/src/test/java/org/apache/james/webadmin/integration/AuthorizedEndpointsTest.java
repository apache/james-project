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

package org.apache.james.webadmin.integration;

import static io.restassured.RestAssured.when;
import static org.hamcrest.core.IsNot.not;

import org.apache.james.CassandraJmapTestRule;
import org.apache.james.DockerCassandraRule;
import org.apache.james.GuiceJamesServer;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.routes.HealthCheckRoutes;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import io.restassured.RestAssured;

public class AuthorizedEndpointsTest {

    @ClassRule
    public static DockerCassandraRule cassandra = new DockerCassandraRule();
    
    @Rule
    public CassandraJmapTestRule cassandraJmapTestRule = CassandraJmapTestRule.defaultTestRule();

    private GuiceJamesServer guiceJamesServer;

    @Before
    public void setUp() throws Exception {
        guiceJamesServer = cassandraJmapTestRule.jmapServer(cassandra.getModule(), new UnauthorizedModule())
                .overrideWith(new WebAdminConfigurationModule());
        guiceJamesServer.start();
        WebAdminGuiceProbe webAdminGuiceProbe = guiceJamesServer.getProbe(WebAdminGuiceProbe.class);

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminGuiceProbe.getWebAdminPort())
            .build();
    }

    @After
    public void tearDown() {
        guiceJamesServer.stop();
    }

    @Test
    public void getHealthchecksShouldNotNeedAuthentication() {
        when()
            .get(HealthCheckRoutes.HEALTHCHECK)
        .then()
            .statusCode(not(HttpStatus.UNAUTHORIZED_401));
    }
}