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

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.with;
import static org.apache.james.jmap.JMAPTestingConstants.jmapRequestSpecBuilder;
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
import org.apache.james.jmap.JmapGuiceProbe;
import org.apache.james.jmap.draft.model.ContinuationToken;
import org.apache.james.junit.categories.BasicFeature;
import org.apache.james.utils.DataProbeImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;

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
                .setPort(jmapServer.getProbe(JmapGuiceProbe.class).getJmapPort().getValue())
                .build();
        
        userCredentials = UserCredentials.builder()
                .username("user@domain.tld")
                .password("password")
                .build();

        
        String domain = "domain.tld";
        jmapServer.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(domain)
            .addUser(userCredentials.getUsername(), userCredentials.getPassword());
        
    }
    
    @After
    public void teardown() {
        jmapServer.stop();
    }

    @Test
    public void mustReturnMalformedRequestWhenContentTypeIsMissing() {
        given()
            .accept(ContentType.JSON)
            .contentType("")
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
            .accept("")
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
    public void mustPositionCorsHeaders() {
        given()
            .contentType(ContentType.JSON)
            .accept(ContentType.JSON)
            .body("{\"username\": \"" + userCredentials.getUsername() + "\", \"clientName\": \"Mozilla Thunderbird\", \"clientVersion\": \"42.0\", \"deviceName\": \"Joe Blogg’s iPhone\"}")
        .when()
            .post("/authentication")
        .then()
            .header("Access-Control-Allow-Origin", "*")
            .header("Access-Control-Allow-Methods", "GET, POST, DELETE, PUT")
            .header("Access-Control-Allow-Headers", "Content-Type, Authorization, Accept")
            .header("Access-Control-Max-Age", "86400");
    }

    @Test
    public void mustReturnJsonResponse() {
        given()
            .contentType(ContentType.JSON)
            .accept(ContentType.JSON)
            .body("{\"username\": \"" + userCredentials.getUsername() + "\", \"clientName\": \"Mozilla Thunderbird\", \"clientVersion\": \"42.0\", \"deviceName\": \"Joe Blogg’s iPhone\"}")
        .when()
            .post("/authentication")
        .then()
            .statusCode(200)
            .header("Content-Length", "447")
            .contentType(ContentType.JSON);
    }

    @Test
    public void methodShouldContainPasswordWhenValidRequest() {
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

    @Category(BasicFeature.class)
    @Test
    public void mustReturnContinuationTokenWhenValidRequest() {
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

    @Category(BasicFeature.class)
    @Test
    public void mustReturnAuthenticationFailedWhenBadPassword() {
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
    public void mustReturnRestartAuthenticationWhenContinuationTokenIsExpired() {
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
    public void mustReturnAuthenticationFailedWhenUsersRepositoryException() {
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
    public void mustReturnCreatedWhenGoodPassword() {
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

    @Category(BasicFeature.class)
    @Test
    public void mustSendJsonContainingAccessTokenAndEndpointsWhenGoodPassword() {
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
    public void getMustReturnUnauthorizedWithoutAuthorizationHeader() {
        given()
        .when()
            .get("/authentication")
        .then()
            .statusCode(401);
    }

    @Test
    public void getMustReturnUnauthorizedWithoutAValidAuthorizationHeader() {
        given()
            .header("Authorization", UUID.randomUUID())
        .when()
            .get("/authentication")
        .then()
            .statusCode(401);
    }

    @Category(BasicFeature.class)
    @Test
    public void getMustReturnEndpointsWhenValidAuthorizationHeader() {
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

    @Category(BasicFeature.class)
    @Test
    public void getMustReturnEndpointsWhenValidJwtAuthorizationHeader() {
        String token = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyQGRvbWFpbi50bGQifQ.U-dUPv6OU6KO5N7CooHUfMkCd" +
            "FJHx2F3H4fm7Q79g1BPfBSkifPj5xyVlZ0JwEGXypC4zBw9ay3l4DxzX7D_6p1Hx_ihXsoLx1Ca-WUo44x-XRSpPfgxiZjHCJkGBLMV3RZlA" +
            "jip-d18mxkcX3JGplX_sCQkFisduAOAHuKSUg9wI6VBgUQi_0B35FYv6tP_bD6eFtvaAUN9QyXXh8UQjEp8CO12lRz6enfLx_V6BG_fEMkee" +
            "6vRqdEqx_F9OF3eWTe1giMp_JhQ7_l1OXXtbd4TndVvTeuVy4irPbsRc-M8x_-qTDpFp6saRRsyOcFspxPp5n3yIhEK7B3UZiseXw";

        given()
            .header("Authorization", "Bearer " + token)
        .when()
            .get("/authentication")
        .then()
            .statusCode(200);
    }

    @Test
    public void getMustReturnEndpointsWhenValidUnknownUserJwtAuthorizationHeader() {
        String token = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1bmtub3duQGRvbWFpbi50bGQifQ.hr8AhlNIpiA3Mv_A5ZLyL" +
            "f1BHeBSaRDfdR_GLV_hlPdIrWv1xtwjBH86E1YnTPx2tTpr_NWTbHcR1OCkuVCpgloEnUNbE3U2l0WrGOX2Eh9dWCXOCtrNvCeSHQuvx5_8W" +
            "nSVENYidk7o2icE8_gz_Giwf0Z3bHJJYXfAxupv__tCkmhqt3E888VZPjs26AsqxQ29YyX0Fjx8UwKbPrH5-tnyftX-kLjjZNtahVIVtbW4v" +
            "b8rEEZ4nzqxHqtI2co6yCXjgyoFMdDAKCOU-Bq35Gdo-Qiu8l7a0kQGhuhkjoaVWvw4bcSvunxAnh_0W5g3Lw-rwljNu2JrJS0gAH6NDA";
        
        given()
            .header("Authorization", "Bearer " + token)
        .when()
            .get("/authentication")
        .then()
            .statusCode(200);
    }

    @Test
    public void getMustReturnBadCredentialsWhenInvalidJwtAuthorizationHeader() {
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
    public void getMustReturnEndpointsWhenCorrectAuthentication() {
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

    @Category(BasicFeature.class)
    @Test
    public void getShouldReturn400WhenMultipleCredentials() {
        String jwtToken = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyQGRvbWFpbi50bGQifQ.U-dUPv6OU6KO5N7CooHUfMkCd" +
            "FJHx2F3H4fm7Q79g1BPfBSkifPj5xyVlZ0JwEGXypC4zBw9ay3l4DxzX7D_6p1Hx_ihXsoLx1Ca-WUo44x-XRSpPfgxiZjHCJkGBLMV3RZlA" +
            "jip-d18mxkcX3JGplX_sCQkFisduAOAHuKSUg9wI6VBgUQi_0B35FYv6tP_bD6eFtvaAUN9QyXXh8UQjEp8CO12lRz6enfLx_V6BG_fEMkee" +
            "6vRqdEqx_F9OF3eWTe1giMp_JhQ7_l1OXXtbd4TndVvTeuVy4irPbsRc-M8x_-qTDpFp6saRRsyOcFspxPp5n3yIhEK7B3UZiseXw";

        String continuationToken = fromGoodContinuationTokenRequest();
        String accessToken = fromGoodAccessTokenRequest(continuationToken);

        given()
            .header("Authorization", "Bearer " + jwtToken)
            .header("Authorization", accessToken)
        .when()
            .get("/authentication")
        .then()
            .statusCode(400);
    }

    @Test
    public void deleteMustReturnUnauthenticatedWithoutAuthorizationHeader() {
        given()
        .when()
            .delete("/authentication")
        .then()
            .statusCode(401);
    }

    @Test
    public void deleteMustReturnUnauthenticatedWithoutAValidAuthroizationHeader() {
        given()
            .header("Authorization", UUID.randomUUID())
        .when()
            .delete("/authentication")
        .then()
            .statusCode(401);
    }

    @Test
    public void deleteMustReturnOKNoContentOnValidAuthorizationToken() {
        String continuationToken = fromGoodContinuationTokenRequest();
        String token = fromGoodAccessTokenRequest(continuationToken);
        given()
            .header("Authorization", token)
        .when()
            .delete("/authentication")
        .then()
            .statusCode(204);
    }

    @Category(BasicFeature.class)
    @Test
    public void deleteMustInvalidAuthorizationOnCorrectAuthorization() {
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
