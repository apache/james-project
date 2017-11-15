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

import static com.jayway.jsonpath.Criteria.where;
import static com.jayway.jsonpath.Filter.filter;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import javax.inject.Inject;

import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import cucumber.runtime.java.guice.ScenarioScoped;

@ScenarioScoped
public class GetMailboxesMethodStepdefs {

    private static final String NAME = "[0][0]";
    private static final String ARGUMENTS = "[0][1]";

    private final UserStepdefs userStepdefs;
    private final HttpClient httpClient;

    @Inject
    private GetMailboxesMethodStepdefs(UserStepdefs userStepdefs, HttpClient httpClient) {
        this.userStepdefs = userStepdefs;
        this.httpClient = httpClient;
    }

    @When("^\"([^\"]*)\" lists mailboxes$")
    public void getMailboxes(String user) throws Throwable {
        userStepdefs.execWithUser(user, 
                () -> httpClient.post("[[\"getMailboxes\", {}, \"#0\"]]"));
    }

    @Then("^a mailboxes answer is returned without error$")
    public void assertGetMailboxesOkStatus() throws Exception {
        assertThat(httpClient.response.getStatusLine().getStatusCode()).isEqualTo(200);
        assertThat(httpClient.jsonPath.<String>read(NAME)).isEqualTo("mailboxes");
    }

    @Then("^the list should contain (\\d+) (?:mailbox|mailboxes)$")
    public void assertNumberOfMailboxes(int numberOfMailboxes) throws Exception {
        assertThat(httpClient.jsonPath.<List<String>>read(ARGUMENTS + ".list")).hasSize(numberOfMailboxes);
    }

    @Then("^the mailboxes should contain \"([^\"]*)\" in \"([^\"]*)\" namespace$")
    public void assertMailboxesNames(String mailboxName, String namespace) throws Exception {
        assertThat(httpClient.jsonPath.<List<String>>read(ARGUMENTS + ".list[*].name")).contains(mailboxName);
        assertThat(httpClient.jsonPath.<List<String>>read(ARGUMENTS + ".list[?].namespace.type",
                filter(where("name").is(mailboxName))))
            .containsOnly(namespace);
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
