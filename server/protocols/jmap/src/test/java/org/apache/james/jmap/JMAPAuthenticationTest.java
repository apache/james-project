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

import org.apache.james.http.jetty.Configuration;
import org.apache.james.http.jetty.JettyHttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.http.ContentType;

public class JMAPAuthenticationTest {

    private JettyHttpServer server;

    @Before
    public void setup() throws Exception {
        server = JettyHttpServer.create(
                Configuration.builder()
                    .serve("/*")
                    .with(AuthenticationServlet.class)
                    .randomPort()
                    .build());

        server.start();
        RestAssured.port = server.getPort();
    }

    @Test
    public void shouldReturnMalformedRequestWhenXMLContentType() {
        given()
            .contentType(ContentType.XML)
        .when()
            .post("/authentication")
        .then()
            .statusCode(400);
    }

    @After
    public void teardown() throws Exception {
        server.stop();
    }

}
