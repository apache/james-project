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
import static io.restassured.RestAssured.with;
import static org.apache.james.jmap.HttpJmapAuthentication.authenticateJamesUser;
import static org.apache.james.jmap.JMAPTestingConstants.ARGUMENTS;
import static org.apache.james.jmap.JMAPTestingConstants.DOMAIN;
import static org.apache.james.jmap.JMAPTestingConstants.calmlyAwait;
import static org.apache.james.jmap.JMAPTestingConstants.jmapRequestSpecBuilder;
import static org.apache.james.jmap.JmapCommonRequests.concatMessageIds;
import static org.apache.james.jmap.JmapCommonRequests.getOutboxId;
import static org.apache.james.jmap.JmapCommonRequests.listMessageIdsForAccount;
import static org.apache.james.jmap.JmapURIBuilder.baseUri;
import static org.awaitility.Durations.TWO_MINUTES;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;

import java.io.IOException;
import java.util.List;

import org.apache.james.GuiceJamesServer;
import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.jmap.AccessToken;
import org.apache.james.jmap.JmapGuiceProbe;
import org.apache.james.junit.categories.BasicFeature;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.probe.MailboxProbe;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.modules.QuotaProbesImpl;
import org.apache.james.probe.DataProbe;
import org.apache.james.utils.DataProbeImpl;
import org.awaitility.core.ConditionFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.google.common.base.Strings;

import io.restassured.RestAssured;
import io.restassured.parsing.Parser;

public abstract class QuotaMailingTest {
    private static final Username HOMER = Username.of("homer@" + DOMAIN);
    private static final Username BART = Username.of("bart@" + DOMAIN);
    private static final String PASSWORD = "password";
    private static final String BOB_PASSWORD = "bobPassword";
    private static final ConditionFactory WAIT_TWO_MINUTES = calmlyAwait.atMost(TWO_MINUTES);
    private QuotaProbesImpl quotaProbe;

    protected abstract GuiceJamesServer createJmapServer() throws IOException;

    private AccessToken homerAccessToken;
    private AccessToken bartAccessToken;
    private GuiceJamesServer jmapServer;

    @Before
    public void setup() throws Throwable {
        jmapServer = createJmapServer();
        jmapServer.start();
        MailboxProbe mailboxProbe = jmapServer.getProbe(MailboxProbeImpl.class);
        DataProbe dataProbe = jmapServer.getProbe(DataProbeImpl.class);
        quotaProbe = jmapServer.getProbe(QuotaProbesImpl.class);

        RestAssured.requestSpecification = jmapRequestSpecBuilder
            .setPort(jmapServer.getProbe(JmapGuiceProbe.class).getJmapPort().getValue())
            .build();
        RestAssured.defaultParser = Parser.JSON;

        dataProbe.addDomain(DOMAIN);
        dataProbe.addUser(HOMER.asString(), PASSWORD);
        dataProbe.addUser(BART.asString(), BOB_PASSWORD);
        mailboxProbe.createMailbox("#private", HOMER.asString(), DefaultMailboxes.INBOX);
        homerAccessToken = authenticateJamesUser(baseUri(jmapServer), HOMER, PASSWORD);
        bartAccessToken = authenticateJamesUser(baseUri(jmapServer), BART, BOB_PASSWORD);
    }

    @After
    public void tearDown() {
        jmapServer.stop();
    }

    @Category(BasicFeature.class)
    @Test
    public void shouldSendANoticeWhenThresholdExceeded() throws Exception {
        quotaProbe.setMaxStorage(quotaProbe.getQuotaRoot(MailboxPath.inbox(HOMER)), QuotaSizeLimit.size(100 * 1000));

        bartSendMessageToHomer();
        // Homer receives a mail big enough to trigger a configured threshold

        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 2);

        List<String> ids = listMessageIdsForAccount(homerAccessToken);
        String idString = concatMessageIds(ids);

        given()
            .header("Authorization", homerAccessToken.asString())
            .body("[[\"getMessages\", {\"ids\": [" + idString + "]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .log().ifValidationFails()
            .body(ARGUMENTS + ".list.subject",
                hasItem("Warning: Your email usage just exceeded a configured threshold"));
    }

    @Test
    public void configurationShouldBeWellLoaded() throws Exception {
        quotaProbe.setMaxStorage(quotaProbe.getQuotaRoot(MailboxPath.inbox(HOMER)), QuotaSizeLimit.size(100 * 1000));

        bartSendMessageToHomer();
        // Homer receives a mail big enough to trigger a 10% configured threshold
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 2);

        bartSendMessageToHomer();
        // Homer receives a mail big enough to trigger a 20% configured threshold
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 4);

        List<String> ids = listMessageIdsForAccount(homerAccessToken);
        String idString = concatMessageIds(ids);

        given()
            .header("Authorization", homerAccessToken.asString())
            .body("[[\"getMessages\", {\"ids\": [" + idString + "]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .log().ifValidationFails()
            .body(ARGUMENTS + ".list.textBody",
                hasItem(containsString("You currently occupy more than 10 % of the total size allocated to you")))
            .body(ARGUMENTS + ".list.textBody",
                hasItem(containsString("You currently occupy more than 20 % of the total size allocated to you")));
    }

    private void bartSendMessageToHomer() {
        String messageCreationId = "creationId";
        String outboxId = getOutboxId(bartAccessToken);
        String bigEnoughBody = Strings.repeat("123456789\n", 12 * 100);
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"headers\":{\"Disposition-Notification-To\":\"" + BART.asString() + "\"}," +
            "        \"from\": { \"name\": \"Bob\", \"email\": \"" + BART.asString() + "\"}," +
            "        \"to\": [{ \"name\": \"User\", \"email\": \"" + HOMER.asString() + "\"}]," +
            "        \"subject\": \"Message without an attachment\"," +
            "        \"textBody\": \"" + bigEnoughBody + "\"," +
            "        \"htmlBody\": \"Test <b>body</b>, HTML version\"," +
            "        \"mailboxIds\": [\"" + outboxId + "\"] " +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        with()
            .header("Authorization", bartAccessToken.asString())
            .body(requestBody)
            .post("/jmap")
        .then()
            .extract()
            .body()
            .path(ARGUMENTS + ".created." + messageCreationId + ".id");
    }
}
