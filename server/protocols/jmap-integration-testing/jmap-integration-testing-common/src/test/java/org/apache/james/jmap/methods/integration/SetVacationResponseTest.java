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
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import java.time.ZonedDateTime;

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

public abstract class SetVacationResponseTest {

    private static final String NAME = "[0][0]";
    private static final String ARGUMENTS = "[0][1]";
    private static final String USERS_DOMAIN = "domain.tld";
    public static final String USER = "username@" + USERS_DOMAIN;
    public static final String PASSWORD = "password";
    public static final String SUBJECT = "subject";

    protected abstract GuiceJamesServer createJmapServer();

    protected abstract void await();

    private AccessToken accessToken;
    private GuiceJamesServer jmapServer;

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
    public void setVacationResponseShouldReturnErrorOnMalformedRequestStructure() {
        String bodyRequest = "[[" +
            "\"setVacationResponse\", " +
            "{" +
                "\"update\":{" +
                    "\"idVacation\" : {" +
                        "\"id\": \"1\"," +
                        "\"isEnabled\": \"true\"," +
                        "\"textBody\": \"Message explaining my wonderful vacations\"" +
                    "}" +
                "}" +
            "}, " +
            "\"#0\"" +
            "]]";

        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body(bodyRequest)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("error"))
            .body(ARGUMENTS + ".type", equalTo("invalidArguments"))
            .body(ARGUMENTS + ".description", equalTo("update field should just contain one entry with key \"singleton\""));
    }

    @Test
    public void setVacationResponseShouldContainAnErrorWhenInvalidId() {
        String bodyRequest = "[[" +
            "\"setVacationResponse\", " +
            "{" +
                "\"update\":{" +
                    "\"singleton\" : {" +
                        "\"id\": \"1\"," +
                        "\"isEnabled\": \"true\"," +
                        "\"textBody\": \"Message explaining my wonderful vacations\"" +
                    "}" +
                "}" +
            "}, " +
            "\"#0\"" +
            "]]";

        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body(bodyRequest)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("vacationResponseSet"))
            .body(ARGUMENTS + ".notUpdated.singleton.type", equalTo("invalidArguments"))
            .body(ARGUMENTS + ".notUpdated.singleton.description", equalTo("There is one VacationResponse object per account, with id set to \"singleton\" and not to 1"));
    }

    @Test
    public void setVacationResponseShouldReturnCorrectAnswerUponValidVacationResponse() {
        String bodyRequest = "[[" +
            "\"setVacationResponse\", " +
            "{" +
                "\"update\":{" +
                    "\"singleton\" : {" +
                        "\"id\": \"singleton\"," +
                        "\"isEnabled\": \"true\"," +
                        "\"textBody\": \"Message explaining my wonderful vacations\"," +
                        "\"fromDate\":\"2014-09-30T14:10:00Z[GMT]\"," +
                        "\"toDate\":\"2014-10-30T14:10:00Z[GMT]\"," +
                        "\"subject\":\"" + SUBJECT + "\"" +
                    "}" +
                "}" +
            "}, " +
            "\"#0\"" +
            "]]";

        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body(bodyRequest)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("vacationResponseSet"))
            .body(ARGUMENTS + ".updated[0]", equalTo("singleton"));

        Vacation vacation = jmapServer.serverProbe().retrieveVacation(AccountId.fromString(USER));
        assertThat(vacation.getTextBody()).isEqualTo("Message explaining my wonderful vacations");
        assertThat(vacation.isEnabled()).isTrue();
        assertThat(vacation.getFromDate()).contains(ZonedDateTime.parse("2014-09-30T14:10:00Z[GMT]"));
        assertThat(vacation.getToDate()).contains(ZonedDateTime.parse("2014-10-30T14:10:00Z[GMT]"));
        assertThat(vacation.getSubject()).contains(SUBJECT);
    }

    @Test
    public void setVacationResponseShouldHandleNamedTimeZone() {
        String bodyRequest = "[[" +
            "\"setVacationResponse\", " +
            "{" +
                "\"update\":{" +
                    "\"singleton\" : {" +
                        "\"id\": \"singleton\"," +
                        "\"isEnabled\": \"true\"," +
                        "\"textBody\": \"Message explaining my wonderful vacations\"," +
                        "\"fromDate\":\"2016-04-03T02:01+07:00[Asia/Vientiane]\"," +
                        "\"toDate\":\"2016-04-07T02:01+07:00[Asia/Vientiane]\"" +
                    "}" +
                "}" +
            "}, " +
            "\"#0\"" +
            "]]";

        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body(bodyRequest)
            .when()
            .post("/jmap")
            .then()
            .statusCode(200)
            .body(NAME, equalTo("vacationResponseSet"))
            .body(ARGUMENTS + ".updated[0]", equalTo("singleton"));

        Vacation vacation = jmapServer.serverProbe().retrieveVacation(AccountId.fromString(USER));
        assertThat(vacation.getTextBody()).isEqualTo("Message explaining my wonderful vacations");
        assertThat(vacation.isEnabled()).isTrue();
        assertThat(vacation.getFromDate()).contains(ZonedDateTime.parse("2016-04-03T02:01+07:00[Asia/Vientiane]"));
        assertThat(vacation.getToDate()).contains(ZonedDateTime.parse("2016-04-07T02:01+07:00[Asia/Vientiane]"));
    }

    @Test
    public void nullTextBodyShouldBeRejected() {
        String bodyRequest = "[[" +
            "\"setVacationResponse\", " +
            "{" +
                "\"update\":{" +
                    "\"singleton\" : {" +
                        "\"id\": \"singleton\"," +
                        "\"isEnabled\": \"true\"," +
                        "\"textBody\": null" +
                    "}" +
                "}" +
            "}, " +
            "\"#0\"" +
            "]]";

        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body(bodyRequest)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("error"))
            .body(ARGUMENTS + ".type", equalTo("invalidArguments"))
            .body(ARGUMENTS + ".description", containsString("textBody property of vacationResponse object should not be null"));
    }

    @Test
    public void noTextBodyShouldBeRejected() {
        String bodyRequest = "[[" +
            "\"setVacationResponse\", " +
            "{" +
                "\"update\":{" +
                    "\"singleton\" : {" +
                        "\"id\": \"singleton\"," +
                        "\"isEnabled\": \"true\"" +
                    "}" +
                "}" +
            "}, " +
            "\"#0\"" +
            "]]";

        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body(bodyRequest)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("error"))
            .body(ARGUMENTS + ".type", equalTo("invalidArguments"))
            .body(ARGUMENTS + ".description", containsString("textBody property of vacationResponse object should not be null"));
    }

    @Test
    public void accountIdIsNotSupported() {
        String bodyRequest = "[[" +
            "\"setVacationResponse\", " +
            "{" +
                "\"accountId\": \"1\"," +
                "\"update\":{" +
                    "\"singleton\" : {" +
                        "\"id\": \"singleton\"," +
                        "\"isEnabled\": \"true\"," +
                        "\"textBody\": \"Message explaining my wonderful vacations\"," +
                        "\"fromDate\":\"2014-09-30T14:10:00Z\"," +
                        "\"toDate\":\"2014-10-30T14:10:00Z\"" +
                    "}" +
                "}" +
            "}, " +
            "\"#0\"" +
            "]]";

        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body(bodyRequest)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("error"))
            .body(ARGUMENTS + ".type", equalTo("Not yet implemented"));
    }
}
