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
import static com.jayway.restassured.config.EncoderConfig.encoderConfig;
import static com.jayway.restassured.config.RestAssuredConfig.newConfig;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;

import java.nio.charset.StandardCharsets;

import org.apache.http.client.utils.URIBuilder;
import org.apache.james.GuiceJamesServer;
import org.apache.james.jmap.HttpJmapAuthentication;
import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.probe.MailboxProbe;
import org.apache.james.mdn.action.mode.DispositionActionMode;
import org.apache.james.mdn.sending.mode.DispositionSendingMode;
import org.apache.james.mdn.type.DispositionType;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.probe.DataProbe;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.JmapGuiceProbe;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.builder.RequestSpecBuilder;
import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.parsing.Parser;

public abstract class SendMDNMethodTest {
    private static final String NAME = "[0][0]";
    private static final String ARGUMENTS = "[0][1]";
    private static final String USERS_DOMAIN = "domain.tld";
    private static final String USERNAME = "username@" + USERS_DOMAIN;
    private static final String BOB = "bob@" + USERS_DOMAIN;
    private static final String PASSWORD = "password";
    private static final String BOB_PASSWORD = "bobPassword";


    protected abstract GuiceJamesServer createJmapServer();

    protected abstract MessageId randomMessageId();

    protected abstract void await();

    private AccessToken accessToken;
    private GuiceJamesServer jmapServer;

    @Before
    public void setup() throws Throwable {
        jmapServer = createJmapServer();
        jmapServer.start();
        MailboxProbe mailboxProbe = jmapServer.getProbe(MailboxProbeImpl.class);
        DataProbe dataProbe = jmapServer.getProbe(DataProbeImpl.class);

        RestAssured.requestSpecification = new RequestSpecBuilder()
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .setConfig(newConfig().encoderConfig(encoderConfig().defaultContentCharset(StandardCharsets.UTF_8)))
                .setPort(jmapServer.getProbe(JmapGuiceProbe.class).getJmapPort())
                .build();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
        RestAssured.defaultParser = Parser.JSON;

        dataProbe.addDomain(USERS_DOMAIN);
        dataProbe.addUser(USERNAME, PASSWORD);
        dataProbe.addUser(BOB, BOB_PASSWORD);
        mailboxProbe.createMailbox("#private", USERNAME, DefaultMailboxes.INBOX);
        accessToken = HttpJmapAuthentication.authenticateJamesUser(baseUri(), USERNAME, PASSWORD);

        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, DefaultMailboxes.OUTBOX);
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, DefaultMailboxes.TRASH);
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, DefaultMailboxes.DRAFTS);
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, DefaultMailboxes.SENT);
        await();
    }

    private URIBuilder baseUri() {
        return new URIBuilder()
            .setScheme("http")
            .setHost("localhost")
            .setPort(jmapServer.getProbe(JmapGuiceProbe.class)
                .getJmapPort())
            .setCharset(StandardCharsets.UTF_8);
    }

    @After
    public void teardown() {
        jmapServer.stop();
    }

    @Test
    public void sendMDNIsNotSupportedYet() {
        String creationId = "creation-1";
        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"setMessages\", {\"sendMDN\": {" +
                "\"" + creationId + "\":{" +
                    "    \"messageId\":\"" + randomMessageId().serialize() + "\"," +
                    "    \"subject\":\"subject\"," +
                    "    \"textBody\":\"textBody\"," +
                    "    \"reportingUA\":\"reportingUA\"," +
                    "    \"disposition\":{" +
                    "        \"actionMode\":\"" + DispositionActionMode.Automatic.getValue() + "\","+
                    "        \"sendingMode\":\"" + DispositionSendingMode.Automatic.getValue() + "\","+
                    "        \"type\":\"" + DispositionType.Processed.getValue() + "\""+
                    "    }" +
                    "}" +
                "}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(ARGUMENTS + ".MDNNotSent", hasEntry(
                equalTo(creationId),
                hasEntry("type", "Not implemented yet")));
    }

    @Test
    public void sendMDNShouldIndicateMissingFields() {
        String creationId = "creation-1";
        // Missing subject
        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"setMessages\", {\"sendMDN\": {" +
                "\"" + creationId + "\":{" +
                    "    \"messageId\":\"" + randomMessageId().serialize() + "\"," +
                    "    \"textBody\":\"textBody\"," +
                    "    \"reportingUA\":\"reportingUA\"," +
                    "    \"disposition\":{" +
                    "        \"actionMode\":\"" + DispositionActionMode.Automatic.getValue() + "\","+
                    "        \"sendingMode\":\"" + DispositionSendingMode.Automatic.getValue() + "\","+
                    "        \"type\":\"" + DispositionType.Processed.getValue() + "\""+
                    "    }" +
                    "}" +
                "}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("error"))
            .body(ARGUMENTS + ".type", is("invalidArguments"))
            .body(ARGUMENTS + ".description", containsString("problem: 'subject' is mandatory"));
    }

    @Test
    public void sendMDNShouldIndicateMissingFieldsInDisposition() {
        String creationId = "creation-1";
        // Missing actionMode
        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"setMessages\", {\"sendMDN\": {" +
                "\"" + creationId + "\":{" +
                "    \"messageId\":\"" + randomMessageId().serialize() + "\"," +
                "    \"subject\":\"subject\"," +
                "    \"textBody\":\"textBody\"," +
                "    \"reportingUA\":\"reportingUA\"," +
                "    \"disposition\":{" +
                "        \"sendingMode\":\"" + DispositionSendingMode.Automatic.getValue() + "\","+
                "        \"type\":\"" + DispositionType.Processed.getValue() + "\""+
                "    }" +
                "}" +
                "}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("error"))
            .body(ARGUMENTS + ".type", is("invalidArguments"))
            .body(ARGUMENTS + ".description", containsString("problem: 'actionMode' is mandatory"));
    }

    @Test
    public void invalidEnumValuesInMDNShouldBeReported() {
        String creationId = "creation-1";
        // Missing actionMode
        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"setMessages\", {\"sendMDN\": {" +
                "\"" + creationId + "\":{" +
                "    \"messageId\":\"" + randomMessageId().serialize() + "\"," +
                "    \"subject\":\"subject\"," +
                "    \"textBody\":\"textBody\"," +
                "    \"reportingUA\":\"reportingUA\"," +
                "    \"disposition\":{" +
                "        \"actionMode\":\"invalid\","+
                "        \"sendingMode\":\"" + DispositionSendingMode.Automatic.getValue() + "\","+
                "        \"type\":\"" + DispositionType.Processed.getValue() + "\""+
                "    }" +
                "}" +
                "}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("error"))
            .body(ARGUMENTS + ".type", is("invalidArguments"))
            .body(ARGUMENTS + ".description", containsString("Unrecognized MDN Disposition action mode invalid. Should be one of [manual-action, automatic-action]"));
    }

}
