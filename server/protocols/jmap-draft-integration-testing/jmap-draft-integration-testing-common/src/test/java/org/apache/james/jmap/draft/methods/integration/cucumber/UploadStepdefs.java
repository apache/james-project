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

package org.apache.james.jmap.draft.methods.integration.cucumber;

import static org.apache.james.jmap.JmapURIBuilder.baseUri;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedInputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import javax.inject.Inject;

import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Async;
import org.apache.http.client.fluent.Content;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.james.jmap.AccessToken;
import org.apache.james.util.io.CountDownConsumeInputStream;
import org.apache.james.util.io.ZeroedInputStream;

import com.google.common.base.CharMatcher;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

@ScenarioScoped
public class UploadStepdefs {
    private static final int _24K = 24 * 1024;
    private static final int _1M = 1024 * 1024;
    private static final int _10M = 10 * _1M;

    private final UserStepdefs userStepdefs;
    private final MainStepdefs mainStepdefs;
    private final URI uploadUri;
    private HttpResponse response;
    private Future<Content> async;
    private boolean isCanceled;

    @Inject
    private UploadStepdefs(UserStepdefs userStepdefs, MainStepdefs mainStepdefs) throws URISyntaxException {
        this.userStepdefs = userStepdefs;
        this.mainStepdefs = mainStepdefs;
        uploadUri = baseUri(mainStepdefs.jmapServer).setPath("/upload").build();
    }

    @Given("^\"([^\"]*)\" is starting uploading a content$")
    public void userStartUploadContent(String username) {
        AccessToken accessToken = userStepdefs.authenticate(username);

        CountDownLatch startSignal = new CountDownLatch(2);
        CountDownConsumeInputStream bodyStream = new CountDownConsumeInputStream(startSignal);
        Request request = Request.Post(uploadUri)
            .bodyStream(new BufferedInputStream(bodyStream, _1M), org.apache.http.entity.ContentType.DEFAULT_BINARY);
        if (accessToken != null) {
            request.addHeader("Authorization", accessToken.asString());
        }
        async = Async.newInstance().execute(request, new FutureCallback<Content>() {
            
            @Override
            public void failed(Exception ex) {
            }
            
            @Override
            public void completed(Content result) {
            }
            
            @Override
            public void cancelled() {
                bodyStream.getStartSignal().countDown();
                if (bodyStream.getStartSignal().getCount() == 1) {
                    isCanceled = true;
                }
            }
        });
    }

    @When("^\"([^\"]*)\" upload a content$")
    public void userUploadContent(String username) throws Throwable {
        AccessToken accessToken = userStepdefs.authenticate(username);
        Request request = Request.Post(uploadUri)
            .bodyStream(new BufferedInputStream(new ZeroedInputStream(_1M), _1M), org.apache.http.entity.ContentType.DEFAULT_BINARY);

        request.addHeader("Authorization", accessToken.asString());
        response = Executor.newInstance(newClient())
            .execute(request)
            .returnResponse();
    }

    @When("^someone upload a content without authentification$")
    public void userUploadContentWithoutAuthentification() throws Throwable {
        Request request = Request.Post(uploadUri)
            .bodyStream(new BufferedInputStream(new ZeroedInputStream(_24K), _24K), org.apache.http.entity.ContentType.DEFAULT_BINARY);
        response = Executor.newInstance(newClient())
            .execute(request)
            .returnResponse();
    }

    private CloseableHttpClient newClient() {
        return HttpClientBuilder.create()
            .disableAutomaticRetries()
            .build();
    }

    @When("^\"([^\"]*)\" upload a content without content type$")
    public void userUploadContentWithoutContentType(String username) throws Throwable {
        AccessToken accessToken = userStepdefs.authenticate(username);
        Request request = Request.Post(uploadUri)
                .bodyByteArray("some text".getBytes(StandardCharsets.UTF_8));
        if (accessToken != null) {
            request.addHeader("Authorization", accessToken.asString());
        }
        response = request.execute().returnResponse();
    }

    @When("^\"([^\"]*)\" upload a too big content$")
    public void userUploadTooBigContent(String username) throws Throwable {
        AccessToken accessToken = userStepdefs.authenticate(username);
        Request request = Request.Post(uploadUri)
                .bodyStream(new BufferedInputStream(new ZeroedInputStream(_10M), _10M), org.apache.http.entity.ContentType.DEFAULT_BINARY);
        if (accessToken != null) {
            request.addHeader("Authorization", accessToken.asString());
        }
        response = request.execute().returnResponse();
    }

    @When("^\"([^\"]*)\" checks for the availability of the upload endpoint$")
    public void optionUpload(String username) throws Throwable {
        userStepdefs.authenticate(username);
        Request request = Request.Options(uploadUri);
        response = request.execute().returnResponse();
    }

    @When("^someone checks without authentification for the availability of the upload endpoint$")
    public void optionUploadWithoutAuthentification() throws Throwable {
        Request request = Request.Options(uploadUri);
        response = request.execute().returnResponse();
    }

    @Then("^the user disconnect$")
    public void stopUpload() {
        async.cancel(true);
    }

    @Then("^the user should receive an authorized response$")
    public void httpAuthorizedStatus() {
        assertThat(response.getStatusLine().getStatusCode()).isEqualTo(200);
    }

    @Then("^the user should receive a created response$")
    public void httpCreatedStatus() {
        assertThat(response.getStatusLine().getStatusCode()).isEqualTo(201);
    }

    @Then("^the user should receive bad request response$")
    public void httpBadRequestStatus() {
        assertThat(response.getStatusLine().getStatusCode()).isEqualTo(400);
    }

    @Then("^the user should receive a not authorized response$")
    public void httpUnauthorizedStatus() {
        assertThat(response.getStatusLine().getStatusCode()).isEqualTo(401);
    }

    @Then("^the user should receive a request entity too large response$")
    public void httpRequestEntityTooBigStatus() {
        assertThat(response.getStatusLine().getStatusCode()).isEqualTo(413);
    }

    @Then("^the user should receive a specified JSON content$")
    public void jsonResponse() throws Exception {
        assertThat(response.getHeaders("Content-Type"))
            .extracting(header ->
                normalizeContentType(header.getValue()))
            .containsExactly(
                normalizeContentType(org.apache.http.entity.ContentType.APPLICATION_JSON.toString()));
        DocumentContext jsonPath = JsonPath.parse(response.getEntity().getContent());
        jsonPath.<String>read("blobId");
        assertThat(jsonPath.<String>read("type")).isEqualTo("application/octet-stream");
        assertThat(jsonPath.<Integer>read("size")).isEqualTo(_1M);
    }

    private String normalizeContentType(String input) {
        return CharMatcher.whitespace().removeFrom(input.toLowerCase(Locale.US));
    }

    @Then("^\"([^\"]*)\" should be able to retrieve the content$")
    public void contentShouldBeRetrievable(String username) throws Exception {
        AccessToken accessToken = userStepdefs.authenticate(username);
        DocumentContext jsonPath = JsonPath.parse(response.getEntity().getContent());
        Request request = Request.Get(baseUri(mainStepdefs.jmapServer).setPath("/download/" + jsonPath.<String>read("blobId")).build());
        if (accessToken != null) {
            request.addHeader("Authorization", accessToken.asString());
        }
        response = request.execute().returnResponse();
        httpAuthorizedStatus();
    }

    @Then("^the request should be marked as canceled$")
    public void requestHasBeenCanceled() {
        assertThat(isCanceled).isTrue();
    }
}
