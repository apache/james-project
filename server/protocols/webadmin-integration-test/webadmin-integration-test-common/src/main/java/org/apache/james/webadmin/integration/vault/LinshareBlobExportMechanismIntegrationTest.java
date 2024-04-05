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

package org.apache.james.webadmin.integration.vault;

import static io.restassured.RestAssured.with;
import static io.restassured.http.ContentType.JSON;
import static org.apache.james.jmap.JMAPTestingConstants.DOMAIN;
import static org.apache.james.jmap.JMAPTestingConstants.LOCALHOST_IP;
import static org.apache.james.jmap.JMAPTestingConstants.calmlyAwait;
import static org.apache.james.jmap.JMAPTestingConstants.jmapRequestSpecBuilder;
import static org.apache.james.jmap.JmapRFCCommonRequests.UserCredential;
import static org.apache.james.jmap.JmapRFCCommonRequests.deleteMessages;
import static org.apache.james.jmap.JmapRFCCommonRequests.getUserCredential;
import static org.apache.james.jmap.JmapRFCCommonRequests.listMessageIdsForAccount;
import static org.apache.james.linshare.LinshareExtension.LinshareAPIForTechnicalAccountTesting;
import static org.apache.james.linshare.LinshareExtension.LinshareAPIForUserTesting;
import static org.apache.james.linshare.LinshareFixture.MATCH_ALL_QUERY;
import static org.apache.james.linshare.LinshareFixture.USER_1;
import static org.apache.james.mailbox.backup.ZipAssert.assertThatZip;
import static org.apache.james.webadmin.integration.vault.DeletedMessagesVaultRequests.exportVaultContent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.TEN_SECONDS;
import static org.hamcrest.Matchers.notNullValue;

import java.io.ByteArrayInputStream;
import java.util.List;

import org.apache.james.GuiceJamesServer;
import org.apache.james.core.Username;
import org.apache.james.jmap.JmapGuiceProbe;
import org.apache.james.jmap.JmapRFCCommonRequests;
import org.apache.james.linshare.client.Document;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.mailbox.backup.ZipAssert;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.probe.MailboxProbe;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.util.Port;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.TestIMAPClient;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.awaitility.core.ConditionFactory;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.restassured.RestAssured;
import io.restassured.parsing.Parser;
import io.restassured.specification.RequestSpecification;

public abstract class LinshareBlobExportMechanismIntegrationTest {

    private static final String HOMER = "homer@" + DOMAIN;
    private static final String BART = "bart@" + DOMAIN;
    private static final String HOMER_PASSWORD = "homerPassword";
    private static final String BART_PASSWORD = "bartPassword";
    private static final ConditionFactory WAIT_TEN_SECONDS = calmlyAwait.atMost(TEN_SECONDS);
    private static final String SUBJECT = "This mail will be restored from the vault!!";
    private static final ExportRequest EXPORT_ALL_HOMER_MESSAGES_TO_USER_1 = ExportRequest
        .userExportFrom(HOMER)
        .exportTo(USER_1.getUsername())
        .query(MATCH_ALL_QUERY);

    @RegisterExtension
    TestIMAPClient testIMAPClient = new TestIMAPClient();

    private UserCredential homerCredential;
    private UserCredential bartCredential;

    private GuiceJamesServer jmapServer;
    private RequestSpecification webAdminApi;
    private LinshareAPIForUserTesting user1LinshareAPI;

    @BeforeEach
    void setup(GuiceJamesServer jmapServer) throws Throwable {
        this.jmapServer = jmapServer;

        jmapServer.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(DOMAIN)
            .addUser(HOMER, HOMER_PASSWORD)
            .addUser(BART, BART_PASSWORD);

        Port jmapPort = jmapServer.getProbe(JmapGuiceProbe.class).getJmapPort();
        RestAssured.requestSpecification = jmapRequestSpecBuilder
            .setPort(jmapPort.getValue())
            .build();
        RestAssured.defaultParser = Parser.JSON;

        webAdminApi = WebAdminUtils.spec(jmapServer.getProbe(WebAdminGuiceProbe.class).getWebAdminPort());

        MailboxProbe mailboxProbe = jmapServer.getProbe(MailboxProbeImpl.class);
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, HOMER, DefaultMailboxes.INBOX);

        homerCredential = getUserCredential(Username.of(HOMER), HOMER_PASSWORD);
        bartCredential = getUserCredential(Username.of(BART), BART_PASSWORD);

        user1LinshareAPI = LinshareAPIForUserTesting.from(USER_1);
    }

    @Test
    void exportShouldShareTheDocumentViaLinshareWhenJmapDelete() {
        bartSendMessageToHomer();

        WAIT_TEN_SECONDS.until(() -> listMessageIdsForAccount(homerCredential).size() == 1);

        homerDeletesMessages(listMessageIdsForAccount(homerCredential));
        WAIT_TEN_SECONDS.until(() -> listMessageIdsForAccount(homerCredential).isEmpty());

        exportVaultContent(webAdminApi, EXPORT_ALL_HOMER_MESSAGES_TO_USER_1);

        assertThat(user1LinshareAPI.listAllDocuments())
            .hasSize(1)
            .allSatisfy(uploadedDoc -> assertThat(uploadedDoc.getName()).endsWith(".zip"));
    }

    @Test
    void exportShouldShareTheDocumentViaLinshareWhenImapDelete() throws Exception {
        bartSendMessageToHomer();

        WAIT_TEN_SECONDS.until(() -> listMessageIdsForAccount(homerCredential).size() == 1);

        testIMAPClient.connect(LOCALHOST_IP, jmapServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(HOMER, HOMER_PASSWORD)
            .select(TestIMAPClient.INBOX)
            .setFlagsForAllMessagesInMailbox("\\Deleted");
        testIMAPClient.expunge();

        WAIT_TEN_SECONDS.until(() -> listMessageIdsForAccount(homerCredential).isEmpty());

        exportVaultContent(webAdminApi, EXPORT_ALL_HOMER_MESSAGES_TO_USER_1);

        assertThat(user1LinshareAPI.listAllDocuments())
            .hasSize(1)
            .allSatisfy(uploadedDoc -> assertThat(uploadedDoc.getName()).endsWith(".zip"));
    }

    @Test
    void exportShouldShareNonEmptyZipViaLinshareWhenJmapDelete(LinshareAPIForTechnicalAccountTesting linshareAPIForTechnicalAccountTesting) throws Exception {
        bartSendMessageToHomer();

        WAIT_TEN_SECONDS.until(() -> listMessageIdsForAccount(homerCredential).size() == 1);

        homerDeletesMessages(listMessageIdsForAccount(homerCredential));
        WAIT_TEN_SECONDS.until(() -> listMessageIdsForAccount(homerCredential).isEmpty());

        exportVaultContent(webAdminApi, EXPORT_ALL_HOMER_MESSAGES_TO_USER_1);

        Document sharedDoc = user1LinshareAPI.listAllDocuments().get(0);
        byte[] sharedFile = linshareAPIForTechnicalAccountTesting
            .downloadFileFrom(USER_1, sharedDoc.getId());

        try (ZipAssert zipAssert = assertThatZip(new ByteArrayInputStream(sharedFile))) {
            zipAssert.hasEntriesSize(1);
        }
    }

    @Test
    void exportShouldShareNonEmptyZipViaLinshareWhenImapDelete(LinshareAPIForTechnicalAccountTesting linshareAPIForTechnicalAccountTesting) throws Exception {
        bartSendMessageToHomer();

        WAIT_TEN_SECONDS.until(() -> listMessageIdsForAccount(homerCredential).size() == 1);

        testIMAPClient.connect(LOCALHOST_IP, jmapServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(HOMER, HOMER_PASSWORD)
            .select(TestIMAPClient.INBOX)
            .setFlagsForAllMessagesInMailbox("\\Deleted");
        testIMAPClient.expunge();

        WAIT_TEN_SECONDS.until(() -> listMessageIdsForAccount(homerCredential).isEmpty());

        exportVaultContent(webAdminApi, EXPORT_ALL_HOMER_MESSAGES_TO_USER_1);

        Document sharedDoc = user1LinshareAPI.listAllDocuments().get(0);
        byte[] sharedFile = linshareAPIForTechnicalAccountTesting
            .downloadFileFrom(USER_1, sharedDoc.getId());

        try (ZipAssert zipAssert = assertThatZip(new ByteArrayInputStream(sharedFile))) {
            zipAssert.hasEntriesSize(1);
        }
    }

    private void bartSendMessageToHomer() {
        String outboxId = JmapRFCCommonRequests.getOutboxId(bartCredential);
        String requestBody =
            "{" +
                "    \"using\": [\"urn:ietf:params:jmap:core\", \"urn:ietf:params:jmap:mail\", \"urn:ietf:params:jmap:submission\"]," +
                "    \"methodCalls\": [" +
                "        [\"Email/set\", {" +
                "            \"accountId\": \"" + bartCredential.accountId() + "\"," +
                "            \"create\": {" +
                "                \"e1526\": {" +
                "                    \"mailboxIds\": { \"" + outboxId + "\": true }," +
                "                    \"subject\": \"" + SUBJECT + "\"," +
                "                    \"htmlBody\": [{" +
                "                        \"partId\": \"a49d\"," +
                "                        \"type\": \"text/html\"" +
                "                    }]," +
                "                    \"bodyValues\": {" +
                "                        \"a49d\": {" +
                "                            \"value\": \"Test <b>body</b>, HTML version\"" +
                "                        }" +
                "                    }," +
                "                    \"to\": [{\"email\": \"" + HOMER + "\"}]," +
                "                    \"from\": [{\"email\": \"" + BART + "\"}]" +
                "                }" +
                "            }" +
                "        }, \"c1\"]," +
                "        [\"Email/get\", {" +
                "            \"accountId\": \"" + bartCredential.accountId() + "\"," +
                "            \"ids\": [\"#e1526\"]," +
                "            \"properties\": [\"sentAt\"]" +
                "        }, \"c2\"]," +
                "        [\"EmailSubmission/set\", {" +
                "            \"accountId\": \"" + bartCredential.accountId() + "\"," +
                "            \"create\": {" +
                "                \"k1490\": {" +
                "                    \"emailId\": \"#e1526\"," +
                "                    \"envelope\": {" +
                "                        \"mailFrom\": {\"email\": \"" + BART + "\"}," +
                "                        \"rcptTo\": [{\"email\": \"" + HOMER + "\"}]" +
                "                    }" +
                "                }" +
                "            }" +
                "        }, \"c3\"]" +
                "    ]" +
                "}";

        with()
            .auth().basic(bartCredential.username().asString(), bartCredential.password())
            .body(requestBody)
            .post("/jmap")
            .then()
            .statusCode(200)
            .contentType(JSON)
            .body("methodResponses[2][1].created", Matchers.is(notNullValue()));
    }

    private void homerDeletesMessages(List<String> idsToDestroy) {
        deleteMessages(homerCredential, idsToDestroy);
    }
}
