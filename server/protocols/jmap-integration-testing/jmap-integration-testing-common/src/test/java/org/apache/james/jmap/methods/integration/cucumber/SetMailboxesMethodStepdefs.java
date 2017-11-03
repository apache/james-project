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

import java.io.ByteArrayInputStream;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.inject.Inject;
import javax.mail.Flags;

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.UnsupportedRightException;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.mail.model.Mailbox;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.Maps;
import com.jayway.awaitility.Awaitility;
import com.jayway.awaitility.Duration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import cucumber.runtime.java.guice.ScenarioScoped;

@ScenarioScoped
public class SetMailboxesMethodStepdefs {

    private static final String NAME = "[0][0]";
    private static final String ARGUMENTS = "[0][1]";

    private final MainStepdefs mainStepdefs;
    private final UserStepdefs userStepdefs;
    private final HttpClient httpClient;

    @Inject
    private SetMailboxesMethodStepdefs(MainStepdefs mainStepdefs, UserStepdefs userStepdefs, HttpClient httpClient) {
        this.mainStepdefs = mainStepdefs;
        this.userStepdefs = userStepdefs;
        this.httpClient = httpClient;
    }

    @Given("^mailbox \"([^\"]*)\" with (\\d+) messages$")
    public void mailboxWithMessages(String mailboxName, int messageCount) throws Throwable {
        mainStepdefs.mailboxProbe.createMailbox("#private", userStepdefs.getConnectedUser(), mailboxName);
        MailboxPath mailboxPath = MailboxPath.forUser(userStepdefs.getConnectedUser(), mailboxName);
        IntStream
            .range(0, messageCount)
            .forEach(Throwing.intConsumer(i -> appendMessage(mailboxPath, i)));
        mainStepdefs.awaitMethod.run();
    }

    private void appendMessage(MailboxPath mailboxPath, int i) throws MailboxException {
        String content = "Subject: test" + i + "\r\n\r\n"
                + "testBody" + i;
        mainStepdefs.mailboxProbe.appendMessage(userStepdefs.getConnectedUser(), mailboxPath,
                new ByteArrayInputStream(content.getBytes()), new Date(), false, new Flags());
    }

    @Given("^\"([^\"]*)\" has a mailbox \"([^\"]*)\"$")
    public void createMailbox(String username, String mailbox) throws Throwable {
        mainStepdefs.mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, mailbox);
    }

    @Given("^\"([^\"]*)\" shares its mailbox \"([^\"]*)\" with rights \"([^\"]*)\" with \"([^\"]*)\"$")
    public void shareMailboxWithRight(String owner, String mailboxName, String rights, String shareTo) throws Throwable {
        userStepdefs.connectUser(owner);
        Mailbox mailbox = mainStepdefs.mailboxProbe.getMailbox("#private", owner, mailboxName);
        String mailboxId = mailbox.getMailboxId().serialize();
        String requestBody =
                "[" +
                    "  [ \"setMailboxes\"," +
                    "    {" +
                    "      \"update\": {" +
                    "        \"" + mailboxId + "\" : {" +
                    "          \"sharedWith\" : { \"" + shareTo + "\" : " + rightsAsString(rights) + " }" +
                    "        }" +
                    "      }" +
                    "    }," +
                    "    \"#0\"" +
                    "  ]" +
                    "]";
        httpClient.post(requestBody);
    }
    
    private String rightsAsString(String rights) throws UnsupportedRightException {
        return MailboxACL.Rfc4314Rights
            .fromSerializedRfc4314Rights(rights)
            .list()
            .stream()
            .map(MailboxACL.Right::asCharacter)
            .map(String::valueOf)
            .map(this::surroundWithDoubleQuotes)
            .collect(Collectors.joining(", ", "[ ", " ]"));
    }
    
    private String surroundWithDoubleQuotes(String input) {
        return "\"" + input + "\"";
    }
    
    @Given("^\"([^\"]*)\" shares (?:his|her) mailbox \"([^\"]*)\" with \"([^\"]*)\" with \"([^\"]*)\" rights$")
    public void shareMailbox(String owner, String mailboxName, String shareTo, String rights) throws Throwable {
        shareMailboxWithRight(owner, mailboxName, rights, shareTo);
    }

    @When("^renaming mailbox \"([^\"]*)\" to \"([^\"]*)\"")
    public void renamingMailbox(String actualMailboxName, String newMailboxName) throws Throwable {
        Mailbox mailbox = mainStepdefs.mailboxProbe.getMailbox("#private", userStepdefs.getConnectedUser(), actualMailboxName);
        String mailboxId = mailbox.getMailboxId().serialize();
        String requestBody =
                "[" +
                    "  [ \"setMailboxes\"," +
                    "    {" +
                    "      \"update\": {" +
                    "        \"" + mailboxId + "\" : {" +
                    "          \"name\" : \"" + newMailboxName + "\"" +
                    "        }" +
                    "      }" +
                    "    }," +
                    "    \"#0\"" +
                    "  ]" +
                    "]";
        httpClient.post(requestBody);
    }

    @When("^moving mailbox \"([^\"]*)\" to \"([^\"]*)\"$")
    public void movingMailbox(String actualMailboxPath, String newParentMailboxPath) throws Throwable {
        String username = userStepdefs.getConnectedUser();
        Mailbox mailbox = mainStepdefs.mailboxProbe.getMailbox("#private", username, actualMailboxPath);
        String mailboxId = mailbox.getMailboxId().serialize();
        Mailbox parent = mainStepdefs.mailboxProbe.getMailbox("#private", username, newParentMailboxPath);
        String parentId = parent.getMailboxId().serialize();

        String requestBody =
                "[" +
                    "  [ \"setMailboxes\"," +
                    "    {" +
                    "      \"update\": {" +
                    "        \"" + mailboxId + "\" : {" +
                    "          \"parentId\" : \"" + parentId + "\"" +
                    "        }" +
                    "      }" +
                    "    }," +
                    "    \"#0\"" +
                    "  ]" +
                    "]";
        httpClient.post(requestBody);
    }

    @Then("^mailbox \"([^\"]*)\" contains (\\d+) messages$")
    public void mailboxContainsMessages(String mailboxName, int messageCount) throws Throwable {
        Duration slowPacedPollInterval = Duration.FIVE_HUNDRED_MILLISECONDS;
        String username = userStepdefs.getConnectedUser();
        Mailbox mailbox = mainStepdefs.mailboxProbe.getMailbox("#private", username, mailboxName);
        String mailboxId = mailbox.getMailboxId().serialize();

        Awaitility.await().atMost(Duration.FIVE_SECONDS).pollDelay(slowPacedPollInterval).pollInterval(slowPacedPollInterval).until(() -> {
            String requestBody = "[[\"getMessageList\", {\"filter\":{\"inMailboxes\":[\"" + mailboxId + "\"]}}, \"#0\"]]";

            httpClient.post(requestBody);

            assertThat(httpClient.response.getStatusLine().getStatusCode()).isEqualTo(200);
            DocumentContext jsonPath = JsonPath.parse(httpClient.response.getEntity().getContent());
            assertThat(jsonPath.<String>read(NAME)).isEqualTo("messageList");

            return jsonPath.<List<String>>read(ARGUMENTS + ".messageIds").size() == messageCount;
        });
    }

    @Then("^\"([^\"]*)\" receives not updated on mailbox \"([^\"]*)\" with kind \"([^\"]*)\" and message \"([^\"]*)\"$")
    public void assertNotUpdatedWithGivenProperties(String userName, String mailboxName, String type, String message) throws Exception {
        Mailbox mailbox = mainStepdefs.mailboxProbe.getMailbox("#private", userName, mailboxName);
        assertThat(httpClient.response.getStatusLine().getStatusCode()).isEqualTo(200);
        assertThat(httpClient.jsonPath.<String>read(NAME)).isEqualTo("mailboxesSet");
        
        Map<String, Map<String, String>> notUpdated = httpClient.jsonPath.<Map<String, Map<String, String>>>read(ARGUMENTS + ".notUpdated");
        assertThat(notUpdated).hasSize(1);
        Map<String, String> parameters = notUpdated.get(mailbox.getMailboxId().serialize());
        assertThat(parameters).contains(Maps.immutableEntry("type", type),
                Maps.immutableEntry("description", message));
    }
}
