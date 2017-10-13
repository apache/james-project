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

import java.util.Map;

import javax.inject.Inject;

import org.apache.james.utils.IMAPMessageReader;

import com.google.common.collect.Maps;

import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import cucumber.runtime.java.guice.ScenarioScoped;

@ScenarioScoped
public class ImapStepdefs {

    private static final int IMAP_PORT = 1143;
    private static final String LOCALHOST = "127.0.0.1";

    private final UserStepdefs userStepdefs;
    private final MainStepdefs mainStepdefs;
    private final Map<String, IMAPMessageReader> imapConnections;

    @Inject
    private ImapStepdefs(UserStepdefs userStepdefs, MainStepdefs mainStepdefs) {
        this.userStepdefs = userStepdefs;
        this.mainStepdefs = mainStepdefs;
        this.imapConnections = Maps.newHashMap();
    }

    @Then("^the user has a IMAP message in mailbox \"([^\"]*)\"$")
    public void hasMessageInMailbox(String mailbox) throws Throwable {
        try (IMAPMessageReader imapMessageReader = new IMAPMessageReader(LOCALHOST, IMAP_PORT);) {
            assertThat(
                imapMessageReader.userReceivedMessageInMailbox(userStepdefs.lastConnectedUser,
                    userStepdefs.passwordByUser.get(userStepdefs.lastConnectedUser),
                    mailbox))
                .isTrue();
        }
    }

    @Then("^the user has a IMAP notification about (\\d+) new message when selecting mailbox \"([^\"]*)\"$")
    public void hasANotificationAboutNewMessagesInMailbox(int numOfNewMessage, String mailbox) throws Throwable {
        try (IMAPMessageReader imapMessageReader = new IMAPMessageReader(LOCALHOST, IMAP_PORT);) {
            assertThat(
                imapMessageReader.userGetNotifiedForNewMessagesWhenSelectingMailbox(userStepdefs.lastConnectedUser,
                    userStepdefs.passwordByUser.get(userStepdefs.lastConnectedUser),
                    numOfNewMessage, mailbox))
                .isTrue();
        }
    }

    @Then("^the user does not have a IMAP message in mailbox \"([^\"]*)\"$")
    public void hasNoMessageInMailbox(String mailbox) throws Throwable {
        try (IMAPMessageReader imapMessageReader = new IMAPMessageReader(LOCALHOST, IMAP_PORT);) {
            assertThat(
                imapMessageReader.userDoesNotReceiveMessageInMailbox(userStepdefs.lastConnectedUser,
                    userStepdefs.passwordByUser.get(userStepdefs.lastConnectedUser),
                    mailbox))
                .isTrue();
        }
    }

    @Given("^the user has an open IMAP connection with mailbox \"([^\"]*)\" selected")
    public void openImapConnectionAndSelectMailbox(String mailbox) throws Throwable {
        IMAPMessageReader imapMessageReader = new IMAPMessageReader(LOCALHOST, IMAP_PORT);

        String login = userStepdefs.lastConnectedUser;
        String password = userStepdefs.passwordByUser.get(login);

        imapMessageReader.connectAndSelect(login, password, mailbox);
        imapConnections.put(mailbox, imapMessageReader);
    }

    @Then("^the user set flags via IMAP to \"([^\"]*)\" for all messages in mailbox \"([^\"]*)\"$")
    public void setFlagsViaIMAPInMailbox(String flags, String mailbox) throws Throwable {
        IMAPMessageReader imapMessageReader = imapConnections.get(mailbox);
        imapMessageReader.setFlagsForAllMessagesInMailbox(flags);
        mainStepdefs.awaitMethod.run();
    }

    @Then("^the user has a IMAP RECENT and a notification about (\\d+) new messages on connection for mailbox \"([^\"]*)\"$")
    public void checkNotificationForNewMessageOnActiveConnection(int numberOfMessages, String mailbox) throws Throwable {
        IMAPMessageReader imapMessageReader = imapConnections.get(mailbox);
        assertThat(imapMessageReader).isNotNull();
        assertThat(imapMessageReader.userGetNotifiedForNewMessages(numberOfMessages)).isTrue();
    }

    @When("^the user copy by IMAP first message of \"([^\"]*)\" to mailbox \"([^\"]*)\"$")
    public void copyAMessageByIMAP(String srcMailbox, String destMailbox) throws Throwable {
        IMAPMessageReader imapMessageReader = new IMAPMessageReader(LOCALHOST, IMAP_PORT);

        String login = userStepdefs.lastConnectedUser;
        String password = userStepdefs.passwordByUser.get(login);

        imapMessageReader.connectAndSelect(login, password, srcMailbox);
        assertThat(imapMessageReader).isNotNull();
        imapMessageReader.copyFirstMessage(destMailbox);
        mainStepdefs.awaitMethod.run();
    }

    @Then("^the user has IMAP EXPUNGE and a notification for (\\d+) message sequence number on connection for mailbox \"([^\"]*)\"$")
    public void checkExpungeNotificationOnActiveConnection(int msn, String mailbox) throws Throwable {
        IMAPMessageReader imapMessageReader = imapConnections.get(mailbox);
        assertThat(imapMessageReader).isNotNull();
        assertThat(imapMessageReader.userGetNotifiedForDeletion(msn)).isTrue();
    }
}
