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

import java.io.BufferedInputStream;
import java.net.URI;
import java.net.URISyntaxException;

import javax.inject.Inject;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.util.ZeroedInputStream;

import com.google.common.base.Charsets;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

import static org.assertj.core.api.Assertions.assertThat;

import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import cucumber.runtime.java.guice.ScenarioScoped;

@ScenarioScoped
public class UploadStepdefs {
    private static final String _1M_ZEROED_FILE_BLOB_ID = "3b71f43ff30f4b15b5cd85dd9e95ebc7e84eb5a3";
    private static final int _1M = 1024 * 1024;
    private static final int _10M = 10 * _1M;

    private final UserStepdefs userStepdefs;
    private final MainStepdefs mainStepdefs;
    private final URI uploadUri;
    private HttpResponse response;

    @Inject
    private UploadStepdefs(UserStepdefs userStepdefs, MainStepdefs mainStepdefs) throws URISyntaxException {
        this.userStepdefs = userStepdefs;
        this.mainStepdefs = mainStepdefs;
        uploadUri = mainStepdefs.baseUri().setPath("/upload").build();
    }

    @When("^\"([^\"]*)\" upload a content$")
    public void userUploadContent(String username) throws Throwable {
        AccessToken accessToken = userStepdefs.tokenByUser.get(username);
        Request request = Request.Post(uploadUri)
            .bodyStream(new BufferedInputStream(new ZeroedInputStream(_1M), _1M), org.apache.http.entity.ContentType.DEFAULT_BINARY);
        if (accessToken != null) {
            request.addHeader("Authorization", accessToken.serialize());
        }
        response = Executor.newInstance(HttpClientBuilder.create().disableAutomaticRetries().build()).execute(request).returnResponse();
    }

    @When("^\"([^\"]*)\" upload a content without content type$")
    public void userUploadContentWithoutContentType(String username) throws Throwable {
        AccessToken accessToken = userStepdefs.tokenByUser.get(username);
        Request request = Request.Post(uploadUri)
                .bodyByteArray("some text".getBytes(Charsets.UTF_8));
        if (accessToken != null) {
            request.addHeader("Authorization", accessToken.serialize());
        }
        response = request.execute().returnResponse();
    }

    @When("^\"([^\"]*)\" upload a too big content$")
    public void userUploadTooBigContent(String username) throws Throwable {
        AccessToken accessToken = userStepdefs.tokenByUser.get(username);
        Request request = Request.Post(uploadUri)
                .bodyStream(new BufferedInputStream(new ZeroedInputStream(_10M), _10M), org.apache.http.entity.ContentType.DEFAULT_BINARY);
        if (accessToken != null) {
            request.addHeader("Authorization", accessToken.serialize());
        }
        response = request.execute().returnResponse();
    }

    @When("^\"([^\"]*)\" checks for the availability of the upload endpoint$")
    public void optionUpload(String username) throws Throwable {
        AccessToken accessToken = userStepdefs.tokenByUser.get(username);
        Request request = Request.Options(uploadUri);
        if (accessToken != null) {
            request.addHeader("Authorization", accessToken.serialize());
        }
        response = request.execute().returnResponse();
    }

    @Then("^the user should receive an authorized response$")
    public void httpAuthorizedStatus() throws Exception {
        assertThat(response.getStatusLine().getStatusCode()).isEqualTo(200);
    }

    @Then("^the user should receive a created response$")
    public void httpCreatedStatus() throws Exception {
        assertThat(response.getStatusLine().getStatusCode()).isEqualTo(201);
    }

    @Then("^the user should receive bad request response$")
    public void httpBadRequestStatus() throws Throwable {
        assertThat(response.getStatusLine().getStatusCode()).isEqualTo(400);
    }

    @Then("^the user should receive a not authorized response$")
    public void httpUnauthorizedStatus() throws Exception {
        assertThat(response.getStatusLine().getStatusCode()).isEqualTo(401);
    }

    @Then("^the user should receive a request entity too large response$")
    public void httpRequestEntityTooBigStatus() throws Exception {
        assertThat(response.getStatusLine().getStatusCode()).isEqualTo(413);
    }

    @Then("^the user should receive a specified JSON content$")
    public void jsonResponse() throws Exception {
        assertThat(response.getHeaders("Content-Type")).extracting(Header::getValue).containsExactly(org.apache.http.entity.ContentType.APPLICATION_JSON.toString());
        DocumentContext jsonPath = JsonPath.parse(response.getEntity().getContent());
        assertThat(jsonPath.<String>read("blobId")).isEqualTo(_1M_ZEROED_FILE_BLOB_ID);
        assertThat(jsonPath.<String>read("type")).isEqualTo("application/octet-stream");
        assertThat(jsonPath.<Integer>read("size")).isEqualTo(_1M);
    }

    @Then("^\"([^\"]*)\" should be able to retrieve the content$")
    public void contentShouldBeRetrievable(String username) throws Exception {
        AccessToken accessToken = userStepdefs.tokenByUser.get(username);
        Request request = Request.Get(mainStepdefs.baseUri().setPath("/download/" + _1M_ZEROED_FILE_BLOB_ID).build());
        if (accessToken != null) {
            request.addHeader("Authorization", accessToken.serialize());
        }
        response = request.execute().returnResponse();
        httpAuthorizedStatus();
    }
}
