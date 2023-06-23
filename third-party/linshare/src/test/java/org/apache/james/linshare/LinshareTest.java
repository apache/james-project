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
package org.apache.james.linshare;

import static io.restassured.RestAssured.given;
import static io.restassured.config.EncoderConfig.encoderConfig;
import static io.restassured.config.RestAssuredConfig.newConfig;
import static org.apache.james.linshare.LinshareExtension.LinshareAPIForAdminTesting;
import static org.apache.james.linshare.LinshareFixture.ACCOUNT_ENABLED;
import static org.apache.james.linshare.LinshareFixture.ADMIN_ACCOUNT;
import static org.apache.james.linshare.LinshareFixture.TECHNICAL_ACCOUNT;
import static org.apache.james.linshare.LinshareFixture.TECHNICAL_PERMISSIONS;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.http.HttpStatus;
import org.apache.james.junit.categories.Unstable;
import org.apache.james.linshare.client.TechnicalAccountResponse;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;

@Tag(Unstable.TAG)
class LinshareTest {

    @RegisterExtension
    static LinshareExtension linshareExtension = new LinshareExtension();

    @BeforeEach
    void setup() {
        RestAssured.requestSpecification = new RequestSpecBuilder()
            .setContentType(ContentType.JSON)
            .setAccept(ContentType.JSON)
            .setConfig(newConfig().encoderConfig(encoderConfig().defaultContentCharset(StandardCharsets.UTF_8)))
            .setBaseUri("http://" + linshareExtension.getLinshare().getIp())
            .setPort(linshareExtension.getLinshare().getPort())
            .build();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @Test
    void linshareShouldStart() {
        given()
            .auth().basic("user1@linshare.org", "password1")
        .when()
            .get("linshare/webservice/rest/user/v2/documents")
        .then()
            .statusCode(HttpStatus.SC_OK);
    }

    @Test
    void linshareShouldHaveATechnicalAccountConfigured() {
        List<TechnicalAccountResponse> technicalAccounts = LinshareAPIForAdminTesting.from(ADMIN_ACCOUNT).allTechnicalAccounts();

        assertThat(technicalAccounts).anySatisfy(account -> SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(account.getName()).isEqualTo(TECHNICAL_ACCOUNT.getUsername());
            softly.assertThat(account.getPermissions()).containsOnly(TECHNICAL_PERMISSIONS.toArray(new String[0]));
            softly.assertThat(account.isEnabled()).isEqualTo(ACCOUNT_ENABLED);
        }));
    }
}
