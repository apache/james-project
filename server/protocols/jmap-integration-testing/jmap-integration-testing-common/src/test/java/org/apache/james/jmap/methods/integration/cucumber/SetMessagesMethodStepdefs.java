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
import java.util.Map;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.mail.Flags;

import org.apache.james.jmap.model.Keywords;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.modules.MailboxProbeImpl;

import com.google.common.collect.ImmutableList;

import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import cucumber.runtime.java.guice.ScenarioScoped;

@ScenarioScoped
public class SetMessagesMethodStepdefs {

    private final MainStepdefs mainStepdefs;
    private final UserStepdefs userStepdefs;
    private final HttpClient httpClient;
    private final GetMessagesMethodStepdefs getMessagesMethodStepdefs;

    @Inject
    private SetMessagesMethodStepdefs(MainStepdefs mainStepdefs, UserStepdefs userStepdefs, HttpClient httpClient, GetMessagesMethodStepdefs getMessagesMethodStepdefs) {
        this.mainStepdefs = mainStepdefs;
        this.userStepdefs = userStepdefs;
        this.httpClient = httpClient;
        this.getMessagesMethodStepdefs = getMessagesMethodStepdefs;
    }

    @When("^\"([^\"]*)\" moves \"([^\"]*)\" to user mailbox \"([^\"]*)\"")
    public void moveMessageToMailboxWithUser(String username, String message, String mailbox) throws Throwable {
        userStepdefs.execWithUser(username, () -> moveMessageToMailbox(message, mailbox));
    }

    @When("^the user moves \"([^\"]*)\" to user mailbox \"([^\"]*)\"")
    public void moveMessageToMailbox(String message, String mailbox) throws Throwable {
        MessageId messageId = getMessagesMethodStepdefs.getMessageId(message);
        MailboxId mailboxId = mainStepdefs.jmapServer
            .getProbe(MailboxProbeImpl.class)
            .getMailbox(MailboxConstants.USER_NAMESPACE, userStepdefs.getConnectedUser(), mailbox)
            .getMailboxId();

        httpClient.post("[" +
            "  [" +
            "    \"setMessages\","+
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

    @When("^\"([^\"]*)\" copies \"([^\"]*)\" from mailbox \"([^\"]*)\" to mailbox \"([^\"]*)\"")
    public void copyMessageToMailbox(String username, String message, String sourceMailbox, String destinationMailbox) throws Throwable {
        userStepdefs.execWithUser(username, () -> copyMessageToMailbox(message, sourceMailbox, destinationMailbox));
    }

    @When("^the user copies \"([^\"]*)\" from mailbox \"([^\"]*)\" to mailbox \"([^\"]*)\"")
    public void copyMessageToMailbox(String message, String sourceMailbox, String destinationMailbox) throws Throwable {
        MessageId messageId = getMessagesMethodStepdefs.getMessageId(message);
        MailboxId sourceMailboxId = mainStepdefs.jmapServer
            .getProbe(MailboxProbeImpl.class)
            .getMailbox(MailboxConstants.USER_NAMESPACE, userStepdefs.getConnectedUser(), sourceMailbox)
            .getMailboxId();
        MailboxId destinationMailboxId = mainStepdefs.jmapServer
            .getProbe(MailboxProbeImpl.class)
            .getMailbox(MailboxConstants.USER_NAMESPACE, userStepdefs.getConnectedUser(), destinationMailbox)
            .getMailboxId();

        httpClient.post("[" +
            "  [" +
            "    \"setMessages\","+
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

    @When("^\"([^\"]*)\" copies \"([^\"]*)\" from mailbox \"([^\"]*)\" of user \"([^\"]*)\" to mailbox \"([^\"]*)\" of user \"([^\"]*)\"")
    public void copyMessageToMailbox(String username, String message, String sourceMailbox, String sourceUser, String destinationMailbox, String destinationUser) throws Throwable {
        userStepdefs.execWithUser(username, () -> copyMessageToMailbox(message, sourceMailbox, sourceUser, destinationMailbox, destinationUser));
    }

    private void copyMessageToMailbox(String message, String sourceMailbox, String sourceUser, String destinationMailbox, String destinationUser) throws Throwable {
        MessageId messageId = getMessagesMethodStepdefs.getMessageId(message);
        MailboxId sourceMailboxId = mainStepdefs.jmapServer
            .getProbe(MailboxProbeImpl.class)
            .getMailbox(MailboxConstants.USER_NAMESPACE, sourceUser, sourceMailbox)
            .getMailboxId();
        MailboxId destinationMailboxId = mainStepdefs.jmapServer
            .getProbe(MailboxProbeImpl.class)
            .getMailbox(MailboxConstants.USER_NAMESPACE, destinationUser, destinationMailbox)
            .getMailboxId();

        httpClient.post("[" +
            "  [" +
            "    \"setMessages\","+
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

    @Given("^\"([^\"]*)\" moves \"([^\"]*)\" to mailbox \"([^\"]*)\" of user \"([^\"]*)\"")
    public void moveMessageToMailbox(String username, String message, String destinationMailbox, String destinationUser) throws Throwable {
        userStepdefs.execWithUser(username, () -> moveMessageToMailbox(message, destinationMailbox, destinationUser));
    }

    private void moveMessageToMailbox(String message, String destinationMailbox, String destinationUser) throws Throwable {
        MessageId messageId = getMessagesMethodStepdefs.getMessageId(message);
        MailboxId destinationMailboxId = mainStepdefs.jmapServer
            .getProbe(MailboxProbeImpl.class)
            .getMailbox(MailboxConstants.USER_NAMESPACE, destinationUser, destinationMailbox)
            .getMailboxId();

        httpClient.post("[" +
            "  [" +
            "    \"setMessages\","+
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

    @When("^\"([^\"]*)\" sets flags \"([^\"]*)\" on message \"([^\"]*)\"")
    public void setFlags(String username, List<String> keywords, String message) throws Throwable {
        userStepdefs.execWithUser(username, () -> setFlags(keywords, message));
    }

    @When("^the user sets flags \"([^\"]*)\" on message \"([^\"]*)\"")
    public void setFlags(List<String> keywords, String message) throws Throwable {
        MessageId messageId = getMessagesMethodStepdefs.getMessageId(message);
        String keywordString = keywords
            .stream()
            .map(value -> "\"" + value + "\" : true")
            .collect(Collectors.joining(","));

        httpClient.post("[" +
            "  [" +
            "    \"setMessages\","+
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

    @When("^message \"([^\"]*)\" has flags (.*) in mailbox \"([^\"]*)\" of user \"([^\"]*)\"")
    public void setMessageFlagsInSpecifiedMailbox(String message, List<String> flags, String mailbox, String mailboxOwner) throws Exception {
        Flags newFlags = Keywords.factory().fromList(flags).asFlags();
        String username = userStepdefs.getConnectedUser();
        MessageId messageId = getMessagesMethodStepdefs.getMessageId(message);
        MailboxId mailboxId = mainStepdefs.jmapServer
            .getProbe(MailboxProbeImpl.class)
            .getMailbox(MailboxConstants.USER_NAMESPACE, mailboxOwner, mailbox)
            .getMailboxId();

        mainStepdefs.messageIdProbe.updateNewFlags(username, newFlags, messageId, ImmutableList.of(mailboxId));
        mainStepdefs.awaitMethod.run();
    }

    @Then("^message \"([^\"]*)\" is not updated$")
    public void assertIdOfTheFirstMessage(String messageName) throws Exception {
        MessageId id = getMessagesMethodStepdefs.getMessageId(messageName);
        assertThat(httpClient.jsonPath.<Map<String, String>>read("[0][1].notUpdated"))
            .containsOnlyKeys(id.serialize());
    }

}
