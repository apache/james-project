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
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.mail.Flags;

import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.response.Response;
import com.jayway.restassured.specification.RequestSpecification;

import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import cucumber.runtime.java.guice.ScenarioScoped;

@ScenarioScoped
public class DownloadStepdefs {

    private final UserStepdefs userStepdefs;
    private final MainStepdefs mainStepdefs;
    private Response response;
    private Multimap<String, String> attachmentsByMessageId;
    private Map<String, String> blobIdByAttachmentId;

    @Inject
    private DownloadStepdefs(MainStepdefs mainStepdefs, UserStepdefs userStepdefs) {
        this.mainStepdefs = mainStepdefs;
        this.userStepdefs = userStepdefs;
        this.attachmentsByMessageId = ArrayListMultimap.create();
        this.blobIdByAttachmentId = new HashMap<>();
    }

    @Given("^\"([^\"]*)\" mailbox \"([^\"]*)\" contains a message \"([^\"]*)\" with an attachment \"([^\"]*)\"$")
    public void appendMessageWithAttachmentToMailbox(String user, String mailbox, String messageId, String attachmentId) throws Throwable {
        MailboxPath mailboxPath = new MailboxPath(MailboxConstants.USER_NAMESPACE, user, mailbox);

        mainStepdefs.jmapServer.serverProbe().appendMessage(user, mailboxPath,
                ClassLoader.getSystemResourceAsStream("eml/oneAttachment.eml"), new Date(), false, new Flags());
        
        attachmentsByMessageId.put(messageId, attachmentId);
        blobIdByAttachmentId.put(attachmentId, "4000c5145f633410b80be368c44e1c394bff9437");
    }

    @When("^\"([^\"]*)\" checks for the availability of the attachment endpoint$")
    public void optionDownload(String username) throws Throwable {
        AccessToken accessToken = userStepdefs.tokenByUser.get(username);
        RequestSpecification with = with();
        if (accessToken != null) {
            with.header("Authorization", accessToken.serialize());
        }

        response = with.options("/download/myBlob");
    }

    @When("^\"([^\"]*)\" downloads \"([^\"]*)\"$")
    public void downloads(String username, String attachmentId) throws Throwable {
        String blobId = blobIdByAttachmentId.get(attachmentId);
        AccessToken accessToken = userStepdefs.tokenByUser.get(username);
        RequestSpecification with = with();
        if (accessToken != null) {
            with.header("Authorization", accessToken.serialize());
        }
        response = with.get("/download/" + blobId);
    }
    

    @When("^\"([^\"]*)\" asks for an attachment without blobId parameter$")
    public void getDownloadWithoutBlobId(String username) throws Throwable {
        AccessToken accessToken = userStepdefs.tokenByUser.get(username);
        response = with()
            .header("Authorization", accessToken.serialize())
            .get("/download/");
    }
    

    @When("^\"([^\"]*)\" asks for an attachment with wrong blobId$")
    public void getDownloadWithWrongBlobId(String username) throws Throwable {
        AccessToken accessToken = userStepdefs.tokenByUser.get(username);
        response = with()
                .header("Authorization", accessToken.serialize())
                .get("/download/badbadbadbadbadbadbadbadbadbadbadbadbadb");
    }

    @When("^\"([^\"]*)\" asks for a token for attachment \"([^\"]*)\"$")
    public void postDownload(String username, String attachmentId) throws Throwable {
        String blobId = blobIdByAttachmentId.get(attachmentId);
        AccessToken accessToken = userStepdefs.tokenByUser.get(username);
        RequestSpecification with = with();
        if (accessToken != null) {
            with = with.header("Authorization", accessToken.serialize());
        }
        response = with
                .post("/download/" + blobId);
    }

    @Then("^the user should be authorized$")
    public void httpStatusDifferentFromUnauthorized() throws Exception {
        response.then()
            .statusCode(not(401));
    }

    @Then("^the user should not be authorized$")
    public void httpUnauthorizedStatus() throws Exception {
        response.then()
            .statusCode(401);
    }

    @Then("^the user should receive a bad request response$")
    public void httpBadRequestStatus() throws Throwable {
        response.then()
            .statusCode(400);
    }

    @Then("^the user should receive that attachment$")
    public void httpOkStatusAndExpectedContent() throws Throwable {
        response.then()
            .statusCode(200)
            .content(notNullValue());
    }

    @Then("^the user should receive a not found response$")
    public void httpNotFoundStatus() throws Throwable {
        response.then()
            .statusCode(404);
    }

    @Then("^the user should receive an attachment access token$")
    public void accessTokenResponse() throws Throwable {
        response.then()
            .statusCode(200)
            .contentType(ContentType.TEXT)
            .content(notNullValue());
    }
}
