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

import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.modules.MailboxProbeImpl;

import com.github.steveash.guavate.Guavate;
import com.google.common.base.Joiner;

import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import cucumber.runtime.java.guice.ScenarioScoped;

@ScenarioScoped
public class GetMessageListMethodStepdefs {

    private static final String ARGUMENTS = "[0][1]";
    private final MainStepdefs mainStepdefs;
    private final HttpClient httpClient;
    private final MessageIdStepdefs messageIdStepdefs;

    @Inject
    private GetMessageListMethodStepdefs(MainStepdefs mainStepdefs, HttpClient httpClient, MessageIdStepdefs messageIdStepdefs) {
        this.mainStepdefs = mainStepdefs;
        this.httpClient = httpClient;
        this.messageIdStepdefs = messageIdStepdefs;
    }

    @When("^\"([^\"]*)\" asks for message list in mailboxes \"([^\"]*)\" with flag \"([^\"]*)\"$")
    public void getMessageList(String username, List<String> mailboxes, String flag) throws Exception {
        String mailboxIds = Joiner.on("\",\"")
            .join(mailboxes.stream()
                .map(mailbox -> mainStepdefs.jmapServer
                    .getProbe(MailboxProbeImpl.class)
                    .getMailbox(MailboxConstants.USER_NAMESPACE, username, mailbox)
                    .getMailboxId()
                    .serialize())
                .collect(Guavate.toImmutableList()));

        httpClient.post(String.format(
                "[[\"getMessageList\", {\"filter\":{" +
                "    \"inMailboxes\":[\"%s\"]," +
                "    \"hasKeyword\":\"%s\"" +
                "}}, \"#0\"]]",
            mailboxIds,
            flag));
    }

    @When("^\"([^\"]*)\" asks for message list in mailbox \"([^\"]*)\" with flag \"([^\"]*)\"$")
    public void getMessageList(String username, String mailbox, String flag) throws Exception {
        MailboxId mailboxId = mainStepdefs.jmapServer
            .getProbe(MailboxProbeImpl.class)
            .getMailbox(MailboxConstants.USER_NAMESPACE, username, mailbox)
            .getMailboxId();

        httpClient.post(String.format(
                "[[\"getMessageList\", {\"filter\":{" +
                "    \"inMailboxes\":[\"%s\"]," +
                "    \"hasKeyword\":\"%s\"" +
                "}}, \"#0\"]]",
            mailboxId.serialize(),
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
    public void assertEmpty() throws Exception {
        assertThat(httpClient.response.getStatusLine().getStatusCode()).isEqualTo(200);
        assertThat(httpClient.jsonPath.<List<String>>read(ARGUMENTS + ".messageIds")).isEmpty();
    }

    @Then("^the message list has size (\\d+)")
    public void assertSize(int size) throws Exception {
        assertThat(httpClient.response.getStatusLine().getStatusCode()).isEqualTo(200);
        assertThat(httpClient.jsonPath.<List<String>>read(ARGUMENTS + ".messageIds")).hasSize(size);
    }

    @Then("^the message list contains \"([^\"]*)\"")
    public void assertContains(String messsage) throws Exception {
        MessageId messageId = messageIdStepdefs.getMessageId(messsage);
        assertThat(httpClient.response.getStatusLine().getStatusCode()).isEqualTo(200);
        assertThat(httpClient.jsonPath.<List<String>>read(ARGUMENTS + ".messageIds")).contains(messageId.serialize());
    }
}
