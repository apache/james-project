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
package org.apache.james.jmap.http;

import static io.restassured.RestAssured.given;
import static io.restassured.config.EncoderConfig.encoderConfig;
import static io.restassured.config.RestAssuredConfig.newConfig;
import static org.apache.james.jmap.JMAPUrls.JMAP;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;

import org.apache.james.core.Username;
import org.apache.james.jmap.JMAPRoute;
import org.apache.james.jmap.methods.Method;
import org.apache.james.jmap.draft.methods.RequestHandler;
import org.apache.james.jmap.model.InvocationResponse;
import org.apache.james.jmap.methods.ErrorResponse;
import org.apache.james.jmap.model.MethodCallId;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.netty.handler.codec.http.HttpMethod;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

public class JMAPApiRoutesTest {
    private static final int RANDOM_PORT = 0;

    private DisposableServer server;
    private RequestHandler requestHandler;
    private Authenticator mockedAuthFilter;
    private UserProvisioner mockedUserProvisionner;

    @Before
    public void setup() throws Exception {
        requestHandler = mock(RequestHandler.class);
        mockedAuthFilter = mock(Authenticator.class);
        mockedUserProvisionner = mock(UserProvisioner.class);

        JMAPApiRoutes jmapApiRoutes = new JMAPApiRoutes(requestHandler, new RecordingMetricFactory(),
            mockedAuthFilter, mockedUserProvisionner);

        JMAPRoute postApiRoute = jmapApiRoutes.routes()
            .filter(jmapRoute -> jmapRoute.getEndpoint().getMethod().equals(HttpMethod.POST))
            .findFirst()
            .get();

        server = HttpServer.create()
            .port(RANDOM_PORT)
            .route(routes -> routes.post(postApiRoute.getEndpoint().getPath(), (req, res) -> postApiRoute.getAction().handleRequest(req, res)))
            .bindNow();

        RestAssured.requestSpecification = new RequestSpecBuilder()
            .setContentType(ContentType.JSON)
            .setAccept(ContentType.JSON)
            .setConfig(newConfig().encoderConfig(encoderConfig().defaultContentCharset(StandardCharsets.UTF_8)))
            .setPort(server.port())
            .setBasePath(JMAP)
            .build();

        doReturn(Mono.just(MailboxSessionUtil.create(Username.of("bob"))))
            .when(mockedAuthFilter).authenticate(any());
        when(mockedUserProvisionner.provisionUser(any()))
            .thenReturn(Mono.empty());
    }

    @After
    public void teardown() {
        server.disposeNow();
    }

    @Test
    public void mustReturnBadRequestOnMalformedRequest() {
        String missingAnOpeningBracket = "[\"getAccounts\", {\"state\":false}, \"#0\"]]";

        given()
            .body(missingAnOpeningBracket)
        .when()
            .post()
        .then()
            .statusCode(400);
    }

    @Test
    public void mustReturnInvalidArgumentOnInvalidState() throws Exception {
        ObjectNode json = new ObjectNode(new JsonNodeFactory(false));
        json.put("type", "invalidArgument");

        when(requestHandler.handle(any()))
            .thenReturn(Flux.just(new InvocationResponse(ErrorResponse.ERROR_METHOD, json, MethodCallId.of("#0"))));

        given()
            .body("[[\"getAccounts\", {\"state\":false}, \"#0\"]]")
        .when()
            .post()
        .then()
            .statusCode(200)
            .body(equalTo("[[\"error\",{\"type\":\"invalidArgument\"},\"#0\"]]"));
    }

    @Test
    public void mustReturnAccountsOnValidRequest() throws Exception {
        ObjectNode json = new ObjectNode(new JsonNodeFactory(false));
        json.put("state", "f6a7e214");
        ArrayNode arrayNode = json.putArray("list");
        ObjectNode list = new ObjectNode(new JsonNodeFactory(false));
        list.put("id", "6asf5");
        list.put("name", "roger@barcamp");
        arrayNode.add(list);

        when(requestHandler.handle(any()))
            .thenReturn(Flux.just(new InvocationResponse(Method.Response.name("accounts"), json, MethodCallId.of("#0"))));

        given()
            .body("[[\"getAccounts\", {}, \"#0\"]]")
        .when()
            .post()
        .then()
            .statusCode(200)
            .body(equalTo("[[\"accounts\",{" +
                    "\"state\":\"f6a7e214\"," + 
                    "\"list\":[" + 
                        "{" + 
                        "\"id\":\"6asf5\"," + 
                        "\"name\":\"roger@barcamp\"" + 
                        "}" + 
                    "]" + 
                    "},\"#0\"]]"));
    }
}
