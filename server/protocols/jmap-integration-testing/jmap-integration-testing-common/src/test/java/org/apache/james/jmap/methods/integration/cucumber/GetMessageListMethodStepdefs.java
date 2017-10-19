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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import javax.inject.Inject;

import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.modules.MailboxProbeImpl;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;

import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import cucumber.runtime.java.guice.ScenarioScoped;

@ScenarioScoped
public class GetMessageListMethodStepdefs {

    private static final String ARGUMENTS = "[0][1]";
    private final MainStepdefs mainStepdefs;
    private final UserStepdefs userStepdefs;
    private final GetMessagesMethodStepdefs messagesMethodStepdefs;

    private HttpResponse response;
    private DocumentContext jsonPath;

    @Inject
    private GetMessageListMethodStepdefs(MainStepdefs mainStepdefs, UserStepdefs userStepdefs, GetMessagesMethodStepdefs messagesMethodStepdefs) {
        this.mainStepdefs = mainStepdefs;
        this.userStepdefs = userStepdefs;
        this.messagesMethodStepdefs = messagesMethodStepdefs;
    }

    @When("^the user asks for message list in mailbox \"([^\"]*)\" with flag \"([^\"]*)\"$")
    public void getMessageList(String mailbox, String flag) throws Exception {
        MailboxId mailboxId = mainStepdefs.jmapServer
            .getProbe(MailboxProbeImpl.class)
            .getMailbox(MailboxConstants.USER_NAMESPACE, userStepdefs.getConnectedUser(), mailbox)
            .getMailboxId();

        post(String.format(
                "[[\"getMessageList\", {\"filter\":{" +
                "    \"inMailboxes\":[\"%s\"]," +
                "    \"hasKeyword\":\"%s\"" +
                "}}, \"#0\"]]",
            mailboxId.serialize(),
            flag));
    }

    @When("^the user asks for message list with flag \"([^\"]*)\"$")
    public void getMessageList(String flag) throws Exception {
        post(String.format(
            "[[\"getMessageList\", {\"filter\":{" +
                "    \"hasKeyword\":\"%s\"" +
                "}}, \"#0\"]]",
            flag));
    }

    @Then("^the message list is empty$")
    public void assertEmpty() throws Exception {
        assertThat(response.getStatusLine().getStatusCode()).isEqualTo(200);
        assertThat(jsonPath.<List<String>>read(ARGUMENTS + ".messageIds")).isEmpty();
    }

    @Then("^the message list has size (\\d+)")
    public void assertSize(int size) throws Exception {
        assertThat(response.getStatusLine().getStatusCode()).isEqualTo(200);
        assertThat(jsonPath.<List<String>>read(ARGUMENTS + ".messageIds")).hasSize(size);
    }

    @Then("^the message list contains \"([^\"]*)\"")
    public void assertContains(String messsage) throws Exception {
        MessageId messageId = messagesMethodStepdefs.getMessageId(messsage);
        assertThat(response.getStatusLine().getStatusCode()).isEqualTo(200);
        assertThat(jsonPath.<List<String>>read(ARGUMENTS + ".messageIds")).contains(messageId.serialize());
    }

    private void post(String requestBody) throws Exception {
        response = Request.Post(mainStepdefs.baseUri().setPath("/jmap").build())
            .addHeader("Authorization", userStepdefs.tokenByUser.get(userStepdefs.getConnectedUser()).serialize())
            .addHeader("Accept", org.apache.http.entity.ContentType.APPLICATION_JSON.getMimeType())
            .bodyString(requestBody, org.apache.http.entity.ContentType.APPLICATION_JSON)
            .execute()
            .returnResponse();
        jsonPath = JsonPath.using(Configuration.defaultConfiguration()
            .addOptions(Option.SUPPRESS_EXCEPTIONS))
            .parse(response.getEntity().getContent());
    }
}
