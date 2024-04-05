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

package org.apache.james.jmap;

import static io.restassured.RestAssured.given;
import static org.apache.james.jmap.HttpJmapAuthentication.authenticateJamesUser;
import static org.apache.james.jmap.JMAPTestingConstants.ARGUMENTS;
import static org.apache.james.jmap.JMAPTestingConstants.NAME;
import static org.apache.james.jmap.JMAPTestingConstants.jmapRequestSpecBuilder;
import static org.apache.james.jmap.JmapURIBuilder.baseUri;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.isA;
import static org.hamcrest.Matchers.notNullValue;

import java.util.List;
import java.util.Optional;

import org.apache.james.GuiceJamesServer;
import org.apache.james.core.Username;
import org.apache.james.jmap.JmapGuiceProbe;
import org.apache.james.utils.DataProbeImpl;
import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Test;

import io.restassured.RestAssured;

public abstract class ModularizeJmapDraftAuthenticationStrategyTest {
    public static String DOMAIN = "domain.tld";
    public static Username BOB = Username.of("bob" + "@" + DOMAIN);
    public static String BOB_PASSWORD = "123456";
    public static Optional<List<String>> ALLOW_AUTHENTICATION_STRATEGY = Optional.of(List.of(AllowAuthenticationStrategy.class.getCanonicalName()));
    public static Optional<List<String>> DENY_AUTHENTICATION_STRATEGY = Optional.of(List.of(DenyAuthenticationStrategy.class.getCanonicalName()));
    public static Optional<List<String>> DEFAULT_STRATEGIES = Optional.empty();

    private GuiceJamesServer jmapServer;
    private AccessToken bobAccessToken;

    protected abstract GuiceJamesServer createJmapServer(Optional<List<String>> authOverride) throws Exception;

    public void setupJamesServerWithCustomAuthenticationStrategy(Optional<List<String>> authOverride) throws Throwable {
        jmapServer = createJmapServer(authOverride);
        jmapServer.start();

        RestAssured.requestSpecification = jmapRequestSpecBuilder
            .setPort(jmapServer.getProbe(JmapGuiceProbe.class).getJmapPort().getValue())
            .build();

        jmapServer.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(DOMAIN)
            .addUser(BOB.asString(), BOB_PASSWORD);
        bobAccessToken = authenticateJamesUser(baseUri(jmapServer), BOB, BOB_PASSWORD);
    }

    @After
    public void teardown() {
        jmapServer.stop();
    }

    @Test
    public void getAuthenticationRouteWithAllowAuthenticationStrategyShouldSucceed() throws Throwable {
        setupJamesServerWithCustomAuthenticationStrategy(ALLOW_AUTHENTICATION_STRATEGY);

        given()
        .when()
            .get("/authentication")
        .then()
            .statusCode(200)
            .body("api", equalTo("/jmap"))
            .body("eventSource", both(isA(String.class)).and(notNullValue()))
            .body("upload", equalTo("/upload"))
            .body("download", equalTo("/download"));
    }

    @Test
    public void getFilterWithAllowAuthenticationStrategyShouldNotRequiredAnyAuthentication() throws Throwable {
        setupJamesServerWithCustomAuthenticationStrategy(ALLOW_AUTHENTICATION_STRATEGY);

        String body = "[[\"getFilter\", {}, \"#0\"]]";

        given()
            .body(body)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, CoreMatchers.equalTo("filter"))
            .body(ARGUMENTS + ".singleton", hasSize(0));
    }

    @Test
    public void getAuthenticationRouteWithDenyAuthenticationStrategyShouldReturnUnauthorizedCode() throws Throwable {
        setupJamesServerWithCustomAuthenticationStrategy(DENY_AUTHENTICATION_STRATEGY);

        given()
        .when()
            .get("/authentication")
        .then()
            .statusCode(401);
    }

    @Test
    public void getAuthenticationRouteWhenDefaultAuthenticationStrategiesWithNonAuthenticationShouldReturnUnauthorizedCode() throws Throwable {
        setupJamesServerWithCustomAuthenticationStrategy(DEFAULT_STRATEGIES);

        given()
        .when()
            .get("/authentication")
        .then()
            .statusCode(401);
    }

    @Test
    public void getAuthenticationRouteWhenDefaultAuthenticationStrategiesWithValidAccessTokenShouldSucceed() throws Throwable {
        setupJamesServerWithCustomAuthenticationStrategy(DEFAULT_STRATEGIES);

        given()
            .header("Authorization", bobAccessToken.asString())
        .when()
            .get("/authentication")
        .then()
            .statusCode(200)
            .body("api", equalTo("/jmap"))
            .body("eventSource", both(isA(String.class)).and(notNullValue()))
            .body("upload", equalTo("/upload"))
            .body("download", equalTo("/download"));
    }

    @Test
    public void getFilterWhenDenyAuthenticationStrategyWithNonAuthenticationShouldReturnUnauthorizedCode() throws Throwable {
        setupJamesServerWithCustomAuthenticationStrategy(DENY_AUTHENTICATION_STRATEGY);

        String body = "[[\"getFilter\", {}, \"#0\"]]";

        given()
            .body(body)
        .when()
            .post("/jmap")
        .then()
            .statusCode(401);
    }

    @Test
    public void getFilterWhenDenyAuthenticationStrategyWithValidAccessTokenShouldReturnUnauthorizedCode() throws Throwable {
        setupJamesServerWithCustomAuthenticationStrategy(DENY_AUTHENTICATION_STRATEGY);

        String body = "[[\"getFilter\", {}, \"#0\"]]";

        given()
            .header("Authorization", bobAccessToken.asString())
            .body(body)
        .when()
            .post("/jmap")
        .then()
            .statusCode(401);
    }

    @Test
    public void getFilterWhenDefaultAuthenticationStrategiesWithValidAccessTokenShouldSucceed() throws Throwable {
        setupJamesServerWithCustomAuthenticationStrategy(DEFAULT_STRATEGIES);

        String body = "[[\"getFilter\", {}, \"#0\"]]";

        given()
            .header("Authorization", bobAccessToken.asString())
            .body(body)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, CoreMatchers.equalTo("filter"))
            .body(ARGUMENTS + ".singleton", hasSize(0));
    }

    @Test
    public void getFilterWhenDefaultAuthenticationStrategiesWithNonAuthenticationShouldFail() throws Throwable {
        setupJamesServerWithCustomAuthenticationStrategy(DEFAULT_STRATEGIES);

        String body = "[[\"getFilter\", {}, \"#0\"]]";

        given()
            .body(body)
        .when()
            .post("/jmap")
        .then()
            .statusCode(401);
    }

}
