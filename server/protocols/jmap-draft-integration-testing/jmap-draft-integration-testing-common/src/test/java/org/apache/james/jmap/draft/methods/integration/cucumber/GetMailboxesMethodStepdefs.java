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

import static com.jayway.jsonpath.Criteria.where;
import static com.jayway.jsonpath.Filter.filter;
import static org.apache.james.jmap.JMAPTestingConstants.ARGUMENTS;
import static org.apache.james.jmap.JMAPTestingConstants.NAME;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;

import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

@ScenarioScoped
public class GetMailboxesMethodStepdefs {

    private final UserStepdefs userStepdefs;
    private final HttpClient httpClient;
    private final MainStepdefs mainStepdefs;

    @Inject
    private GetMailboxesMethodStepdefs(UserStepdefs userStepdefs, HttpClient httpClient, MainStepdefs mainStepdefs) {
        this.userStepdefs = userStepdefs;
        this.httpClient = httpClient;
        this.mainStepdefs = mainStepdefs;
    }

    @Given("^the user has a mailbox \"([^\"]*)\"$")
    public void hasMailbox(String mailbox) {
        mainStepdefs.mailboxProbe.createMailbox(MailboxPath.forUser(
            Username.of(userStepdefs.getConnectedUser()), mailbox));
    }

    @When("^\"([^\"]*)\" lists mailboxes$")
    public void getMailboxes(String user) {
        userStepdefs.execWithUser(user, 
                () -> httpClient.post("[[\"getMailboxes\", {}, \"#0\"]]"));
    }

    @Then("^a mailboxes answer is returned without error$")
    public void assertGetMailboxesOkStatus() {
        assertThat(httpClient.response.getStatusLine().getStatusCode()).isEqualTo(200);
        assertThat(httpClient.jsonPath.<String>read(NAME)).isEqualTo("mailboxes");
    }

    @Then("^the list should contain (\\d+) (?:mailbox|mailboxes)$")
    public void assertNumberOfMailboxes(int numberOfMailboxes) {
        assertThat(httpClient.jsonPath.<List<String>>read(ARGUMENTS + ".list")).hasSize(numberOfMailboxes);
    }

    @Then("^the mailboxes should contain \"([^\"]*)\" in \"([^\"]*)\" namespace$")
    public void assertMailboxesNames(String mailboxName, String namespace) throws Exception {
        assertThat(httpClient.jsonPath.<List<String>>read(ARGUMENTS + ".list[*].name")).contains(mailboxName);
        assertThat(httpClient.jsonPath.<List<String>>read(ARGUMENTS + ".list[?].namespace.type",
                filter(where("name").is(mailboxName))))
            .containsOnly(namespace);
    }

    @Then("^the mailboxes should not contain \"([^\"]*)\" in \"([^\"]*)\" namespace$")
    public void assertNotMailboxesNames(String mailboxName, String namespace) throws Exception {
        assertThat(httpClient.jsonPath.<List<String>>read(ARGUMENTS + ".list[?].namespace.type",
            filter(where("name").is(mailboxName))))
            .doesNotContain(namespace);
    }

    @Then("^the mailboxes should contain \"([^\"]*)\" in \"([^\"]*)\" namespace, with parent mailbox \"([^\"]*)\" of user \"([^\"]*)\"$")
    public void assertMailboxesNames(String mailboxName, String namespace, String parentName, String parentOwner) throws Exception {
        MailboxId parentMailbox = mainStepdefs.mailboxProbe.getMailboxId(MailboxConstants.USER_NAMESPACE, parentOwner, parentName);
        assertThat(httpClient.jsonPath.<List<String>>read(ARGUMENTS + ".list[*].name")).contains(mailboxName);
        assertThat(httpClient.jsonPath.<List<String>>read(ARGUMENTS + ".list[?].namespace.type",
                filter(where("name").is(mailboxName))))
            .containsOnly(namespace);
        assertThat(httpClient.jsonPath.<List<String>>read(ARGUMENTS + ".list[?].parentId",
                filter(where("name").is(mailboxName))))
            .containsOnly(parentMailbox.serialize());
    }

    @Then("^the mailboxes should not contain \"([^\"]*)\" in \"([^\"]*)\" namespace, with parent mailbox \"([^\"]*)\" of user \"([^\"]*)\"$")
    public void assertNotMailboxesNames(String mailboxName, String namespace, String parentName, String parentOwner) throws Exception {
        MailboxId parentMailbox = mainStepdefs.mailboxProbe.getMailboxId(MailboxConstants.USER_NAMESPACE, parentOwner, parentName);
        assertThat(httpClient.jsonPath.<List<String>>read(ARGUMENTS + ".list[?].parentId",
            filter(where("name").is(mailboxName).and("namespace.type").is(namespace))))
            .doesNotContain(parentMailbox.serialize());
    }

    @Then("^the mailboxes should contain \"([^\"]*)\" in \"([^\"]*)\" namespace, with no parent mailbox$")
    public void assertMailboxesNamesNoParent(String mailboxName, String namespace) throws Exception {
        ArrayList<Object> noParent = new ArrayList<>();
        noParent.add(null); // Trick to allow collection with null element matching

        assertThat(httpClient.jsonPath.<List<String>>read(ARGUMENTS + ".list[*].name")).contains(mailboxName);
        assertThat(httpClient.jsonPath.<List<String>>read(ARGUMENTS + ".list[?].namespace.type",
                filter(where("name").is(mailboxName))))
            .containsOnly(namespace);
        assertThat(httpClient.jsonPath.<List<String>>read(ARGUMENTS + ".list[?].parentId",
                filter(where("name").is(mailboxName))))
            .isEqualTo(noParent);
    }

    @Then("^the mailboxes should not contain \"([^\"]*)\"$")
    public void assertNoMailboxesNames(String mailboxName) throws Exception {
        assertThat(httpClient.jsonPath.<List<String>>read(ARGUMENTS + ".list[*].name")).doesNotContain(mailboxName);
    }

    @Then("^the mailbox \"([^\"]*)\" has (\\d+) (?:messages|message)$")
    public void assertNumberOfMessages(String mailboxName, int numberOfMessages) throws Exception {
        assertThat(httpClient.jsonPath.<List<Integer>>read(ARGUMENTS + ".list[?].totalMessages",
                filter(where("name").is(mailboxName))))
            .containsOnly(numberOfMessages);
    }

    @Then("^the mailbox \"([^\"]*)\" has (\\d+) unseen (?:messages|message)$")
    public void assertNumberOfUnseenMessages(String mailboxName, int numberOfUnseenMessages) throws Exception {
        assertThat(httpClient.jsonPath.<List<Integer>>read(ARGUMENTS + ".list[?].unreadMessages",
                filter(where("name").is(mailboxName))))
            .containsOnly(numberOfUnseenMessages);
    }
}
