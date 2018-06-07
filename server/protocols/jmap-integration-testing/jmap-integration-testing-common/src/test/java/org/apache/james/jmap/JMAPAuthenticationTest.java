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
import static com.jayway.restassured.RestAssured.with;
import static org.apache.james.jmap.TestingConstants.jmapRequestSpecBuilder;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.isA;
import static org.hamcrest.Matchers.notNullValue;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.apache.james.GuiceJamesServer;
import org.apache.james.jmap.model.ContinuationToken;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.JmapGuiceProbe;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.http.ContentType;

public abstract class JMAPAuthenticationTest {

    private static final ZonedDateTime oldDate = ZonedDateTime.parse("2011-12-03T10:15:30+01:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    private static final ZonedDateTime newDate = ZonedDateTime.parse("2011-12-03T10:16:30+01:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    private static final ZonedDateTime afterExpirationDate = ZonedDateTime.parse("2011-12-03T10:30:31+01:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME);

    protected abstract GuiceJamesServer createJmapServer(FixedDateZonedDateTimeProvider zonedDateTimeProvider) throws IOException;

    private UserCredentials userCredentials;
    private FixedDateZonedDateTimeProvider zonedDateTimeProvider;
    private GuiceJamesServer jmapServer;

    @Before
    public void setup() throws Throwable {
        zonedDateTimeProvider = new FixedDateZonedDateTimeProvider();
        zonedDateTimeProvider.setFixedDateTime(oldDate);
        jmapServer = createJmapServer(zonedDateTimeProvider);
        jmapServer.start();
        RestAssured.requestSpecification = jmapRequestSpecBuilder
                .setPort(jmapServer.getProbe(JmapGuiceProbe.class).getJmapPort())
                .build();
        
        userCredentials = UserCredentials.builder()
                .username("user@domain.tld")
                .password("password")
                .build();

        
        String domain = "domain.tld";
        jmapServer.getProbe(DataProbeImpl.class)
            .fluentAddDomain(domain)
            .fluentAddUser(userCredentials.getUsername(), userCredentials.getPassword());
        
    }
    
    @After
    public void teardown() {
        jmapServer.stop();
    }

    @Test
    public void mustReturnMalformedRequestWhenContentTypeIsMissing() {
        given()
            .accept(ContentType.JSON)
        .when()
            .post("/authentication")
        .then()
            .statusCode(400);
    }

    @Test
    public void mustReturnMalformedRequestWhenContentTypeIsNotJson() {
        given()
            .contentType(ContentType.XML)
            .accept(ContentType.JSON)
        .when()
            .post("/authentication")
        .then()
            .statusCode(400);
    }

    @Test
    public void mustReturnMalformedRequestWhenAcceptIsMissing() {
        given()
            .contentType(ContentType.JSON)
        .when()
            .post("/authentication")
        .then()
            .statusCode(400);
    }

    @Test
    public void mustReturnMalformedRequestWhenAcceptIsNotJson() {
        given()
            .contentType(ContentType.JSON)
            .accept(ContentType.XML)
        .when()
            .post("/authentication")
        .then()
            .statusCode(400);
    }

    @Test
    public void mustReturnMalformedRequestWhenCharsetIsNotUTF8() {
        given()
            .contentType("application/json; charset=ISO-8859-1")
            .accept(ContentType.JSON)
        .when()
            .post("/authentication")
        .then()
            .statusCode(400);
    }

    @Test
    public void mustReturnMalformedRequestWhenBodyIsEmpty() {
        given()
            .contentType(ContentType.JSON)
            .accept(ContentType.JSON)
        .when()
            .post("/authentication")
        .then()
            .statusCode(400);
    }

    @Test
    public void mustReturnMalformedRequestWhenBodyIsNotAcceptable() {
        given()
            .contentType(ContentType.JSON)
            .accept(ContentType.JSON)
            .body("{\"badAttributeName\": \"value\"}")
        .when()
            .post("/authentication")
        .then()
            .statusCode(400);
    }

    @Test
    public void mustReturnJsonResponse() throws Exception {
        given()
            .contentType(ContentType.JSON)
            .accept(ContentType.JSON)
            .body("{\"username\": \"" + userCredentials.getUsername() + "\", \"clientName\": \"Mozilla Thunderbird\", \"clientVersion\": \"42.0\", \"deviceName\": \"Joe Blogg’s iPhone\"}")
        .when()
            .post("/authentication")
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON);
    }

    @Test
    public void methodShouldContainPasswordWhenValidResquest() throws Exception {
        given()
            .contentType(ContentType.JSON)
            .accept(ContentType.JSON)
            .body("{\"username\": \"" + userCredentials.getUsername() + "\", \"clientName\": \"Mozilla Thunderbird\", \"clientVersion\": \"42.0\", \"deviceName\": \"Joe Blogg’s iPhone\"}")
        .when()
            .post("/authentication")
        .then()
            .statusCode(200)
            .body("methods", hasItem(userCredentials.getPassword()));
    }

    @Test
    public void mustReturnContinuationTokenWhenValidResquest() throws Exception {
        given()
            .contentType(ContentType.JSON)
            .accept(ContentType.JSON)
            .body("{\"username\": \"" + userCredentials.getUsername() + "\", \"clientName\": \"Mozilla Thunderbird\", \"clientVersion\": \"42.0\", \"deviceName\": \"Joe Blogg’s iPhone\"}")
        .when()
            .post("/authentication")
        .then()
            .statusCode(200)
            .body("continuationToken", isA(String.class));
    }

    @Test
    public void mustReturnAuthenticationFailedWhenBadPassword() throws Exception {
        String continuationToken = fromGoodContinuationTokenRequest();

        given()
            .contentType(ContentType.JSON)
            .accept(ContentType.JSON)
            .body("{\"token\": \"" + continuationToken + "\", \"method\": \"password\", \"password\": \"badpassword\"}")
        .when()
            .post("/authentication")
        .then()
            .statusCode(401);
    }

    @Test
    public void mustReturnAuthenticationFailedWhenContinuationTokenIsRejectedByTheContinuationTokenManager() throws Exception {
        ContinuationToken badContinuationToken = new ContinuationToken(userCredentials.getUsername(), newDate, "badSignature");

        given()
            .contentType(ContentType.JSON)
            .accept(ContentType.JSON)
            .body("{\"token\": \"" + badContinuationToken.serialize() + "\", \"method\": \"password\", \"password\": \"" + userCredentials.getPassword() + "\"}")
        .when()
            .post("/authentication")
        .then()
            .statusCode(401);
    }

    @Test
    public void mustReturnRestartAuthenticationWhenContinuationTokenIsExpired() throws Exception {
        String continuationToken = fromGoodContinuationTokenRequest();
        zonedDateTimeProvider.setFixedDateTime(afterExpirationDate);

        given()
            .contentType(ContentType.JSON)
            .accept(ContentType.JSON)
            .body("{\"token\": \"" + continuationToken + "\", \"method\": \"password\", \"password\": \"" + userCredentials.getPassword() + "\"}")
        .when()
            .post("/authentication")
        .then()
            .statusCode(403);
    }

    @Test
    public void mustReturnAuthenticationFailedWhenUsersRepositoryException() throws Exception {
        String continuationToken = fromGoodContinuationTokenRequest();

        given()
            .contentType(ContentType.JSON)
            .accept(ContentType.JSON)
            .body("{\"token\": \"" + continuationToken + "\", \"method\": \"password\", \"password\": \"" + "wrong password" + "\"}")
        .when()
            .post("/authentication")
        .then()
            .statusCode(401);
    }

    @Test
    public void mustReturnCreatedWhenGoodPassword() throws Exception {
        String continuationToken = fromGoodContinuationTokenRequest();
        zonedDateTimeProvider.setFixedDateTime(newDate);

        given()
            .contentType(ContentType.JSON)
            .accept(ContentType.JSON)
            .body("{\"token\": \"" + continuationToken + "\", \"method\": \"password\", \"password\": \"" + userCredentials.getPassword() + "\"}")
        .when()
            .post("/authentication")
        .then()
            .statusCode(201);
    }

    @Test
    public void mustSendJsonContainingAccessTokenAndEndpointsWhenGoodPassword() throws Exception {
        String continuationToken = fromGoodContinuationTokenRequest();
        zonedDateTimeProvider.setFixedDateTime(newDate);

        given()
            .contentType(ContentType.JSON)
            .accept(ContentType.JSON)
            .body("{\"token\": \"" + continuationToken + "\", \"method\": \"password\", \"password\": \"" + userCredentials.getPassword() + "\"}")
        .when()
            .post("/authentication")
        .then()
            .body("accessToken", isA(String.class))
            .body("api", equalTo("/jmap"))
            .body("eventSource", both(isA(String.class)).and(notNullValue()))
            .body("upload", equalTo("/upload"))
            .body("download", equalTo("/download"));
    }
    
    @Test
    public void getMustReturnUnauthorizedWithoutAuthroizationHeader() throws Exception {
        given()
        .when()
            .get("/authentication")
        .then()
            .statusCode(401);
    }

    @Test
    public void getMustReturnUnauthorizedWithoutAValidAuthroizationHeader() throws Exception {
        given()
            .header("Authorization", UUID.randomUUID())
        .when()
            .get("/authentication")
        .then()
            .statusCode(401);
    }

    @Test
    public void getMustReturnEndpointsWhenValidAuthorizationHeader() throws Exception {
        String continuationToken = fromGoodContinuationTokenRequest();
        String token = fromGoodAccessTokenRequest(continuationToken);

        given()
            .header("Authorization", token)
        .when()
            .get("/authentication")
        .then()
            .statusCode(200)
            .body("api", equalTo("/jmap"))
            .body("eventSource", both(isA(String.class)).and(notNullValue()))
            .body("upload", equalTo("/upload"))
            .body("download", equalTo("/download"));
    }

    @Test
    public void getMustReturnEndpointsWhenValidJwtAuthorizationHeader() throws Exception {
        String token = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIn0.T04BTk" +
                "LXkJj24coSZkK13RfG25lpvmSl2MJ7N10KpBk9_-95EGYZdog-BDAn3PJzqVw52z-Bwjh4VOj1-j7cURu0cT4jXehhUrlCxS4n7QHZ" +
                "DN_bsEYGu7KzjWTpTsUiHe-rN7izXVFxDGG1TGwlmBCBnPW-EFCf9ylUsJi0r2BKNdaaPRfMIrHptH1zJBkkUziWpBN1RNLjmvlAUf" +
                "49t1Tbv21ZqYM5Ht2vrhJWczFbuC-TD-8zJkXhjTmA1GVgomIX5dx1cH-dZX1wANNmshUJGHgepWlPU-5VIYxPEhb219RMLJIELMY2" +
                "qNOR8Q31ydinyqzXvCSzVJOf6T60-w";

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/authentication")
                .then()
                .statusCode(200);
    }

    @Test
    public void getMustReturnEndpointsWhenValidUnkwnonUserJwtAuthorizationHeader() throws Exception {
        String token = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMzM3IiwibmFtZSI6Ik5ldyBVc2VyIn0.ci8U04EOWKpi_y"
                + "faKs8fnCBcu1mWs8fvf-t9SDP2kkvDDfD-ya4sEGn4ueCp2dA2ndefZfVu_IfvdlVtxqzSf0tQ-dFKrIe-OtSKhI2otjWctLtk9A"
                + "G7jpWkXoDgr5IOVmsqg37Zxc2bgkLkC5FJqV6oCp51TNQTH6zZbXIUeuGFbHj2-iJeX8sACKTQB0llwc6TFm7GYUF03rv4DfJjqp"
                + "Kd0g8RdnlevSOjV-gGzvKEItugtexS5pgOZ2GYcvqEUDb9EnQR7Qe2EzPAX_FCJfGhlv7bDQlTgOHHAjqw2lD4-zeAznw-3wlYLS"
                + "zhi4ivvPjT-y2T5wnnhzeeYOpYOQ";
        
        given()
            .header("Authorization", "Bearer " + token)
        .when()
            .get("/authentication")
        .then()
            .statusCode(200);
    }
    
    @Test
    public void getMustReturnBadCredentialsWhenInvalidJwtAuthorizationHeader() throws Exception {
        String token = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIn0.T04BTk" +
                "LXkJj24coSZkK13RfG25lpvmSl2MJ7N10KpBk9_-95EGYZdog-BDAn3PJzqVw52z-Bwjh4VOj1-j7cURu0cT4jXehhUrlCxS4n7QHZ" +
                "EN_bsEYGu7KzjWTpTsUiHe-rN7izXVFxDGG1TGwlmBCBnPW-EFCf9ylUsJi0r2BKNdaaPRfMIrHptH1zJBkkUziWpBN1RNLjmvlAUf" +
                "49t1Tbv21ZqYM5Ht2vrhJWczFbuC-TD-8zJkXhjTmA1GVgomIX5dx1cH-dZX1wANNmshUJGHgepWlPU-5VIYxPEhb219RMLJIELMY2" +
                "qNOR8Q31ydinyqzXvCSzVJOf6T60-w";

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/authentication")
                .then()
                .statusCode(401);
    }

    @Test
    public void optionsRequestsShouldNeverRequireAuthentication() {
        given()
                .when()
                .options("/authentication")
                .then()
                .statusCode(200);
    }

    @Test
    public void getMustReturnEndpointsWhenCorrectAuthentication() throws Exception {
        String continuationToken = fromGoodContinuationTokenRequest();
        zonedDateTimeProvider.setFixedDateTime(newDate);
    
        String accessToken = fromGoodAccessTokenRequest(continuationToken);
    
        given()
            .header("Authorization", accessToken)
        .when()
            .get("/authentication")
        .then()
            .statusCode(200)
            .body("api", isA(String.class));
    }
    
    @Test
    public void deleteMustReturnUnauthenticatedWithoutAuthorizationHeader() throws Exception {
        given()
        .when()
            .delete("/authentication")
        .then()
            .statusCode(401);
    }

    @Test
    public void deleteMustReturnUnauthenticatedWithoutAValidAuthroizationHeader() throws Exception {
        given()
            .header("Authorization", UUID.randomUUID())
        .when()
            .delete("/authentication")
        .then()
            .statusCode(401);
    }
    
    @Test
    public void deleteMustReturnOKNoContentOnValidAuthorizationToken() throws Exception {
        String continuationToken = fromGoodContinuationTokenRequest();
        String token = fromGoodAccessTokenRequest(continuationToken);
        given()
            .header("Authorization", token)
        .when()
            .delete("/authentication")
        .then()
            .statusCode(204);
    }

    @Test
    public void deleteMustInvalidAuthorizationOnCorrectAuthorization() throws Exception {
        String continuationToken = fromGoodContinuationTokenRequest();
        zonedDateTimeProvider.setFixedDateTime(newDate);
    
        String accessToken = fromGoodAccessTokenRequest(continuationToken);
        
        goodDeleteAccessTokenRequest(accessToken);
    
        given()
            .header("Authorization", accessToken)
        .when()
            .get("/authentication")
        .then()
            .statusCode(401);
    }

    private String fromGoodContinuationTokenRequest() {
        return with()
            .contentType(ContentType.JSON)
            .accept(ContentType.JSON)
            .body("{\"username\": \"" + userCredentials.getUsername() + "\", \"clientName\": \"Mozilla Thunderbird\", \"clientVersion\": \"42.0\", \"deviceName\": \"Joe Blogg’s iPhone\"}")
        .post("/authentication")
            .body()
            .path("continuationToken")
            .toString();
    }

    private String fromGoodAccessTokenRequest(String continuationToken) {
        return with()
            .contentType(ContentType.JSON)
            .accept(ContentType.JSON)
            .body("{\"token\": \"" + continuationToken + "\", \"method\": \"password\", \"password\": \"" + userCredentials.getPassword() + "\"}")
        .post("/authentication")
            .path("accessToken")
            .toString();
    }

    private void goodDeleteAccessTokenRequest(String accessToken) {
        with()
            .header("Authorization", accessToken)
            .delete("/authentication");
    }
}
