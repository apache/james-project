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

package org.apache.james.webadmin.integration;

import static io.restassured.RestAssured.with;
import static org.apache.james.jmap.JMAPTestingConstants.ALICE;
import static org.apache.james.jmap.JMAPTestingConstants.ALICE_PASSWORD;
import static org.apache.james.jmap.JMAPTestingConstants.BOB;
import static org.apache.james.jmap.JMAPTestingConstants.BOB_PASSWORD;
import static org.apache.james.jmap.JMAPTestingConstants.CEDRIC;
import static org.apache.james.jmap.JMAPTestingConstants.CEDRIC_PASSWORD;
import static org.apache.james.jmap.JMAPTestingConstants.DOMAIN;
import static org.apache.james.jmap.JMAPTestingConstants.jmapRequestSpecBuilder;
import static org.apache.james.jmap.JmapRFCCommonRequests.ACCEPT_JMAP_RFC_HEADER;
import static org.apache.james.jmap.JmapRFCCommonRequests.UserCredential;
import static org.apache.james.jmap.JmapRFCCommonRequests.getUserCredential;
import static org.apache.james.jmap.JmapRFCCommonRequests.listMessageIdsForAccount;
import static org.apache.james.webadmin.integration.TestFixture.WAIT_THIRTY_SECONDS;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.hamcrest.Matchers.notNullValue;

import java.io.IOException;

import org.apache.james.GuiceJamesServer;
import org.apache.james.jmap.JmapGuiceProbe;
import org.apache.james.jmap.JmapRFCCommonRequests;
import org.apache.james.junit.categories.BasicFeature;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.mailbox.probe.MailboxProbe;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.probe.DataProbe;
import org.apache.james.util.Port;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

public abstract class ForwardIntegrationTest {

    private SMTPMessageSender messageSender;
    private RequestSpecification webAdminApi;
    private Port jmapPort;

    @BeforeEach
    void setUp(GuiceJamesServer jmapServer) throws Exception {
        MailboxProbe mailboxProbe = jmapServer.getProbe(MailboxProbeImpl.class);

        messageSender = new SMTPMessageSender(DOMAIN);
        DataProbe dataProbe = jmapServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DOMAIN);
        dataProbe.addUser(BOB.asString(), BOB_PASSWORD);
        dataProbe.addUser(ALICE.asString(), ALICE_PASSWORD);
        dataProbe.addUser(CEDRIC.asString(), CEDRIC_PASSWORD);
        mailboxProbe.createMailbox("#private", ALICE.asString(), DefaultMailboxes.INBOX);
        mailboxProbe.createMailbox("#private", CEDRIC.asString(), DefaultMailboxes.INBOX);

        jmapPort = jmapServer.getProbe(JmapGuiceProbe.class).getJmapPort();
        RestAssured.requestSpecification = jmapRequestSpecBuilder
            .setPort(jmapPort.getValue())
            .addHeader(ACCEPT_JMAP_RFC_HEADER.getName(), ACCEPT_JMAP_RFC_HEADER.getValue())
            .build();

        webAdminApi = WebAdminUtils.spec(jmapServer.getProbe(WebAdminGuiceProbe.class).getWebAdminPort());

    }

    @AfterEach
    void tearDown() throws IOException {
        messageSender.close();
    }

    @Tag(BasicFeature.TAG)
    @Test
    void messageShouldBeForwardedWhenDefinedInRESTAPI() {
        webAdminApi.put(String.format("/address/forwards/%s/targets/%s", ALICE.asString(), BOB.asString()));

        cedricSendAnEmailToAlice();

        UserCredential bobCredential = getUserCredential(BOB, BOB_PASSWORD);
        WAIT_THIRTY_SECONDS.untilAsserted(() -> assertThat(listMessageIdsForAccount(bobCredential))
                .hasSize(1));
    }

    @Test
    void messageShouldBeForwardedWhenBaseRecipientWhenInDestination() {
        webAdminApi.put(String.format("/address/forwards/%s/targets/%s", ALICE.asString(), BOB.asString()));
        webAdminApi.put(String.format("/address/forwards/%s/targets/%s", ALICE.asString(), ALICE.asString()));

        cedricSendAnEmailToAlice();

        UserCredential bobCredential = getUserCredential(BOB, BOB_PASSWORD);
        WAIT_THIRTY_SECONDS.untilAsserted(() -> assertThat(listMessageIdsForAccount(bobCredential))
                .hasSize(1));

        UserCredential aliceCredential = getUserCredential(BOB, BOB_PASSWORD);
        WAIT_THIRTY_SECONDS.untilAsserted(() -> assertThat(listMessageIdsForAccount(aliceCredential))
                .hasSize(1));
    }

    @Test
    void smtpMessageShouldBeForwardedWhenBaseRecipientWhenInDestination(GuiceJamesServer jmapServer) throws Exception {
        webAdminApi.put(String.format("/address/forwards/%s/targets/%s", ALICE.asString(), BOB.asString()));
        webAdminApi.put(String.format("/address/forwards/%s/targets/%s", ALICE.asString(), ALICE.asString()));

        messageSender
            .connect("127.0.0.1", jmapServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage("cedric@other.com", ALICE.asString());

        UserCredential bobCredential = getUserCredential(BOB, BOB_PASSWORD);
        WAIT_THIRTY_SECONDS.untilAsserted(() -> assertThat(listMessageIdsForAccount(bobCredential))
            .hasSize(1));

        UserCredential aliceCredential = getUserCredential(BOB, BOB_PASSWORD);
        WAIT_THIRTY_SECONDS.untilAsserted(() -> assertThat(listMessageIdsForAccount(aliceCredential))
            .hasSize(1));
    }

    @Test
    void recursiveForwardShouldWork() {
        webAdminApi.put(String.format("/address/forwards/%s/targets/%s", ALICE.asString(), CEDRIC.asString()));
        webAdminApi.put(String.format("/address/forwards/%s/targets/%s", CEDRIC.asString(), BOB.asString()));

        cedricSendAnEmailToAlice();

        UserCredential bobCredential = getUserCredential(BOB, BOB_PASSWORD);
        WAIT_THIRTY_SECONDS.untilAsserted(() -> assertThat(listMessageIdsForAccount(bobCredential))
            .hasSize(1));
    }

    @Test
    void recursiveWithRecipientCopyForwardShouldWork() {
        webAdminApi.put(String.format("/address/forwards/%s/targets/%s", ALICE.asString(), ALICE.asString()));
        webAdminApi.put(String.format("/address/forwards/%s/targets/%s", ALICE.asString(), BOB.asString()));
        webAdminApi.put(String.format("/address/forwards/%s/targets/%s", BOB.asString(), CEDRIC.asString()));

        cedricSendAnEmailToAlice();

        UserCredential aliceCredential = getUserCredential(ALICE, ALICE_PASSWORD);
        WAIT_THIRTY_SECONDS.untilAsserted(() -> assertThat(listMessageIdsForAccount(aliceCredential))
            .hasSize(1));
    }

    @Test
    void baseRecipientShouldNotReceiveEmailOnDefaultForward() {
        webAdminApi.put(String.format("/address/forwards/%s/targets/%s", ALICE.asString(), BOB.asString()));

        cedricSendAnEmailToAlice();

        UserCredential bobCredential = getUserCredential(BOB, BOB_PASSWORD);
        WAIT_THIRTY_SECONDS.untilAsserted(() -> assertThat(listMessageIdsForAccount(bobCredential))
            .hasSize(1));

        UserCredential aliceCredential = getUserCredential(ALICE, ALICE_PASSWORD);
        assertThat(listMessageIdsForAccount(aliceCredential))
            .hasSize(0);
    }

    private void cedricSendAnEmailToAlice() {
        UserCredential cedricCredential = getUserCredential(CEDRIC, CEDRIC_PASSWORD);
        String cedricOutboxId = JmapRFCCommonRequests.getOutboxId(cedricCredential);
        String request =
            "{" +
                "    \"using\": [\"urn:ietf:params:jmap:core\", \"urn:ietf:params:jmap:mail\", \"urn:ietf:params:jmap:submission\"]," +
                "    \"methodCalls\": [" +
                "        [\"Email/set\", {" +
                "            \"accountId\": \"" + cedricCredential.accountId() + "\"," +
                "            \"create\": {" +
                "                \"e1526\": {" +
                "                    \"mailboxIds\": { \"" + cedricOutboxId + "\": true }," +
                "                    \"subject\": \"subject\"," +
                "                    \"htmlBody\": [{" +
                "                        \"partId\": \"a49d\"," +
                "                        \"type\": \"text/html\"" +
                "                    }]," +
                "                    \"bodyValues\": {" +
                "                        \"a49d\": {" +
                "                            \"value\": \"Test <b>body</b>, HTML version\"" +
                "                        }" +
                "                    }," +
                "                    \"to\": [{\"email\": \"" + ALICE.asString() + "\"}]," +
                "                    \"from\": [{\"email\": \"" + CEDRIC.asString() + "\"}]" +
                "                }" +
                "            }" +
                "        }, \"c1\"]," +
                "        [\"EmailSubmission/set\", {" +
                "             \"accountId\": \"" + cedricCredential.accountId() + "\"," +
                "             \"create\": {" +
                "                 \"k1490\": {" +
                "                     \"emailId\": \"#e1526\"," +
                "                     \"envelope\": {" +
                "                         \"mailFrom\": {\"email\": \"" + CEDRIC.asString() + "\"}," +
                "                         \"rcptTo\": [{\"email\": \"" + ALICE.asString() + "\"}]" +
                "                     }" +
                "                 }" +
                "             }" +
                "         }, \"c3\"]" +
                "    ]" +
                "}";

        with()
            .auth().basic(cedricCredential.username().asString(), cedricCredential.password())
            .body(request)
            .post("/jmap")
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("methodResponses[0][1].created.e1526", Matchers.is(notNullValue()));
    }

}
