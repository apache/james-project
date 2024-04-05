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

package org.apache.james.jmap.draft.methods.integration;

import static io.restassured.RestAssured.given;
import static org.apache.james.jmap.HttpJmapAuthentication.authenticateJamesUser;
import static org.apache.james.jmap.JMAPTestingConstants.ALICE;
import static org.apache.james.jmap.JMAPTestingConstants.ALICE_PASSWORD;
import static org.apache.james.jmap.JMAPTestingConstants.DOMAIN;
import static org.apache.james.jmap.JMAPTestingConstants.jmapRequestSpecBuilder;
import static org.apache.james.jmap.JmapURIBuilder.baseUri;

import java.io.IOException;

import org.apache.james.GuiceJamesServer;
import org.apache.james.jmap.AccessToken;
import org.apache.james.jmap.JmapGuiceProbe;
import org.apache.james.probe.DataProbe;
import org.apache.james.utils.DataProbeImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.restassured.RestAssured;

public abstract class CorsHeaderAPITest {
    protected abstract GuiceJamesServer createJmapServer() throws IOException;

    private AccessToken accessToken;
    private GuiceJamesServer jmapServer;
    
    @Before
    public void setup() throws Throwable {
        jmapServer = createJmapServer();
        jmapServer.start();

        RestAssured.requestSpecification = jmapRequestSpecBuilder
                .setPort(jmapServer.getProbe(JmapGuiceProbe.class).getJmapPort().getValue())
                .build();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();

        DataProbe dataProbe = jmapServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DOMAIN);
        dataProbe.addUser(ALICE.asString(), ALICE_PASSWORD);
        accessToken = authenticateJamesUser(baseUri(jmapServer), ALICE, ALICE_PASSWORD);
    }

    @After
    public void teardown() {
        jmapServer.stop();
    }
    
    @Test
    public void apiShouldPositionCorsHeaders() {
        given()
            .header("Authorization", accessToken.asString())
            .body("[[\"getMailboxes\", {\"accountId\": \"1\"}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .header("Access-Control-Allow-Origin", "*")
            .header("Access-Control-Allow-Methods", "GET, POST, DELETE, PUT")
            .header("Access-Control-Allow-Headers", "Content-Type, Authorization, Accept")
            .header("Access-Control-Max-Age", "86400");
    }
}
