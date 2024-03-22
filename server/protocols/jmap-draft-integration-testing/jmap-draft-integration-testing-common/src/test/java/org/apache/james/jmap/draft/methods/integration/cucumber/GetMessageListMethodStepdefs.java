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

import static org.apache.james.jmap.JMAPTestingConstants.ARGUMENTS;
import static org.apache.james.jmap.JMAPTestingConstants.calmlyAwait;

import java.util.List;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Splitter;
import jakarta.inject.Inject;

import org.apache.james.mailbox.model.MessageId;

import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

@ScenarioScoped
public class GetMessageListMethodStepdefs {

    private final MainStepdefs mainStepdefs;
    private final HttpClient httpClient;
    private final MessageIdStepdefs messageIdStepdefs;
    private final UserStepdefs userStepdefs;

    @Inject
    private GetMessageListMethodStepdefs(MainStepdefs mainStepdefs, HttpClient httpClient, MessageIdStepdefs messageIdStepdefs, UserStepdefs userStepdefs) {
        this.mainStepdefs = mainStepdefs;
        this.httpClient = httpClient;
        this.messageIdStepdefs = messageIdStepdefs;
        this.userStepdefs = userStepdefs;
    }

    @When("^\"([^\"]*)\" asks for message list in mailboxes \"([^\"]*)\" with flag \"([^\"]*)\"$")
    public void getMessageListWithFlag(String username, String mailboxes, String flag) throws Exception {
        httpClient.post(String.format(
                "[[\"getMessageList\", {\"filter\":{" +
                "    \"inMailboxes\":[\"%s\"]," +
                "    \"hasKeyword\":\"%s\"" +
                "}}, \"#0\"]]",
            mainStepdefs.getMailboxIds(username, Splitter.on(',').trimResults().splitToList(mailboxes)),
            flag));
    }

    @When("^\"([^\"]*)\" asks for message list in (?:mailboxes|mailbox) \"([^\"]*)\"$")
    public void getMessageList(String username, String mailboxes) throws Exception {
        getMessageListFromMailboxIds(mainStepdefs.getMailboxIds(username, Splitter.on(',').trimResults().splitToList(mailboxes)));
    }

    @When("^\"([^\"]*)\" asks for message list in delegated (?:mailboxes|mailbox) \"([^\"]*)\" from \"([^\"]*)\"$")
    public void getMessageListFromDelegated(String sharee, String mailboxes, String sharer) throws Exception {
        userStepdefs.execWithUser(sharee, () ->
            getMessageListFromMailboxIds(mainStepdefs.getMailboxIds(sharer, Splitter.on(',').trimResults().splitToList(mailboxes))));
    }

    private void getMessageListFromMailboxIds(String mailboxIds) throws Exception {
        httpClient.post(String.format(
            "[[\"getMessageList\", {\"filter\":{" +
                "    \"inMailboxes\":[\"%s\"]" +
                "}}, \"#0\"]]",
            mailboxIds));
    }

    @When("^\"([^\"]*)\" asks for message list in mailbox \"([^\"]*)\" with flag \"([^\"]*)\"$")
    public void getMessageList(String username, String mailbox, String flag) throws Exception {

        httpClient.post(String.format(
                "[[\"getMessageList\", {\"filter\":{" +
                "    \"inMailboxes\":[\"%s\"]," +
                "    \"hasKeyword\":\"%s\"" +
                "}}, \"#0\"]]",
            mainStepdefs
                .getMailboxId(username, mailbox)
                .serialize(),
            flag));
    }

    @When("^the user asks for message list with flag \"([^\"]*)\"$")
    public void getMessageList(String flag) throws Exception {
        httpClient.post(String.format(
            "[[\"getMessageList\", {\"filter\":{" +
                "    \"hasKeyword\":\"%s\"" +
                "}}, \"#0\"]]",
            flag));
    }

    @Then("^the message list is empty$")
    public void assertEmpty() {
        calmlyAwait
            .atMost(30, TimeUnit.SECONDS)
            .until(
                () -> httpClient.response.getStatusLine().getStatusCode() == 200
                    && httpClient.jsonPath.<List<String>>read(ARGUMENTS + ".messageIds").isEmpty());
    }

    @Then("^the message list has size (\\d+)")
    public void assertSize(int size) {
        calmlyAwait
            .atMost(30, TimeUnit.SECONDS)
            .until(
                () -> httpClient.response.getStatusLine().getStatusCode() == 200
                    && httpClient.jsonPath.<List<String>>read(ARGUMENTS + ".messageIds").size() == size);
    }

    @Then("^the message list contains \"([^\"]*)\"")
    public void assertContains(String message) {
        MessageId messageId = messageIdStepdefs.getMessageId(message);
        calmlyAwait
            .atMost(30, TimeUnit.SECONDS)
            .until(
                () -> httpClient.response.getStatusLine().getStatusCode() == 200
                    && httpClient.jsonPath.<List<String>>read(ARGUMENTS + ".messageIds").contains(messageId.serialize()));
    }
}
