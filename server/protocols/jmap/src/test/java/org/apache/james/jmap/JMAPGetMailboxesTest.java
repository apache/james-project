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

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.config.EncoderConfig.encoderConfig;
import static com.jayway.restassured.config.RestAssuredConfig.newConfig;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.apache.james.http.jetty.Configuration;
import org.apache.james.http.jetty.JettyHttpServer;
import org.apache.james.jmap.api.AccessTokenManager;
import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.jmap.methods.GetMailboxesMethod;
import org.apache.james.jmap.methods.JmapRequestParser;
import org.apache.james.jmap.methods.JmapRequestParserImpl;
import org.apache.james.jmap.methods.JmapResponseWriter;
import org.apache.james.jmap.methods.JmapResponseWriterImpl;
import org.apache.james.jmap.methods.RequestHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableSet;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.http.ContentType;

public class JMAPGetMailboxesTest {

    private RequestHandler requestHandler;
    private JettyHttpServer server;
    private UUID accessToken;
    
    @Before
    public void setup() throws Exception {
        AccessTokenManager mockedAccessTokenManager = mock(AccessTokenManager.class);
        JmapRequestParser jmapRequestParser = new JmapRequestParserImpl();
        JmapResponseWriter jmapResponseWriter = new JmapResponseWriterImpl();

        requestHandler = new RequestHandler(ImmutableSet.of(new GetMailboxesMethod(jmapRequestParser, jmapResponseWriter)));
        JMAPServlet jmapServlet = new JMAPServlet(requestHandler);

        AuthenticationFilter authenticationFilter = new AuthenticationFilter(mockedAccessTokenManager);
        
        when(mockedAccessTokenManager.isValid(any(AccessToken.class))).thenReturn(true);
        
        accessToken = UUID.randomUUID();
        
        server = JettyHttpServer.create(
                Configuration.builder()
                .filter("/jmap")
                .with(authenticationFilter)
                .serve("/jmap")
                .with(jmapServlet)
                .randomPort()
                .build());
        
        server.start();

        RestAssured.port = server.getPort();
        RestAssured.config = newConfig().encoderConfig(encoderConfig().defaultContentCharset(Charsets.UTF_8));
    }

    @After
    public void teardown() throws Exception {
        server.stop();
    }
    
    @Test
    public void getMailboxesShouldErrorNotSupportedWhenRequestContainsNonNullAccountId() throws Exception {
        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken)
            .body("[[\"getMailboxes\", {\"accountId\": \"1\"}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .content(equalTo("[[\"error\",{\"type\":\"Not yet implemented\"},\"#0\"]]"));
    }

    
    @Test
    public void getMailboxesShouldErrorNotSupportedWhenRequestContainsNonNullIds() throws Exception {
        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken)
            .body("[[\"getMailboxes\", {\"ids\": []}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .content(equalTo("[[\"error\",{\"type\":\"Not yet implemented\"},\"#0\"]]"));
    }
    
    @Test
    public void getMailboxesShouldErrorNotSupportedWhenRequestContainsNonNullProperties() throws Exception {
        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken)
            .body("[[\"getMailboxes\", {\"properties\": []}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .content(equalTo("[[\"error\",{\"type\":\"Not yet implemented\"},\"#0\"]]"));
    }
    
    @Test
    public void getMailboxesShouldErrorInvalidArgumentsWhenRequestIsInvalid() throws Exception {
        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken)
            .body("[[\"getMailboxes\", {\"ids\": true}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .content(equalTo("[[\"error\",{\"type\":\"invalidArguments\"},\"#0\"]]"));
    }
}
