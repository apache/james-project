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

package org.apache.james.jmap.methods.integration;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.config.EncoderConfig.encoderConfig;
import static com.jayway.restassured.config.RestAssuredConfig.newConfig;
import static org.hamcrest.core.IsNull.nullValue;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.apache.james.GuiceJamesServer;
import org.apache.james.jmap.JmapAuthentication;
import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.jmap.api.vacation.AccountId;
import org.apache.james.jmap.api.vacation.Vacation;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.base.Charsets;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.http.ContentType;

public abstract class GetVacationResponseTest {

    private static final String NAME = "[0][0]";
    private static final String ARGUMENTS = "[0][1]";
    private static final String USERS_DOMAIN = "domain.tld";
    public static final String USER = "username@" + USERS_DOMAIN;
    public static final String PASSWORD = "password";

    protected abstract GuiceJamesServer<?> createJmapServer();

    protected abstract void await();

    private AccessToken accessToken;
    private GuiceJamesServer<?> jmapServer;

    @Before
    public void setup() throws Throwable {
        jmapServer = createJmapServer();
        jmapServer.start();
        RestAssured.port = jmapServer.getJmapPort();
        RestAssured.config = newConfig().encoderConfig(encoderConfig().defaultContentCharset(Charsets.UTF_8));

        jmapServer.serverProbe().addDomain(USERS_DOMAIN);
        jmapServer.serverProbe().addUser(USER, PASSWORD);
        accessToken = JmapAuthentication.authenticateJamesUser(USER, PASSWORD);

        await();
    }

    @After
    public void teardown() {
        jmapServer.stop();
    }

    @Test
    public void getVacationResponseShouldReturnDefaultValue() {
        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[" +
                "\"getVacationResponse\", " +
                "{}, " +
                "\"#0\"" +
                "]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("vacationResponse"))
            .body(ARGUMENTS + ".accountId", equalTo(USER))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(ARGUMENTS + ".list[0].id", equalTo("singleton"))
            .body(ARGUMENTS + ".list[0].fromDate", nullValue())
            .body(ARGUMENTS + ".list[0].toDate", nullValue())
            .body(ARGUMENTS + ".list[0].isEnabled", equalTo(false))
            .body(ARGUMENTS + ".list[0].textBody", equalTo(""));
    }

    @Test
    public void getVacationResponseShouldReturnStoredValue() {
        jmapServer.serverProbe().modifyVacation(AccountId.fromString(USER),
            Vacation.builder()
                .enabled(true)
                .fromDate(Optional.of(ZonedDateTime.parse("2014-09-30T14:10:00Z")))
                .toDate(Optional.of(ZonedDateTime.parse("2014-10-30T14:10:00Z")))
                .textBody("Test explaining my vacations")
                .build());

        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[" +
                "\"getVacationResponse\", " +
                "{}, " +
                "\"#0\"" +
                "]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("vacationResponse"))
            .body(ARGUMENTS + ".accountId", equalTo(USER))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(ARGUMENTS + ".list[0].id", equalTo("singleton"))
            .body(ARGUMENTS + ".list[0].fromDate", equalTo("2014-09-30T14:10:00Z"))
            .body(ARGUMENTS + ".list[0].toDate", equalTo("2014-10-30T14:10:00Z"))
            .body(ARGUMENTS + ".list[0].isEnabled", equalTo(true))
            .body(ARGUMENTS + ".list[0].textBody", equalTo("Test explaining my vacations"));
    }

    @Test
    public void getVacationResponseShouldReturnStoredValueWithNonDefaultTimezone() {
        jmapServer.serverProbe().modifyVacation(AccountId.fromString(USER),
            Vacation.builder()
                .enabled(true)
                .fromDate(Optional.of(ZonedDateTime.parse("2014-09-30T14:10:00+02:00")))
                .toDate(Optional.of(ZonedDateTime.parse("2016-04-15T11:56:32.224+07:00")))
                .textBody("Test explaining my vacations")
                .build());

        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[" +
                "\"getVacationResponse\", " +
                "{}, " +
                "\"#0\"" +
                "]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("vacationResponse"))
            .body(ARGUMENTS + ".accountId", equalTo(USER))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(ARGUMENTS + ".list[0].id", equalTo("singleton"))
            .body(ARGUMENTS + ".list[0].fromDate", equalTo("2014-09-30T14:10:00+02:00"))
            .body(ARGUMENTS + ".list[0].toDate", equalTo("2016-04-15T11:56:32.224+07:00"))
            .body(ARGUMENTS + ".list[0].isEnabled", equalTo(true))
            .body(ARGUMENTS + ".list[0].textBody", equalTo("Test explaining my vacations"));
    }

    @Test
    public void accountIdIsNotSupported() {
        jmapServer.serverProbe().modifyVacation(AccountId.fromString(USER),
            Vacation.builder()
                .enabled(true)
                .fromDate(Optional.of(ZonedDateTime.parse("2014-09-30T14:10:00+02:00")))
                .toDate(Optional.of(ZonedDateTime.parse("2014-10-30T14:10:00+02:00")))
                .textBody("Test explaining my vacations")
                .build());

        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[" +
                "\"getVacationResponse\", " +
                "{\"accountId\":\"1\"}, " +
                "\"#0\"" +
                "]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("error"))
            .body(ARGUMENTS + ".type", equalTo("Not yet implemented"));
    }

}
