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
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.core.IsNull.nullValue;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;

import org.apache.http.client.utils.URIBuilder;
import org.apache.james.GuiceJamesServer;
import org.apache.james.jmap.FixedDateZonedDateTimeProvider;
import org.apache.james.jmap.HttpJmapAuthentication;
import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.jmap.api.vacation.AccountId;
import org.apache.james.jmap.api.vacation.VacationPatch;
import org.apache.james.probe.DataProbe;
import org.apache.james.util.date.ZonedDateTimeProvider;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.JmapGuiceProbe;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.builder.RequestSpecBuilder;
import com.jayway.restassured.http.ContentType;

public abstract class GetVacationResponseTest {

    private static final String NAME = "[0][0]";
    private static final String ARGUMENTS = "[0][1]";
    private static final String USERS_DOMAIN = "domain.tld";
    public static final String USER = "username@" + USERS_DOMAIN;
    public static final String PASSWORD = "password";
    public static final String SUBJECT = "subject";
    public static final ZonedDateTime DATE_2015 = ZonedDateTime.parse("2015-10-15T14:10:00Z");
    public static final ZonedDateTime DATE_2014 = ZonedDateTime.parse("2014-09-30T14:10:00+02:00");
    public static final ZonedDateTime DATE_2016 = ZonedDateTime.parse("2016-04-15T11:56:32.224+07:00[Asia/Vientiane]");
    private JmapGuiceProbe jmapGuiceProbe;

    protected abstract GuiceJamesServer createJmapServer(ZonedDateTimeProvider zonedDateTimeProvider);

    protected abstract void await();

    private AccessToken accessToken;
    private GuiceJamesServer jmapServer;
    private FixedDateZonedDateTimeProvider fixedDateZonedDateTimeProvider;

    @Before
    public void setup() throws Throwable {
        fixedDateZonedDateTimeProvider = new FixedDateZonedDateTimeProvider();
        fixedDateZonedDateTimeProvider.setFixedDateTime(DATE_2015);

        jmapServer = createJmapServer(fixedDateZonedDateTimeProvider);
        jmapServer.start();

        jmapGuiceProbe = jmapServer.getProbe(JmapGuiceProbe.class);
        RestAssured.requestSpecification = new RequestSpecBuilder()
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .setConfig(newConfig().encoderConfig(encoderConfig().defaultContentCharset(StandardCharsets.UTF_8)))
                .setPort(jmapGuiceProbe.getJmapPort())
                .build();

        DataProbe dataProbe = jmapServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(USERS_DOMAIN);
        dataProbe.addUser(USER, PASSWORD);
        accessToken = HttpJmapAuthentication.authenticateJamesUser(baseUri(), USER, PASSWORD);

        await();
    }

    private URIBuilder baseUri() {
        return new URIBuilder()
            .setScheme("http")
            .setHost("localhost")
            .setPort(jmapServer.getProbe(JmapGuiceProbe.class)
                .getJmapPort())
            .setCharset(StandardCharsets.UTF_8);
    }

    @After
    public void teardown() {
        jmapServer.stop();
    }

    @Test
    public void getVacationResponseShouldReturnDefaultValue() {
        given()
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
            .body(ARGUMENTS + ".list[0].subject", nullValue())
            .body(ARGUMENTS + ".list[0].textBody", nullValue())
            .body(ARGUMENTS + ".list[0].htmlBody", nullValue());
    }

    @Test
    public void getVacationResponseShouldReturnStoredValue() {
        jmapGuiceProbe.modifyVacation(AccountId.fromString(USER),
            VacationPatch.builder()
                .isEnabled(true)
                .fromDate(ZonedDateTime.parse("2014-09-30T14:10:00Z"))
                .toDate(ZonedDateTime.parse("2014-10-30T14:10:00Z"))
                .textBody("Test explaining my vacations")
                .subject(SUBJECT)
                .htmlBody("<p>Test explaining my vacations</p>")
                .build());

        given()
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
            .body(ARGUMENTS + ".list[0].textBody", equalTo("Test explaining my vacations"))
            .body(ARGUMENTS + ".list[0].subject", equalTo(SUBJECT))
            .body(ARGUMENTS + ".list[0].htmlBody", equalTo("<p>Test explaining my vacations</p>"));
    }

    @Test
    public void getVacationResponseShouldReturnStoredValueWithNonDefaultTimezone() {
        jmapGuiceProbe.modifyVacation(AccountId.fromString(USER),
            VacationPatch.builder()
                .isEnabled(true)
                .fromDate(ZonedDateTime.parse("2014-09-30T14:10:00+02:00"))
                .toDate(ZonedDateTime.parse("2016-04-15T11:56:32.224+07:00[Asia/Vientiane]"))
                .textBody("Test explaining my vacations")
                .build());

        given()
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
            .body(ARGUMENTS + ".list[0].toDate", equalTo("2016-04-15T11:56:32.224+07:00[Asia/Vientiane]"))
            .body(ARGUMENTS + ".list[0].isEnabled", equalTo(true))
            .body(ARGUMENTS + ".list[0].textBody", equalTo("Test explaining my vacations"));
    }

    @Test
    public void getVacationResponseShouldReturnIsActivatedWhenInRange() {
        jmapGuiceProbe.modifyVacation(AccountId.fromString(USER),
            VacationPatch.builder()
                .isEnabled(true)
                .fromDate(DATE_2014)
                .toDate(DATE_2016)
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
            .body(ARGUMENTS + ".list[0].isActivated", equalTo(true));
    }

    @Test
    public void getVacationResponseShouldNotReturnIsActivatedWhenOutOfRange() {
        fixedDateZonedDateTimeProvider.setFixedDateTime(DATE_2014);

        jmapGuiceProbe.modifyVacation(AccountId.fromString(USER),
            VacationPatch.builder()
                .isEnabled(true)
                .fromDate(DATE_2015)
                .toDate(DATE_2016)
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
            .body(ARGUMENTS + ".list[0].isActivated", equalTo(false));
    }

    @Test
    public void accountIdIsNotSupported() {
        jmapGuiceProbe.modifyVacation(AccountId.fromString(USER),
            VacationPatch.builder()
                .isEnabled(true)
                .fromDate(ZonedDateTime.parse("2014-09-30T14:10:00+02:00"))
                .toDate(ZonedDateTime.parse("2014-10-30T14:10:00+02:00"))
                .textBody("Test explaining my vacations")
                .build());

        given()
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
