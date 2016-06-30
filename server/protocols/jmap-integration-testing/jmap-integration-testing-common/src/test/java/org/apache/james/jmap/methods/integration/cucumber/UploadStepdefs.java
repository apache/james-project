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

import javax.inject.Inject;

import org.apache.james.jmap.api.access.AccessToken;

import com.jayway.restassured.response.Response;
import com.jayway.restassured.specification.RequestSpecification;

import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import cucumber.runtime.java.guice.ScenarioScoped;

@ScenarioScoped
public class UploadStepdefs {

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

    @Then("^the user should receive a not authorized response$")
    public void httpUnauthorizedStatus() throws Exception {
        response.then()
            .statusCode(401);
    }
}
