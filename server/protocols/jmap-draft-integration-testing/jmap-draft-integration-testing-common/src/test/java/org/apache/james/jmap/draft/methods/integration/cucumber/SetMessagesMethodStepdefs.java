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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.google.common.base.Splitter;
import jakarta.inject.Inject;
import jakarta.mail.Flags;

import org.apache.james.core.Username;
import org.apache.james.jmap.draft.model.Keywords;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;

import com.google.common.collect.ImmutableList;

import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

@ScenarioScoped
public class SetMessagesMethodStepdefs {

    private final MainStepdefs mainStepdefs;
    private final UserStepdefs userStepdefs;
    private final HttpClient httpClient;
    private final MessageIdStepdefs messageIdStepdefs;

    @Inject
    private SetMessagesMethodStepdefs(MainStepdefs mainStepdefs, UserStepdefs userStepdefs, HttpClient httpClient, MessageIdStepdefs messageIdStepdefs) {
        this.mainStepdefs = mainStepdefs;
        this.userStepdefs = userStepdefs;
        this.httpClient = httpClient;
        this.messageIdStepdefs = messageIdStepdefs;
    }

    @When("^\"([^\"]*)\" moves \"([^\"]*)\" to user mailbox \"([^\"]*)\"$")
    public void moveMessageToMailboxWithUser(String username, String message, String mailbox) {
        userStepdefs.execWithUser(username, () -> moveMessageToMailbox(message, mailbox));
    }

    @When("^the user moves \"([^\"]*)\" to user mailbox \"([^\"]*)\"$")
    public void moveMessageToMailbox(String message, String mailbox) throws Throwable {
        MessageId messageId = messageIdStepdefs.getMessageId(message);
        MailboxId mailboxId = mainStepdefs.getMailboxId(userStepdefs.getConnectedUser(), mailbox);

        httpClient.post("[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"update\": { \"" + messageId.serialize() + "\" : {" +
            "        \"mailboxIds\": [\"" + mailboxId.serialize() + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]");
        mainStepdefs.awaitMethod.run();
    }

    @When("^the user moves \"([^\"]*)\" to user mailbox \"([^\"]*)\" and set flags \"([^\"]*)\"$")
    public void moveMessageToMailboxAndChangeFlags(String message, String mailbox, String keywords) throws Throwable {
        MessageId messageId = messageIdStepdefs.getMessageId(message);
        MailboxId mailboxId = mainStepdefs.getMailboxId(userStepdefs.getConnectedUser(), mailbox);
        String keywordString = toKeywordsString(Splitter.on(',').trimResults().omitEmptyStrings().splitToList(keywords));

        httpClient.post("[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"update\": { \"" + messageId.serialize() + "\" : {" +
            "        \"mailboxIds\": [\"" + mailboxId.serialize() + "\"]," +
            "        \"keywords\": {" + keywordString + "}" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]");
        mainStepdefs.awaitMethod.run();
    }

    @When("^\"([^\"]*)\" copies \"([^\"]*)\" from mailbox \"([^\"]*)\" to mailbox \"([^\"]*)\"$")
    public void copyMessageToMailbox(String username, String message, String sourceMailbox, String destinationMailbox) {
        userStepdefs.execWithUser(username, () -> copyMessageToMailbox(message, sourceMailbox, destinationMailbox));
    }

    @When("^the user copies \"([^\"]*)\" from mailbox \"([^\"]*)\" to mailbox \"([^\"]*)\"$")
    public void copyMessageToMailbox(String message, String sourceMailbox, String destinationMailbox) throws Throwable {
        MessageId messageId = messageIdStepdefs.getMessageId(message);
        String user = userStepdefs.getConnectedUser();
        MailboxId sourceMailboxId = mainStepdefs.getMailboxId(user, sourceMailbox);
        MailboxId destinationMailboxId = mainStepdefs.getMailboxId(user, destinationMailbox);

        httpClient.post("[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"update\": { \"" + messageId.serialize() + "\" : {" +
            "        \"mailboxIds\": [\"" + destinationMailboxId.serialize() + "\",\"" + sourceMailboxId.serialize() + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]");
        mainStepdefs.awaitMethod.run();
    }

    @When("^\"([^\"]*)\" copies \"([^\"]*)\" from mailbox \"([^\"]*)\" of user \"([^\"]*)\" to mailbox \"([^\"]*)\" of user \"([^\"]*)\"$")
    public void copyMessageToMailbox(String username, String message, String sourceMailbox, String sourceUser, String destinationMailbox, String destinationUser) {
        userStepdefs.execWithUser(username, () -> copyMessageToMailbox(message, sourceMailbox, sourceUser, destinationMailbox, destinationUser));
    }

    private void copyMessageToMailbox(String message, String sourceMailbox, String sourceUser, String destinationMailbox, String destinationUser) throws Throwable {
        MessageId messageId = messageIdStepdefs.getMessageId(message);
        MailboxId sourceMailboxId = mainStepdefs.getMailboxId(sourceUser, sourceMailbox);
        MailboxId destinationMailboxId = mainStepdefs.getMailboxId(destinationUser, destinationMailbox);

        httpClient.post("[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"update\": { \"" + messageId.serialize() + "\" : {" +
            "        \"mailboxIds\": [\"" + destinationMailboxId.serialize() + "\",\"" + sourceMailboxId.serialize() + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]");
        mainStepdefs.awaitMethod.run();
    }

    @Given("^\"([^\"]*)\" moves \"([^\"]*)\" to mailbox \"([^\"]*)\" of user \"([^\"]*)\"$")
    public void moveMessageToMailbox(String username, String message, String destinationMailbox, String destinationUser) {
        userStepdefs.execWithUser(username, () -> moveMessageToMailbox(message, destinationMailbox, destinationUser));
    }

    private void moveMessageToMailbox(String message, String destinationMailbox, String destinationUser) throws Throwable {
        MessageId messageId = messageIdStepdefs.getMessageId(message);
        MailboxId destinationMailboxId = mainStepdefs.getMailboxId(destinationUser, destinationMailbox);

        httpClient.post("[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"update\": { \"" + messageId.serialize() + "\" : {" +
            "        \"mailboxIds\": [\"" + destinationMailboxId.serialize() + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]");
        mainStepdefs.awaitMethod.run();
    }

    @When("^\"([^\"]*)\" sets flags \"([^\"]*)\" on message \"([^\"]*)\"$")
    public void setFlags(String username, String keywords, String message) {
        userStepdefs.execWithUser(username, () -> setFlags(keywords, message));
    }

    @When("^\"([^\"]*)\" marks the message \"([^\"]*)\" as flagged$")
    public void flag(String username, String message) {
        userStepdefs.execWithUser(username, () -> {
            MessageId messageId = messageIdStepdefs.getMessageId(message);

            httpClient.post("[" +
                "  [" +
                "    \"setMessages\"," +
                "    {" +
                "      \"update\": { \"" + messageId.serialize() + "\" : {" +
                "        \"isFlagged\": true" +
                "      }}" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]");
            mainStepdefs.awaitMethod.run();
        });
    }

    @When("^\"([^\"]*)\" marks the message \"([^\"]*)\" as draft")
    public void draft(String username, String message) throws Throwable {
        userStepdefs.execWithUser(username, () -> {
            MessageId messageId = messageIdStepdefs.getMessageId(message);

            httpClient.post("[" +
                "  [" +
                "    \"setMessages\"," +
                "    {" +
                "      \"update\": { \"" + messageId.serialize() + "\" : {" +
                "        \"isDraft\": true" +
                "      }}" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]");
            mainStepdefs.awaitMethod.run();
        });
    }


    @When("^\"([^\"]*)\" destroys message \"([^\"]*)\"$")
    public void destroyMessage(String username, String message) {
        MessageId messageId = messageIdStepdefs.getMessageId(message);
        userStepdefs.execWithUser(username, () -> {
            httpClient.post("[" +
                "  [" +
                "    \"setMessages\"," +
                "    {" +
                "      \"destroy\": [ \"" + messageId.serialize() + "\" ]" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]");
            mainStepdefs.awaitMethod.run();
        });
    }

    @Given("^\"([^\"]*)\" tries to create a draft message \"([^\"]*)\" in mailbox \"([^\"]*)\"$")
    public void createDraft(String username, String message, String mailboxName) {
        userStepdefs.execWithUser(username, () -> {
            String mailboxId = mainStepdefs.getMailboxId(username, mailboxName).serialize();
            httpClient.post("[" +
                "  [" +
                "    \"setMessages\"," +
                "    {" +
                "      \"create\": { \"" + message  + "\" : {" +
                "        \"subject\": \"subject\"," +
                "        \"from\": { \"name\": \"Me\", \"email\": \"" + username + "\"}," +
                "        \"to\": [{ \"name\": \"Me\", \"email\": \"" + username + "\"}]," +
                "        \"keywords\": {\"$Draft\": true}," +
                "        \"mailboxIds\": [\"" + mailboxId + "\"]" +
                "      }}" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]");
            mainStepdefs.awaitMethod.run();
            Optional.ofNullable(
                httpClient.jsonPath.<String>read("[0][1].created." + message + ".id"))
                .map(mainStepdefs.messageIdFactory::fromString)
                .ifPresent(id -> messageIdStepdefs.addMessageId(message, id));
        });
    }

    @When("^the user sets flags \"([^\"]*)\" on message \"([^\"]*)\"$")
    public void setFlags(String keywords, String message) throws Throwable {
        MessageId messageId = messageIdStepdefs.getMessageId(message);
        String keywordString = toKeywordsString(Splitter.on(',').omitEmptyStrings().trimResults().splitToList(keywords));

        httpClient.post("[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"update\": { \"" + messageId.serialize() + "\" : {" +
            "        \"keywords\": {" + keywordString + "}" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]");
        mainStepdefs.awaitMethod.run();
    }

    private String toKeywordsString(List<String> keywords) {
        return keywords
                .stream()
                .map(value -> "\"" + value + "\" : true")
                .collect(Collectors.joining(","));
    }

    @When("^message \"([^\"]*)\" has flags (.*) in mailbox \"([^\"]*)\" of user \"([^\"]*)\"$")
    public void setMessageFlagsInSpecifiedMailbox(String message, String flags, String mailbox, String mailboxOwner) throws Exception {
        Flags newFlags = Keywords.lenientFactory().fromCollection(Splitter.on(',').trimResults().splitToList(flags)).asFlags();
        Username username = Username.of(userStepdefs.getConnectedUser());
        MessageId messageId = messageIdStepdefs.getMessageId(message);
        MailboxId mailboxId = mainStepdefs.getMailboxId(mailboxOwner, mailbox);

        mainStepdefs.messageIdProbe.updateNewFlags(username, newFlags, messageId, ImmutableList.of(mailboxId));
        mainStepdefs.awaitMethod.run();
    }

    @Then("^message \"([^\"]*)\" is not updated$")
    public void assertNotUpdate(String messageName) {
        MessageId id = messageIdStepdefs.getMessageId(messageName);
        assertThat(httpClient.jsonPath.<Map<String, String>>read("[0][1].notUpdated"))
            .containsOnlyKeys(id.serialize());
    }

    @Then("^message \"([^\"]*)\" is updated$")
    public void assertUpdated(String messageName) {
        MessageId id = messageIdStepdefs.getMessageId(messageName);
        assertThat(httpClient.jsonPath.<List<String>>read("[0][1].updated"))
            .containsOnly(id.serialize());
    }

    @Then("^message \"([^\"]*)\" is not created$")
    public void assertNotCreated(String messageName) {
        assertThat(httpClient.jsonPath.<Map<String, String>>read("[0][1].notCreated"))
            .containsOnlyKeys(messageName);
    }

    @Then("^message \"([^\"]*)\" is created$")
    public void assertCreated(String messageName) {
        assertThat(httpClient.jsonPath.<Map<String, String>>read("[0][1].created"))
            .containsOnlyKeys(messageName);
    }

}
