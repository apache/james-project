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
import static org.apache.james.jmap.JMAPTestingConstants.NAME;
import static org.apache.james.jmap.JMAPTestingConstants.calmlyAwait;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.UnsupportedRightException;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mime4j.dom.Message;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.Maps;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

@ScenarioScoped
public class SetMailboxesMethodStepdefs {

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
    public void mailboxWithMessages(String mailboxName, int messageCount) {
        mainStepdefs.mailboxProbe.createMailbox("#private", userStepdefs.getConnectedUser(), mailboxName);
        MailboxPath mailboxPath = MailboxPath.forUser(Username.of(userStepdefs.getConnectedUser()), mailboxName);
        IntStream
            .range(0, messageCount)
            .forEach(Throwing.intConsumer(i -> appendMessage(mailboxPath, i)));
        mainStepdefs.awaitMethod.run();
    }

    private void appendMessage(MailboxPath mailboxPath, int i) throws Exception {
        mainStepdefs.mailboxProbe.appendMessage(userStepdefs.getConnectedUser(), mailboxPath,
                MessageManager.AppendCommand.from(Message.Builder.of()
                    .setSubject("test" + i)
                    .setBody("testBody" + i, StandardCharsets.UTF_8)
                    .build()));
    }

    @Given("^\"([^\"]*)\" has a mailbox \"([^\"]*)\"$")
    public void createMailbox(String username, String mailbox) {
        mainStepdefs.mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, mailbox);
    }

    @Given("^\"([^\"]*)\" shares its mailbox \"([^\"]*)\" with rights \"([^\"]*)\" with \"([^\"]*)\"$")
    public void shareMailboxWithRight(String owner, String mailboxName, String rights, String shareTo) throws Throwable {
        userStepdefs.connectUser(owner);

        String mailboxId = mainStepdefs.getMailboxId(owner, mailboxName).serialize();

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

    @Given("^\"([^\"]*)\" shares \"([^\"]*)\" delegated mailbox \"([^\"]*)\" with rights \"([^\"]*)\" with \"([^\"]*)\"$")
    public void shareMailboxWithRight(String connectedUser, String owner,  String mailboxName, String rights, String shareTo) throws Throwable {
        userStepdefs.connectUser(connectedUser);

        String mailboxId = mainStepdefs.getMailboxId(owner, mailboxName).serialize();

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

    @When("^\"([^\"]*)\" renames the mailbox, owned by \"([^\"]*)\", \"([^\"]*)\" to \"([^\"]*)\"$")
    public void renamingMailbox(String user, String mailboxOwner, String currentMailboxName, String newMailboxName) throws Throwable {
        MailboxId mailboxId = mainStepdefs.getMailboxId("#private", mailboxOwner, currentMailboxName);
        userStepdefs.connectUser(user);
        renamingMailbox(mailboxId, newMailboxName);
    }

    @When("^\"([^\"]*)\" moves the mailbox \"([^\"]*)\" owned by \"([^\"]*)\", into mailbox \"([^\"]*)\" owned by \"([^\"]*)\"$")
    public void movingMailbox(String user, String mailboxName, String mailboxOwner, String newParentName, String newParentOwner) throws Throwable {
        String mailbox = mainStepdefs.getMailboxId(mailboxOwner, mailboxName).serialize();
        String newParent = mainStepdefs.getMailboxId(newParentOwner, newParentName).serialize();

        String requestBody =
            "[" +
                "  [ \"setMailboxes\"," +
                "    {" +
                "      \"update\": {" +
                "        \"" + mailbox + "\" : {" +
                "          \"parentId\" : \"" + newParent + "\"" +
                "        }" +
                "      }" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";

        userStepdefs.execWithUser(user, () -> httpClient.post(requestBody));
    }

    @When("^\"([^\"]*)\" moves the mailbox \"([^\"]*)\" owned by \"([^\"]*)\" as top level mailbox$")
    public void movingMailboxAsTopLevel(String user, String mailboxName, String mailboxOwner) throws Throwable {
        String mailbox = mainStepdefs.getMailboxId(mailboxOwner, mailboxName).serialize();
        String requestBody =
            "[" +
                "  [ \"setMailboxes\"," +
                "    {" +
                "      \"update\": {" +
                "        \"" + mailbox + "\" : {" +
                "          \"parentId\" : null" +
                "        }" +
                "      }" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";
        userStepdefs.execWithUser(user, () -> httpClient.post(requestBody));
    }

    @When("^\"([^\"]*)\" creates mailbox \"([^\"]*)\" with creationId \"([^\"]*)\" in mailbox \"([^\"]*)\" owned by \"([^\"]*)\"$")
    public void createChildMailbox(String user, String mailboxName, String creationId, String parentMailboxName, String parentOwner) throws Throwable {
        String parentMailbox = mainStepdefs.getMailboxId(parentOwner, parentMailboxName).serialize();
        userStepdefs.execWithUser(user, () -> {
            String requestBody =
                "[" +
                    "  [ \"setMailboxes\"," +
                    "    {" +
                    "      \"create\": {" +
                    "        \"" + creationId + "\" : {" +
                    "          \"name\" : \"" + mailboxName + "\"," +
                    "          \"parentId\" : \"" + parentMailbox + "\"" +
                    "        }" +
                    "      }" +
                    "    }," +
                    "    \"#0\"" +
                    "  ]" +
                    "]";
            httpClient.post(requestBody);
        });
    }

    @When("^\"([^\"]*)\" renames (?:her|his) mailbox \"([^\"]*)\" to \"([^\"]*)\"$")
    public void renamingMailbox(String user, String actualMailboxName, String newMailboxName) throws Throwable {
        MailboxId mailboxId = mainStepdefs.getMailboxId(user, actualMailboxName);
        renamingMailbox(mailboxId, newMailboxName);
    }

    private void renamingMailbox(MailboxId mailboxId, String newMailboxName) throws Exception {
        String requestBody =
                "[" +
                    "  [ \"setMailboxes\"," +
                    "    {" +
                    "      \"update\": {" +
                    "        \"" + mailboxId.serialize() + "\" : {" +
                    "          \"name\" : \"" + newMailboxName + "\"" +
                    "        }" +
                    "      }" +
                    "    }," +
                    "    \"#0\"" +
                    "  ]" +
                    "]";
        httpClient.post(requestBody);
    }

    @When("^renaming mailbox \"([^\"]*)\" to \"([^\"]*)\"")
    public void renamingMailbox(String actualMailboxName, String newMailboxName) throws Throwable {
        renamingMailbox(userStepdefs.getConnectedUser(), actualMailboxName, newMailboxName);
    }

    @When("^\"([^\"]*)\" deletes the mailbox \"([^\"]*)\" owned by \"([^\"]*)\"$")
    public void deletesMailbox(String user, String mailboxName, String owner) throws Throwable {
        String mailboxId = mainStepdefs.getMailboxId(owner, mailboxName).serialize();
        userStepdefs.connectUser(user);
        String requestBody =
                "[" +
                    "  [ \"setMailboxes\"," +
                    "    {" +
                    "      \"destroy\": [ \"" + mailboxId + "\" ]" +
                    "    }," +
                    "    \"#0\"" +
                    "  ]" +
                    "]";
        httpClient.post(requestBody);
    }

    @When("^moving mailbox \"([^\"]*)\" to \"([^\"]*)\"$")
    public void movingMailbox(String actualMailboxPath, String newParentMailboxPath) throws Throwable {
        String username = userStepdefs.getConnectedUser();
        String mailboxId = mainStepdefs.getMailboxId(username, actualMailboxPath).serialize();
        String parentId = mainStepdefs.getMailboxId(username, newParentMailboxPath).serialize();

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
    public void mailboxContainsMessages(String mailboxName, int messageCount) {
        String username = userStepdefs.getConnectedUser();
        String mailboxId = mainStepdefs.getMailboxId(username, mailboxName).serialize();

        calmlyAwait.until(() -> {
            String requestBody = "[[\"getMessageList\", {\"filter\":{\"inMailboxes\":[\"" + mailboxId + "\"]}}, \"#0\"]]";

            httpClient.post(requestBody);

            assertThat(httpClient.response.getStatusLine().getStatusCode()).isEqualTo(200);
            DocumentContext jsonPath = JsonPath.parse(httpClient.response.getEntity().getContent());
            assertThat(jsonPath.<String>read(NAME)).isEqualTo("messageList");

            return jsonPath.<List<String>>read(ARGUMENTS + ".messageIds").size() == messageCount;
        });
    }

    @Then("^\"([^\"]*)\" receives not updated on mailbox \"([^\"]*)\" with kind \"([^\"]*)\" and message \"([^\"]*)\"$")
    public void assertNotUpdatedWithGivenProperties(String userName, String mailboxName, String type, String message) {
        String mailboxId = mainStepdefs.getMailboxId(userName, mailboxName).serialize();
        assertThat(httpClient.response.getStatusLine().getStatusCode()).isEqualTo(200);
        assertThat(httpClient.jsonPath.<String>read(NAME)).isEqualTo("mailboxesSet");
        
        Map<String, Map<String, String>> notUpdated = httpClient.jsonPath.<Map<String, Map<String, String>>>read(ARGUMENTS + ".notUpdated");
        assertThat(notUpdated).hasSize(1);
        Map<String, String> parameters = notUpdated.get(mailboxId);
        assertThat(parameters).contains(Maps.immutableEntry("type", type),
                Maps.immutableEntry("description", message));
    }

    @Then("^mailbox \"([^\"]*)\" owned by \"([^\"]*)\" is not updated")
    public void assertNotUpdated(String mailboxName, String owner) {
        String mailboxId = mainStepdefs.getMailboxId(owner, mailboxName).serialize();
        assertThat(httpClient.jsonPath.<Map<String, String>>read("[0][1].notUpdated"))
            .containsOnlyKeys(mailboxId);
    }

    @Then("^mailbox \"([^\"]*)\" owned by \"([^\"]*)\" is not destroyed$")
    public void assertNotDestroyed(String mailboxName, String owner) {
        String mailboxId = mainStepdefs.getMailboxId(owner, mailboxName).serialize();
        assertThat(httpClient.jsonPath.<Map<String, String>>read("[0][1].notDestroyed"))
            .containsOnlyKeys(mailboxId);
    }


    @Then("^mailbox with creationId \"([^\"]*)\" is not created")
    public void assertNotCreated(String creationId) {
        assertThat(httpClient.jsonPath.<Map<String, String>>read("[0][1].notCreated"))
            .containsOnlyKeys(creationId);
    }
}
