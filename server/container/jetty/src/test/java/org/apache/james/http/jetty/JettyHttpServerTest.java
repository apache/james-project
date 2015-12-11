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

package org.apache.james.http.jetty;

import static com.jayway.restassured.RestAssured.when;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.jayway.restassured.RestAssured;

public class JettyHttpServerTest {

    @FunctionalInterface
    private interface ServletMethod {
        void handle(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException;
    }
    
    private static HttpServlet get(ServletMethod method) {
        return new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest req,
                    HttpServletResponse resp) throws ServletException, IOException {
                method.handle(req, resp);
            }
        };
    }
    
    private JettyHttpServer testee;
    
    @Before
    public void setup() {
    }
    
    @After
    public void teardown() throws Exception {
        testee.stop();
    }
    
    @Test
    public void shouldReturn404WhenNoServletConfigured() throws Exception {
        testee = JettyHttpServer.start(Configuration.empty());
        RestAssured.port = testee.getPort();
        when()
            .get("/")
        .then()
            .assertThat()
                .statusCode(404);
    }
    
    @Test
    public void shouldLetConfiguredServletHandleIncomingRequestWhenServletConfigured() throws Exception {
        ServletMethod getHandler = (req, resp) -> resp.getWriter().append("served").close();
        
        testee = JettyHttpServer.start(Configuration
                                        .builder()
                                        .serve("/")
                                        .with(get(getHandler)).build());
        
        RestAssured.port = testee.getPort();
        
        when()
            .get("/")
        .then()
            .assertThat()
                .statusCode(200)
                .body(Matchers.equalTo("served"));
    }
    
    @Test
    public void shouldDispatchToRightServletWhenTwoServletConfigured() throws Exception {
        ServletMethod fooGetHandler = (req, resp) -> resp.getWriter().append("served").close();
        ServletMethod barGetMethod = (req, resp) -> resp.sendError(400, "should not be called");
        
        testee = JettyHttpServer.start(Configuration
                                        .builder()
                                        .serve("/foo")
                                        .with(get(fooGetHandler))
                                        .serve("/bar")
                                        .with(get(barGetMethod))
                                        .build());
        
        RestAssured.port = testee.getPort();
        
        when()
            .get("/foo")
        .then()
            .assertThat()
                .statusCode(200)
                .body(Matchers.equalTo("served"));
    }
    
}
