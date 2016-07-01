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

package org.apache.james.jmap.methods.integration.cucumber;

import static com.jayway.restassured.RestAssured.with;
import static com.jayway.restassured.config.RestAssuredConfig.newConfig;
import static org.hamcrest.Matchers.equalTo;

import java.io.BufferedInputStream;
import java.io.InputStream;

import javax.inject.Inject;

import org.apache.james.jmap.api.access.AccessToken;

import com.google.common.base.Charsets;
import com.jayway.restassured.config.EncoderConfig;
import com.jayway.restassured.config.RestAssuredConfig;
import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.response.Response;
import com.jayway.restassured.specification.RequestSpecification;

import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import cucumber.runtime.java.guice.ScenarioScoped;

@ScenarioScoped
public class UploadStepdefs {
    private static final RestAssuredConfig NO_CHARSET = newConfig().encoderConfig(EncoderConfig.encoderConfig().appendDefaultContentCharsetToContentTypeIfUndefined(false));
    private static final String _1M_ZEROED_FILE_BLOB_ID = "3b71f43ff30f4b15b5cd85dd9e95ebc7e84eb5a3";
    private static final int _1M = 1024 * 1024;
    private static final int _10M = 10 * _1M;

    private final UserStepdefs userStepdefs;
    private Response response;

    @Inject
    private UploadStepdefs(UserStepdefs userStepdefs) {
        this.userStepdefs = userStepdefs;
    }

    @When("^\"([^\"]*)\" upload a content$")
    public void userUploadContent(String username) throws Throwable {
        AccessToken accessToken = userStepdefs.tokenByUser.get(username);
        RequestSpecification with = with();
        if (accessToken != null) {
            with.header("Authorization", accessToken.serialize());
        }
        response = with
            .config(NO_CHARSET)
            .contentType(ContentType.BINARY)
            .content(new BufferedInputStream(new ZeroedInputStream(_1M), _1M))
            .post("/upload");
    }

    @When("^\"([^\"]*)\" upload a content without content type$")
    public void userUploadContentWithoutContentType(String username) throws Throwable {
        AccessToken accessToken = userStepdefs.tokenByUser.get(username);
        RequestSpecification with = with();
        if (accessToken != null) {
            with.header("Authorization", accessToken.serialize());
        }
        response = with
            .config(NO_CHARSET)
            .contentType("")
            .content("some text".getBytes(Charsets.UTF_8))
            .post("/upload");
    }

    @When("^\"([^\"]*)\" upload a too big content$")
    public void userUploadTooBigContent(String username) throws Throwable {
        AccessToken accessToken = userStepdefs.tokenByUser.get(username);
        RequestSpecification with = with();
        if (accessToken != null) {
            with.header("Authorization", accessToken.serialize());
        }
        response = with
            .contentType(ContentType.BINARY)
            .content(new BufferedInputStream(new ZeroedInputStream(_10M), _10M))
            .post("/upload");
    }

    @When("^\"([^\"]*)\" checks for the availability of the upload endpoint$")
    public void optionUpload(String username) throws Throwable {
        AccessToken accessToken = userStepdefs.tokenByUser.get(username);
        RequestSpecification with = with();
        if (accessToken != null) {
            with.header("Authorization", accessToken.serialize());
        }
        response = with
            .options("/upload");
    }

    @Then("^the user should receive an authorized response$")
    public void httpAuthorizedStatus() throws Exception {
        response.then()
            .statusCode(200);
    }

    @Then("^the user should receive a created response$")
    public void httpCreatedStatus() throws Exception {
        response.then()
            .statusCode(201);
    }

    @Then("^the user should receive bad request response$")
    public void httpBadRequestStatus() throws Throwable {
        response.then()
            .statusCode(400);
    }

    @Then("^the user should receive a not authorized response$")
    public void httpUnauthorizedStatus() throws Exception {
        response.then()
            .statusCode(401);
    }

    @Then("^the user should receive a request entity too large response$")
    public void httpRequestEntityTooBigStatus() throws Exception {
        response.then()
            .statusCode(413);
    }

    @Then("^the user should receive a specified JSON content$")
    public void jsonResponse() throws Exception {
        response.then()
            .contentType(ContentType.JSON)
            .body("blobId", equalTo(_1M_ZEROED_FILE_BLOB_ID))
            .body("type", equalTo("application/octet-stream"))
            .body("size", equalTo(_1M));
    }

    @Then("^\"([^\"]*)\" should be able to retrieve the content$")
    public void contentShouldBeRetrievable(String username) throws Exception {
        AccessToken accessToken = userStepdefs.tokenByUser.get(username);
        RequestSpecification with = with();
        if (accessToken != null) {
            with.header("Authorization", accessToken.serialize());
        }
        with
            .get("/download/" + _1M_ZEROED_FILE_BLOB_ID)
        .then()
            .statusCode(200);
    }

    public static class ZeroedInputStream extends InputStream {
        public static final int RETURNED_VALUE = 0;

        private final int max;
        private int pos;

        public ZeroedInputStream(int max) {
            this.max = max;
            this.pos = 0;
        }

        @Override
        public int read() {
            if (pos < max) {
                pos++;
                return RETURNED_VALUE;
            }
            return -1;
        }
    }
}
