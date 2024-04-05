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

package org.apache.james.jmap.draft.methods.integration;

import static io.restassured.RestAssured.given;
import static io.restassured.config.EncoderConfig.encoderConfig;
import static io.restassured.config.RestAssuredConfig.newConfig;
import static org.apache.james.jmap.HttpJmapAuthentication.authenticateJamesUser;
import static org.apache.james.jmap.JMAPTestingConstants.ARGUMENTS;
import static org.apache.james.jmap.JMAPTestingConstants.NAME;
import static org.apache.james.jmap.JMAPTestingConstants.calmlyAwait;
import static org.apache.james.jmap.JmapCommonRequests.bodyOfMessage;
import static org.apache.james.jmap.JmapCommonRequests.getLatestMessageId;
import static org.apache.james.jmap.JmapCommonRequests.getOutboxId;
import static org.apache.james.jmap.JmapCommonRequests.receiversOfMessage;
import static org.apache.james.jmap.JmapURIBuilder.baseUri;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.FIVE_HUNDRED_MILLISECONDS;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.collection.IsMapWithSize.aMapWithSize;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.apache.james.GuiceJamesServer;
import org.apache.james.core.Username;
import org.apache.james.jmap.AccessToken;
import org.apache.james.jmap.JmapGuiceProbe;
import org.apache.james.mailbox.Role;
import org.apache.james.mailbox.probe.MailboxProbe;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.probe.DataProbe;
import org.apache.james.utils.DataProbeImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.parsing.Parser;

public abstract class SetMessagesMethodReRoutingTest {
    private static final String PASSWORD = "password";

    private static final String DESTINATION_DOMAIN = "domain1.com";
    private static final String ALIAS_DOMAIN = "domain2.com";
    private static final String RECEIVER_AT_DESTINATION_DOMAIN = "user@domain1.com";
    private static final String RECEIVER_AT_ALIAS_DOMAIN = "user@domain2.com";
    private static final String SENDER_AT_DESTINATION_DOMAIN = "sender@domain1.com";

    private AccessToken receiverAtDestinationDomainToken;
    private AccessToken senderAtDestinationDomainToken;

    protected abstract GuiceJamesServer createJmapServer() throws IOException;

    private GuiceJamesServer jmapServer;
    private MailboxProbe mailboxProbe;
    private DataProbe dataProbe;

    @Before
    public void setup() throws Throwable {
        jmapServer = createJmapServer();
        jmapServer.start();
        mailboxProbe = jmapServer.getProbe(MailboxProbeImpl.class);
        dataProbe = jmapServer.getProbe(DataProbeImpl.class);

        RestAssured.requestSpecification = new RequestSpecBuilder()
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .setConfig(newConfig().encoderConfig(encoderConfig().defaultContentCharset(StandardCharsets.UTF_8)))
                .setPort(jmapServer.getProbe(JmapGuiceProbe.class).getJmapPort().getValue())
                .build();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
        RestAssured.defaultParser = Parser.JSON;

        dataProbe.addDomain(DESTINATION_DOMAIN);
        dataProbe.addDomain(ALIAS_DOMAIN);
        dataProbe.addUser(RECEIVER_AT_DESTINATION_DOMAIN, PASSWORD);
        dataProbe.addUser(SENDER_AT_DESTINATION_DOMAIN, PASSWORD);
        receiverAtDestinationDomainToken = authenticateJamesUser(baseUri(jmapServer), Username.of(RECEIVER_AT_DESTINATION_DOMAIN), PASSWORD);
        senderAtDestinationDomainToken = authenticateJamesUser(baseUri(jmapServer), Username.of(SENDER_AT_DESTINATION_DOMAIN), PASSWORD);
    }

    @After
    public void teardown() {
        jmapServer.stop();
    }

    @Test // MAILET-136
    public void sendShouldReRouteMailToDestinationAddressWhenDomainAliasMapping() throws Exception {
        dataProbe.addDomainAliasMapping(ALIAS_DOMAIN, DESTINATION_DOMAIN);

        String messageContent = "content content";
        String sendMessageBody =
            "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"creationId1337\" : {" +
            "        \"from\": { \"name\": \"Sender\", \"email\": \"" + SENDER_AT_DESTINATION_DOMAIN + "\"}," +
            "        \"to\": [{ \"name\": \"User\", \"email\": \"" + RECEIVER_AT_ALIAS_DOMAIN + "\"}]," +
            "        \"subject\": \"Thank you for joining example.com!\"," +
            "        \"textBody\": \"" + messageContent + "\"," +
            "        \"mailboxIds\": [\"" + getOutboxId(senderAtDestinationDomainToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";
        given()
            .header("Authorization", senderAtDestinationDomainToken.asString())
            .body(sendMessageBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(ARGUMENTS + ".notCreated", aMapWithSize(0))
            .body(ARGUMENTS + ".created", aMapWithSize(1));

        calmlyAwait
            .pollDelay(FIVE_HUNDRED_MILLISECONDS)
            .atMost(30, TimeUnit.SECONDS)
            .untilAsserted(() ->
                assertThat(getLatestMessageId(receiverAtDestinationDomainToken, Role.INBOX))
                    .isNotNull());

        String inboxMessageId = getLatestMessageId(receiverAtDestinationDomainToken, Role.INBOX);
        assertThat(bodyOfMessage(receiverAtDestinationDomainToken, inboxMessageId))
            .isEqualTo(messageContent);
    }

    @Test // MAILET-136
    public void sendShouldNotCreateNewUserOrMailboxOfAliasAddressWhenDomainAliasMapping() throws Exception {
        dataProbe.addDomainAliasMapping(ALIAS_DOMAIN, DESTINATION_DOMAIN);

        String messageContent = "content content";
        String sendMessageBody =
            "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"creationId1337\" : {" +
            "        \"from\": { \"name\": \"Sender\", \"email\": \"" + SENDER_AT_DESTINATION_DOMAIN + "\"}," +
            "        \"to\": [{ \"name\": \"User\", \"email\": \"" + RECEIVER_AT_ALIAS_DOMAIN + "\"}]," +
            "        \"subject\": \"Thank you for joining example.com!\"," +
            "        \"textBody\": \"" + messageContent + "\"," +
            "        \"mailboxIds\": [\"" + getOutboxId(senderAtDestinationDomainToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";
        given()
            .header("Authorization", senderAtDestinationDomainToken.asString())
            .body(sendMessageBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(ARGUMENTS + ".notCreated", aMapWithSize(0))
            .body(ARGUMENTS + ".created", aMapWithSize(1));

        calmlyAwait
            .pollDelay(FIVE_HUNDRED_MILLISECONDS)
            .atMost(30, TimeUnit.SECONDS)
            .untilAsserted(() ->
                assertThat(getLatestMessageId(receiverAtDestinationDomainToken, Role.INBOX))
                    .isNotNull());

        assertThat(dataProbe.listUsers())
            .doesNotContain("user@domain2.com");
        assertThat(mailboxProbe.listUserMailboxes("user@domain2.com"))
            .isEmpty();
    }


    @Test // MAILET-136
    public void sendShouldSaveToAsTheAliasAddressWhenDomainAliasMapping() throws Exception {
        dataProbe.addDomainAliasMapping(ALIAS_DOMAIN, DESTINATION_DOMAIN);

        String messageContent = "content content";
        String sendMessageBody =
            "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"creationId1337\" : {" +
            "        \"from\": { \"name\": \"Sender\", \"email\": \"" + SENDER_AT_DESTINATION_DOMAIN + "\"}," +
            "        \"to\": [{ \"name\": \"User\", \"email\": \"" + RECEIVER_AT_ALIAS_DOMAIN + "\"}]," +
            "        \"subject\": \"Thank you for joining example.com!\"," +
            "        \"textBody\": \"" + messageContent + "\"," +
            "        \"mailboxIds\": [\"" + getOutboxId(senderAtDestinationDomainToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";
        given()
            .header("Authorization", senderAtDestinationDomainToken.asString())
            .body(sendMessageBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(ARGUMENTS + ".notCreated", aMapWithSize(0))
            .body(ARGUMENTS + ".created", aMapWithSize(1));

        calmlyAwait
            .pollDelay(FIVE_HUNDRED_MILLISECONDS)
            .atMost(30, TimeUnit.SECONDS)
            .untilAsserted(() ->
                assertThat(getLatestMessageId(senderAtDestinationDomainToken, Role.SENT))
                    .isNotNull());

        String sentMessageId = getLatestMessageId(senderAtDestinationDomainToken, Role.SENT);
        assertThat(receiversOfMessage(senderAtDestinationDomainToken, sentMessageId))
            .containsOnly(RECEIVER_AT_ALIAS_DOMAIN);
    }
}
