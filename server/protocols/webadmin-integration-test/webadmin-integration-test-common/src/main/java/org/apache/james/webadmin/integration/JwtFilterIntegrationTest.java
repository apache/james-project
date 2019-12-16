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

import static io.restassured.RestAssured.given;
import static org.apache.james.webadmin.Constants.SEPARATOR;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.apache.james.GuiceJamesServer;
import org.apache.james.jwt.JwtConfiguration;
import org.apache.james.util.ClassLoaderUtils;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.routes.DomainsRoutes;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.restassured.RestAssured;

public abstract class JwtFilterIntegrationTest {

    private static final String DOMAIN = "domain";
    private static final String SPECIFIC_DOMAIN = DomainsRoutes.DOMAINS + SEPARATOR + DOMAIN;
    private static final String VALID_TOKEN_ADMIN_TRUE = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJhZG1pbkBvcGVuL" +
        "XBhYXMub3JnIiwiYWRtaW4iOnRydWUsImlhdCI6MTQ4OTAzODQzOH0.rgxCkdWEa-92a4R-72a9Z49k4LRvQDShgci5Y7qWRUP9IGJCK-lMkrHF" +
        "4H0a6L87BYppxVW701zaZ6dNxRMvHnjLBBWnPsC2B0rkkr2hEL2zfz7sb-iNGV-J4ICx97t8-TfQ5rz3VOX0FwdusPL_rJtmlGEGRivPkR6_aBe1" +
        "kQnvMlwpqF_3ox58EUqYJk6lK_6rjKEV3Xfre31IMpuQUy6c7TKc95sL2-13cknelTierBEmZ00RzTtv9SHIEfzZTfaUK2Wm0PvnQjmU2nIdEvU" +
        "EqE-jrM3yYXcQzoO-YTQnEhdl-iqbCfmEpYkl2Bx3eIq7gRxxnr7BPsX6HrCB0w";
    private static final String VALID_TOKEN_ADMIN_FALSE = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJhZG1pbkBvcGVu" +
        "LXBhYXMub3JnIiwiYWRtaW4iOmZhbHNlLCJpYXQiOjE0ODkwNDA4Njd9.reQc3DiVvbQHF08oW1qOUyDJyv3tfzDNk8jhVZequiCdOI9vXnRlOe" +
        "-yDYktd4WT8MYhqY7MgS-wR0vO9jZFv8ZCgd_MkKCvCO0HmMjP5iQPZ0kqGkgWUH7X123tfR38MfbCVAdPDba-K3MfkogV1xvDhlkPScFr_6MxE" +
        "xtedOK2JnQZn7t9sUzSrcyjWverm7gZkPptkIVoS8TsEeMMME5vFXe_nqkEG69q3kuBUm_33tbR5oNS0ZGZKlG9r41lHBjyf9J1xN4UYV8n866d" +
        "a7RPPCzshIWUtO0q9T2umWTnp-6OnOdBCkndrZmRR6pPxsD5YL0_77Wq8KT_5__fGA";

    private GuiceJamesServer guiceJamesServer;
    private DataProbeImpl dataProbe;
    private WebAdminGuiceProbe webAdminGuiceProbe;

    @Before
    public void setUp() throws Exception {
        JwtConfiguration jwtConfiguration = new JwtConfiguration(
            Optional.of(ClassLoaderUtils.getSystemResourceAsString("jwt_publickey")));

        guiceJamesServer = createJamesServer(jwtConfiguration);
        guiceJamesServer.start();
        dataProbe = guiceJamesServer.getProbe(DataProbeImpl.class);
        webAdminGuiceProbe = guiceJamesServer.getProbe(WebAdminGuiceProbe.class);

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminGuiceProbe.getWebAdminPort()).build();
    }

    @After
    public void tearDown() {
        guiceJamesServer.stop();
    }

    protected abstract GuiceJamesServer createJamesServer(JwtConfiguration jwtConfiguration) throws Exception;

    @Test
    public void jwtAuthenticationShouldWork() throws Exception {
        given()
            .header("Authorization", "Bearer " + VALID_TOKEN_ADMIN_TRUE)
        .when()
            .put(SPECIFIC_DOMAIN)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(dataProbe.listDomains())
            .contains(DOMAIN);
    }

    @Test
    public void jwtShouldRejectNonAdminRequests() throws Exception {
        given()
            .header("Authorization", "Bearer " + VALID_TOKEN_ADMIN_FALSE)
        .when()
            .put(SPECIFIC_DOMAIN)
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);

        assertThat(dataProbe.listDomains())
            .doesNotContain(DOMAIN);
    }

    @Test
    public void jwtShouldRejectInvalidRequests() throws Exception {
        given()
            .header("Authorization", "Bearer invalid")
        .when()
            .put(SPECIFIC_DOMAIN)
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);

        assertThat(dataProbe.listDomains())
            .doesNotContain(DOMAIN);
    }

}
