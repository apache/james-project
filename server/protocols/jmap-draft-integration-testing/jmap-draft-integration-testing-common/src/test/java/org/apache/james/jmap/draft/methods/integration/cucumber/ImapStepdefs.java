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

import static org.apache.james.transport.mailets.remote.delivery.HeloNameProvider.LOCALHOST;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import javax.inject.Inject;

import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.utils.TestIMAPClient;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.Maps;

import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

@ScenarioScoped
public class ImapStepdefs {
    private final UserStepdefs userStepdefs;
    private final MainStepdefs mainStepdefs;
    private final Map<String, TestIMAPClient> imapConnections;

    @Inject
    private ImapStepdefs(UserStepdefs userStepdefs, MainStepdefs mainStepdefs) {
        this.userStepdefs = userStepdefs;
        this.mainStepdefs = mainStepdefs;
        this.imapConnections = Maps.newHashMap();
    }

    public void closeConnections() {
        imapConnections.values().stream()
            .forEach(Throwing.consumer(TestIMAPClient::close));
    }

    @Then("^the user has a IMAP message in mailbox \"([^\"]*)\"$")
    public void hasMessageInMailbox(String mailbox) throws Throwable {
        try (TestIMAPClient testIMAPClient = new TestIMAPClient()) {
            testIMAPClient.connect(LOCALHOST, mainStepdefs.jmapServer.getProbe(ImapGuiceProbe.class).getImapPort())
                .login(userStepdefs.getConnectedUser(),
                        userStepdefs.getUserPassword(userStepdefs.getConnectedUser()))
                .select(mailbox);
            assertThat(testIMAPClient.hasAMessage()).isTrue();
        }
    }

    @Then("^the message has IMAP flag \"([^\"]*)\" in mailbox \"([^\"]*)\" for \"([^\"]*)\"$")
    public void hasMessageWithFlagInMailbox(String flags, String mailbox, String username) throws Throwable {
        try (TestIMAPClient testIMAPClient = new TestIMAPClient()) {
            testIMAPClient.connect(LOCALHOST, mainStepdefs.jmapServer.getProbe(ImapGuiceProbe.class).getImapPort())
                .login(userStepdefs.getConnectedUser(),
                        userStepdefs.getUserPassword(username))
                .select(mailbox);
            assertThat(testIMAPClient.hasAMessageWithFlags(flags))
                .isTrue();
        }
    }

    @Then("^the user has a IMAP notification about (\\d+) new message when selecting mailbox \"([^\"]*)\"$")
    public void hasANotificationAboutNewMessagesInMailbox(int numOfNewMessage, String mailbox) throws Throwable {
        try (TestIMAPClient testIMAPClient = new TestIMAPClient()) {
            testIMAPClient.connect(LOCALHOST, mainStepdefs.jmapServer.getProbe(ImapGuiceProbe.class).getImapPort())
                .login(userStepdefs.getConnectedUser(),
                        userStepdefs.getUserPassword(userStepdefs.getConnectedUser()))
                .select(mailbox);
            assertThat(
                testIMAPClient.userGetNotifiedForNewMessagesWhenSelectingMailbox(numOfNewMessage))
                .isTrue();
        }
    }

    @Then("^the user does not have a IMAP message in mailbox \"([^\"]*)\"$")
    public void hasNoMessageInMailbox(String mailbox) throws Throwable {
        try (TestIMAPClient testIMAPClient = new TestIMAPClient()) {
            testIMAPClient.connect(LOCALHOST, mainStepdefs.jmapServer.getProbe(ImapGuiceProbe.class).getImapPort())
                .login(userStepdefs.getConnectedUser(),
                        userStepdefs.getUserPassword(userStepdefs.getConnectedUser()))
                .select(mailbox);
            assertThat(testIMAPClient.userDoesNotReceiveMessage())
                .isTrue();
        }
    }

    @SuppressWarnings("resource")
    @Given("^the user has an open IMAP connection with mailbox \"([^\"]*)\" selected")
    public void openImapConnectionAndSelectMailbox(String mailbox) throws Throwable {
        String login = userStepdefs.getConnectedUser();
        String password = userStepdefs.getUserPassword(login);

        imapConnections.put(mailbox, new TestIMAPClient()
            .connect(LOCALHOST, mainStepdefs.jmapServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(login, password)
            .select(mailbox));
    }

    @Then("^the user set flags via IMAP to \"([^\"]*)\" for all messages in mailbox \"([^\"]*)\"$")
    public void setFlagsViaIMAPInMailbox(String flags, String mailbox) throws Throwable {
        TestIMAPClient testIMAPClient = imapConnections.get(mailbox);
        testIMAPClient.setFlagsForAllMessagesInMailbox(flags);
        mainStepdefs.awaitMethod.run();
    }

    @Then("^the user has a IMAP RECENT and a notification about (\\d+) new messages on connection for mailbox \"([^\"]*)\"$")
    public void checkNotificationForNewMessageOnActiveConnection(int numberOfMessages, String mailbox) throws Throwable {
        TestIMAPClient testIMAPClient = imapConnections.get(mailbox);
        assertThat(testIMAPClient).isNotNull();
        assertThat(testIMAPClient.userGetNotifiedForNewMessages(numberOfMessages)).isTrue();
    }

    @When("^the user copy by IMAP first message of \"([^\"]*)\" to mailbox \"([^\"]*)\"$")
    public void copyAMessageByIMAP(String srcMailbox, String destMailbox) throws Throwable {
        String login = userStepdefs.getConnectedUser();
        String password = userStepdefs.getUserPassword(login);

        try (TestIMAPClient testIMAPClient = new TestIMAPClient()) {
            testIMAPClient.connect(LOCALHOST, mainStepdefs.jmapServer.getProbe(ImapGuiceProbe.class).getImapPort())
                .login(login, password)
                .select(srcMailbox);
            assertThat(testIMAPClient).isNotNull();
            testIMAPClient.copyFirstMessage(destMailbox);
            mainStepdefs.awaitMethod.run();
        }
    }

    @Then("^the user has IMAP EXPUNGE and a notification for (\\d+) message sequence number on connection for mailbox \"([^\"]*)\"$")
    public void checkExpungeNotificationOnActiveConnection(int msn, String mailbox) throws Throwable {
        TestIMAPClient testIMAPClient = imapConnections.get(mailbox);
        assertThat(testIMAPClient).isNotNull();
        assertThat(testIMAPClient.userGetNotifiedForDeletion(msn)).isTrue();
    }
}
