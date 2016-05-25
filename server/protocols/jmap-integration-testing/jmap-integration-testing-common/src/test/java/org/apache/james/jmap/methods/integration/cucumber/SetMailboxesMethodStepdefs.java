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

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.RestAssured.with;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.io.ByteArrayInputStream;
import java.util.Date;
import java.util.stream.IntStream;

import javax.inject.Inject;
import javax.mail.Flags;

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.mail.model.Mailbox;

import com.github.fge.lambdas.Throwing;
import com.jayway.restassured.http.ContentType;

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

    @Inject
    private SetMailboxesMethodStepdefs(MainStepdefs mainStepdefs, UserStepdefs userStepdefs) {
        this.mainStepdefs = mainStepdefs;
        this.userStepdefs = userStepdefs;
    }

    @Given("^mailbox \"([^\"]*)\" with (\\d+) messages$")
    public void mailboxWithMessages(String mailboxName, int messageCount) throws Throwable {
        mainStepdefs.jmapServer.serverProbe().createMailbox("#private", userStepdefs.username, mailboxName);
        MailboxPath mailboxPath = new MailboxPath(MailboxConstants.USER_NAMESPACE, userStepdefs.username, mailboxName);
        IntStream
            .range(0, messageCount)
            .forEach(Throwing.intConsumer(i -> appendMessage(mailboxPath, i)));
        mainStepdefs.awaitMethod.run();
    }

    private void appendMessage(MailboxPath mailboxPath, int i) throws MailboxException {
        String content = "Subject: test" + i + "\r\n\r\n"
                + "testBody" + i;
        mainStepdefs.jmapServer.serverProbe().appendMessage(userStepdefs.username, mailboxPath,
                new ByteArrayInputStream(content.getBytes()), new Date(), false, new Flags());
    }

    @When("^renaming mailbox \"([^\"]*)\" to \"([^\"]*)\"")
    public void renamingMailbox(String actualMailboxName, String newMailboxName) throws Throwable {
        Mailbox mailbox = mainStepdefs.jmapServer.serverProbe().getMailbox("#private", userStepdefs.username, actualMailboxName);
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
            .header("Authorization", userStepdefs.accessToken.serialize())
            .body(requestBody)
            .post("/jmap");
    }

    @When("^moving mailbox \"([^\"]*)\" to \"([^\"]*)\"$")
    public void movingMailbox(String actualMailboxPath, String newParentMailboxPath) throws Throwable {
        Mailbox mailbox = mainStepdefs.jmapServer.serverProbe().getMailbox("#private", userStepdefs.username, actualMailboxPath);
        String mailboxId = mailbox.getMailboxId().serialize();
        Mailbox parent = mainStepdefs.jmapServer.serverProbe().getMailbox("#private", userStepdefs.username, newParentMailboxPath);
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
            .header("Authorization", userStepdefs.accessToken.serialize())
            .body(requestBody)
            .post("/jmap");
    }

    @Then("^mailbox \"([^\"]*)\" contains (\\d+) messages$")
    public void mailboxContainsMessages(String mailboxName, int messageCount) throws Throwable {
        Mailbox mailbox = mainStepdefs.jmapServer.serverProbe().getMailbox("#private", userStepdefs.username, mailboxName);
        String mailboxId = mailbox.getMailboxId().serialize();
        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", userStepdefs.accessToken.serialize())
            .body("[[\"getMessageList\", {\"filter\":{\"inMailboxes\":[\"" + mailboxId + "\"]}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", hasSize(messageCount));
    }
}
