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

package org.apache.james.jmap.methods.integration;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.RestAssured.with;
import static com.jayway.restassured.config.EncoderConfig.encoderConfig;
import static com.jayway.restassured.config.RestAssuredConfig.newConfig;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.io.ByteArrayInputStream;
import java.util.Date;
import java.util.stream.IntStream;

import javax.mail.Flags;

import org.apache.james.GuiceJamesServer;
import org.apache.james.jmap.JmapAuthentication;
import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.mail.model.Mailbox;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Charsets;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.http.ContentType;

import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;

public class SetMailboxesMethodStepdefs {
    private static final String NAME = "[0][0]";
    private static final String ARGUMENTS = "[0][1]";

    public GuiceJamesServer jmapServer;
    public Runnable awaitMethod = () -> {};

    private AccessToken accessToken;
    private String username;

    public void init() throws Exception {
        jmapServer.start();
        RestAssured.port = jmapServer.getJmapPort();
        RestAssured.config = newConfig().encoderConfig(encoderConfig().defaultContentCharset(Charsets.UTF_8));
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }
    
    public void tearDown() {
        jmapServer.stop();
    }
    

    @Given("^a domain named \"([^\"]*)\"$")
    public void createDomain(String domain) throws Throwable {
        jmapServer.serverProbe().addDomain(domain);
    }

    @Given("^a current user with username \"([^\"]*)\" and password \"([^\"]*)\"$")
    public void createUserWithPasswordAndAuthenticate(String username, String password) throws Throwable {
        this.username = username;
        jmapServer.serverProbe().addUser(username, password);
        accessToken = JmapAuthentication.authenticateJamesUser(username, password);
    }

    @Given("^mailbox \"([^\"]*)\" with (\\d+) messages$")
    public void mailboxWithMessages(String mailboxName, int messageCount) throws Throwable {
        jmapServer.serverProbe().createMailbox("#private", username, mailboxName);
        MailboxPath mailboxPath = new MailboxPath(MailboxConstants.USER_NAMESPACE, username, mailboxName);
        IntStream
            .range(0, messageCount)
            .forEach(Throwing.intConsumer(i -> appendMessage(mailboxPath, i)));
        awaitMethod.run();
    }

    private void appendMessage(MailboxPath mailboxPath, int i) throws MailboxException {
        String content = "Subject: test" + i + "\r\n\r\n"
                + "testBody" + i;
        jmapServer.serverProbe().appendMessage(username, mailboxPath,
                new ByteArrayInputStream(content.getBytes()), new Date(), false, new Flags());
    }

    @When("^renaming mailbox \"([^\"]*)\" to \"([^\"]*)\"")
    public void renamingMailbox(String actualMailboxName, String newMailboxName) throws Throwable {
        Mailbox mailbox = jmapServer.serverProbe().getMailbox("#private", username, actualMailboxName);
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

        with()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", this.accessToken.serialize())
            .body(requestBody)
            .post("/jmap");
    }

    @When("^moving mailbox \"([^\"]*)\" to \"([^\"]*)\"$")
    public void movingMailbox(String actualMailboxPath, String newParentMailboxPath) throws Throwable {
        Mailbox mailbox = jmapServer.serverProbe().getMailbox("#private", username, actualMailboxPath);
        String mailboxId = mailbox.getMailboxId().serialize();
        Mailbox parent = jmapServer.serverProbe().getMailbox("#private", username, newParentMailboxPath);
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

        with()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", this.accessToken.serialize())
            .body(requestBody)
            .post("/jmap");
    }

    @Then("^mailbox \"([^\"]*)\" contains (\\d+) messages$")
    public void mailboxContainsMessages(String mailboxName, int messageCount) throws Throwable {
        Mailbox mailbox = jmapServer.serverProbe().getMailbox("#private", username, mailboxName);
        String mailboxId = mailbox.getMailboxId().serialize();
        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMessageList\", {\"filter\":{\"inMailboxes\":[\"" + mailboxId + "\"]}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", hasSize(messageCount));
    }
}
