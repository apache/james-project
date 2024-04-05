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

import static io.restassured.RestAssured.given;
import static org.apache.james.jmap.JMAPTestingConstants.BOB;
import static org.apache.james.jmap.JMAPTestingConstants.BOB_PASSWORD;
import static org.apache.james.jmap.JMAPTestingConstants.CEDRIC;
import static org.apache.james.jmap.JMAPTestingConstants.CEDRIC_PASSWORD;
import static org.apache.james.jmap.JMAPTestingConstants.DOMAIN;
import static org.apache.james.jmap.JMAPTestingConstants.jmapRequestSpecBuilder;
import static org.hamcrest.Matchers.containsInAnyOrder;

import java.nio.charset.StandardCharsets;
import java.util.stream.IntStream;

import org.apache.james.GuiceJamesServer;
import org.apache.james.jmap.JmapGuiceProbe;
import org.apache.james.junit.categories.BasicFeature;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.modules.ACLProbeImpl;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.probe.DataProbe;
import org.apache.james.util.Port;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.google.common.hash.Hashing;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

public abstract class QuotaSearchIntegrationTest {
    private RequestSpecification webAdminApi;
    private Port jmapPort;

    protected abstract void awaitSearchUpToDate() throws Exception;

    @BeforeEach
    void setUp(GuiceJamesServer jmapServer) throws Exception {
        DataProbe dataProbe = jmapServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DOMAIN);
        dataProbe.addUser(BOB.asString(), BOB_PASSWORD);
        dataProbe.addUser(CEDRIC.asString(), CEDRIC_PASSWORD);

        jmapPort = jmapServer.getProbe(JmapGuiceProbe.class).getJmapPort();
        RestAssured.requestSpecification = jmapRequestSpecBuilder
            .setPort(jmapPort.getValue())
            .build();

        webAdminApi = WebAdminUtils.spec(jmapServer.getProbe(WebAdminGuiceProbe.class).getWebAdminPort());
    }

    @Tag(BasicFeature.TAG)
    @Test
    public void quotaSearchShouldPlayWellWithDelegation(GuiceJamesServer server) throws Exception {
        // GIVEN two users with 0/20 mails quota
        webAdminApi.body("20")
            .put("/quota/users/" + BOB.asString() + "/count");
        webAdminApi.body("20")
            .put("/quota/users/" + CEDRIC.asString() + "/count");
        // AND CEDRIC delegates his mailbox to BOB
        MailboxId mailboxId = server.getProbe(MailboxProbeImpl.class).createMailbox(MailboxPath.inbox(CEDRIC));
        server.getProbe(ACLProbeImpl.class)
            .replaceRights(MailboxPath.inbox(CEDRIC), BOB.asString(), MailboxACL.FULL_RIGHTS);

        // WHEN BOB adds 19 message to CEDRIC mailbox
        String accountId = Hashing.sha256().hashString(BOB.asString(), StandardCharsets.UTF_8).toString();
        String requestBody = "{" +
            "  \"using\": [\"urn:ietf:params:jmap:core\", \"urn:ietf:params:jmap:mail\"]," +
            "  \"methodCalls\": [" +
            "    [\"Email/set\", " +
            "      {" +
            "         \"accountId\": \"" + accountId + "\"," +
            "         \"create\": {" +
            "           \"aaaaaa\":{ " +
            "             \"mailboxIds\": {\"" + mailboxId.serialize() + "\": true}" +
            "           }" +
            "         }" +
            "       }, " +
            "     \"c1\"" +
            "   ]" +
            " ]" +
            "}";
        IntStream.range(0, 19)
            .forEach(i -> given()
                .auth().basic(BOB.asString(), BOB_PASSWORD)
                .contentType(ContentType.JSON)
                .accept("application/json; jmapVersion=rfc-8621")
                .body(requestBody)
                .post("/jmap"));
        awaitSearchUpToDate();

        // THEN we expect CEDRIC to be reported with a 95% quota ration by quota search
        webAdminApi
            .param("minOccupationRatio", 0.9)
        .when()
            .get("/quota/users")
        .then()
            .body("username", containsInAnyOrder(CEDRIC.asString()));
    }
}
