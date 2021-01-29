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

package org.apache.james.webadmin.routes;

import static io.restassured.RestAssured.when;
import static org.apache.james.webadmin.routes.ErrorRoutes.INTERNAL_SERVER_ERROR;
import static org.apache.james.webadmin.routes.ErrorRoutes.INVALID_ARGUMENT_EXCEPTION;
import static org.apache.james.webadmin.routes.ErrorRoutes.JSON_EXTRACT_EXCEPTION;
import static org.apache.james.webadmin.utils.ErrorResponder.ErrorType.INVALID_ARGUMENT;
import static org.apache.james.webadmin.utils.ErrorResponder.ErrorType.SERVER_ERROR;
import static org.eclipse.jetty.http.HttpStatus.BAD_REQUEST_400;
import static org.eclipse.jetty.http.HttpStatus.INTERNAL_SERVER_ERROR_500;
import static org.eclipse.jetty.http.HttpStatus.NOT_FOUND_404;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.restassured.RestAssured;

class ErrorRoutesTest {
    private static final String NOT_FOUND = "notFound";

    private WebAdminServer webAdminServer;

    @BeforeEach
    void setUp() throws Exception {
        webAdminServer = WebAdminUtils.createWebAdminServer(new ErrorRoutes())
            .start();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
                .setBasePath(ErrorRoutes.BASE_URL)
                .build();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @AfterEach
    void tearDown() {
        webAdminServer.destroy();
    }

    @Test
    void defineInternalErrorShouldReturnInternalErrorJsonFormat() {
        when()
            .get(INTERNAL_SERVER_ERROR)
        .then()
            .statusCode(INTERNAL_SERVER_ERROR_500)
            .body("statusCode", equalTo(INTERNAL_SERVER_ERROR_500))
            .body("type", equalTo(SERVER_ERROR.getType()))
            .body("message", equalTo("WebAdmin encountered an unexpected internal error"));
    }

    @Test
    void defineNotFoundShouldReturnNotFoundJsonFormat() {
        when()
            .get(NOT_FOUND)
        .then()
            .statusCode(NOT_FOUND_404)
            .body("statusCode", equalTo(NOT_FOUND_404))
            .body("type", equalTo(ErrorResponder.ErrorType.NOT_FOUND.getType()))
            .body("message", equalTo("GET /errors/notFound can not be found"));
    }

    @Test
    void defineJsonExtractExceptionShouldReturnBadRequestJsonFormat() {
        when()
            .get(JSON_EXTRACT_EXCEPTION)
        .then()
            .statusCode(BAD_REQUEST_400)
            .body("statusCode", equalTo(BAD_REQUEST_400))
            .body("type", equalTo(INVALID_ARGUMENT.getType()))
            .body("message", equalTo("JSON payload of the request is not valid"))
            .body("details", containsString("Unrecognized token 'a': was expecting (JSON String, Number, Array, Object or token 'null', 'true' or 'false')"));
    }

    @Test
    void defineIllegalExceptionShouldReturnBadRequestJsonFormat() {
        when()
            .get(INVALID_ARGUMENT_EXCEPTION)
        .then()
            .statusCode(BAD_REQUEST_400)
            .body("statusCode", equalTo(BAD_REQUEST_400))
            .body("type", equalTo(INVALID_ARGUMENT.getType()))
            .body("message", equalTo("Invalid arguments supplied in the user request"))
            .body("details", containsString("Argument is non valid"));
    }
}
